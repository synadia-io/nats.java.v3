# Request Behavior Improvement (advanced-only, `MessageResult` + boundary-throw)

> Standalone v3 redesign of request/response behavior — **not** an upstream-PR integration. It borrows only the five-reason *classification* and the cancel/last-error semantics from `nats-io/nats.java` PR #1582; the opt-in option and the carrier `Message` are discarded. Tracked on its own, not in `INTEGRATION_PLAN_OVERVIEW.md` / the `todo.md` PR table.

> **Premise (decided with the maintainer).** Advanced request behavior is the *only* behavior, the legacy `null`/generic-timeout path is removed, and since v3 is a new major version the `request`/`publish` API contracts change and propagate to JetStream. Design refined over the original throw-only sketch into a **hybrid**:
>
> - **Methods that return a message-or-`null` today** (core blocking `request(...)`) → return a **`MessageResult`** (reply **or** classified failure). No `null`, no `instanceof`-on-`Message`, no forced `try/catch`.
> - **The async `requestAsync(...)`** → returns **`CompletableFuture<MessageResult>`** (the failure is adapted from the future's existing exceptional completion via a single `.handle(...)` — no rework of the delivery/cleanup machinery).
> - **Methods that already throw `IOException`** (JetStream request paths, `ackSync` once widened) → **throw `RequestFailureException`** (extends `IOException`), minted by converting a `MessageResult` failure *at that boundary*.
>
> One classifier produces one failure value object (`RequestFailure`); it is either returned (in a `MessageResult`) or wrapped (in a `RequestFailureException`). Upstream #1582 supplies only the five-reason classification + the cancel/last-error semantics to read them from.

## Motivation from the field — jnats V2 issue #1596

Added 2026-08-13 from `z-claude-done/ISSUE_1596_REVIEW.md`. A real reported failure that this plan is the fix for, worth keeping attached because it is the concrete answer to "why bother".

A user created an ordered consumer, the create timed out, and they got an exception that had nothing to do with the real problem. The underlying cause was a **connection close racing an in-flight consumer create**. V3 has the same race: `NatsConnection.close` invalidates every subscription (`:876`), then cancels the in-flight requests (`:890`), and only then sets the status to `CLOSED` (`:896`) — so throughout that window `isClosed()` is `false` and every closed-connection guard stays silent. The request is cancelled, and what surfaces is a timeout.

**The point for this plan is what the user can tell afterwards, which today is nothing.** V3 throws a dedicated `JetStreamTimeoutException` rather than V2's generic `IOException("Timeout or no response waiting for NATS JetStream server")` — a better *type*, but it carries no reason. Faced with it, a caller cannot distinguish:

* the connection was closing underneath the call (retrying is pointless, the connection is gone),
* the server was genuinely slow or unreachable (retrying is exactly right),
* or a permissions error arrived as a `-ERR` during the request.

Those want opposite responses, and the exception gives no basis for choosing. **`RequestFailureReason.CONNECTION_CLOSING` is precisely the missing field** — with it, the close-race diagnosis is a single value the user reads off the exception instead of an investigation.

V2 has an answer here and V3 does not: upstream added `advancedRequestBehavior()` in 2.26.0, and the advice to the reporter of #1596 was to enable it and read the reason. V3 never ported PR #1582 — this plan supersedes it — so until this lands, V3 is strictly behind V2 on diagnosing a failure both clients can produce.

Two related findings from that review are already fixed and are *not* waiting on this plan: the ordered-consumer reset no longer dies silently on an unchecked exception, and a consumer created for a subscribe that then fails is now deleted rather than orphaned (`0537c485`, `c47bb0d7`). What remains unfixed is only the diagnosability gap above.

## Target flow

```
                       NatsConnection.requestFuture(...)  ── raw NatsRequestCompletableFuture (unchanged machinery)
                                  │
        ┌─────────────────────────┼──────────────────────────┐
   blocking request()        requestAsync()              (internal use)
   → MessageResult           → CompletableFuture<MessageResult>
        │                         │  via inner.handle((msg,ex) -> Reply | classify(...))
        │   get(timeout)/catch    │
        │   classify(...)         │
        └──────────┬──────────────┘
                   ▼
            MessageResult = Reply(message) | RequestFailure(reason,status,lastError,cause)
                   │
   ┌───────────────┼────────────────────────────────┐
 Discovery     JetStream makeRequestResponseRequired   ackSync
 messageOrNull   getMessageOrThrow() → throws           getMessageOrThrow() → throws
 → null on fail  RequestFailureException (IOException)   RequestFailureException (IOException)
```

The **get-timeout is classified** (maintainer decision): if the blocking `get(timeout)` deadline fires before the inner future completes, that `TimeoutException` is mapped to `RequestFailure(TIMEOUT)` — so blocking and async agree on the reason regardless of which deadline wins.

## The types (4 new files, package `io.synadia.client`)

1. **`RequestFailureReason`** — enum `TIMEOUT, CONNECTION_CLOSING, NO_RESPONDERS, SERVER_ERROR, CANCELLED` (verbatim from upstream, with the per-constant javadoc).

2. **`RequestFailure`** — the classification value object, and the failure arm of `MessageResult`:
   ```java
   package io.synadia.client;
   import org.jspecify.annotations.Nullable;

   public record RequestFailure(RequestFailureReason reason,
                                ConnectionStatus connectionStatus,
                                @Nullable String lastError,
                                @Nullable Throwable cause) implements MessageResult {}
   ```
   (`ConnectionStatus`/`RequestFailureReason` are same-package — no imports.)

3. **`MessageResult`** — sealed result with a nested `Reply` and convenience accessors so callers don't have to pattern-match unless they want to:
   ```java
   package io.synadia.client;
   import io.synadia.client.Message;
   import org.jspecify.annotations.Nullable;

   public sealed interface MessageResult permits MessageResult.Reply, RequestFailure {
       record Reply(Message message) implements MessageResult {}

       default boolean isReply()   { return this instanceof Reply; }
       default boolean isFailure() { return this instanceof RequestFailure; }

       /** The reply message, or throw the classified failure (used at throwing boundaries). */
       default Message getMessageOrThrow() throws RequestFailureException {
           if (this instanceof RequestFailure f) throw new RequestFailureException(f);
           return ((Reply) this).message();
       }

       /** The reply message, or null on failure (used by the Discovery-style "did I get a reply?" callers). */
       default @Nullable Message messageOrNull() {
           return this instanceof Reply r ? r.message() : null;
       }
   }
   ```
   Pattern-match form for callers that want the reason:
   ```java
   switch (nc.request(...)) {
       case MessageResult.Reply r -> use(r.message());
       case RequestFailure f      -> handle(f.reason());
   }
   ```

4. **`RequestFailureException`** — `extends IOException`, wraps a `RequestFailure` (no duplicated fields):
   ```java
   package io.synadia.client;
   import org.jspecify.annotations.Nullable;
   import java.io.IOException;

   public class RequestFailureException extends IOException {
       @Serial
       private static final long serialVersionUID = 1L;
       private final RequestFailure failure;
       public RequestFailureException(RequestFailure failure) {
           super(buildMessage(failure), failure.cause());
           this.failure = failure;
       }
       public RequestFailure getFailure() { return failure; }
       public RequestFailureReason getReason() { return failure.reason(); }
       public ConnectionStatus getConnectionStatus() { return failure.connectionStatus(); }
       public @Nullable String getLastError() { return failure.lastError(); }
       private static String buildMessage(RequestFailure f) {
           StringBuilder sb = new StringBuilder("Request failed [reason=").append(f.reason())
               .append(", connectionStatus=").append(f.connectionStatus());
           if (f.lastError() != null && !f.lastError().isEmpty()) sb.append(", lastError=").append(f.lastError());
           return sb.append(']').toString();
       }
   }
   ```

> No `RequestFailureMessage` (the carrier), no Options option/property/builder/getter. All of that is out of scope.

## v2 → v3 mapping (kept parts)

| v2 | v3 | Note |
|---|---|---|
| `Connection` interface | concrete `io.synadia.client.impl.NatsConnection` | no interface to update |
| `Connection.Status` | top-level `ConnectionStatus` (returned by `getStatus()`) | |
| `wasCancelledClosing()/…TimedOut()/getCancelAction()` | single-l `wasCanceledClosing()/getCancelAction()` on `utils.NatsRequestCompletableFuture` (`CancelAction = CANCEL, REPORT, COMPLETE`); `wasCanceledTimedOut()` is **removed** (see Companion cleanup) | classification inputs |
| `requestInternal` (blocking) | `NatsConnection.request(...)` `:1355` | becomes `MessageResult` |
| `requestFutureInternal` | the innermost `requestAsync(...)` that builds the future (`:1471-1508`) | extract as `requestFuture(...)` returning `NatsRequestCompletableFuture` |
| `responseRequired` | `JetStreamImpl.responseRequired` `:209` (callers `:192,:201`) | delete; use `getMessageOrThrow()` |
| `ackSync` | core `JetStreamMessage.ackSync` `:37` (+ `Message` iface `:116`, `NatsMessage` `:282`) | `throws IOException`. Takes `long timeoutMillis`, not `Duration` — see Step 8 |
| service `Discovery` | `service/.../Discovery.java:174` | `messageOrNull()` |
| `emptyAsNull` | `utils.Validator.emptyAsNull:358` | last-error hygiene |

## Step-by-step (tree compiles after each step)

### Step 1–4 — create the four types
`RequestFailureReason`, `RequestFailure`, `MessageResult` (+ nested `Reply`), `RequestFailureException` as above, in `core/src/main/java/io/synadia/client/`. (Match the header convention of existing v3 public types — check `AuthenticationException.java`.)

### Step 5 — `NatsConnection`: last-error `""` → `null` hygiene
`SERVER_ERROR` fires only when a *new* `-ERR` arrives during the request, detected by `getLastError()` changing. So "no error" must be `null`, not `""`.
- `processError(errorText)` (~`:2010`): `String err = Validator.emptyAsNull(errorText); this.lastError.set(err); this.connectError.set(err);` (keep `isAuthenticationError(errorText)` on the raw text).
- `lastError.set("")` (~`:212`) and any `connectError.set("")` in the connect/reconnect loop → `set(null)`.
- Pre-check: grep `getLastError()`/`lastError.get()`/`connectError` for `.isEmpty()`/`.equals("")`/unguarded deref. v3 `getLastError()` is already `@Nullable` and `clearLastError()` already sets `null`.

### Companion cleanup — make `useTimeoutException` the only behavior, and drop `wasCanceledTimedOut`

Same spirit as the rest of this redesign: a setting that should just *be* the behavior. `useTimeoutException` only chooses, inside `NatsRequestCompletableFuture.cancelTimedOut()`, between completing a timed-out request with `TimeoutException` (informative) vs `CancellationException` (legacy). Make `TimeoutException` the only outcome and delete the option. This also makes a timeout *unambiguous* (always `TimeoutException`, never `CancellationException`), which is why the classifier above no longer needs `wasCanceledTimedOut` — so that flag/getter is removed too. (`wasCanceledClosing` stays — the classifier needs it.)

Touchpoints to remove:
- `core/.../utils/NatsRequestCompletableFuture.java`: drop the `useTimeoutException` field/ctor-param/getter; `cancelTimedOut()` → `completeExceptionally(new TimeoutException(CANCEL_MESSAGE));` unconditionally; **also** drop the `wasCanceledTimedOut` field + getter (and stop setting it in `cancelTimedOut`). New ctor: `NatsRequestCompletableFuture(CancelAction cancelAction, @Nullable Duration timeout)`.
- `core/.../impl/NatsConnection.java:1507-1508`: drop the trailing `options.useTimeoutException()` argument (now 2-arg + cancelAction). (This is the same `requestFuture(...)` builder from Step 6a — fold the change in there.)
- `core/.../client/Options.java`: remove the `useTimeoutException` field (`:94`), ctor assign (`:250`), getter (`:900`).
- `core/.../client/OptionsBuilder.java`: remove the field (`:98`), the `PROP_USE_TIMEOUT_EXCEPTION` parse (`:246`), the `useTimeoutException()` setter (`:1124`), the copy-ctor entry (`:1390`).
- `core/.../client/OptionsProperties.java`: remove `PROP_USE_TIMEOUT_EXCEPTION` (`:250`).
- `core/src/test/java/io/synadia/client/impl/RequestTests.java` (`:535-578`): drop the boolean ctor arg; remove the `useTimeoutException()` / `wasCanceledTimedOut()` assertions; the former `false`-case tests (`cancelTimedOut()` → `CancellationException`) now expect a `TimeoutException`.

Note: in the **current** blocking path both `TimeoutException` and `CancellationException` are caught and collapse to the same result, so this is observable only to **async** future consumers (a timed-out future now always completes with `TimeoutException`) — which is the intended improvement and exactly what the new `requestAsync`/classifier rely on. Production reads of `wasCanceledTimedOut()`/`wasCanceledClosing()` are **zero today** (only `RequestTests`); the classifier in Step 6b is the first/only production consumer of `wasCanceledClosing()`.

### Step 6 — `NatsConnection`: future + classifier + the two request entry points
6a. **Extract the raw future.** Rename/retype the innermost future-building `requestAsync(subject, headers, data, futureTimeout, cancelAction, flush)` (`:1471-1508`) to:
```java
protected NatsRequestCompletableFuture requestFuture(String subject, Headers headers, byte[] data,
        Duration futureTimeout, CancelAction cancelAction, boolean flush) { /* existing body */ }
```
(returns the concrete future; this is the single place that builds it.)

6b. **One classifier** (private):
```java
private RequestFailure classify(NatsRequestCompletableFuture future, @Nullable Throwable cause, @Nullable String serverError) {
    ConnectionStatus status = getStatus();
    RequestFailureReason reason;
    if (future.wasCanceledClosing() || status == ConnectionStatus.RECONNECTING || status == ConnectionStatus.DISCONNECTED)
        reason = RequestFailureReason.CONNECTION_CLOSING;
    else if (serverError != null)
        reason = RequestFailureReason.SERVER_ERROR;
    else if (cause instanceof CancellationException)   // a timeout is now always a TimeoutException (see Companion cleanup), never CancellationException
        reason = future.getCancelAction() == CancelAction.CANCEL
            ? RequestFailureReason.NO_RESPONDERS : RequestFailureReason.CANCELLED;
    else
        reason = RequestFailureReason.TIMEOUT;   // includes the get()-timeout (maintainer decision)
    return new RequestFailure(reason, status, serverError, cause);
}
private @Nullable String serverErrorSince(@Nullable String before) {
    String now = getLastError();
    return (now != null && !now.equals(before)) ? now : null;
}
private static @Nullable Throwable unwrap(@Nullable Throwable ex) {     // handle() may hand a CompletionException
    return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
}
```

**Gap found 2026-09-02 — as drafted, `NO_RESPONDERS` is unreachable on the default path.** The only arm that produces it requires `cause instanceof CancellationException` *and* `getCancelAction() == CANCEL`, but `CANCEL` is the one action the library never selects: `REPORT` is the default on every public overload (`NatsConnection.java:1418, 1436, 1457, 1521, 1536, 1551, 1567, 1586, 1604`) and on `JetStreamImpl:201`. Trace a 503 through `deliverReply` (`:1685`) per action:

| cancelAction | 503 becomes | classified as | correct? |
|---|---|---|---|
| `REPORT` (default everywhere) | `completeExceptionally(StatusException)` → `ExecutionException` cause | falls past every arm to the final `else` → **`TIMEOUT`** | no |
| `COMPLETE` (`JetStreamImpl:205`, `JetStream:480`) | `complete(msg)` with the 503 status message | `msg != null` → **`MessageResult.Reply`**, and `getMessageOrThrow()` hands a status message back as a reply | no |
| `CANCEL` (never selected internally) | `cancel(true)` → `CancellationException` | `NO_RESPONDERS` | yes |

Fix: add a `StatusException` arm keyed on code 503 **ahead of** the `CancellationException` arm, and check for a 503 status message on the `Reply` path so `COMPLETE` cannot pass one off as a response. Once that is in, `NO_RESPONDERS` no longer depends on `CancelAction` at all — **which feeds the `CANCEL_ACTION_REVISIT.md` decision**: that doc's lean is "keep `CANCEL`, though the library never selects it", and this fix strengthens the *remove* case. Leave it unfixed and any internal caller needing `NO_RESPONDERS` has to pass `CANCEL`, making it the first production consumer of the value that doc calls unused. **Decide the two together.** (That doc also cites `deliverReply` at `:1528`; it is at `:1685` now.)

### First internal consumer: a real `Service.isStarted`

Designed 2026-09-02, and the reason the gap above was found. `Service.isStarted(long, TimeUnit)` (`Service.java:373`) claims to report readiness but only waits on a future that `startService` completes synchronously four lines after creating it (`:195`, `:203`) — so it always returns true immediately and proves nothing about the server. The replacement does a round trip to the service's own `$SRV.PING.<name>.<id>` (that endpoint is already registered, `Service.java:146`), which doubles as a marker in the connection's byte stream: the SUBs were queued first, the server processes one connection in order, so a response proves every subscription is registered *and* that the dispatcher is delivering.

That check is precisely the caller this plan is for — it has to tell "not yet, keep waiting" from "never, stop":

| result | readiness check does |
|---|---|
| `Reply` | started → true |
| `NO_RESPONDERS` / `TIMEOUT` | not ready yet → retry to the deadline |
| `CONNECTION_CLOSING` | false now; retrying is pointless |
| `SERVER_ERROR` | surface it — a permissions denial on `$SRV.>` currently looks identical to a slow start and burns the whole timeout before returning a bare `false` |

Without the classification it has to treat every failure as "retry", which is the same dead end as `ackSync`.

6c. **Public async** — all `requestAsync(...)` overloads change to `CompletableFuture<MessageResult>`; the 6-arg one adapts the future:
```java
public CompletableFuture<MessageResult> requestAsync(String subject, Headers headers, byte[] data,
        Duration timeout, CancelAction cancelAction, boolean flush) {
    String lastErrorBefore = getLastError();
    NatsRequestCompletableFuture inner = requestFuture(subject, headers, data, timeout, cancelAction, flush);
    return inner.handle((msg, ex) -> msg != null
        ? new MessageResult.Reply(msg)
        : classify(inner, unwrap(ex), serverErrorSince(lastErrorBefore)));
}
```
(the thin 2/3/4/5-arg overloads now return `CompletableFuture<MessageResult>` and delegate here.)

6d. **Blocking** `request(...)` (`:1355`) → returns `MessageResult`, still `throws InterruptedException` only (no `IOException` — failures are *returned*):
```java
public MessageResult request(String subject, Headers headers, byte[] data,
        Duration timeout, CancelAction cancelAction, boolean flush) throws InterruptedException {
    String lastErrorBefore = getLastError();
    NatsRequestCompletableFuture inner = requestFuture(subject, headers, data, timeout, cancelAction, flush);
    if (timeout == null) timeout = getOptions().getConnectionTimeout();
    try {
        return new MessageResult.Reply(inner.get(timeout.toNanos(), TimeUnit.NANOSECONDS));
    }
    catch (TimeoutException | CancellationException e) {
        return classify(inner, e, serverErrorSince(lastErrorBefore));   // get-timeout classified → TIMEOUT
    }
    catch (ExecutionException e) {
        return classify(inner, unwrap(e.getCause()), serverErrorSince(lastErrorBefore));
    }
}
```
Update the thin `request(Message,Duration)` (`:1344`) and 5-arg (`:1350`) overloads to return `MessageResult` and drop their `@Nullable`.

### Step 7 — JetStream boundary (throw here)
`jetstream/.../impl/JetStreamImpl.java` `:192,:201`:
```java
return conn.request(prependPrefix(subject), bytes, timeout).getMessageOrThrow();
return conn.request(subject, headers, data, timeout, cancelAction).getMessageOrThrow();
```
**Delete `responseRequired`** (`:209`) — dead. Methods already declare `throws IOException`; `getMessageOrThrow()` throws `RequestFailureException` (an `IOException`), so callers are unchanged in signature, richer in detail. The async publish path (`publishAsyncInternal`) now does `requestAsync(...).thenApply(MessageResult::getMessageOrThrow).thenCompose(...)` — so **async JetStream publish also surfaces `RequestFailureException`** (the future completes exceptionally), closing the sync/async asymmetry upstream left open.

### Step 8 — `ackSync` → `throws IOException` (the todo)

**Signatures below were refreshed 2026-09-02** — they were written against the `Duration` API and the client moved to straight millis in `9a04792e`. Current: `void ackSync(long timeoutMillis) throws TimeoutException, InterruptedException`.

**Why this one matters more than its size suggests — it is the worked example of the anti-pattern.** Today `JetStreamMessage.ackSync` (`:37-45`) is:

```java
public void ackSync(long timeoutMillis) throws InterruptedException, TimeoutException {
    if (ackHasntBeenTermed()) {
        NatsConnection nc = getJetStreamValidatedConnection();
        if (nc.request(replyTo, AckAck.bytes, timeoutMillis) == null) {
            throw new TimeoutException("Ack response timed out.");
        }
        lastAck = AckAck;
    }
}
```

A null response is converted straight into `TimeoutException("Ack response timed out.")`, so no responders, a connection closing underneath the call, a 503 and a genuine timeout all surface as the same message, naming only the last of the four. **This is exactly what a user writing their own request code is forced to do**, because `request(...)` returning `@Nullable Message` gives them nothing else to branch on — which is the argument for this whole plan, in eight lines of our own code. Found 2026-09-02 while writing an `ackSync` test.

- `Message.java:116`: `void ackSync(long timeoutMillis) throws IOException, InterruptedException;` (drop now-unthrown `TimeoutException` — Open #2).
- `JetStreamMessage.java:37`:
  ```java
  public void ackSync(long timeoutMillis) throws IOException, InterruptedException {
      if (ackHasntBeenTermed()) {
          NatsConnection nc = getJetStreamValidatedConnection();
          nc.request(replyTo, AckAck.bytes, timeoutMillis).getMessageOrThrow();   // classified, not "timed out"
          lastAck = AckAck;
      }
  }
  ```
- `NatsMessage.java:282`: widen its `ackSync` `throws` to match the interface.

### Step 9 — service `Discovery`
`discoverOne` (`:171-185`):
```java
try {
    Message m = conn.request(subject, null, Duration.ofNanos(maxTimeNanos)).messageOrNull();
    if (m != null) return m.getData();
}
catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
return null;
```
No new import beyond `MessageResult` being implicit via the `request(...)` return — `messageOrNull()` keeps the old "null on no responder" contract with no `try/catch` for the failure.

### Step 10 — tests
10a. Caller/fallout grep:
```bash
grep -rn "\.request(" core jetstream service --include=*.java       # now returns MessageResult / CompletableFuture<MessageResult>
grep -rn "requestAsync(" core jetstream service --include=*.java    # now CompletableFuture<MessageResult>
grep -rn "Timeout or no response" core jetstream --include=*.java    # gone
grep -rnE "request\([^;]*\)\s*==\s*null|assertNull\([^)]*request"
```
Internal consumers are only `ackSync`, `JetStreamImpl`, `Discovery` (handled) + the JetStream async publish (`thenApply(getMessageOrThrow)`).
10b. New `core/src/test/java/io/synadia/client/impl/AdvancedRequestBehaviorTests.java` (always-on): unsubscribed subject → `request(...)` returns `RequestFailure` with `reason==NO_RESPONDERS`; slow/absent responder → `TIMEOUT`; JetStream call with no server → `RequestFailureException` (an `IOException`); async (`requestAsync`/JetStream `publishAsync`) completes exceptionally / returns a `RequestFailure`; connection-closing → `CONNECTION_CLOSING` (racy — generous timeouts). Adapt to v3 harness (`runInServer`/`NatsTestServer`/`standardConnection`).
10c. Any existing test doing `assertNull(nc.request(...))` for a no-response → assert a `RequestFailure`/`getReason()`; any catching the old generic JS message → assert the classified one.

### Step 11 — verification (no gradle here)
Grep the new symbols; confirm `requestAsync` covariant/return-type change compiles for all overloads; confirm the last-error `""`→`null` grep is clean; confirm `MessageResult.Reply` message is non-null and `RequestFailure` carries the four fields.

## API changes catalog (intended breaks)

- `NatsConnection.request(...)` → returns **`MessageResult`** (was `@Nullable Message`); still `throws InterruptedException` only. No `null`, no `IOException` from this method.
- `NatsConnection.requestAsync(...)` → returns **`CompletableFuture<MessageResult>`** (was `CompletableFuture<Message>`).
- `Message.ackSync(Duration)` (+ impls) → `throws IOException` (drops `TimeoutException`).
- JetStream blocking + async request paths → surface a classified **`RequestFailureException`** (still an `IOException`; existing `catch (IOException)` works) instead of the generic "Timeout or no response" / silent async exception.
- `Discovery` public contract unchanged (null on no responder).

## Open decisions

1. **Type layout / names.** `MessageResult` (sealed) with nested `Reply` + top-level shared `RequestFailure`; `RequestFailureException` wraps `RequestFailure`. Adjust names (`Reply` vs `MessageReply`, nest `RequestFailure` too) to taste.
2. **`ackSync` keeping `TimeoutException`** — recommend dropping it (folded into `RequestFailureException`); keep only if you don't want to touch callers that catch it.
3. **`CancelAction` fidelity** — only `CANCEL` yields `NO_RESPONDERS`; confirm the JetStream/core paths pass the cancelAction the tests expect (503→`CANCEL`→`NO_RESPONDERS`).
4. **Header convention** on the 4 new public types — match existing v3 public types.

## Risks

- **Biggest blast radius: the `request`/`requestAsync` return-type changes.** Every caller (core/jetstream/service/tests + user code) adapts. Internal set is small and enumerated; the rest is a deliberate major-version break.
- **`requestAsync` return-type change** ripples to `publishAsyncInternal` and any direct caller — handled via `thenApply(getMessageOrThrow)`; grep for others.
- **Last-error `""`→`null`** touches the connect/reconnect hot path — grep for unguarded `getLastError()` derefs.
- **Dual-timeout** now explicitly classified (get-timeout → `TIMEOUT`); make sure the inner future's own timeout and the `get(timeout)` deadline don't double-log/contend confusingly — both end in `RequestFailure(TIMEOUT)` so the observable reason is stable.
- **Racy** `CONNECTION_CLOSING` integration test — generous timeouts.

## Size estimate

- 4 new files (~30 + ~10 + ~35 + ~30 LOC).
- `NatsConnection` ~50 LOC (extract `requestFuture`, classifier + helpers, `requestAsync`→`MessageResult` via `handle`, blocking `request`→`MessageResult`, last-error hygiene).
- ~15 LOC across `JetStreamImpl` (delete `responseRequired`, `getMessageOrThrow`, async `thenApply`), `ackSync` trio, `Discovery`.
- 1 new integration test (~150 LOC) + caller/test fixups from Step 10.
- **Companion cleanup** (`useTimeoutException` + `wasCanceledTimedOut` removal): net deletion across `NatsRequestCompletableFuture` / `Options` / `OptionsBuilder` / `OptionsProperties` / `NatsConnection` + `RequestTests` fixups (~−40 LOC, one fewer option).
- Net: no Options plumbing, no carrier, one *fewer* existing option; the cost is the `request`/`requestAsync` return-type change — the deliberate, ergonomic major-version break.

Also adds to the **API changes catalog**: the `useTimeoutException` option (builder method + `PROP_USE_TIMEOUT_EXCEPTION` + `Options.useTimeoutException()` getter) is **removed** — a timed-out request future now always completes with `TimeoutException`.
