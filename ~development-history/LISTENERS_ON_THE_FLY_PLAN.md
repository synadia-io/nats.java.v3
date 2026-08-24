# Plan: multiple Error/Connection listeners, configurable in Options and add/remove live

**Status: IMPLEMENTED — all checklist items done, `core` + `jetstream` suites green. Uncommitted, under review.**

**One deviation from the plan as written:** the id-based removers are named `removeErrorListenerById(String)` / `removeConnectionListenerById(String)`, not overloads of `removeErrorListener`/`removeConnectionListener`. An overload taking `String` is ambiguous with the instance overload for a `null` literal — `nc.removeConnectionListener(null)` stopped compiling, which `ConnectionListenerTests:110` does deliberately to assert the `@NonNull` contract. Distinct names keep `remove(null)` compiling and reading as before.

Scope (settled with Scott): **`ErrorListener` and `ConnectionListener` go multi** — several can be supplied when building `Options`, and any can be added or removed on a live `NatsConnection`. **`ReadListener` stays single** — it's a debug hook, not a production path; it keeps one slot, set in the builder and set/removed on the connection.

## Current state (re-verified 2026-08-03)

**ConnectionListener — half done.** `NatsConnection` holds `protected final Collection<ConnectionListener> connectionListeners` (`ConcurrentHashMap.newKeySet()`, NatsConnection.java:68), seeded from `options.getConnectionListener()` at construction (135-138), with public `addConnectionListener(@NonNull)` (1732) / `removeConnectionListener(@NonNull)` (1741) and a per-listener `makeCallback` fan-out in `processConnectionEvent` (2121-2126). The **live add/remove half exists**; the **Options half does not** — the builder takes exactly one. `ConnectionListener` is a **functional interface** (single abstract `connectionEvent`, ConnectionListener.java:19) and is already used as a lambda (`ConnectionListenerTests.java:116`, `InfoHandlerTests.java:130`).

**ErrorListener — nothing.** `final ErrorListener errorListener` on `Options` (:100), single builder setter (OptionsBuilder.java:984). `build()` seeds an anonymous no-op when unset (1487-1488), so `getErrorListener()` is never null. 10 `default void` methods plus `default String supplyMessage(...)` (:145) — no abstract, so not a functional interface. Call sites: 4 direct in `NatsConnection` (`messageDiscarded` 2009, `slowConsumerDetected` 2077, `exceptionOccurred` 2082, `errorOccurred` 2096), the fan-out helper `notifyErrorListener(ErrorListenerCaller)` (2099-2105) used by 9 remote callers (`SocketDataPort` 1; jetstream `MessageManager` 1, `PullMessageManager` 4, `PushMessageManager` 2), and one pre-connection site `Nats.java:277` (`conn == null`, no `NatsConnection` yet).

**ReadListener — single-slot replace, works, one latent bug.** `readListener` is already `private volatile` (NatsConnectionReader.java:67) — *the `volatile` gap in the original plan is fixed, do not re-fix it*. The connection-repoint feature landed since: `currentUserRl` (:71) + `refreshReadListener` (:119-141), called from the ctor (:88) and `setConnection` (:167-170). `NatsConnection.setReadListener` (:198) delegates to `reader.setReadListener` (:91-108); `null` installs a no-op. **Bug:** `setReadListener` never updates `currentUserRl`, so on a repoint `refreshReadListener` compares the *new* connection's `Options` listener against the *old connection's* — a repoint to a connection with a different `Options` ReadListener silently stomps a runtime-set listener. Latent (`setConnection` has no production caller, tests only), and now that ReadListener stays single it does not get deleted by this work — it needs an explicit small fix. See R2.

**`makeCallback` (:2058)** already wraps each task in try/catch and swallows per-task exceptions, so one `makeCallback` per listener buys listener isolation for free.

**Blast radius of the builder change:** 91 call sites of `.errorListener(` / `.connectionListener(` / `.readListener(`, all in tests + `utils/OptionsUtils`. A **varargs** setter keeps every one source-compatible.

**Doc bug:** `Options.getErrorListener()` javadoc (Options.java:581) claims "Will be an instance of ErrorListenerLoggerImpl if not user supplied." No such class exists (only `ErrorListenerConsoleImpl`); the default is an anonymous no-op.

## Identity and ids — DECIDED

**Each interface gets its own differently-named default id method. No base interface, no registry class.** The listener supplies its own identity; the connection stores listeners in a plain map keyed by that id.

```java
// ErrorListener
default String getErrorListenerId() { return "ErrorListener-" + hashCode(); }

// ConnectionListener
default String getConnectionListenerId() { return "ConnectionListener-" + hashCode(); }
```

Storage and operations on `NatsConnection` — this is the whole mechanism:

```java
Map<String, ErrorListener> errorListeners = new ConcurrentHashMap<>();

void addErrorListener(ErrorListener el)    { errorListeners.put(el.getErrorListenerId(), el); }
void removeErrorListener(ErrorListener el) { errorListeners.remove(el.getErrorListenerId()); }
void removeErrorListenerById(String id)   { errorListeners.remove(id); }
// fan-out iterates errorListeners.values()
```

Why this is the right shape:
- **Distinct method names mean no diamond, ever.** `getErrorListenerId()` and `getConnectionListenerId()` are different signatures, so a class implementing both interfaces inherits both defaults independently with no conflict and no forced override. `utils/DebugListener.java:9` (`ErrorListener, ConnectionListener, ReadListener`, **main source**) and the test harness `utils/Listener.java:21` compile untouched, and **no user code breaks on upgrade**. A single `getId()` on both interfaces would have been a compile error (JLS 9.4.1.3) for exactly those classes; a shared `Listener` base can't fix it either, since any per-interface override reintroduces the same conflict one level down.
- **A both-roles class correctly gets two ids** — one per map. The maps are separate (`errorListeners`, `connectionListeners`), so ids only need to be unique within their own map, and the two roles are independently addressable.
- **No scan, no reverse index, no minted-id bookkeeping.** Remove-by-instance is a direct keyed removal because the instance states its own id.
- **Interfaces stay interfaces** — `default` methods don't count against the single-abstract-method rule, so `ConnectionListener` remains functional and its existing lambda usage (`ConnectionListenerTests.java:116`, `InfoHandlerTests.java:130`) keeps working.
- **The user has an escape hatch.** Anyone wanting a stable or meaningful id overrides `getErrorListenerId()` / `getConnectionListenerId()`.
- **Literal interface-name prefix, not `getClass()`** — simpler, and it sidesteps anonymous listeners, where `getSimpleName()` returns `""` and would degrade an id to `"-12345"`.

**Accepted limitation (Scott's call, documented deliberately):** the default id is derived from `hashCode()`, so two listeners that override `equals`/`hashCode` and compare equal will produce the same id and evict each other, and a listener whose `hashCode()` changes after registration cannot be removed by instance. Scott's position — agreed as realistic — is that listeners implementing `equals`/`hashCode` is not a real-world pattern. The `getErrorListenerId()` / `getConnectionListenerId()` override is the documented remedy for anyone who hits it. This belongs in the javadoc, not just here.

**`ReadListener` gets no id method.** It stays single-slot, so nothing would consume one; a method with no caller cuts against the "simple as it should be" goal.

**Not possible: a `default toString()` returning the id.** Java forbids an interface from declaring a `default` for any `java.lang.Object` method — verified with javac: `error: default method toString in interface X overrides a member of java.lang.Object`. Every implementing class already inherits `Object.toString()`, and a class implementation always beats an interface default, so the default could never take effect; the language rejects it at declaration instead of silently doing nothing. It would not work conceptually either — `utils/DebugListener` implements both interfaces, and `toString()` has one signature, so it could not carry both ids. That is the same conflict the split names (`getErrorListenerId`/`getConnectionListenerId`) exist to avoid, except here it is unavoidable. The id methods already serve as the printable identity: anywhere the library needs to name a listener it calls them directly, and a user wanting pretty output overrides `toString()` in their own class.

**Rejected along the way** (recorded so it isn't re-litigated): a `ListenerRegistry<L>` class with minted ids; a `Map` keyed by `hashCode()` or by `identityHashCode + hashCode` (both are unstable or non-unique as *keys* — hash values belong in buckets, not key spaces); converting the interfaces to abstract classes (single inheritance kills `DebugListener`/`Listener`, and abstract classes can't be lambda targets); and a shared `Listener` base interface (can't carry per-interface names without recreating the diamond it was meant to avoid).

## Decisions — ALL SETTLED (Scott)

1. **D1 — Builder setter semantics: REPLACE.** `errorListener(a, b)` sets the list to `[a, b]`, matching every other `OptionsBuilder` setter and the copy constructor's assumption (1549-1551). Callers wanting to accumulate pass them together or build a `List`.
2. **D2 — `Options` getters go PLURAL.** `getErrorListener()` → `getErrorListeners()`, `getConnectionListener()` → `getConnectionListeners()`, both unmodifiable `List`; the singular forms are **dropped**, not kept as convenience. `getReadListener()` stays singular — the asymmetry is deliberate and gets a javadoc line. Breaking change to public `Options` API; fine pre-release.
3. **D3 — REMOVE the no-op `ErrorListener` default** (OptionsBuilder.java:1487-1488). Scott: "I've hated the default error listener — it's fine to not have any error listener." Empty list *is* the no-op, and a seeded instance would otherwise linger in the map forever. Consequences to handle: `Nats.java:277` becomes a loop over a possibly-empty list, and `OptionsTests.java:726` (`assertNotNull(o.getErrorListener())`) must change.
4. **D4 — Properties: SAME property key, now comma-separated.** Keep `PROP_ERROR_LISTENER_CLASS` / `PROP_CONNECTION_LISTENER_CLASS` as the only keys — no new plural property — and teach them to accept a comma-separated classname list, so multiple listeners are configurable from properties (O6). Existing single-value configs keep working untouched. This matches the codebase's own convention for multi-valued properties: `PROP_SERVERS` splits on `",\\s*"` at OptionsBuilder.java:183-186. A Java classname can't contain a comma, so the form is unambiguous. `PROP_READ_LISTENER_CLASS` stays single-valued.
5. **D5 — Registration order: UNORDERED.** `ConcurrentHashMap.values()` iteration order is arbitrary and that's accepted; do not document an order, and nothing may come to depend on one.
6. **D6 — Live adds do NOT write back to `Options`.** `Options` is immutable and is only the *seed*; a second `NatsConnection` built from the same `Options` starts from the options list, not from whatever was added live to the first. One javadoc sentence so it isn't surprising.

## Checklist

### I — Interfaces

- [x] **I1** Add `default String getErrorListenerId() { return "ErrorListener-" + hashCode(); }` to `ErrorListener` and `default String getConnectionListenerId() { return "ConnectionListener-" + hashCode(); }` to `ConnectionListener`. Javadoc must state what the id is used for (map key for add/remove on the connection), that overriding gives a stable/custom id, and the `equals`/`hashCode` caveat above. `ReadListener` gets nothing.
- [x] **I2** No overrides needed anywhere — distinct method names mean `utils/DebugListener.java:9` and `utils/Listener.java:21` compile untouched. Confirm that's true once I1 lands (it's the whole reason for the naming), and confirm no user-facing break to note in release notes.

### O — Options / builder

- [x] **O1** `Options`: `final ErrorListener errorListener` (:100) and `connectionListener` (:101) become unmodifiable `List<>` fields; assign in the ctor (:234-235). `readListener` (:102) unchanged.
- [x] **O2** `OptionsBuilder`: fields 107/108 become lists. Per type, a varargs setter `errorListener(ErrorListener... listeners)` plus `errorListeners(List<ErrorListener>)`, same for connection. Varargs keeps all 91 existing call sites compiling. **Null handling:** a null array clears (preserves today's "pass null to clear"); null elements inside the array are skipped. `readListener(ReadListener)` (1007) unchanged.
- [x] **O3** `Options` getters: 584 → `getErrorListeners()`, 592 → `getConnectionListeners()`, both unmodifiable; per D2 drop the singular forms. `getReadListener()` (600) unchanged. Fix the stale `ErrorListenerLoggerImpl` javadoc (:581) while here, and note the deliberate singular/plural asymmetry.
- [x] **O4** Builder copy constructor (1549-1550): copy the two lists **defensively** — a copied builder must not share a mutable list with its source. `readListener` (1551) unchanged.
- [x] **O5** Per D3, delete the no-op `ErrorListener` seeding in `build()` (1487-1488). **This removes a non-null guarantee two call sites currently rely on — both must be fixed in the same change or they NPE / fail:**
  - [x] **O5a** `Nats.java:277` — `options.getErrorListener().exceptionOccurred(null, ex)` assumes non-null. Fixed by O7 (loop the list).
  - [x] **O5b** `OptionsTests.java:726-729` — `assertNotNull(o.getErrorListener())` asserts exactly the behavior being removed, and `:728-729` then calls through it. Fixed by T1.
  - [x] **O5c** Verified there are no other consumers: the only `getErrorListener()` references in the tree are `Nats.java:277`, the five `NatsConnection` sites (all rewritten by E3/E4), and `OptionsTests`. Re-grep after O3 to confirm nothing new appeared.
- [x] **O6** Per D4, add a `classnameListProperty` helper to `OptionsProperties` (next to `classnameProperty`, :428) that splits on `",\\s*"` — matching `PROP_SERVERS` at OptionsBuilder.java:184 — maps each name through the existing `createInstanceOf` (:436), and hands the consumer the whole list once (so it lands on the replace-semantics setter as one call). Wire it at OptionsBuilder.java:236-237. Leave 238 (`PROP_READ_LISTENER_CLASS`) on `classnameProperty`. Document the comma-separated form in `OptionsProperties`.
- [x] **O7** `Nats.connectAsynchronously`: `getConnectionListener() == null` (Nats.java:269) → `getConnectionListeners().isEmpty()`, message unchanged; `getErrorListener().exceptionOccurred(null, ex)` (:277) → loop the list. Keep both on `Options` (pre-connection, no `NatsConnection` exists) and comment why.

### C — ConnectionListener on the connection

- [x] **C1** Change `connectionListeners` (:68) from `Collection<ConnectionListener>` to `Map<String, ConnectionListener>` (`ConcurrentHashMap`); seed by looping `options.getConnectionListeners()` (135-138). **Note:** two seeded listeners sharing an id means last-one-wins — acceptable per the accepted limitation, but worth a comment at the seeding loop.
- [x] **C2** `addConnectionListener(@NonNull)` (1732) → `put(cl.getConnectionListenerId(), cl)`; `removeConnectionListener(@NonNull)` (1741) → `remove(cl.getConnectionListenerId())`. **Both keep their existing `void` signatures — no API change.** Add `removeConnectionListenerById(String id)` (see the deviation note at the top - a `String` overload of `removeConnectionListener` is ambiguous with the instance form for a `null` literal).
- [x] **C3** `processConnectionEvent` (2121) iterates `connectionListeners.values()`; keep one `makeCallback` per listener.

### E — ErrorListener on the connection

- [x] **E1** Add `Map<String, ErrorListener> errorListeners` (`ConcurrentHashMap`), seeded from `options.getErrorListeners()` in the ctor next to the connection listeners.
- [x] **E2** Public `addErrorListener(@NonNull ErrorListener)` → `put(el.getErrorListenerId(), el)`, `removeErrorListener(@NonNull ErrorListener)` → `remove(el.getErrorListenerId())`, `removeErrorListenerById(String id)` — mirroring C2.
- [x] **E3** Rewrite `notifyErrorListener(ErrorListenerCaller)` (:2103) to iterate `errorListeners.values()` with **one `makeCallback` per listener** — the single fan-out point. All 9 remote callers get multi-listener for free, no signature change.
- [x] **E4** Route the 4 direct sites through it: 2009 `messageDiscarded`, 2077 `slowConsumerDetected`, 2082 `exceptionOccurred`, 2096 `errorOccurred`. After this, zero `options.getErrorListener*` references remain in `NatsConnection`. **Watch:** those lambdas capture `this` today — use the `ErrorListenerCaller`'s `conn` parameter instead.

### R — ReadListener (stays single — minimal work)

- [x] **R1** No multi-listener work, no `getId()`, no `volatile` work (`NatsConnectionReader.java:67` is already correct), no builder or `Options` change.
- [x] **R2** Fix the `currentUserRl` bug: have `setReadListener` (NatsConnectionReader.java:91) also set `currentUserRl = rl`, so a later repoint compares against what's actually installed rather than the previous connection's `Options`. Decide and document the precedence in the `refreshReadListener` comment block (:110-118) — does a runtime `setReadListener` win over a later repoint's `Options`, or does the repoint win? Low priority: `setConnection` has no production caller yet.
- [x] **R3** `NatsConnection.setReadListener` (:198) has no javadoc and no nullability annotation while accepting `null` as "clear". Add javadoc + `@Nullable`. Optionally add `removeReadListener()` as a clearer spelling of `setReadListener(null)`.

### T — Tests

- [x] **T1** `OptionsTests` — uses the singular getters (726-751) and will not compile after O3. Update, and add coverage for varargs + `List` setters, null/empty handling, D1's replace semantics, and the D4 comma-separated property form (single value still works; two classnames both load). **Per O5b, `assertNotNull(o.getErrorListener())` (:726) inverts** — with no error listener configured, `getErrorListeners()` must now return an **empty** list, not a list holding a no-op. Assert that explicitly rather than deleting the assertion.
- [x] **T2** `ErrorListenerTests` (339 lines) — live add/remove: add after connect and confirm delivery; remove and confirm silence; two listeners both fire; remove one, the other still fires; remove by id; removing an unknown id is a no-op; adding the same instance twice is idempotent (same id → same map entry).
- [x] **T3** `ConnectionListenerTests` (154 lines) — same matrix, plus multiple listeners supplied via `Options`. :116 already covers throw-isolation for connection listeners; add the equivalent for error listeners (relies on `makeCallback`'s per-task try/catch).
- [x] **T4** `getErrorListenerId()` / `getConnectionListenerId()`: default id is stable across calls on one instance and differs between two instances; an anonymous listener produces a usable id; an overridden id is what the connection keys on, so two otherwise-identical listeners with distinct overridden ids both register and remove independently. Also cover a both-roles class (`DebugListener`) registering in both maps under its two independent ids.
- [x] **T5** `NatsConnectionReaderRepointTests` — add the R2 case: runtime `setReadListener`, then repoint to a connection whose `Options` ReadListener differs.
- [x] **T6** **The D3 state — zero error listeners — is newly legal and needs its own coverage.** Previously impossible (the builder always seeded a no-op), so nothing exercises it today. Connect with no error listener configured and drive the paths that used to be guaranteed a target: `processError` (server `-ERR`), `processException`, `processSlowConsumer`, `queueOutgoing`'s `messageDiscarded`, and at least one `notifyErrorListener` caller. All must be silent no-ops, not NPEs. Include `Nats.connectAsynchronously` with a failing connect and zero error listeners (O5a's site — it runs on its own thread, so an NPE there is swallowed and would otherwise go unnoticed).
- [x] **T7** Run `core` + `jetstream` suites — the 9 `notifyErrorListener` callers live in jetstream (`MessageManager`, `PullMessageManager`, `PushMessageManager`) and `SocketDataPort`.

## Estimate

Small. Two `default` methods on two interfaces + `Options`/`OptionsBuilder` (2 listener types × 2 setters, getters, copy ctor, one new property helper) + `NatsConnection` (two `ConcurrentHashMap` fields, ~6 add/remove methods that are one line each, 2 fan-out paths) + a 3-line `Nats.java` change + two small ReadListener fixes. No new classes. Tests are the long pole: `OptionsTests` is forced by O3, and the live add/remove matrix is new for both multi types.

No hot-path risk — that was ReadListener's per-message fan-out, which is no longer being changed.
