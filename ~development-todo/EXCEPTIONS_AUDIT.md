# Exceptions Audit — API Surface & Consolidation Options

Audit of the checked-exception surface on `NatsConnection`, `JetStream`, `JetStreamManagement`, `StreamContext`, `ConsumerContext` (+ the KV/OS code that rides on them), and a proposal set for consolidating behind a `JetStreamException` base.

## 1. Current exception inventory

| Type | Package | Extends | Checked? |
|---|---|---|---|
| `AuthenticationException` | `io.synadia.client` | `IOException` | yes |
| `StatusException` | `io.synadia.client.impl` | `IllegalStateException` | **no** |
| `JetStreamApiException` | `io.synadia.client.impl` | `Exception` | yes |
| `JetStreamStatusException` | `io.synadia.client.impl` | `StatusException` | **no** |
| `JetStreamStatusCheckedException` | `io.synadia.client.impl` | `Exception` | yes |

Two structural oddities before we even get to the signatures:

- **Every JetStream exception lives in `impl`.** These are types users must `catch` by name and reference in their own `throws` clauses. They are public API sitting in a package whose name tells users not to touch it. `AuthenticationException` is in `io.synadia.client`, so the convention already exists and JetStream diverges from it.
- **`JetStreamStatusCheckedException` exists only to launder a checked exception out of an unchecked one.** It has no state, no accessors — its entire body is `super(cause)`. Its reason for existing is that `JetStreamStatusException` was made unchecked (via `StatusException extends IllegalStateException`) and some call paths needed it checked. That is a workaround for a decision, not a design.

## 2. The headline number

**252 method signatures across `jetstream/src/main` declare `throws IOException, JetStreamApiException`.**

| File | Count |
|---|---|
| `JetStream` | 44 |
| `JetStreamManagement` | 36 |
| `kv/KeyValue` | 33 |
| `NatsStreamContext` | 22 |
| `StreamContext` | 21 |
| `os/ObjectStore` | 16 |
| `NatsConsumerContext` | 14 |
| `BaseConsumerContext` / `NatsOrderedConsumerContext` | 12 each |
| (18 more files) | 42 |

The worst signature on the surface is `BaseConsumerContext.next()`, which declares four:

```java
Message next() throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException;
```

## 3. The core finding: `IOException` on the JetStream surface is a lie

I traced every `throw new IOException` in `jetstream/src/main` and the connection methods feeding it. **None of them is an I/O error.** `NatsConnection.request(...)` — the only transport call the JetStream layer makes — declares `throws InterruptedException` and nothing else. There is no real `IOException` to propagate, so the layer manufactures one at every point it wants to fail:

| Site | Message / cause | What it actually is |
|---|---|---|
| `JetStreamImpl:183`, `:192` | `new IOException(InterruptedException)` | interruption, re-flagged then reboxed |
| `JetStreamImpl:198` | `"Timeout or no response waiting for NATS JetStream server"` | timeout |
| `JetStream:342` | `"Error Publishing: " + resp.getStatus()...` | **a status error** — belongs with `JetStreamStatusException` |
| `PublishAck:40`, `:45` | `"Invalid JetStream ack."` | protocol/parse error |
| `NatsConsumerContext:103` | `"The ordered consumer is already receiving messages..."` | **`IllegalStateException`** |
| `NatsConsumerContext:110` | `"Pinned not allowed with " + label` | **`IllegalArgumentException`** |
| `kv/KeyValue:35` | `new IOException(JetStreamApiException)` | an API error, wrapped |
| `NatsConnection:2618` | `"A JetStream context can't be established during close."` | **`IllegalStateException`** |

So `throws IOException` on 252 methods is not an I/O channel — it is an undifferentiated "something went wrong" channel that seven unrelated failure modes were poured into because it was already in the signature. Three observations follow:

**`IOException` is already doing the job you're proposing `JetStreamException` should do** — badly, under a name that actively misleads. Users catching `IOException` expecting a socket problem get argument validation errors.

**Two of these are programming errors reported as checked exceptions.** `NatsConsumerContext:103` and `:110` are state and argument bugs. A caller cannot recover from "Pinned not allowed" at runtime — they have to change their code. Forcing a `catch` there is pure noise, and it is noise the compiler makes mandatory.

**`kv/KeyValue:35` carries a comment that dates the whole design:**

```java
} catch (JetStreamApiException e) {
    // can't throw directly, that would be a breaking change
    throw new IOException(e);
}
```

That constraint is V2's. V3 is the major version where it does not apply. This is the moment to fix it or accept it for another major cycle.

## 3a. Measured: how many `throws IOException` are vestigial? Exactly one.

The obvious follow-up to §3 is "if the `IOException`s are fake, can we just delete the declarations?" I measured it rather than guessed, in a throwaway copy of the repo.

**Method.** Strip `throws IOException` from every declaration in `jetstream/src/main` (276 of them), then loop: compile, and add it back *only* where javac reports `unreported exception IOException` or an impl/interface mismatch. Repeat to a green build. Whatever javac never demands back was vestigial.

**A trap worth recording:** javac reveals these errors *progressively*. It halts flow analysis once a batch of errors is found, so the first compile after stripping all 276 reported only **2** errors — which looks like "274 were vestigial" and is completely wrong. Each fix exposes a new layer. The loop took 15+ rounds to converge. Any future attempt to eyeball this from one compile will reach a badly wrong conclusion.

**Result: 276 declarations in → 277 proved required at convergence.** Starting from zero and adding back only what the compiler *proves* is necessary reconstructs essentially the same set that is already there. (277 vs 276 is slight over-adding in my interface-propagation step, which matched overloads by name; the true minimum may be marginally under 276, not materially.)

Exactly one genuinely vestigial declaration exists in the whole module:

```java
// ObjectStore.java:36 — super(bucketName, existing) resolves to
// AbstractBucketFeature(String, AbstractBucketFeature), which does NOT throw
private ObjectStore(String bucketName, ObjectStore existing) throws IOException {
```

**This is the important finding, and it inverts the premise.** The `throws IOException` clauses are not dead weight that accumulated by copy-paste — they are *honest propagation*. `IOException` really is thrown underneath essentially everywhere, because `makeRequestResponseRequired` / `responseRequired` really do throw it on every request. The declarations are correct; what's wrong is the *root sites* in §3, which chose `IOException` to represent a timeout, an interruption, a status error, and two argument bugs.

So there is no cleanup available that doesn't change behavior. **`IOException` cannot be reduced by editing signatures — only by retargeting the 8 root throw sites**, and that requires picking what they throw instead, which is the §5/§6 decision. The two are the same task, not sequential ones.

## 4. Interruption is handled two different ways

`JetStreamImpl:183` catches `InterruptedException`, re-sets the interrupt flag, and reboxes it as `IOException` — while `BaseConsumerContext.next()` declares `throws InterruptedException` directly. Same library, same layer, two opposite conventions. Whatever else changes, this should land on one.

## 4a. Measured: A5 (`JetStreamImpl:183`/`:192`) cannot be fixed in isolation

I built A5 end-to-end in a throwaway copy to find out what it costs. The edit itself is the *nicest* one in this document — propagating instead of reboxing deletes the try/catch outright:

```java
// before
Message makeRequestResponseRequired(String subject, byte @Nullable[] bytes, long timeoutMillis) throws IOException {
    try {
        return responseRequired(conn.request(prependPrefix(subject), null, bytes, timeoutMillis, CancelAction.REPORT, conn.isForceFlushOnRequest()));
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
    }
}

// after
Message makeRequestResponseRequired(String subject, byte @Nullable[] bytes, long timeoutMillis) throws IOException, InterruptedException {
    return responseRequired(conn.request(prependPrefix(subject), null, bytes, timeoutMillis, CancelAction.REPORT, conn.isForceFlushOnRequest()));
}
```

Then I drove the cascade to a green build. **`InterruptedException` declarations go from 55 to 281 (+226).** Every public JetStream method picks up a *third* checked exception:

```java
public StreamInfo getStreamInfo(String streamName) throws InterruptedException, IOException, JetStreamApiException
```

**That is worse than today, and it is the whole reason A5 is blocked.** The goal is fewer exceptions per signature; done alone, A5 takes 2 → 3. It only pays off *combined* with A10/A14, where `IOException + JetStreamApiException` collapse into `JetStreamException` and the result lands at `throws JetStreamException, InterruptedException` — 2, not 3. A5 is not a standalone step under any option; do not land it by itself.

### The structural discovery: the `IOException` wrapper is load-bearing

The cascade does not stop at the public API. It runs into a hard wall in the pull-consumer callback path:

`MessageManager.configureIdleHeartbeat` schedules a timer lambda (`MessageManager.java:141-146`) that calls `handleHeartbeatError()` → `PullMessageManager:49` → `PullManagerObserver.pullTerminatedByError()` → `NatsMessageConsumer:118`, which calls `doSub(false)` — **a JetStream request issued from a timer thread**. A `Runnable` cannot throw `InterruptedException`, and there is no caller to receive it. The propagation has nowhere to go.

Today that path compiles only because of this:

```java
// NatsMessageConsumer:127 — currently catches the *interrupt* too, via the IOException wrapper
catch (JetStreamApiException | IOException e) {
    resetOnException();
}
```

So the §3 wrapper is not merely cosmetic mislabeling — **it is structurally load-bearing here.** Any fix must add an explicit `InterruptedException` handler at this boundary; the callback interface must stay non-throwing.

### A latent bug this exposed (independent of everything else)

Because the interrupt arrives disguised as `IOException`, an interrupted auto-repull is today indistinguishable from an I/O failure and falls into `resetOnException()`, which does:

```java
fullResetPending();
pmm.updateLastMessageReceived();
pmm.initOrResetHeartbeatTimer();   // consumer restarts and keeps going
```

The consumer resets its heartbeat timer and continues, while `Thread.currentThread().interrupt()` has left the interrupt flag set on the thread that ran the callback — a pooled timer thread, not a user thread. Nothing on this path checks the flag. Worth a look on its own merits: interruption during repull is silently absorbed as a retryable IO error, and a pooled thread is left flagged. I have not tried to characterize what that does to later tasks on that thread, so treat this as "worth investigating", not a confirmed defect.

## 4b. Where interruption actually comes from — and a third mislabeling, in core

Worth writing down because it is not obvious: **futures do not generate `InterruptedException`. Parking does.** `CompletableFuture` never produces it on its own; Java's cooperative cancellation makes any parked thread interruptible, because throwing `InterruptedException` is the JVM's only way to unblock one.

The API proves it. `requestAsync` (`NatsConnection:1547`) is the real primitive and declares no `InterruptedException` — validate, register the response future, publish, return. `request` is just `requestAsync` + `incoming.get(getMillis, MILLISECONDS)` (`:1428`). The interruption is entirely attributable to the *synchronous wrapper*, not to NATS or to futures. Same on the publish side: `publishAsync` throws nothing; only sync `publish` does.

A synchronous request has **two** interruptible waits:

1. **`WriterMessageQueue.push`** — `editLock.tryLock(pushTimeoutNanos, ...)` (`:42`) and `queue.offer(msg, timeoutNanosLeft, ...)` (`:59`). Backpressure on the bounded outgoing queue.
2. **`NatsConnection.request:1428`** — `incoming.get(...)`, awaiting the reply. This is the one §3/§4a is about.

**Core already swallows #1, and mislabels it a third way:**

```java
// WriterMessageQueue:74-77
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return false;                      // indistinguishable from "queue was full"
}
```

`queueOutgoing` (`NatsConnection:1996`) sees `false` and fires `errorListener.messageDiscarded(this, msg)`. So an interrupt while publishing surfaces to the user's error listener as a **discarded message** — in core, not JetStream.

For a request it is worse, because `requestAsync` never learns the publish failed. Reading `:1582-1589` in order: `responsesAwaiting.put(...)` → `incrementOutstandingRequests()` → `_publish(...)` (silently discards) → `incrementRequestsSent()` (records a send that never happened) → returns a future that can never complete, left for the request-cleanup timer to reap. `get()` then throws `InterruptedException` immediately anyway, since the flag is set.

**The same mistake now appears at three layers:** discarded-message in the writer (`WriterMessageQueue:74`), `IOException` in JetStream (`JetStreamImpl:183`/`:192`), and swallowed-into-`resetOnException` in the consumer callback (`NatsMessageConsumer:127`). Only the third is a legitimate boundary (§4a) — the other two have callers that could receive it.

**Can `InterruptedException` be designed away?** Only by making things worse. `join()` is uninterruptible (records the interrupt, keeps waiting) and has no timeout variant, so it would need `orTimeout` plumbing and would lose the ability to unblock a parked call at shutdown — exactly the case that motivates D3. Async-only is a different API, not a fix. `InterruptedException` is the intrinsic and correct price of a synchronous API over an async core.

## 5. Options

### Option 1 — Status quo

No work. Keeps V2 muscle memory intact for porting users. Preserves everything in §3.

### Option 2 — `JetStreamException` base, checked, NOT sealed (chosen)

Your proposal, refined by the triage above:

```
JetStreamException extends Exception          (NOT sealed — io.synadia.client.api)
├── JetStreamApiException          — server returned an Error (code, apiErrorCode, description)
├── JetStreamStatusException       — unexpected/unhandled status message (carries Status)
├── JetStreamTimeoutException      — no response within the request timeout
└── JetStreamProtocolException     — malformed response (the PublishAck "Invalid ack" cases)
```

- The 276 `IOException` declarations (252 of which also carry `JetStreamApiException`) collapse to `throws JetStreamException`.
- `JetStreamStatusCheckedException` is **deleted** — `JetStreamStatusException` becomes checked and moves under the base, which is the thing it was invented to fake.
- `NatsConsumerContext:103`/`:110` and `NatsConnection.ensureNotClosingAndNotCLosed` become `IllegalStateException`/`IllegalArgumentException` — unchecked, off the surface entirely.
- `IOException` disappears from the JetStream surface. Nothing is lost, because §3 shows nothing real was ever there. If a genuine I/O error ever needs to surface, it goes in as the `cause`.
- Interruption: pick one (see §6).

**On your switch-statement concern — Java 21 gives you this for free, and better than an enum would.** The project baseline is 21 (`build.gradle:165`), so pattern-matching switch over the hierarchy already works:

```java
try {
    js.publish(subject, data);
}
catch (JetStreamException e) {
    switch (e) {
        case JetStreamApiException api when api.getApiErrorCode() == JS_NO_MESSAGE_FOUND_ERR -> handleMissing();
        case JetStreamApiException api -> report(api.getErrorDescription());
        case JetStreamStatusException st -> inspect(st.getStatus());
        case JetStreamTimeoutException t -> retry();
        case JetStreamProtocolException p -> fail(p);
        default -> fail(e);   // required — the base is not sealed (§5a)
    }
}
```

The `default` is not a wart; it is the thing that lets a future exception type be additive instead of breaking. You still get the quick-triage ergonomics you asked for, with no parallel `Kind` enum to keep in sync with the type hierarchy by hand. What is given up versus `sealed` is only the compiler *proving* the switch complete — see §5a for why that trade is worth taking.

### 5a. Why not `sealed` — measured

An earlier draft of this document recommended `sealed`, on the argument that the four-way partition ("server said no / said something odd / said nothing / said nonsense") was closed. **That recommendation was wrong.** Three findings, all verified against `javac` 21 rather than reasoned about.

**What `sealed` actually means.** It is not `final`. It means only the classes named in the `permits` clause may extend the base:

```java
public sealed abstract class JetStreamException extends Exception
    permits JetStreamApiException, JetStreamStatusException,
            JetStreamTimeoutException, JetStreamProtocolException {}
```

Adding a fifth type is a one-line edit *for us*. Third parties can never extend it. So the question is not "can we extend it later" — it is "what does extending it later do to everyone else."

**1. Adding a subclass later breaks users twice, not once.** The earlier draft called it "a source-breaking change." It is worse. Compiling a consumer against a 2-subclass hierarchy, then adding a 3rd and recompiling *only the library*:

```
handle(ApiEx) = api
Exception in thread "main" java.lang.MatchException     <- old compiled consumer, new subclass
	at App.handle(App.java:4)
```

Already-deployed consumer code throws `MatchException` at runtime; recompiling then fails with `error: the switch expression does not cover all possible input values`. (Only affects exhaustive switches with no `default`. `catch (JetStreamException e)` is unaffected.)

**2. A sealed base must be `abstract` to be exhaustive.** A non-abstract sealed class is itself instantiable, so it remains a possible input and the switch does not compile. Consequence: **you cannot `throw new JetStreamException(...)`** — every throw must be a concrete subclass. Any "client-side validation" case would need its own fifth type, which is the most expensive kind to add.

**3. `sealed` cannot cross a package on the classpath.** JLS: for a sealed class in the unnamed module — which is what these jars are — every permitted subclass must be in the **same package**. Verified:

```
error: cannot extend a sealed class in a different package
```

All four types would live in `io.synadia.client.api`, so this is fine today. But **once `kv` and `os` split into their own projects** (`io.synadia.client.kv` / `.os`), they could never define a `JetStreamException` subclass — not "breaking", structurally impossible. We would be planning modules that are barred from the hierarchy.

**The decisive evidence that the partition is not closed:** within this same review, on reading A2, Scott's instinct was *"I think this is where we start the JetStreamException"* — a **client-side validation** case, a fifth category, proposed an hour after the four-way partition was declared closed. A set that attracts a new member that fast is not closed. Had `sealed` already shipped, that instinct would have been a breaking change rather than a design conversation.

**Conclusion:** `JetStreamException` is public API — users catch it and name it in their own `throws` clauses. For public API, the freedom to add a type without breaking anyone is worth more than the compiler proving a switch exhaustive. **Do not seal.**

### Option 3 — One concrete `JetStreamException` + `Kind` enum

Single type, `e.getKind()` returns `Kind.API | STATUS | TIMEOUT | PROTOCOL`, switch on the enum.

Simpler (one class, no hierarchy) and stays evolvable (new enum constants don't break `default`-bearing switches). But every type-specific accessor — `getApiErrorCode()`, `getStatus()` — has to hang off the one class and return null/`-1` for the wrong kind. Weaker typing, and Option 2 gets the same switch ergonomics without that. Only prefer this if you expect the failure taxonomy to churn.

### Option 4 — Unchecked (`JetStreamException extends RuntimeException`)

Deletes all 276 `throws` clauses outright and matches where modern Java libraries have landed. Biggest ergonomic win, biggest departure from V2 — and it silently changes the failure mode of ported user code: a `catch (IOException)` that used to be mandatory now compiles fine, does nothing, and lets the exception escape at runtime. That is the worst kind of migration break, because it moves a compile error to production. If you want this, it should be a deliberate, loudly documented decision, not a side effect of consolidation.

### Option 5 — Minimal: collapse only `IOException`

Keep `JetStreamApiException` exactly as-is, replace `IOException` with `JetStreamException` on the surface (`throws JetStreamException, JetStreamApiException`). Fixes the misleading name without touching the hierarchy. Half the churn — but keeps two exceptions in every signature, which is the thing you actually want gone.

## 6. Decisions

**All five are resolved — see §8, "Decisions — RESOLVED".** Outcome: checked, **not** sealed, propagate `InterruptedException`, types live in `io.synadia.client.api`, and this rework happens **before** the KV/OS split (which is deferred until the API settles) with kv/os in scope.

The resulting target shape:

```java
public StreamInfo getStreamInfo(String streamName) throws JetStreamException, InterruptedException
```

Two exceptions, both of which are true — replacing today's two, one of which never happens (§3).

## 6a. Hard constraint found during A10: status exceptions can't be fully unified

Attempting A12 (make `JetStreamStatusException` checked) hit a wall that reverses A12/A13 as originally written. **`JetStreamSubscription.nextMessage*` implements core `Subscription.nextMessage*`, which declares only `throws InterruptedException`.** A Java override cannot add a checked exception the interface doesn't declare, and core **cannot** reference a jetstream type (core→jetstream dependency is forbidden). So `JetStreamStatusException` *cannot* be made checked on that path — which is exactly why it was built unchecked in the first place.

Compounding it: the public core-bound `nextMessage*` is called not just by legacy code but by **modern** callers too — `NatsNextConsumer` (`ConsumerContext.next()`), `NatsIterableMessageConsumer`, `ObjectStore`, `AbstractBucketFeature.visitSubject`. Full unification would require rewiring all of those off `nextMessage` onto the internal `_nextUnmanaged*` (different semantics: `expectedPullSubject`, terminus/error filtering) — risky surgery for a naming cleanup.

**Resolution (Scott, minimal):** keep the two types, encode the existing internal/user split into the hierarchy instead of collapsing it.
- `JetStreamStatusException` — stays `extends StatusException` (unchecked), **internal** signal. Unchanged.
- `JetStreamStatusCheckedException` — **re-parented from `Exception` to `JetStreamException`** (checked, user-facing). Now `catch (JetStreamException)` catches the status errors the modern API raises, which was the actual goal.

**A12 → dropped** (infeasible). **A13 → "delete the wrapper" becomes "re-parent the wrapper"** — the two status types are kept because they encode a real distinction (internal unchecked signal vs. user-raised checked error), not debt.

**Rename DONE (Scott, via IDE):** `JetStreamStatusException` → `JetStreamStatusInternalException`; `JetStreamStatusCheckedException` → `JetStreamStatusException`. Motive: "Checked" names a Java mechanic, not a domain concept, and it's the type users catch — the user-facing one should own the clean name. **Internal type made non-public DONE (2026-07):** `JetStreamStatusInternalException` is now package-private (`class`, members reduced to package-private), and the bridge ctor `JetStreamStatusException(JetStreamStatusInternalException)` is package-private too — nothing outside `io.synadia.client.impl` referenced it (only main-code impl sites + one test, which moved to `JetStreamStatusInternalExceptionTests` in the `impl` package). It is JS-only (jetstream module, never in core), so the boundary/package plan may relocate it later.

## 7a. Status — what is implemented

- **A1** — done (vestigial `throws IOException` on `ObjectStore` private ctor).
- **A2/A3** — done. Both `NatsConsumerContext` client-side checks now throw `IllegalStateException` (A3 corrected from the audit's original `IllegalArgumentException`).
- **A4** — done. `NatsConnection.ensureNotClosingAndNotClosed()` removed and inlined into the `JetStreamImpl` constructor as an `IllegalStateException` guard.
- **Ripple** — the A4 constructor change made 15 further constructors/factories' `throws IOException` vestigial; all removed. **276 → 257** `IOException` declarations in `jetstream/src/main`. Main/tests/examples green.
  - Two of those (`KeyValueManagement(nc)`, `ObjectStoreManagement(nc)` single-arg) were missed on the first pass and caught later while fact-checking the migration guide — they delegate via `this(connection, null)` to the two-arg ctor that had already lost its throw. Lesson: hand-tracing `new X()` call sites misses constructor `this(...)`/`super(...)` delegation; only the §3a fixpoint (strip-all, re-add-what-javac-demands) catches that class reliably. javac does **not** flag a vestigial `throws` on a constructor, so compile-green is not proof of completeness here.
- **Migration guide** — `MIGRATION_GUIDE.md` has a new top-level "Exceptions — read this first" section (marked WIP) covering the two shipped flavors: (1) constructors that dropped `throws IOException` → old `catch (IOException)` becomes a compile error (safe); (2) `ConsumerContext.next/fetch/iterate/consume` still declare `IOException` but their client-side checks now throw unchecked `IllegalStateException`, so an old catch silently stops catching them (dangerous — no compiler warning). Notes the KV-throws/OS-doesn't asymmetry (`keyValue()` verifies the stream, `objectStore()` doesn't).
- **A10/A11 done + green:** `JetStreamException` base + `JetStreamTimeoutException` + `JetStreamProtocolException` created in `io.synadia.client.api`; `JetStreamApiException` re-parented under it + `getError()` added; `JetStreamStatusCheckedException` re-parented under it (§6a). `JetStreamApiException`/`JetStreamStatusException` stay in `.impl` for now — the `.impl`→`.api` move (D4) is **no longer a standalone task**; it is subsumed into the *Core↔JetStream boundary & package topology* plan (TODO.md `## Plans / Audits TBD`), where the exception packages should fall out of the boundary decision rather than being hand-moved now and re-moved later. Not sealed, base is concrete. Post-rename final names: user-facing checked status = `JetStreamStatusException`; internal unchecked = `JetStreamStatusInternalException`.
- **A5/A6/A7/A8/A9/A14/A19 DONE + green** (main+test+examples compile; server-independent unit tests pass):
  - **A5** — `JetStreamImpl` request utils propagate `InterruptedException` (try/catch removed).
  - **A6** — timeout → `JetStreamTimeoutException`.
  - **A7** — publish-status error → base `JetStreamException` with message. *Deviation from audit's "→ JetStreamStatusException": the renamed `JetStreamStatusException` only takes a `JetStreamStatusInternalException` (a subscription signal), which doesn't fit the publish path (no subscription). Base `JetStreamException` is honest and catchable.*
  - **A8** — bad ack → `JetStreamProtocolException`.
  - **A9** — `KeyValue:35` IOException-wrapper removed; `JetStreamApiException` propagates.
  - **A14** — collapsed `throws IOException, JetStreamApiException` (and `JetStreamStatusException`) → `throws JetStreamException` across ~250 signatures, `InterruptedException` propagated where the request path reaches (measured cascade). Base `JetStreamException` used, not precise subtypes — catch base, switch on subtype.
  - **A19** — `NatsMessageConsumer.pullTerminatedByError` (timer-lambda callback, non-throwing) gets an explicit `catch (InterruptedException)` that re-sets the flag and `resetOnException()`.
  - **Genuine-IOException carve-out (discovered during collapse):** the blanket collapse wrongly stripped `java.io.IOException` from code that does real I/O, not JetStream. Restored on `ObjectStore.put`/`get` (InputStream/OutputStream read/write) and `ApiJavaSerializationTests` (ObjectOutputStream). Those correctly keep `IOException` alongside `JetStreamException`. Only those two files do genuine I/O.
- **A15 DONE + green:** rewrote the stale `@throws` javadoc across the JetStream surface to match the collapsed A14 signatures. Every boilerplate `@throws IOException covers various communication issues with the NATS server...` + `@throws JetStreamApiException the request had an error related to the data` pair collapsed to `@throws JetStreamException covers communication and server-side JetStream errors` + `@throws InterruptedException if interrupted while waiting for the server`. 11 files, 149 umbrella lines; `:jetstream:javadoc` is now BUILD SUCCESSFUL, **0 errors** (down from ~100 doclint errors). *Deviation from the audit's "rewrite per failure type, not find/replace":* used one uniform umbrella sentence, not per-subtype text — deliberate, because D2 landed on catch-the-base-and-switch-on-subtype, so the surface documents the base `JetStreamException`, not each subtype. A block-aware script gated the rewrite on each method's **real** `throws` clause, so the 5 genuine-I/O methods (`ObjectStore.put`/`get`, InputStream/OutputStream) correctly **keep** `@throws IOException`. Also swept one non-boilerplate stale line (`KeyValueManagement.keyValue` — `various IO exception...`) and fixed one pre-existing malformed-HTML doclint error (`KeyValuePurgeOptions`, a literal `<= 0` → `{@code <= 0}`) that was blocking a green javadoc. Uncommitted.
- **A16 DONE:** `MIGRATION_GUIDE.md` "Exceptions — read this first" section extended with a **`JetStreamException` consolidation** subsection — the `throws IOException, JetStreamApiException` → `throws JetStreamException, InterruptedException` shape change, the catch-base-and-switch-on-subtype pattern (Java 21, with the required `default` and *why* the base isn't sealed), the subtype table (type / package / meaning / accessor — `getError()`, `getStatus()` verified present), the `JetStreamStatusCheckedException`→`JetStreamStatusException` and old-`JetStreamStatusException`→`JetStreamStatusInternalException` renames, and the `ObjectStore.put`/`get` genuine-`IOException` carve-out. The pre-existing reclassification (A2/A3/A4) content was already there. Uncommitted.
- **New test coverage added (uncommitted):** (1) `JetStreamExceptionTests` (jetstream test, `io.synadia.client.api`) — 7 deterministic, server-free contract tests locking the hierarchy: catch-base catches every user-facing subtype, base is checked/not-`RuntimeException`, `JetStreamStatusInternalException` is unchecked and provably outside the hierarchy (§6a), `getError()` delegation (A11), the `JetStreamStatusException` `Status`/note carry + internal-cause bridge (A7/A13), and the D2 pattern-switch dispatch incl. the required `default`. (2) `JetStreamTimeoutTests` (`io.synadia.client.impl`) — A6: `responseRequired(null)` throws `JetStreamTimeoutException` with the exact message and is catchable as the base; a non-null response passes through. Tested the root site directly (deterministic) rather than provoking a timing-flaky network timeout. **Both pass** — the contract test standalone, the timeout test against a live nats-server. Fills the two real gaps (`JetStreamTimeoutException` and the status/hierarchy types had 0 prior references).
- **A20 resolved** — keep recover-and-continue (the A19 site runs only on NATS-owned threads; a real interrupt is our own `shutdownNow()` at close, handled by the stopped/fullClose path, and a stray one is transient → recovering is correct); the overclaiming comment was tightened. **A21 resolved** — `push` now throws unchecked `IllegalStateException(OUTPUT_QUEUE_INTERRUPTED)` on interrupt (uniformly, like the full/busy throws — no internal-vs-user split) instead of laundering it into a phantom `messageDiscarded`; `requestAsync` future leak fixed. **D4** (the `.impl`→`.api` move) is no longer standalone — subsumed into the *Core↔JetStream boundary & package topology* plan (TODO.md `## Plans / Audits TBD`); the exception packages fall out of that decision. With that, the exceptions audit A1–A23 + A20/A21 is effectively closed.
- **Test reconciliation — VALIDATED against a live nats-server:** 9 `assertThrows`/`assertInstanceOf` updated. 5 PublishAck → `JetStreamProtocolException` (unit tests pass). 4 server-dependent (`testJetNotEnabled` account, 2 publish, 1 async-cause) → `JetStreamException` — all **PASS** against a running `nats-server` (`JetStreamGeneralTests`, `JetStreamPubTests` green). The 87 `assertThrows(JetStreamApiException.class)` unchanged — still the runtime type for server errors.
- **Full jetstream suite: passes.** On a normal filesystem the full suite is BUILD SUCCESSFUL (test-retry recovers a couple of timing flakes). On the WSL2 `/mnt/c` mount (slow 9p I/O to Windows), 7 timing-flaky tests fail — `SimplificationTests` fetch tests asserting `elapsed < 100` ms, plus `JetStreamPushTests.testDeliveryPolicy`. **Not a regression:** the fetch-path files have zero logic delta vs HEAD (diff-verified, only throws/rename changed); measured fetch elapsed is 2–15 ms on fast disk; the same code passes the full suite on fast disk; and HEAD flakes on the same assertions. The slow `/mnt/c` I/O (JetStream writes stream data to disk) pushes the timing assertion over 100 ms and exhausts retries. Environment, not the change.

## 7. Chosen approach

**Option 2 — checked, non-sealed `JetStreamException`, with the §3 root-site triage applied** (D1/D2). The triage is the part that matters most: roughly a third of what rides the `IOException` channel isn't a recoverable failure at all, it's argument and state validation that should never have been checked. Consolidating without it would just rename the catch-all.

**Correction from §3a:** an earlier draft of this document said to "land the triage first, it's independently correct and mostly mechanical, then add the base type." The measurement disproves that sequencing. Retargeting the two `JetStreamImpl` request-path sites (A5/A6) is what actually removes `IOException` from the surface, and those two cannot be retargeted without first naming what they throw instead — i.e. without the base type. Triage and consolidation are one change, not two phases. Only A1–A4 are genuinely separable.

## 8. Action items

Decisions first — A5 onward are blocked on them.

### Decisions — RESOLVED (Scott, 2026-07-15)

- [x] **D1 — Checked.** `JetStreamException extends Exception`. Compile-time enforcement stays; a porting V2 user gets a compile error rather than a runtime surprise. Rules out Option 4.
- [x] **D2 — NOT sealed.** *(Reversed 2026-07-15. This document originally recommended `sealed`; that recommendation was wrong — see §5a for the measurements and the reasoning.)* `JetStreamException` is a plain public base; users write a `default` branch in any switch. Adding exception types later stays additive and non-breaking, which is the property that matters for a public API.
- [x] **D3 — Declare `throws InterruptedException`; do not wrap.** Matches core, which already propagates it from `NatsConnection.request` (`:1418`) — JetStreamImpl was the lone layer reboxing it. Constraints, both measured: must ship bundled with A10/A14 (alone it is 3 exceptions per signature, not 2 — §4a), and needs A19 first (the timer-lambda callback has no caller to receive it).
- [x] **D4 — `io.synadia.client.api`.** With the payloads the exceptions carry (`Error`, `Status`). Note the deciding argument is *coherence, not OSGi*: `.api`, `.impl` and `.utils` are **already** split across the core and jetstream jars, so those jars already cannot sit on the JPMS module path together. Putting the exceptions in `.api` adds to an existing split package but creates nothing new. The module-system problem is real but orthogonal and is tracked separately in **OSGi_JPMS_TODO.md**; if that work ever re-roots the packages, the exception types move along with everything else in `.api` at no extra cost.
- [x] **D5 — Do the exceptions FIRST; the KV/OS split is deferred.** *(Settled 2026-07-15 after a brief reversal — see below. This lands back on the original recommendation, but for a better reason than the one originally given.)*

### D5 — how it settled, and why the circularity matters

This flipped twice, and the reasoning is worth keeping because it is easy to get backwards.

The document first recommended exceptions-before-split on scope grounds. That was then reversed to split-first. Then Scott clarified the actual intent: **KV/OS will become their own projects that depend on jetstream, but deliberately *later* — once the JetStream API is worked out** — because KV/OS may need jetstream internals that have not settled yet. For now they stay as packages inside jetstream and access concerns are left for split time.

That makes split-first **circular**: the split waits for the API to settle, and *this exception rework is part of settling the API*. Blocking exceptions on the split would block both indefinitely. Sequencing is therefore:

1. **Exceptions now**, with `kv/` and `os/` fully in scope as packages inside jetstream — one module, one compile, no cross-jar version coordination.
2. **Split later**, against an API that has stopped moving.

Consequences of doing it this way:

- All ~276 `IOException` declarations are in scope, including kv/os (`KeyValue` 34, `ObjectStore` 17 post-A1, `KeyValueManagement` 9, `ObjectStoreManagement` 8, plus the watch subscriptions).
- **A9** (`kv/KeyValue:35`) stays in this effort. Good — its `// can't throw directly, that would be a breaking change` comment is exactly the V2 baggage this rework exists to remove.
- `AbstractBucketFeature` and the rest of the shared bucket infra (`FeatureOptions`, `NatsWatchSubscription`, `Watcher` — ~342 LOC, used *only* by kv/os) stay where they are. Where they ultimately land is a **split-time** question, not an exceptions question. Do not decide it here.
- No cross-jar floor: KV/OS never have to depend on "whichever jetstream version introduced `JetStreamException`", because they are still inside jetstream when it lands.

**Net: this audit is NOT blocked.** A2–A4 are separable and can start now; A5–A18 proceed once someone picks the work up.

### Independently separable — no decision needed

- [x] **A1 — `ObjectStore.java:36`** — vestigial `throws IOException` on the private copy constructor removed (`super(...)` resolves to the non-throwing `AbstractBucketFeature(String, AbstractBucketFeature)`). `:jetstream:compileJava` green. **Done.** This was the only signature-only cleanup that exists (§3a).
- [x] **A2 — `NatsConsumerContext.checkState()`** — `IOException("The ordered consumer is already receiving messages...")` → **`IllegalStateException`**. **Done.** Confirmed a pure client-side invariant: every input (`lastConsumer`, `isOrdered`, `finished`) is client state; the server does not enforce single-ordered-consumer, so there was no server error being pre-empted. Violation = the caller invoked `consume()`/`next()` while another was live = a programming error. Method no longer throws checked.
- [x] **A3 — `NatsConsumerContext.checkNotPinned()`** — `IOException("Pinned not allowed with " + label)` → **`IllegalStateException`** (the audit originally said `IllegalArgumentException` — that was wrong: `label` is an internal constant `"Next"`/`"Fetch"`, not caller input; the check tests the *consumer's configured PinnedClient policy* against the operation, which is state, not argument). **Done.**
- [x] **A4 — `NatsConnection.ensureNotClosingAndNotClosed()`** (spelling since fixed by Scott) — **removed entirely and inlined** into the sole caller, the `JetStreamImpl` constructor, as `if (connection.isClosing() || connection.isClosed()) throw new IllegalStateException(...)`. **Done.** Only one caller ever existed, so the method earned no keep. Cost, noted for the record: `isClosing()`/`isClosed()` are `protected` on `NatsConnection`, and jetstream previously called only *public* members of it — so this inlining is the first cross-jar use of a protected member. `isClosed()` alone could go through the public `getStatus() == CLOSED`, but `isClosing()` reads a private field with no public accessor, so the protected call is unavoidable without adding new public surface. Acceptable given both jars ship together, but it is a new coupling.

A2–A4 are correct under *every* option including status quo. **Ripple measured (§3a fixpoint + hand-trace):** removing `throws IOException` from the `JetStreamImpl` constructor cascaded through every constructor/factory whose body only constructs a JetStream context and does no request — `JetStream` (2 ctors + 2 `instance`), `JetStreamManagement` (2 + 2), `AbstractBucketFeature`, `KeyValueManagement`, `ObjectStore`, `ObjectStoreManagement.objectStore()`. **Net: 276 → 259 `IOException` declarations in `jetstream/src/main` (−17 total; A1 was −1, A2–A4 + ripple −16).** The boundary held exactly where a body first hits a real request: `NatsStreamContext`, `KeyValue`, `KeyValueManagement.keyValue()`, and `DebugJs` all call `getStreamInfo()` in-constructor and correctly kept their `throws`. All green: main, tests, examples compile; no stale `@throws IOException` javadoc (verified — `:jetstream:javadoc` fails on pristine HEAD too, 102 pre-existing warnings, none about exceptions). **Tests reconciled and passing on a live server:** 5 assertions in 2 files (`JetStreamGeneralTests` ×2 — JS/JSM ctor on a closed connection; `SimplificationTests` ×3 — `next`/`fetchMessages`/`consume` on an already-running ordered consumer) changed from `assertThrows(IOException.class)` to `IllegalStateException` and confirmed passing by Scott (2026-07). This is a legitimate contract-changed test update (A2/A4 were decided reclassifications), not a red-test-made-green. `PublishAck`/`publish`/`getAccountStatistics` assertions correctly left on `IOException` — those paths (A5–A8) are not done yet.

### D1/D2 work — DONE (the change that moved the 276)

*All items in this group are implemented and green (uncommitted); see §7a for the as-built detail and deviations. A20 resolved (keep recover-and-continue; comment tightened); A21 resolved (interrupt throws unchecked `IllegalStateException` uniformly — no internal-vs-user split — requestAsync leak fixed); A18 is the live-server test run.*

- [x] **A5 — `JetStreamImpl:183` / `:192`** — `catch (InterruptedException e) { ...; throw new IOException(e); }` in `makeRequestResponseRequired` / `makeInternalRequestResponseRequired`. Per D3, either propagate `InterruptedException` or wrap in `JetStreamException`. **This and A6 are why 276 signatures carry `IOException`.** **Built and measured (§4a): must land together with A10/A14 — alone it takes every signature from 2 checked exceptions to 3 (`InterruptedException` 55 → 281), which is worse than status quo.** Requires A19 first.
- [x] **A19 — Callback boundary for interruption** (prerequisite for A5, §4a). `PullManagerObserver.pullTerminatedByError()` must stay non-throwing — it is reached from a timer lambda in `MessageManager:141-146` that cannot throw. Add an explicit `catch (InterruptedException)` in `NatsMessageConsumer:118` (today the interrupt is absorbed by the `catch (... | IOException)` at `:127`). Decide the semantics there: `resetOnException()` and keep running, or stop the consumer. Same treatment for `PullOrderedMessageManager:48`.
- [x] **A21 — `WriterMessageQueue` swallowed interruption (core, §4b). DONE + green.** The old `catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }` made an interrupted publish indistinguishable from a full queue, so `queueOutgoing` (`NatsConnection:1996`) mislabeled it to the user as `errorListener.messageDiscarded(...)`, and `requestAsync` leaked a never-completable future. **Resolved (checked propagation rejected as the A5 blast radius — `push`→`queue`→`_publish`→public `publish()` which doesn't declare `InterruptedException`).** A failed enqueue is treated the same regardless of cause: `push` re-asserts the interrupt flag and throws an unchecked `IllegalStateException(OUTPUT_QUEUE_INTERRUPTED, e)` — **uniformly, with no internal-vs-user special case** — exactly like the sibling `OUTPUT_QUEUE_IS_FULL`/`OUTPUT_QUEUE_BUSY` throws in the same method (internal messages already throw on full/busy, so an interrupt is just another failed enqueue). No new exception type; a caller that cares whether it was specifically an interrupt checks `Thread.interrupted()`. (An earlier draft gated the throw on `internal`; dropped — it was inconsistent with `queueInternalMessage`'s reconnect branch, which already pushes with `internal == false`.) `requestAsync` wraps `_publish` in `try/catch (RuntimeException)` that unregisters the future from `responsesAwaiting` and undoes `incrementOutstandingRequests()` before rethrowing — fixes the leak for the interrupt case **and** the pre-existing full/busy case. One deterministic test (`MessageQueueTests.testInterruptedPushThrows`, using a pre-set interrupt flag so `tryLock` throws on entry; asserts both the 1-arg and internal `push` throw). core RequestTests/ErrorListenerTests/PublishTests green on live server.
- [x] **A20 — Investigate the latent bug in §4a** (independent of this whole effort). An interrupted auto-repull lands in `resetOnException()` — the consumer restarts its heartbeat timer and continues, and the interrupt flag is left set on a pooled timer thread that nothing subsequently checks. **Resolved: keep the recover-and-continue behavior.** `pullTerminatedByError()` runs only on NATS-owned threads (heartbeat-timer pool via `MessageManager:139`, or the message-delivery thread via `PullOrderedMessageManager:48`). The only realistic interrupter is our own close — `Options.shutdownExecutors()` → `ses.shutdownNow()` (`Options.java:541`) — and in that case the `stopped`/`fullClose()` path performs teardown while the rescheduled heartbeat lands on the now-dead executor and no-ops; a *stray* interrupt is transient and recovering is the desired outcome. So recover-and-continue is a safe superset of "stop." The `Thread.currentThread().interrupt()` re-flag is ceremonial on a pooled worker (nothing reads it) but kept as standard hygiene; the misleading "so the interrupt isn't swallowed" comment was rewritten to say what actually holds.
- [x] **A6 — `JetStreamImpl:198`** — `responseRequired(null)` → `new IOException("Timeout or no response waiting for NATS JetStream server")` → `JetStreamTimeoutException`.
- [x] **A7 — `JetStream:342`** — `throw new IOException("Error Publishing: " + resp.getStatus().getMessageWithCode())` → `JetStreamStatusException`. This one is a *status* error already; it is simply thrown as the wrong type.
- [x] **A8 — `PublishAck:40` / `:45`** — `throw new IOException("Invalid JetStream ack.")` → `JetStreamProtocolException`.
- [x] **A9 — `kv/KeyValue:35`** — delete the `catch (JetStreamApiException e) { throw new IOException(e); }` wrapper and let it propagate. The `// can't throw directly, that would be a breaking change` comment is V2's constraint and does not apply to V3.
- [x] **A10 — Add the base type.** `JetStreamException extends Exception` in `io.synadia.client.api` (D4), **not sealed** (D2, §5a) — no `permits` clause, and `abstract` only if we decide the base should never be thrown directly, which is now an open choice rather than something `sealed` forces. Subclasses: `JetStreamApiException`, `JetStreamStatusException`, `JetStreamTimeoutException`, `JetStreamProtocolException`.
- [x] **A11 — Re-parent `JetStreamApiException`** from `Exception` to `JetStreamException`. Add the missing `getError()` accessor while touching it — it holds an `Error` but exposes only code/apiErrorCode/description.
- [x] **A12 — SUPERSEDED (§6a — infeasible as written).** Making the internal `JetStreamStatusException` checked is blocked: `JetStreamSubscription.nextMessage*` implements core `Subscription.nextMessage*`, which declares only `throws InterruptedException`, and core cannot reference a jetstream type — so that status signal must stay unchecked. Resolved differently instead: the internal signal was **renamed** `JetStreamStatusInternalException` (stays unchecked, out of hierarchy) and the checked user-facing status role moved to `JetStreamStatusException` (see A13). Core's `StatusException` stays unchecked.
- [x] **A13 — DONE, as re-parent + rename, not delete (§6a).** `JetStreamStatusCheckedException` was **re-parented** from `Exception` to `JetStreamException` (so `catch (JetStreamException)` now catches the status errors the modern API raises) and **renamed** `JetStreamStatusException`. Kept rather than deleted because it encodes a real distinction — the user-facing checked error vs. the internal unchecked signal (`JetStreamStatusInternalException`).
- [x] **A14 — Collapse the signatures.** Replace `throws IOException, JetStreamApiException` with `throws JetStreamException` across the 276 (+ `InterruptedException` per D3). Mostly mechanical once A5–A13 land.
- [x] **A15 — Javadoc. DONE + green (see §7a).** The stale boilerplate `@throws IOException covers various communication issues with the NATS server...` + `@throws JetStreamApiException ...` pairs became `@throws JetStreamException ...` + `@throws InterruptedException ...` across 11 files (149 lines); `:jetstream:javadoc` is 0-error. Landed as one uniform umbrella sentence (documenting the base per D2), **not** the per-failure-type rewrite this item originally called for — the non-sealed catch-base design made per-subtype javadoc redundant. Genuine-I/O `ObjectStore.put`/`get` kept `@throws IOException` (script gated on the real `throws` clause).
- [x] **A16 — Migration guide. DONE (see §7a).** `MIGRATION_GUIDE.md` now documents both the reclassifications (already present) and the `JetStreamException` consolidation (catch-base + switch-on-subtype, subtype table, renames, ObjectStore genuine-I/O carve-out). N/A-risk noted: D1 landed **checked** (not Option 4), so the dangerous silent-`catch (IOException)` case doesn't apply — the compile error leads the user to the change.

- [x] **A22 — Timeout exception carries operation context, not a redundant message.** `responseRequired(Message, String context)` throws `JetStreamTimeoutException(context)` on a null response — the *type* already says "timeout", so the old `"Timeout or no response waiting for NATS JetStream server"` text was redundant. `makeRequestResponseRequired` gained a **caller-supplied** `context`, and all ~20 management call sites label their operation (`getStreamInfo`, `addStream`, `deleteConsumer`, `getConsumers`, …) instead of leaking the internal `$JS.API.*` subject — the subject is a server-API detail, not useful context. The publish path (`makeInternalRequestResponseRequired` / `publishAsyncInternal`) keeps the publish subject, which is the user's own and already meaningful. **The no-responders split briefly proposed here was dropped:** no-responders (503) is a real *response* (a status message), not a no-response — and a null response is always a timeout whether the server is slow or gone (network/server), so there is nothing to split. `JetStreamTimeoutTests` green.

- [x] **A23 — Restore precision where A14 over-widened.** The A14 collapse widened 19 methods that throw *exactly one* subtype down to the base `JetStreamException`, losing precision (flagged by Scott on `ListRequestEngine`). Reverted each to its precise type — verified by compile that none were forced back to base (their bodies really do throw exactly one): **(a) `JetStreamApiException`** — `ApiResponse.throwOnHasError`, `ListRequestEngine(Message)`, `JetStreamImpl.createAndCacheStreamInfoThrowOnError`, `AbstractListReader.process`, `StreamInfoReader.process` (+2 test mirrors); **(b) `JetStreamStatusException`** — the fetch/iterate `nextMessage*` API (`FetchMessageConsumer`/`IterableMessageConsumer` interfaces + `NatsFetchMessageConsumer`/`NatsIterableMessageConsumer` impls + `process`) (+1 test). The multi-subtype `ConsumerContext.next()` family correctly **keeps** the base — it genuinely throws Status *and* Api. **Principle: one subtype → declare it; multiple → base.** 7 now-dead `JetStreamException` imports removed; main+test green, exception unit tests pass.

### Verification

- [x] **A17 DONE — `IOException` verified gone from the JetStream surface.** Rather than the §3a strip-and-re-add fixpoint (which answers "are the remaining declarations minimal" — now moot post-collapse), used an exhaustive static audit, which answers the actual question ("gone vs. merely hidden") directly and is not subject to javac's progressive-error trap: (1) **0** synthetic `throw new IOException` remain in `jetstream/src/main` — all 8 §3 root sites retargeted; (2) exactly **5** `throws IOException` declarations remain, all genuine-I/O `ObjectStore.put`/`get` (InputStream/OutputStream); (3) the now-dead `import java.io.IOException` lines have since been stripped — **0** dead IOException imports remain in `jetstream/src/main` (verified 2026-07). Full compile green (main + test + examples).
- [x] **A18 DONE — full suite green on a live nats-server (v2.14.0-dev).** `:jetstream:test` BUILD SUCCESSFUL (3m56s) and `:core:test` BUILD SUCCESSFUL (4m8s, zero failures). New `JetStreamExceptionTests` (×7) + `JetStreamTimeoutTests` pass; all reconciled exception assertions (`PublishAckTests`, `ApiFieldsTest`, `JetStreamGeneralTests`, `JetStreamPubTests`) pass. Only failures were `SimplificationTests.testReconnectOverOrdered` / `.testOrderedBehaviorFetchByStartTime` — timing/reconnect flakes on the slow `/mnt/c` mount, self-recovered on retry (build green), not exception-related. Ran here since `~/.local/bin/nats-server` is present.

### Client-error catalog (added 2026-09-21)

**Rule (Scott, 2026-09-21): a new `ClientError` constant goes in the module README's Client Error Messages table as part of adding it.** The core `README.md` section explains the mechanism and `jetstream/README.md` carries the `SUB`/`CON` and `OS` tables; the `OS` table moves to `os/README.md`, and a `KV` one starts there, when those modules split out. Mirrors v2's `### Client Error Messages` section.

- [x] **A24 — `NatsConsumerContext` raises catalog errors, not bare `IllegalStateException`.** Both of its `IllegalStateException` throws became `JetStreamClientError` constants in a new `CON` group, continuing v2's `NatsJetStreamClientError` numbering (v2 used CON-90301..90303; v2 threw `IOException` for both of these, so neither had an id): `JsConsumerOrderedAlreadyReceiving` (CON-90304, `checkState`) and `JsConsumerPinnedNotAllowed` (CON-90305, `checkNotPinned`, which passes the operation label through the parameterized `instance(Object...)` of A26). Same exception type reaches callers, now with a stable id in the message. `:jetstream:compileTestJava` green.
- [ ] **A25 — Review the remaining fixed-message state errors for catalog membership.** Scoped by the rule already settled in `~development-history/CLIENT_ERROR_AUDIT.md` (§2a, and "The rule, stated"): *a fixed-message, condition-specific error is a catalog entry; label-parameterized field validation stays in `Validator` as a raw `IllegalArgumentException`*. That leaves the 29 `IllegalArgumentException` sites in `jetstream/src/main` out of scope as settled validation - including both of the ones still in `NatsConsumerContext` (`:55` internal guard, `:195` max-wait check). Only 4 `IllegalStateException` sites remain, and one of them is settled too: `utils/JsValidator:138` (`label1 + " must match " + label2 ...`) is the label-parameterized replacement Scott chose for the removed `JsConsumerNameDurableMismatch` on 2026-08-31. The three actual candidates: `api/StreamConfiguration:47` ("Stream Configuration does not have required name."), `impl/JetStreamImpl:45` ("A JetStream context can't be established during close."), `kv/KeyValueEntry:38` ("Invalid Message Info for a Key Value Entry"). Each is a fixed message with no field label, which is the catalog shape. **The one place validation-shaped conditions did go in the catalog is ObjectStore** - `OsCantLinkToLink` (OS-90204), `OsLinkNotAllowOnPut` (OS-90209) and `OsCantLinkToDeletedObject` (OS-90210) are the only `KIND_ILLEGAL_ARGUMENT` constants anywhere. Left as is; revisit later (Scott, 2026-09-21).
- [x] **A26 — `ClientError` takes labels.** A description may now contain `%s` placeholders, and `instance(Object...)` fills them: `new ClientError(CON, 90305, "Pinned not allowed with %s.", KIND_ILLEGAL_STATE)` thrown as `.instance(label)` produces `[CON-90305] Pinned not allowed with Next.`. The old append-semantics `instance(String extraMessage)` is gone - it had one caller - and the single varargs `instance` covers both shapes, so every existing `X.instance()` call site is unchanged. The label count is derived at construction, and a mismatch never fails - a wrong count is a library mistake, not a caller's, so `instance` fits what it is given: extra labels fold into the last placeholder comma delimited (`"C, D, E"`), missing ones read `ClientError.MISSING_LABEL` (`???`), and labels passed to a fixed message are ignored (Scott, 2026-09-21). **`matches(Exception)` now compares kind plus the `[GROUP-CODE] ` prefix instead of the whole message**, which is what makes a filled-in parameterized error still match; the id is the identity, so this is no weaker for fixed messages. Verified by `JetStreamPullTests.testPinnedClient` (parameterized) and `JetStreamSubscribeTests.testJetStreamSubscribeErrors` (fixed), both green. Note this removes the *technical* objection in the A25 rule but not the volume one - promoting the ~30 `Validator` field validations would still mean 40-60 constants, so the rule stands until revisited.
- [x] **A27 — A13 reversed: the status types are one unchecked family, and JetStream raises its own.** `JetStreamStatusInternalException` is deleted. `JetStreamStatusException` was **re-parented from `JetStreamException` to core's `StatusException`**, so it is unchecked, still carries the note and the subscription, and `catch (StatusException)` now covers core and JetStream alike. `JetStreamSubscription._nextUnmanaged*` and the legacy pull `fetch` raise it directly; the two fetch/iterate bridges are gone, reduced to `catch (StatusException e) { throw e; }` so the stopped-consumer `catch (IllegalStateException)` below cannot swallow a status. No signature changed. `StatusException` stays in core because `NatsConnection.deliverReply:1691` raises it for a 503 on a plain core request (`RequestTests:370`). A13's property - `catch (JetStreamException)` catches every user-facing error - no longer holds for status; all 5 `catch (JetStreamException)` sites in main were reviewed and none regress. `MIGRATION_GUIDE.md` now documents the status family separately. Written up in `NEXTMESSAGE_STATUS_EXCEPTION_ANALYSIS.md` §10.
