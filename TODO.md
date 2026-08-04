# Instructions

The single source of truth for the things currently in progress — so any session (or a fresh read) gets an accurate, current recap instead of a stale one.

- In the `# Recap` section...
  - Exactly **one** implementation item lives here at a time. `In progress` means a session is implementing it, or has implemented it and it's under review. I will usually do this myself.
      - Section is `## Current Implementation`
  - Whenever an audit or plan is written for the first time, add it to the TODO.md as the last entry under `## Plans / Audits`
  - Whenever an audit or plan is written or updated, update the `#Last Plan Made` item
- Nothing work in progress is tracked here. It's only tracked in the audit or plan as I ask you to mark things done.
- Not pushed live: re-read this when you begin or resume work.

**Implementation State** is one of:
- **In progress** — a session is actively writing the change.
- **Under Review** — implemented, not yet committed; the working tree is being reviewed.
- **Committed** — committed; the commit(s) are being built
- **Closed** — reviewed and accepted, build passed; ready to be replaced by the next item.

# Recap

## Current Implementation

**Item:** PUBLIC_API_MISSING_JAVADOC_AUDIT.md — **COMPLETE, uncommitted.** Tier 1 went **142 → 0**; `:core:javadoc :jetstream:javadoc :service:javadoc` report 0 tier-1 warnings and 0 errors; `compileJava`/`compileTestJava` clean. 22 files, +476 lines, **comments only — no behavior change**, so no test run was needed (plan item V3). Covered: `Status` (55, class + ctors + accessors + all protocol constants), `KeyValueUtils` (23), `ObjectStoreUtil` (16), `KeyValue` (10), `OptionsProperties` (7), `StreamCreator` (5), `HostnameResolveMode` (4), the kv/os management + watch-subscription classes, the `jetstream/api` copy-ctors and `PushDeliverSubjectInterface`, and `ServiceConstants`. The 570 `impl`/`utils` warnings are **deliberately out of scope**.

**Two traps recorded in the audit for any future doc pass:** (1) **javadoc must precede annotations** — anchoring inserts on the signature put 11 blocks between `@NonNull` and the declaration in `KeyValueUtils`, which javadoc silently ignores (the file still showed 23 warnings after being "documented"); (2) **never add `@throws` without checking the signature** — assuming the KV/OS *Management* ctors threw produced 8 `error: exception not thrown` (the *WatchSubscription* ctors do declare them, the Management ones do not). Both were caught only because the javadoc warning count was re-run as the oracle after every step.

**Extended past tier 1 (Scott's call):** the validators and the client-error catalogue were added by name — `Validator` 49, `JsValidator` 25, `JetStreamClientError` 23 = **97 more, all now 0**. Running total **712 → 473**, tier 1 still 0, javadoc errors 0, compile clean.

**Gradle question answered (investigated, NOT applied — see the audit).** `-Xdoclint/package:-io.synadia.client.utils,-io.synadia.client.impl` works (core 337 → 0); the Javadoc task's `exclude` does **not** (both glob and source-relative forms left all 90 `NatsConstants` warnings). Two caveats make it a trade rather than a win: it disables *every* doclint group for those packages, so broken `@link`/HTML stops being reported (the `<pUnlike` bug lived in `impl`); and scoping is per-package with no per-file granularity, while `NatsConstants`, `Validator` and `JsValidator` all sit in `io.synadia.client.utils` — so nothing can silence the constants wall while still enforcing the validators. Untested alternative: Java 9+ `@hidden` on the two constants classes.

**Still out of scope: 473** missing-comment warnings, all `impl`/`utils`, of which `NatsConstants` (90) + `JetStreamConstants` (58) are 148.

**IMPORTANT SCOPE CORRECTION — a second warning category exists.** Everything above measured only `warning: no comment` (no doc comment at all). Javadoc separately reports **263** *incomplete*-comment warnings, and **161 of them are in tier 1** — so tier 1 is done for **missing** javadoc, NOT for **well-formed** javadoc. Do not describe the public API as fully documented. Six were mine and are already fixed (3 `@param <T>` in `Validator`, 3 `@param` on `classnameListProperty` — the latter shipped incomplete in the pushed listener commit `7e203856`). Queued as a follow-up under `## Plans / Audits` at Scott's request.

**State:** Under Review (uncommitted).

**Previously closed:** `PLAN_FORCE_RECONNECT_READER_STOP.md`, pushed as `e64ad979`.

## Open / carried forward

**Flaky tests — OPEN, monitoring, nothing changed. Builds green since.** Watch rather than act on an unreproduced hypothesis; recent runs pass, so the rate is low and does not justify changing tests on a theory. On the list: `TLSConnectTests.testProxyTlsFirst` / `.testReconnectFailsAfterCertExpires` / `.testForceReconnectFailsAfterCertExpires`, `WebsocketConnectTests.testTLSOnReconnect`, `AuthTests.testJWTAuthWithCredsFileAlso`, `JetStreamPushTests.testDeliveryPolicy` / `.testAcks`, `SimplificationTests.testFetchOrdered` / `.testFetchDurable`, `ReconnectTests.testForceReconnectQueueBehaviorCheck` (added 2026-08-04 — flaked once during the javadoc pass, passed on retry and clean rerun; ruled out as caused by it since that diff had zero non-comment lines; ~29s and deliberately timing-sensitive via `ForceReconnectQueueCheckDataPort.DELAY = 75`). **No test or product code has been changed for these:** `CLIENT_CERT_VALIDITY_MILLIS` is still `5000` and the WAIT ladder / `connectionTimeout` are untouched, so the tension in `FLAKY_TESTS_ANALYSIS.md` ("Concrete lead") is still live. Revisit if the rate rises or one starts failing *consistently* — `testConnectPendingCountCoverage` is the precedent for "fails every time = real signal, not a flake".

**Release-note item, not yet written up:** `forceReconnect` may now take marginally longer (measured ~+0.6s in one scenario), because it no longer proceeds while the previous reader/writer threads are still running. That is the fix working, not a regression — detail in `PLAN_FORCE_RECONNECT_READER_STOP.md`.

## Recently Closed
* **PLAN_FORCE_RECONNECT_READER_STOP.md** — `e64ad979`. `forceReconnectImpl` now stops reader/writer **first** (capturing both `stopped` futures), then submits the async port close unchanged, then joins both with `options.getConnectionTimeout()` millis instead of `.get(100, MILLISECONDS)`. Ordering is the correctness half: with `running` already false, the IOException the close raises in the blocked reader reads as an expected shutdown rather than a comm issue. Covered by `ReconnectTests.testForceReconnectWaitsForStaleReaderToStop` — **verified to fail 5/5 without the fix** and pass with it, via a worktree at `1680430c`. Full as-built detail, the timing measurements, and the false-negative first draft of that test are in the plan doc.
* **`Nats.connectAsynchronously` returns a future** — `5f754250`. Now returns `CompletableFuture<NatsConnection>` (completes only on successful connect, exceptionally on failure), plus an `Executor` overload. Dropped the `IllegalArgumentException` for a missing `ConnectionListener` and the vestigial `throws InterruptedException`. Javadoc discourages taking the handle from the ConnectionListener and warns against passing any `Options` executor.
* **`package-info.java` javadoc fix** — `1680430c` (message says "unflaking tests" but the commit contains only that one file). `:core:javadoc` reports zero errors as a result.
* **LISTENERS_ON_THE_FLY_PLAN.md** — `7e203856`. `ErrorListener` + `ConnectionListener` are multi-listener via `getErrorListenerId()` / `getConnectionListenerId()` defaults and keyed `ConcurrentHashMap`s; `Options` carries unmodifiable `List`s with varargs + `List` setters; the no-op `ErrorListener` default is gone (zero error listeners is legal); comma-separated classname properties. `ReadListener` stays single. Rejected alternatives are recorded in the plan doc.
* **EXCEPTIONS_AUDIT** — A1–A23 + A20/A21; detail in `EXCEPTIONS_AUDIT.md` §7a/§8. Pushed in 5 parts, last `1f21ac26`. D4 (`.impl`→`.api` exception move) is **not** standalone — subsumed into `PLAN_CORE_JETSTREAM_BOUNDARY.md`.

*(`TODO.md` is tracked as of `c86b0387` — edits to it are now commit-eligible and backed up. **Every other working doc in the repo root is still untracked**, including all the plan/audit files listed under `## Plans / Audits` below: `PLAN_FORCE_RECONNECT_READER_STOP.md`, `FLAKY_TESTS_ANALYSIS.md`, `EXCEPTIONS_AUDIT.md`, `PLAN_CORE_JETSTREAM_BOUNDARY.md` and the rest. They exist only in the working tree with no backup, so anything that must survive belongs either in a commit or in this file.)*

## Last Plan Made

**Item:** PUBLIC_API_MISSING_JAVADOC_AUDIT.md — **work plan added (S/KV/OS/JS/C/V groups), audit re-verified and its numbers corrected.** Key change: the original audit used a comment/brace **heuristic**; the plan re-measures with **javadoc's own `warning: no comment`**, which is compiler-exact and dissolves the audit's caveats 1-2. **True total is 712, not the heuristic's 1232.** **Trap to avoid: javadoc caps output at 100 warnings per module** — a plain `:core:javadoc :jetstream:javadoc :service:javadoc` reports only **102** and looks nearly solved; lift it with an `-Xmaxwarns` init script (command in the doc). **Key insight — the tool's scope already matches the audit's intent:** doclint flags undocumented `public` members but **not `protected`** ones (verified: `ConsumerCreator.java` = **0** warnings despite ~40 undocumented protected builder fields), which is exactly what the audit's §5 said to exclude. So "zero `no comment` warnings in tier-1 packages" is a **machine-checkable definition of done**. That also explains the audit-vs-javadoc gap in `jetstream/api` (81 heuristic vs 14 real). **Tier 1 = 142 warnings across 21 files**, dominated by `Status.java` (55), `KeyValueUtils` (23), `ObjectStoreUtil` (16), `KeyValue` (10); tier 2/3 (`core/utils` 257, `jetstream/impl` 169, `core/impl` 80, `jetstream/utils` 64 = 570) stays out of scope. **kv/os still ship inside jetstream** — `settings.gradle:20` has `// include 'kv'` commented out and top-level `kv/`+`os/` hold only READMEs — so documenting them now is not blocked by the deferred split ([[project_kv_os_split]]). **One decision needed before starting:** whether to document public constants (~66 of the 142, mostly `Status` protocol codes + `StreamCreator` defaults). Recommend yes with terse one-liners (`{@value}` makes it cheap) since it is the only path to a zero count; if declined, the target becomes "zero non-constant warnings" and must be stated so it is not re-litigated later.

**Item:** PLAN_FORCE_RECONNECT_READER_STOP.md — **DONE and pushed as `e64ad979`; the doc now records the as-built fix, the before/after timings, and the T3/T4 verification.** (Historical: re-verified against the tree, bug was still present, work WAS needed.) Added a verification-log table (10 claims, all CONFIRMED) plus **action items A1-A5 / T1-T4**. Corrected drifted line numbers: `tryToConnect`'s reader re-join guard is `NatsConnection.java:531` (was 516), `cleanUpPongQueue()` `:540` (was 525), `Options.getConnectionTimeout()` `:835` (was 831). The live defect: `forceReconnectImpl` (`:332-392`) submits the async port close at `:355-371` **before** stopping i/o at `:373-385`, and both joins are still `.get(100, TimeUnit.MILLISECONDS)` (`:375`, `:381`). **The original draft's caveat is discharged** — the V3 reader lifecycle was read directly and matches V2: `stop(false)` clears `running` without `shutdownInput()` (`NatsConnectionReader.java:191-204`), the comm-issue guard is `if (running.get())` (`:257-260`), and `finally { running.set(false) }` (`:267-268`) is the flag stomp. **Two findings beyond the original plan:** (1) V2's `reader.isRunning() ? ... : null` guard is **unnecessary in V3** — `stopped` is initialized to an already-completed future in the reader ctor (`:76-78`), so joining a never-started reader returns immediately; capture unguarded (A1 confirms the writer does the same). (2) A **deliberate behavior call** is needed — today a missed 100 ms join is swallowed by `processException` + proceed, which is exactly what hides this race; after the change a timeout means a thread refused to die within the *connection timeout*. Recommend keeping `processException` + proceed so forceReconnect never starts throwing where it did not before. **Test note:** `ReconnectTests` (forceReconnect coverage `:710`, `:797`) and `TLSConnectTests` are the regression suites, and **both are on the current flaky watch list** (`TLSConnectTests` has three entries) — T1 requires a clean baseline first so a known flake is not misread as a regression. T3 is cheaper than the plan assumed: `ForceReconnectQueueCheckDataPort` (has a static `DELAY`) and `SocketDataPortBlockSimulator` already exist in `core/src/test/java/io/synadia/client/impl/` and should be extended rather than writing a third data port.

**Item:** FLAKY_TESTS_ANALYSIS.md — added a **"Second observation pass — local WSL Linux"** section, marked **STATUS: OPEN — monitoring, nothing changed** (Scott's call; no test or product code touched, `CLIENT_CERT_VALIDITY_MILLIS` still `5000`, ladder untouched, so these are expected to keep flapping). Eight tests flapped during the `connectAsynchronously` work, none overlapping the original CI set: `TLSConnectTests.testProxyTlsFirst` / `.testReconnectFailsAfterCertExpires` / `.testForceReconnectFailsAfterCertExpires`, `WebsocketConnectTests.testTLSOnReconnect`, `AuthTests.testJWTAuthWithCredsFileAlso`, `JetStreamPushTests.testDeliveryPolicy` / `.testAcks`, `SimplificationTests.testFetchOrdered`. Recorded honestly as an **observation log, not a diagnosis** — verified only that none reference `connectAsynchronously` and each passes on rerun; per-test mechanisms were not traced. **Hypothesis checked and rejected:** they do NOT share the first pass's `managedConnect` CLOSED-retry gap — all five connect-side tests call `Nats.connect(...)` directly or drive a `ProxyConnection`, so the still-unimplemented helper fix would not have prevented any of them. **The real lead (Scott's steer that load sensitivity was already addressed):** these flap *despite* the first pass's global budget increases (`connectionTimeout` 4000→10000, WAIT ladder to 11000/14000/18000/26000), so "give it more time" is spent. Worse, for the cert-expiry pair the bump looks **causal**: `CLIENT_CERT_VALIDITY_MILLIS = 5000` (TLSConnectTests:485) is the entire premise of those tests, and `connectionTimeout` is now **2.0×** and `DEFAULT_WAIT` **2.2×** that window — so a slow setup is now *permitted* to straddle the expiry boundary where before it failed fast. Hypothesis, not reproduced. Fix candidates: scale `CLIENT_CERT_VALIDITY_MILLIS` above the ladder (preferred — the test wants "expires while connected", not "expires during connect"), or give those two a locally reduced timeout. **General lesson recorded:** a global timeout bump is only safe for tests asserting something *succeeds within* a budget; tests asserting something *fails after* a deadline have the opposite polarity and can be silently invalidated. Worth scanning for other deadline-polarity tests before raising the ladder again.

**Item:** LISTENERS_ON_THE_FLY_PLAN.md — **re-verified against current code, rescoped with Scott, full checklist (I/O/C/E/R/T)**; next item up for implementation. **SCOPE (settled): `ErrorListener` + `ConnectionListener` go MULTI** (several in `Options` via varargs + `List` setters, plus live add/remove on the connection); **`ReadListener` STAYS SINGLE** — Scott's call, it's a debug hook not a production path, so it keeps one slot (set in builder, set/remove on connection) and gets only two small fixes. Verified state: (1) ConnectionListener is **half done** — live add/remove exists (collection :68, seeded 135-138, add/remove 1732/1741, per-listener `makeCallback` fan-out `processConnectionEvent` 2121) but the builder still takes one. (2) ErrorListener has nothing — `final` on `Options`, builder seeds an anonymous no-op (1487) so `getErrorListener()` is never null; 4 direct `NatsConnection` sites (2009/2077/2082/2096) + `notifyErrorListener` fan-out (2103) feeding 9 remote callers (`SocketDataPort` 1; jetstream `MessageManager` 1 / `PullMessageManager` 4 / `PushMessageManager` 2); `Nats.java:269/277` are pre-connection and stay on `Options`. (3) ReadListener: the **`volatile` gap is ALREADY FIXED** (NatsConnectionReader.java:67) — do not re-fix; the repoint feature landed since (`currentUserRl` :71 + `refreshReadListener` :119-141) and carries a latent bug (`setReadListener` never updates `currentUserRl`, so a repoint to a connection with a different `Options` ReadListener silently stomps a runtime-set one) — now that ReadListener stays single this is NOT deleted by the work and needs the explicit small fix in R2. **ID DECISION (Scott, final): DEFAULT ID METHODS ON THE INTERFACES, NO REGISTRY CLASS, NO BASE INTERFACE** — **each interface gets its OWN differently-named default id method** — `ErrorListener.getErrorListenerId()` → `"ErrorListener-" + hashCode()`, `ConnectionListener.getConnectionListenerId()` → `"ConnectionListener-" + hashCode()`. **Interface name as a literal prefix, NOT `getClass()`** (Scott: simpler, and it sidesteps anonymous classes, where `getSimpleName()` returns `""` and would degrade an id to `"-12345"`). `ReadListener` gets nothing (single-slot, no consumer for an id). `NatsConnection` stores a plain `Map<String,L>` (`ConcurrentHashMap`) keyed by that id; `addErrorListener` = `put(el.getErrorListenerId(), el)`, **remove-by-instance** = `remove(el.getErrorListenerId())` (direct keyed removal, no scan, no reverse index), plus a `remove(String id)` overload; same shape with `getConnectionListenerId()` for connection listeners. `addConnectionListener`/`removeConnectionListener` keep their existing `void` signatures — no API change. Interfaces stay interfaces, so `ConnectionListener` remains functional (lambdas at ConnectionListenerTests:116 / InfoHandlerTests:130 keep working) and the multi-interface classes survive — `utils/DebugListener.java:9` (**main, shipped**) implements all three, test `utils/Listener.java:21` implements two; both would be IMPOSSIBLE as abstract classes (single inheritance), which is why the abstract-class idea was dropped. **WHY two names and not one `getId()`: the diamond.** A single `default getId()` on two unrelated interfaces means any class implementing BOTH inherits two unrelated defaults for one signature — compile error (JLS 9.4.1.3) until it overrides. That would have hit `utils/DebugListener.java:9` (main, implements all three) and test `utils/Listener.java:21` (two) immediately, plus any USER class implementing both on upgrade. A shared `Listener` base does NOT fix it — any per-interface override recreates the same conflict one level down (explored and dropped). **Distinct names (`getErrorListenerId`/`getConnectionListenerId`) are different signatures, so there is no conflict, no forced override, and NO user break at all** — and a both-roles class correctly gets two independent ids, one per map (the maps are separate, so ids need only be unique within their own map). **Accepted limitation, deliberate:** the default id derives from `hashCode()`, so listeners that override `equals`/`hashCode` to compare equal share an id and evict each other, and a mutable `hashCode()` makes a listener unremovable-by-instance — Scott's call that this isn't a real-world pattern; the `getErrorListenerId()`/`getConnectionListenerId()` override is the documented remedy and belongs in the javadoc. **Superseded/retracted along the way:** the `ListenerRegistry<L>` + minted-id design (gone entirely), and the `volatile L[]` snapshot requirement (existed only for `ReadListener.message` on the per-message read loop NatsConnectionReader:439 — both remaining fan-outs are cold, so `ConcurrentHashMap.values()` suffices; D5 covers ordering if ever wanted). **D4 answered (Scott's properties question re OptionsBuilder 236-237): reuse the SAME property, comma-separated** — the codebase already does this for `PROP_SERVERS` (splits `",\\s*"` at OptionsBuilder:184), classnames can't contain commas, existing single values keep working, and a separate plural property would need a precedence rule; add a `classnameListProperty` helper beside `classnameProperty` (OptionsProperties:428) reusing `createInstanceOf` (:436). **ALL DECISIONS D1-D6 NOW SETTLED (Scott) — plan is ready to implement, nothing outstanding:** **D1** builder setters **REPLACE** (matches every other setter + the copy ctor). **D2** `Options` getters go **PLURAL** — `getErrorListeners()`/`getConnectionListeners()`, singular forms **dropped** (breaking, fine pre-release); `getReadListener()` stays singular, deliberate asymmetry. **D3 REMOVE the no-op ErrorListener default** (OptionsBuilder 1487-1488) — Scott: "I've hated the default error listener, it's fine to not have any"; empty list IS the no-op; forces `Nats.java:277` to loop a possibly-empty list and `OptionsTests:726` `assertNotNull` to change. **D4** same property key, **now comma-separated** (confirmed: multi-value support IS in scope, O6 stands). **D5 unordered** — `ConcurrentHashMap.values()` order is arbitrary and accepted; do NOT document an order or let anything depend on one. **D6** live adds do not write back to `Options`. Blast radius: 91 `.errorListener(`/`.connectionListener(`/`.readListener(` call sites, all tests + `OptionsUtils` — **varargs keeps all 91 source-compatible**; `OptionsTests` 726-751 is forced to change by D2. Also fix stale `Options.getErrorListener()` javadoc (:581) naming a nonexistent `ErrorListenerLoggerImpl`.

**Item:** PLAN_CORE_JETSTREAM_BOUNDARY.md — the Core↔JetStream boundary & package topology plan (was `## Plans / Audits TBD` item 1; now written). Design/plan only, no code; still gated on settling in-flight work ([[project_connection_removal]] especially). Built on a **measured** coupling map, which corrects the old TODO estimate: `MessageManager`/`PullMessageManager`/`NatsMessageConsumer` are jetstream-*internal*, not core coupling. Real core-internal surface (jetstream files/refs): `NatsConnection` 18/35, `NatsDispatcher` 12/22, `Headers` 11/70, `NatsMessage` 5/5, then `NatsSubscription`/`StatusException`/`NatsSubscriptionFactory`/`JetStreamMetaData`; utils `ApiUtils` 21, `Validator` 17, `NatsConstants` 12. Every touched core type is a `public class` in `.impl`, so the boundary is currently unenforced in BOTH directions; only `module-info` with selective exports closes it. On `NatsConnection` all but **four** called members are already public — the true internal reach is just `createSubscriptionInternal` (pkg-private) + `getScheduledExecutor`/`isClosed`/`isClosing` (protected). Plan splits the coupling into 3 buckets (A user-like connection ops → public connection surface; B user-facing value types `Headers`/`NatsMessage` → promote to public; C shared utils → common home), **recommends** distinct per-module internal packages + `module-info` (rejects flatten-up-one-level as making the split worse; that's the enforcement ratchet, applied LAST after the API exists), and shows D4 (move `JetStreamApiException`/`JetStreamStatusException` `.impl`→`.api`) falls out of Phase 4. Subsumes `OSGi_JPMS_TODO.md` O3 — don't solve separately. Phases 0–5 in the doc. **§8 "clean up core first" design exploration** added: (1) public methods currently leak `.impl` return types (`createDispatcher`→`NatsDispatcher`, `subscribe`→`NatsSubscription`, `publish(NatsMessage)`) — retype to the already-public `Message`/`Dispatcher`/`Subscription` interfaces (covariant, safe); (2) irreducible internal tail is just TWO one-call-site needs, and Scott's steer is **Strategy 1 = promote to public** for both (a qualified-export SPI would contradict the "JS uses core like any user" goal — a user can't consume a qualified export): **executors → public, documented "use with care"**; **subscription factory → public + rename** (`createSubscriptionInternal`→`createSubscription`, `NatsSubscriptionFactory`→`SubscriptionFactory`). Caveat: the factory hook is inheritance (`JetStreamPushSubscription`→`JetStreamSubscription`→`NatsSubscription`→`NatsMessageSink`), so promoting it publishes core's subscription base as a "bring your own Subscription" extension API (drags `NatsMessageSink`, bakes `Connection`/`Dispatcher` into the public ctor). Alternative to avoid that = JS composes a core `Subscription` instead of subclassing (bigger refactor). **Refinement (Scott):** this hook is SPI-shaped ("for jnats client-lib developers, not average users") but **Scott rejects special packaging** — no `.spi` package / `ServiceLoader` / qualified-export machinery, since that's for third-party providers discovered at runtime (JDBC/SLF4J) and here the only provider is us (an "ecosystem of one"). **Decision: lightweight = (1) make overload public + rename off `Internal` → `subscribe(subject,queue,dispatcher,SubscriptionFactory)`; (2) label advanced via `@apiNote` javadoc (matches Options' existing "advanced setting" convention); (3) optional marker annotation later.** Key point: packaging is only a signpost — promoting the factory makes `NatsSubscription` a public subclassable base regardless of package; the only real GATE is qualified export (needs module-info + closed first-party audience), which we skip unless we ever want a hard boundary. (3) the `Connection` interface is already REMOVED ([[project_connection_removal]] done — `NatsConnection implements AutoCloseable` only), and the boundary does NOT require re-creating it — a concrete public `Connection` class + qualified SPI hides internals just as well; reintroduce the interface only for an independent reason (multiple impls / mockability), cheap to add later. Most value (kill type leak, shrink tail to one hook) lands BEFORE the disruptive module rename.

**Item:** PLAN_FORCE_RECONNECT_READER_STOP.md — V3 port of `FORCE_RECONNECT_AUDIT.md` Finding 1 (the audit lives in the java-active-passive repo; V2 fix already implemented in nats.java as the reference). `forceReconnectImpl` stops the reader with `reader.stop(false)` (clears `running`, no `shutdownInput`), closes the port on an async task, then joins only **100 ms** and proceeds regardless. Because `stop(false)` pre-clears `running`, the downstream `tryToConnect` re-joins the reader only `if (reader.isRunning())` — now false — so a reader still alive past the 100 ms can later throw, get `running` flipped back true by the new `start()`, fire `handleCommunicationIssue` on the now-healthy connection, and stomp the shared `running` flag. Fix: stop reader/writer **before** the close (correct shutdown ordering), capture the stopped-futures, join with the **full connection timeout** instead of 100 ms. **V3-specific:** `Options.getConnectionTimeout()` returns a `long` millis (not V2's `Duration`), so use `.get(millis, MILLISECONDS)` — the V2 `.toNanos()` line will not compile. Rest of the AP issue set (3/5/7/9/10/11/12) verified NOT to reproduce on this path (routes through `tryToConnect` → `cleanUpPongQueue` + fresh ping; drives own status to DISCONNECTED so `pingTask` short-circuits). Plan only, nothing implemented.

## Plans / Audits
* INTEGRATION_PLAN_PR_1578.md 
  * Reconnect Delay Behavior and options cleanup
  * [#1578](https://github.com/nats-io/nats.java/pull/1578) 
* PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md
* UNDERSCORE_INTERNAL_NAMING_AUDIT.md
* INTERFACE_DEFAULT_METHODS_AUDIT.md
* FLAKY_TESTS_ANALYSIS.md
  * maybe ask claude to look for other potential flaky - maybe by pointing to the build history of V2
* INTERFACES_REPORT.md
    * Dispatcher unsubscribe - Every Subscription is a NatsSubscription. Review this when looking at INTERFACE
* REQUEST_BEHAVIOR_IMPROVEMENT.md
  * ??? Status
* VIRTUAL_THREAD_DISPATCHER_EXAMPLE.md
* CANCEL_ACTION_REVISIT.md
* PUBLIC_API_MISSING_JAVADOC_AUDIT.md
  * missing-comment pass DONE (712 -> 473; tier 1 = 0; validators + JetStreamClientError also done)
  * **FOLLOW-UP QUEUED: incomplete-javadoc warnings (263)** — a category distinct from the missing-comment work. Comments that exist but lack tags. **161 are in tier 1**, so "tier 1 = 0" covers missing comments only. Types: `no main description` 151, `no @param` 73, `no @return` 18, `use of default constructor` 11, `no @throws` 10. Concentrated in `impl/JetStream.java` 62, `api/ConsumerConfiguration.java` 35, `api/ConsumerCreator.java` 34, `client/OptionsProperties.java` 20, the kv/os Configuration+Creator pairs ~43, `impl/NatsConnection.java` 15
  * **Main descriptions get fixed** (Scott). Nothing exempted, so tier 1 for this category is the full **161** — 134 main descriptions + 27 missing tags. Descriptions must add what the tag does not (meaning, unit, default, constraint); restating the tag is not a fix
  * **Group M DONE (27 missing tags):** `OptionsProperties` 20, `ObjectStore` 4 `@throws InterruptedException`, 3 class-level `@param <T>`. 263 → 236 overall; tier 1 161 → 134, and that 134 is exactly group N. Checklist lives in the audit: **D/M/N/X/V groups**. M (27 missing tags, 5 files) is independent of N (134 main descriptions, 8 files); N1 settles the wording convention on one fluent Configuration/Creator pair and the rest follow
  * **Oracle:** same `-Xmaxwarns` init script but `grep "warning:" | grep -v "no comment"`. Lesson from this pass: `no comment` alone is NOT a sufficient oracle — it hid 263 warnings including 6 of my own
* PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md
  * remove intrusive `NatsMessage.next`; `accumulate` fills a reusable `MessageBatch` instead of chaining
* PLAN_FLUENT_SUBSCRIBE_BUILDER.md
  * fluent JetStream subscribe/subscription builder — explored + reverted; recommends source-first builder over the reverted no-arg builder
* OSGi_JPMS_TODO.md
  * 3 split packages across core+jetstream (`io.synadia.client.api`, `.impl`, `.utils`) → the jars can't sit on the JPMS module path together; OSGi resolves only one wiring
  * **O2 DONE:** every subproject published as `io.synadia:jnats3` (root hardcoded `"jnats3" + jarEnd`) — so `jnats3-jetstream`'s POM depended on *itself*, not on core. Now per-module `artifactNameExt` → `jnats3-core` / `-jetstream` / `-service` / `-examples`. kv/os get `jnats3-kv` / `jnats3-os` when split out
  * O1 open — examples are repo-only but the build still declares a publication for them. A `publishExt` fix was written then reverted (Gradle changes scoped to artifact id only); if picked up, name it `libraryExt` — every library always publishes, so the only real predicate is "is this a library"
  * O3 (split-package strategy) still open — a breaking rename if taken, so V3 or never
* PLAN_FORCE_RECONNECT_READER_STOP.md
  * V3 port of `FORCE_RECONNECT_AUDIT.md` Finding 1 — `forceReconnectImpl` joins reader/writer only 100 ms then proceeds; a reader that outlives that window can fire `handleCommunicationIssue` on the now-healthy connection and stomp the shared `running` flag
  * fix: stop before close (ordering) + join with full connection timeout; **V3 adaptation** — `getConnectionTimeout()` is `long` millis, not a `Duration`
  * V2 already implemented in nats.java as the reference; plan only on V3
* PLAN_CORE_JETSTREAM_BOUNDARY.md
  * Make JetStream consume core only through public API; kill the split-package reach (`.impl`/`.api`/`.utils` shared by both jars, no `module-info`)
  * measured coupling map replaces the old estimate; 3 buckets (connection ops / value types / shared utils) + recommend distinct per-module internal packages + `module-info` as the enforcement ratchet
  * subsumes D4 (JS exception `.impl`→`.api`) and `OSGi_JPMS_TODO.md` O3; gated on [[project_connection_removal]]

## Plans / Audits TBD

1. ObjectStore line 107 / ObjectStore nullability


## More

### JetStream
- THIS NEEDS REVIEW 
  - static class AsyncMessageHandler implements MessageHandler
- unused parameter
  - private CompletableFuture<PublishAck> publishAsyncInternal(String subject, @Nullable Headers headers, byte @Nullable [] data, @Nullable String sData, @Nullable PublishOptions options, boolean validateSubjectAndReplyTo) {

### Options
- server/servers

### Tests ???
- nextMessage variations specifically testing edges
- audit for untested messages (after finished porting tests)

### IterableMessageConsumer
should this require a timeout

### NatsDispatcher/NatsSubscription
- can we get rid of the interface. Don't forget about vertx
- See code
- // This should never, ever happen

### PublishOptions
1. Remind claude to migrate doc the PO properties
2. Where else are builders taking props
3. REVISIT
    /**
     * Use this variable for the default publish timeout in milliseconds.
     */
    public static final long DEFAULT_TIMEOUT = DEFAULT_CONNECTION_TIMEOUT;

### NatsConnection.drain
publish String data like I did with JetStream.

### NatsConnection.drain
Also NatsConsumer
timeout of <= 0 (V2 null or 0)
seems suspicious / bad / footgun


### Options removal
  supportUtf8Subjects
  secure
  server/servers/url
  ???

### SocketDataPortWithWriteTimeout
  combine with SocketDataPort and make default
    Options. buildDataPort

### NatsConnection 
public void close() throws InterruptedException

### FIX ackSync to throw IOException 
after applying advanced request behavior

### internal timeout usage
where is appropriate to use connectionTimeout and where should we use a different timeout 
`es.awaitTermination(connectionTimeout, TimeUnit.MILLISECONDS);`

### Tests

StreamCreatorConfigurationTests

 
### Improve Server Pool
in relation to A/P

### RTT
should it have a timeout parameter or should it have a bigger request timeout

### COME BACK TO 
JetStreamGeneralTests
  at the end of the file

Have claude add coverage to SimplificationTests.testCoverage

create consumer variants validate stream names

JetStreamManagementTests
```java
    // Stream names that validateStreamName rejects (mirrors the canonical list in JsValidatorTests
    // plus slash variants implied by the validator name: validatePrintableExceptWildDotGtSlashes).
    private static final String[] INVALID_STREAMS = {
        null,
        "",
        HAS_SPACE,
        HAS_DOT,
        STAR_NOT_SEGMENT,
        GT_NOT_SEGMENT,
        HAS_FWD_SLASH,
        HAS_BACK_SLASH,
        HAS_LOW,
        HAS_127
    };

    @Test
    public void testPushConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PushConsumerCreator", PushConsumerCreator::new);
    }

    @Test
    public void testPullConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PullConsumerCreator", PullConsumerCreator::new);
    }

    @Test
    public void testPushOrderedConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PushOrderedConsumerCreator", PushOrderedConsumerCreator::new);
    }

    @Test
    public void testPullOrderedConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PullOrderedConsumerCreator", PullOrderedConsumerCreator::new);
    }

    private static void assertRejectsAll(String creatorName, Function<String, ?> factory) {
        for (String invalid : INVALID_STREAMS) {
            assertThrows(IllegalArgumentException.class,
                () -> factory.apply(invalid),
                creatorName + " must reject invalid stream: " + invalid);
        }
    }
```

### SimplificationTests
There is outstanding work to be done.

### NatsMessage vs Message

Connection doesn't publish a Message anymore. At a minimum add this to the migration guide.

### NoRespondersException verus 
generic status `io.synadia.client.impl.StatusException: 503 No Responders Available For Request`
NatsConnection line 1538

### RequestsTest use proper run in
see testNoResponders

### Block simplification from push consumers

### Interface / removal

Classes don't need Nats prefix

- NatsConnection back to Connection
- Dispatcher vs NatsDispatcher
  - don't forget about NatsDispatcherWithExecutor
- StreamContext vs NatStreamContext
- Entire MessageConsumer Hierarchy

### testRequestNoResponder

### advanced stats should just be turned on by implementation plugin choice

### KV test ?

### testDirectMessageRepublishedSubject

### move buildProtocolConnectOptionsString out of Options

### Is Options the correct place for public DataPort buildDataPort() {

### makeInternalRequestResponseRequired

cancelAction is always COMPLETE

### GLOBAL Stuff

### Nats/NatsImpl

Ordered — MEM STORAGE

### MessageManager, remove getters for direct access

### FLUSH IMPROVEMENTS

### DO I NEED ANYMORE ? NatsJetStreamSubscription.setConsumerName

```java
void setConsumerName(String consumerName) {
    this.consumerName = consumerName;
}
```

Look in NatsJetStream createSubscription, just before return I used to set the consumer name, because I didn't always have it already b/c sometimes I made the consumer here

### NatsRequestCompletableFuture is a place for changes

### Consumer hierarchy is dumb

### Headers cleanup

### Better Exceptions / Timeouts

### public static final long DURATION_MIN_LONG = 1;

### remove raiseStatusWarnings

### Move ApiConstants

### NatsImpl

### Do I need the implements Serializable?
- SerializableConsumeOptions
- SerializableConsumerConfiguration
- SerializableFetchConsumeOptions
- SerializableOrderedConsumerConfiguration

### StreamConfiguration / ConsumerConfiguration / OrderedConsumerConfiguration

### That pending limits hack during subscribe
- JetStreamPushTests testPendingLimits

### public void testOrderedCreation()

### StandardCharsets.UTF_8

### getCalculatedPending

### could WebSocket handshake could be replaced with java.net.http.HttpClient's WebSocket support

### Testbase.runInJsHubLeaf move to JetStream base

### Other `Duration` → `long` ms conversions on `Options` (deferred)

`PLAN_DURATION_TO_MILLIS.md` only covers the three reconnect-delay fields (`reconnectWait`, `reconnectJitter`, `reconnectJitterTls`). The same argument — visible-unit naming, no per-call allocation, no null handling — applies to the other five `Duration` fields on `OptionsBuilder` / `Options`:

- `connectionTimeout`
- `socketWriteTimeout`
- `pingInterval`
- `requestCleanupInterval`
- `writeQueuePushTimeout`

Revisit as a follow-up when we want a consistent unit story across all timing fields. Plan should follow the same shape as the reconnect-fields plan (rename to `…Millis`, retype, drop `Duration` from the API).

### `RequestTests` no-responders coverage audit

Per `PLAN_REMOVE_NOHEADERS_NORESPONDERS.md`, the four `.noNoResponders()` calls in `core/src/test/java/io/synadia/client/impl/RequestTests.java` were removed mechanically. Two of the affected test methods are named `testRequireCleanupOnTimeoutNoNoResponders` (~line 282) and `testRequireCleanupWithTimeoutNoNoResponders` (~line 455), so the original intent was almost certainly to exercise the "tell the server we don't want auto NO_RESPONDERS replies" path. v3 always advertises `no_responders=true` in the connect string, so the flag was already a no-op when those tests were ported — they may have been silently broken for a while. Verify whether those tests are actually exercising the path their names claim, and either rename them or restore proper coverage (likely by hand-rolling the server-side behaviour with a `NatsServerProtocolMock.Customizer`).

### `CancelAction.CANCEL` — used only by tests?

`NatsRequestCompletableFuture.CancelAction` has three values; `REPORT` (core requests) and `COMPLETE` (JetStream) are used by the library, but `CANCEL` has zero internal references — it's only reachable via the public `request(..., CancelAction)` overload (`NatsConnection.java:1346`, there's no public `requestAsync(..., CancelAction)`) and otherwise appears only in `RequestTests`. Decide whether to keep it (it's the legacy v2 cancel-on-No-Responders behavior, a valid public option) or drop it (and then also drop the public `cancelAction` overload + the `CANCEL` cases in `RequestTests`). Full notes in `CANCEL_ACTION_REVISIT.md`.

### Reconsider Abstract Builders

### NatsOrderedConsumerContext 
just has an impl- remember why you did this

### Consumer
This name just clashes, I've want to change it for the longest time. Subscriber?

### Service
- Endpoint should I only have builder.
- Ability to load from config json

### Nullability Review

### ConsumerMessageQueue/MessageQueueBase
should pop and _poll have the split
public @Nullable Message pop(long timeoutMillis) throws InterruptedException {
public @Nullable Message pop(long timeout, TimeUnit unit) throws InterruptedException {
public @Nullable Message pop() throws InterruptedException {
public @Nullable Message pop() throws InterruptedException {

public @Nullable Message _poll(long timeoutMillis) throws InterruptedException {
public @Nullable Message _poll(long timeout, TimeUnit unit) throws InterruptedException {
public @Nullable Message _poll() throws InterruptedException {
public @Nullable Message _poll() throws InterruptedException {

### SubscribeBehavior
- Should I differentiate push and pull
- Is there a better way 
    // Only applicable for non-dispatched (sync) push consumers.
    private long pendingMessageLimit = Consumer.DEFAULT_MAX_MESSAGES;
    private long pendingByteLimit = Consumer.DEFAULT_MAX_BYTES;

### impl package cleanup
