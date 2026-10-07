# Instructions

The single source of truth for the things currently in progress — so any session (or a fresh read) gets an accurate, current recap instead of a stale one.

- In the `# Recap` section...
  - Exactly **one** implementation item lives here at a time. `In progress` means a session is implementing it, or has implemented it and it's under review. I will usually do this myself.
      - Section is `## Current Implementation`
  - Whenever an audit or plan is written for the first time, add it to the TODO.md as the last entry under `## Plans / Audits`
  - **Keep `## Current Implementation` and `## Recently Closed` concise** - a line or two each, naming the commit. The detail belongs in the doc, not here.
  - **When an item is completed, delete its entry from `## Plans / Audits`** as part of moving its doc to `~development-history/`. The two happen together; no **Complete, archived** bullet is left behind.
- Nothing work in progress is tracked here. It's only tracked in the audit or plan as I ask you to mark things done.
- Not pushed live: re-read this when you begin or resume work.
- **Where the docs live** (as of 2026-08-25, all tracked): open plans/audits and this file are in `~development-todo/`; finished ones move to `~development-history/`.
  - A doc can sit in `~development-todo/` with **no entry here** when it is parked rather than active - `ORBIT_CONSTANTS.md` is that case, and stays out of this list until Orbit work actually starts. Do not "helpfully" add it.
  - **The directory is the authority, not the entry here.** A doc sitting in `~development-history/` was moved there deliberately and stays there, even if its entry below still describes open work - that means the entry is stale, so delete the entry rather than moving the file back.

**Implementation State** is one of:
- **In progress** — a session is actively writing the change.
- **Under Review** — implemented, not yet committed; the working tree is being reviewed.
- **Committed** — committed; the commit(s) are being built
- **Closed** — reviewed and accepted, build passed; ready to be replaced by the next item.

# Recap

## Current Implementation

- **Request behavior improvement** - **In progress**, `REQUEST_BEHAVIOR_IMPROVEMENT.md`. Sequence is RTT -> `Service.isStarted` -> the general rework; **the first two are done and committed**, next is Steps 1-4 (the four types).
  - Two findings from the RTT spike to carry into the classifier: entry-state errors (not connected, max pings out) stay **out** of it as `IllegalStateException`, and do **not** classify on exception type alone - `CancellationException` means different things depending on the `CancelAction`.
  - Still open in the plan: the classifier gap making `NO_RESPONDERS` unreachable on the default `REPORT` path, which is coupled to the `CANCEL_ACTION_REVISIT.md` decision. Migration-guide entries go in **as each change lands**, not batched.
- **Validator / JsValidator coverage + TEST_TRACKING cleanup** - **Committed**. `TestBase.PLAIN` is now `"AZaz09"`; new `HAS_EXCLAMATION` / `HAS_TILDE` / `HAS_COLON` / `HAS_AT` / `HAS_BRACKET` / `HAS_BRACE`. 15 tests appended to `ValidatorTests`, 10 to `JsValidatorTests`; every public method of both classes is now called directly; `ValidatorTests` 35/35 and `JsValidatorTests` 57/57 green. Listed in `V2_V3_TEST_METHOD_AUDIT.md` under `ValidatorTests.java`. `TEST_TRACKING.md` trimmed of completed entries (751 -> ~480 lines).
- **`NatsConnectionV3` + connection proposal** - **Committed** to main 2026-10-07 (`da6dcaa5`, javadoc `95669e86`). `PLAN_NATS_CONNECTION_V3.md`. Subclass selected by `OptionsBuilder.connectionImplementation(...)` or the properties key; **V3 is the default since 2026-10-06**; `-PconnectionImplementation=Classic` runs the tests on classic; post-merge `build-variants.yml` = Windows default (V3), Windows classic, Linux classic. Outgoing data in blocks of `bufferSize` (one per write, so a failed write loses at most one block, as classic). Measured 2.2x / 4.2x small-message publish (1 / 4 threads), bad-auth connect 3 ms vs 2,001 ms. D2, D3, D4, D6, D9, D10, D11, D12, D15 done; D1, D5, D7, D8, D13, D14 open. Full module suites green on V3 on Linux and Windows (2026-10-06).
  - Kept-block limits (2026-10-07): standard pool by bytes (larger of 1 MB or 4 blocks); large pool capped by the first server's max payload + max control line + 2, keeps the largest. `OutboundBufferTests.testLargePoolKeepsBiggestUpToMax`.
  - Part B applied to classic: B-S1 (protocol buffer sized to the control line), B-S2 (`tryingToConnect.compareAndSet`), B-S3 (pending getters without `closeSocketLock`/`writerLock`, = TBD #4), B-S6 (`sendPing` removes its pong future when queueing throws; 2026-10-07 also when the PING is discarded, in classic and V3 `sendPing` and in `RTT`, per `PR1636_REVIEW.md`; `queueOutgoing` / `queueInternalOutgoing` now return whether queued). B-S8 (per-run reader/writer flag, D12) applied then removed 2026-10-07, not worth the complexity; removed from V2 too. B-S4 dropped (D3). Two new `NatsConnectionImplTests` (fail 5/5 without the fixes). 12 affected core classes green on classic.

## Recently Closed

- **Port `PingTests.testPingFailsWhenClosed` / `testMessagesDelayPings`** - `0c3c8373`, build green. Same commit carried the `ConnectionEvents` -> `ConnectionEvent` rename (+ `ConnectionListenerStaleStateTests`) and the KV/OS create-from-status / create-from-creator methods. `testPingFailsWhenClosed` dropped v2's `pingInterval(10)` / `maxPingsOut(5)`: the protocol mock does not answer timer PINGs, so the connection closed before `standardConnect` confirmed CONNECTED. `testMessagesDelayPings` runs on the shared server with random subjects. Audit entries marked ☑️.

- **CI: module-based builds, snapshots, releases** - `d865181e` through `caf9f0c9`, all green. `affected-modules.sh` picks the changed modules plus their dependents (root build files and the workflows build everything, docs build nothing); `build-modules.yml` runs one job per module with per-module Coveralls flags and carryforward, publishes snapshots from main (`3.0.0.<branch>-SNAPSHOT` for a manual run on a branch), never from PRs; Gradle cache; Coveralls steps `continue-on-error`. Windows tests only the affected modules in one job. `build-release.yml` releases one module from a GitHub release tagged `<name>/<version>` (`js/3.0.0`) - not yet run for real. Snapshot publish needed `useStaging = isRelease` / `packageGroup` in the root `nexusPublishing`. Batch-publish artifact renamed `jnats3-bp`.

- **KV/OS split into `:kv` / `:os`** - `95e2ca02`. Own modules (`jnats3-kv` / `jnats3-os`) on `api project(':jetstream')`, packages unchanged; `NatsWatchSubscription` takes a `boolean pushConsume` so jetstream has no kv/os imports; `ObjectStoreClientError` moved to `io.synadia.client.os` with its README table. Jetstream artifact renamed `jnats3-js`.

- **`RTT` + a real `Service.isStarted`** - first two steps of the request-behavior work. `NatsConnection.RTT()` returns nanos, gained `RTT(long timeoutMillis)`, and split one `IOException` into `IllegalStateException` / `TimeoutException` / `IOException` / `InterruptedException`. **Three of those were bugs**: the pong future leaked on timeout (a dead future then ate the next PONG a live `flush()` was owed), the `maxPingsOut` guard was bypassed, and a `CancellationException` escaped a method declared `throws IOException` when the connection dropped. `Service.isStarted(long, TimeUnit)` -> `isStarted(long timeoutMillis)`, now confirming via RTT instead of reading a future `startService` completes synchronously. `testQueueGroup` gated on real readiness and made retry-safe (it never stopped its services, and its subjects are fixed). Migration guide updated for both. Name kept as `RTT` deliberately.

- **`subjects` -> `filterSubjects` + deliver-subject readme** - `bfe8f237`, not built locally. `ConsumerCreator.subjects(...)` renamed on both overloads with call sites updated across jetstream main and tests; README gains a draft push-consumer/deliver-subject section; `JetStreamPullTests` port in progress (v2 `ConsumerConfiguration`/`PullSubscribeOptions`/`Duration` -> `PullConsumerCreator`/millis). Also removed an unused `import io.synadia.client.utils.Debug` from `JetStreamSubscribeTests` - `Debug*.java` is gitignored, so that file would not compile from a fresh clone.

- **ClientError split + creator validation tests** - `f2057f8f`, `:jetstream:test` green. `ClientError` moved to core and is no longer subclassed; `JetStreamClientError` / `ObjectStoreClientError` are constant holders, its constructor `public` for a future JPMS package split with an "internal use, API not guaranteed" javadoc note. All 12 constants reviewed argument-vs-state (**9 STATE / 3 ARGUMENT**), `OsObjectIsDeleted` split, `JsConsumerNameDurableMismatch` removed for a label-parameterized `IllegalStateException` in `JsValidator`. Every creator-hierarchy validation gained a negative test, which found two real bugs in `ConsumerCreator._flowControl`. Written up in `~development-history/CLIENT_ERROR_AUDIT.md`.

- **Test JSON to resource files** - **Committed**. All inline JSON is out of `ApiFieldsTest`; 21 tests that duplicated `JsonParsingTests` / `ConsumerInfoJsonTests` / `StreamInfoJsonTests` / `ApiResponseTests` / `PublishAckTests` were deleted in favor of those, with their real gaps (empty-object defaults, lazy-field caching, a few edges) folded in as 17 new tests. One shared `Empty.json` replaces `PlacementEmpty.json` and every `{}` literal. Also: `JetStreamSubscribeConfig` ordered/prefix coverage, and a `CONFIG_ONLY_GETTERS` exclusion so the creator/configuration parity test accepts `isPushConsumer()`/`isPullConsumer()`. Written up in `~development-history/APIFIELDSTEST_JSON_TO_RESOURCES.md`.

- **Subscribe registration order** - the SUB reached the server before the dispatcher's handler was registered, so a message arriving in that window was queued and then silently discarded; `reSubscribe` sent before the sid was even in `subscriptions`. Present since `2f22b9160 "Start V3"`, not from the recent refactors. Handler is now tracked inside the `NatsSubscriptionFactory` the connection already calls before the send; `reSubscribe` takes the sid up front. Also: unroutable messages are counted instead of dropped silently, `subscribe(subject, queueName)` now refuses a null default handler, and `NatsDispatcherWithExecutor` lost its duplicated `run()` for a `deliverToHandler` override. Written up in `~development-history/SUBSCRIBE_REGISTRATION_ORDER.md`; v2 review note filed in the nats.java repo.

- **`discoverMany` no responders status** - `57ea1286`, build green. Port of nats.java #1620: a 503 on the reply inbox reached `new PingResponse(...)` and threw out of `ping`/`info`/`stats`; guarded via `msg.isStatusMessage()`. Not a hard gate - `testInboxSupplier` catches it ~2 in 3.
- **Pending queue lookup race** - `6ae12b95`, build green. Port of nats.java #1615: single-read queue getters returning `-1`, `getDeliverabilityState(queue)` replacing `hasReachedPendingLimits()`, `isDrained()` to `<= 0`.
- **`subscribeDeleteConsumerOnException` inlined** - `081902d7`, build green. Off `JetStream`, body moved to its one call site in `NatsConsumerContext`.
- **Connection owns the subscription/dispatcher link** - `433bab1d` (+ `aeb8d26b`), build green. `subscriptions` -> `SubscriptionInfo`, two stale-sid leaks fixed, dispatchers held in a `Set`. Removes two v2 APIs: `Subscription.getDispatcher()` and `Dispatcher.start(String)` - release-note material.

## Plans / Audits
* PLAN_CORE_JETSTREAM_BOUNDARY.md
    * **2026-10-07: direction superseded** (no JPMS requirement); to be re-looked at fresh. Measurements still valid
    * 2026-10-07: connection internals made package-private, then reverted the same day: `protected` stays (see `PROTECTED_ACCESS_AUDIT.md`)
    * §10 (2026-10-07): `Connection` interface + boundary planned together, touchpoints re-measured; open C1–C5
    * **Absorbs the package/protected/private visibility question** — do not start a separate plan for it. §3a (added 2026-08-15) records the member-level pass done as a by-product of the naming audit: 11 cross-object internal accesses exist, **only 2 cross a module boundary**, both `JetStream` reaching into core (`_createSubscriptionByFactory`, `_subscribeByFactory`). The second is a new finding and matches what §8 predicted
    * Still outstanding for its Phase 0: `Headers`, `NatsMessage`, and inheritance-based reach (`JetStreamSubscription extends NatsSubscription`), which the qualified-call sweep cannot see
    * Make JetStream consume core only through public API; kill the split-package reach (`.impl`/`.api`/`.utils` shared by both jars, no `module-info`)
    * measured coupling map replaces the old estimate; 3 buckets (connection ops / value types / shared utils) + recommend distinct per-module internal packages + `module-info` as the enforcement ratchet
    * subsumes D4 (JS exception `.impl`→`.api`) and `OSGi_JPMS_TODO.md` O3; gated on [[project_connection_removal]]
* PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md
* INTERFACE_DEFAULT_METHODS_AUDIT.md
* TEST_TRACKING.md **(was FLAKY_TESTS_ANALYSIS.md until 2026-08-28)** - now the single doc for test tracking: `tdb/` ports, coverage gaps, test improvements, and the flaky history (preserved verbatim in its Part 2)
  * **Three "flaky" tests were port-4222 contention — RESOLVED 2026-08-17. It was a `NatsServerRunner` bug, not a conf-design problem.** `ws_operator.conf` / `wss_operator.conf` had no top-level `port` line, so nats-server used its 4222 default and any two of the up-to-6 parallel forks (`build.gradle:131`) collided. Adding the missing `port: 0` then traded that for `cannot assign port multiple times`: the runner allowed only one literal `port:` per file, counting a top-level one and one inside a `ws { }` block as a conflict — the guard ran *before* the brace-depth check, so a nested listener port claimed the top-level slot. **Fixed upstream by the repo owner (throws only for multiple top-level ports), released as `jnats-server-runner:4.0.0`; `build.gradle` bumped from 3.1.0.** Final conf state: a uniform literal `port: 0` in all five, the whole change being the two missing lines
  * **Test-side half:** with the runner fixed, `WebsocketConnectTests` failed 12/19 — every positive connect test — building `ws://` URIs from `getNatsPort()` (one live server: conf nats `44917`, ws `46557`, test dialed `ws://…:44917`). 4.0.0 exposes `getNatsPort` / `getNonNatsPort` / `getConfigPort` / `getReadyPort` / `getMappedPort(String)`; the tests used only `getNatsPort`. Fixed by the owner in `wsBuilder`/`wssBuilder`, `testWebSocketCoverage`, and `NatsTestServer.getLocalhostUri(String)` / `getLocalhostUris(String,...)` which now choose the port from the schema
  * **Full suite green: 937 tests, 0 failed** — core 542, jetstream 380, service 15 (`kv` is commented out of `settings.gradle`, `examples` has no tests). Measured *before* the last two `NatsTestServer` helper edits, so re-run to confirm those
  * Two things worth carrying forward: the schema test was first written `equals(WS) == equals(WSS)`, true only when both are false — inverted, `||` intended, and **no test would have caught it**; and the four negative wss tests passed all through the broken period because they assert a connection *fails*, which it did for the wrong reason. A passing negative test proves nothing until you know why it failed
  * Reference, since it drove several wrong turns: a **literal** `port:` is rewritten with the value `getPort()` returns; a **named token** (`<ws>`, `<wss>`, `<p>`) gets an *independent* allocation under that name. `<p>` is a valid placeholder but must not be used for a port a test intends to dial — one live server's conf read `45727` while the test dialed `45723`. Also: only 3 of the 19 `WebsocketConnectTests` cases dial the plain client port, which is most of why this stayed hidden
  * **My earlier retraction was the error, not the original call.** I "verified" the runner substitutes the port by diffing a generated conf — but that showed the *websocket* port templated, never the client port the error names. Verify substitution on the port named in the error
  * **Always clean before a full run.** Windows: `C:\Programs\nt3.bat` (nkill + storage wipe + conf wipe + `gradlew clean test`). **`nkill.bat` cannot see WSL processes** — kill there too (`pkill -9 nats-server`, `rm -f /tmp/nats_java_test*.conf`). Every run recorded before this was measured dirty; 3596 stale conf files and 3 stray servers were found in WSL
  * maybe ask claude to look for other potential flaky - maybe by pointing to the build history of V2
* INTERFACES_REPORT.md
    * Dispatcher unsubscribe - Every Subscription is a NatsSubscription. Review this when looking at INTERFACE
* REQUEST_BEHAVIOR_IMPROVEMENT.md
  * **now the Current Implementation item** - `MessageResult` (`Reply` | `RequestFailure`) replacing `@Nullable Message`, `RequestFailureException extends IOException` at throwing boundaries, five classified reasons
  * 2026-09-02: `ackSync` written up as the worked example (a null response becomes `TimeoutException("Ack response timed out.")`, hiding no-responders / connection-closing / 503); Step 8 signatures refreshed off `Duration` to millis
  * 2026-09-02: **classifier gap** - `NO_RESPONDERS` needs `CancelAction.CANCEL`, which the library never selects, so a 503 under the default `REPORT` classifies as `TIMEOUT` and under `COMPLETE` comes back as a `Reply` carrying a status message. Fix feeds the CANCEL_ACTION_REVISIT decision - decide the two together
* VIRTUAL_THREAD_DISPATCHER_EXAMPLE.md
* CANCEL_ACTION_REVISIT.md
* PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md
  * remove intrusive `NatsMessage.next`; `accumulate` fills a reusable `MessageBatch` instead of chaining
  * **2026-09-07 added:** also serialize a user message at publish hand-off, so the caller cannot mutate it (headers especially) while it sits in the writer queue. Same change as the batch rework - it makes the queue hold three kinds (control markers, internal protocol messages, serialized user messages), which the batch type has to model
  * markers become serialized bytes too - a pattern no legal outgoing message can start with, since every real one leads with a client op from `NatsConstants:73-98`. Kills the `msg == END_RECONNECT` identity check and keeps the queue homogeneous
* PLAN_FLUENT_SUBSCRIBE_BUILDER.md
  * fluent JetStream subscribe/subscription builder — explored + reverted; recommends source-first builder over the reverted no-arg builder
* OSGi_JPMS_TODO.md
  * **2026-10-07: JPMS module path dropped as a requirement**; split packages kept by design (O3 decided, O4/O5 dropped). O6 OSGi fixed (core exports, jetstream fragment, `OsgiBundleTests` in Felix); jnats-json / nkeys still export nothing. Open: O7 `Automatic-Module-Name`
  * 3 split packages across core+jetstream (`io.synadia.client.api`, `.impl`, `.utils`) → the jars can't sit on the JPMS module path together; OSGi resolves only one wiring
  * **O2 DONE:** every subproject published as `io.synadia:jnats3` (root hardcoded `"jnats3" + jarEnd`) — so `jnats3-js`'s POM depended on *itself*, not on core. Now per-module `artifactNameExt` → `jnats3-core` / `-js` / `-service` / `-examples`. kv/os are `jnats3-kv` / `jnats3-os` (split 2026-09-30)
  * O1 open — examples are repo-only but the build still declares a publication for them. A `publishExt` fix was written then reverted (Gradle changes scoped to artifact id only); if picked up, name it `libraryExt` — every library always publishes, so the only real predicate is "is this a library"
  * O3 (split-package strategy) still open — a breaking rename if taken, so V3 or never
* EXCEPTIONS_AUDIT.md
  * checked-exception surface across `NatsConnection`, `JetStream`, `JetStreamManagement`, `StreamContext`, `ConsumerContext` and the KV/OS code riding on them - inventory, then options
  * core finding: **`throws IOException` on the JetStream surface is a lie** - measured, exactly one is vestigial (§3, §3a)
  * second finding: interruption is handled two different ways, and `JetStreamImpl:183`/`:192` cannot be fixed in isolation (§4, §4a, §4b)
  * options are in §5; nothing decided or started
* V2_V3_TEST_METHOD_AUDIT.md
  * v2 `@Test` method names absent from v3, grouped by v2 file. 107 staged in `tdb/`, 80 came from the extracted json/nkey library, 174 in neither - of those 119 were replaced by a renamed v3 test, 35 no longer apply, **20 are real gaps**. Companion to TEST_TRACKING.md
* PLAN_REMOVE_LEGACY_PULL_METHODS.md
  * remove `JetStreamPullSubscription.fetch/iterate/reader` + `JetStreamReader`/`JetStreamReaderImpl`, superseded by `ConsumerContext` fetch/iterate/consume. The primitive `pull*` methods stay
  * collateral verified: `drainAlreadyBuffered` and `MIN_EXPIRE_MILLIS` orphan; `durationGtZeroRequired`, `EXPIRE_ADJUSTMENT` and `_nextUnmanaged*` do **not** - leave them
  * fallout: 5 javadoc lines on the staying `pull*` methods say "Prefer fetch, iterate or reader"; 3 of 9 `JetStreamPullTests` tests; `INTERFACES_REPORT.md` and `MIGRATION_GUIDE.md`
  * behavior is handed to the facade, not discarded - see PLAN_V2_FACADE.md
* PLAN_V2_FACADE.md
  * idea capture only - a **v2 facade over v3** as its own project (probably V2 Core Facade + V2 JetStream Facade) to give v2 developers a migration ramp; explicitly not seamless, and gated on the v3 API settling
  * doubles as the **collector for v3 removals** that the facade should be the home for - add a section per removal as it lands
  * first entry: **legacy advanced pull behaviors**. No copies kept; originals stay in nats.java 2.26.3, with the non-obvious semantics a rewrite would lose written down
* NEXTMESSAGE_STATUS_EXCEPTION_ANALYSIS.md
  * why `testOverflow` line 603 fails - `sub.nextMessage` leaks the package-private unchecked `JetStreamStatusInternalException`, which its own javadoc says is never exposed
  * a checked exception cannot be added to `_nextUnmanaged`: `nextMessage` overrides core `Subscription`, and `iterate` throws from `Iterator.hasNext()`
  * **implemented 2026-09-21 (§8)**: the subscription paths throw the core unchecked `StatusException`, `JetStreamStatusInternalException` deleted, no signature changed, `JetStreamException` stays checked

* BatchPublish port (no doc yet)
  * `batch-publish` module scaffolded 2026-09-23 - `settings.gradle` include plus a `build.gradle` with `jnats3-bp` / `io.synadia.jnats3.bp` identity (renamed from `jnats3-batch-publish` 2026-10-01), `api project(':jetstream')`, and the `:core` + `:jetstream` testOutput configurations. Builds, no sources yet
  * source is `/mnt/c/nats/orbit.java/batch-publish` (14 main classes, 6 test classes, 8 examples, Java 8 on jnats 2.26.3). The orbit session is writing the port brief; the port itself happens here

* ACCOUNT_PUSH_PULL_EXPORTS.md
  * cross account reading: push rides the `_INBOX.>` stream export, pull rides `$JS.API.>` which must be `response_type: Stream`. Measured both ways, tests in `ConsumeInAccountTests` with `account_push.conf` / `account_pull.conf`
  * carries the CLI only reproduction for the server team (overlapping specific Stream export beside a broad Singleton one drops replies), and a separate client finding: `ConsumeOptions.batchSize(1)` stalls after one message, same account too
* PLAN_ACTIVE_PASSIVE_V3.md
    * java-active-passive as a V3 subproject: protected adoption API in NatsConnectionV3 (recommended), Connection interface not needed for it (AP1-AP5 open)
* TESTING_JAVA_OVERVIEW.md
    * How /mnt/c/nats/testing.java runs the 4-machine cloud benchmark, and what running it against V3 takes
* PLAN_NATS_CONNECTION_V3.md
    * Part A: `NatsConnectionV3` rewrite (outgoing byte lanes, synchronous connect handshake, one-guard lifecycle, per-socket reader/writer) - code in the working tree
    * Part B: surgical changes for classic - B-S2 (CAS on `tryingToConnect`), B-S3 (lock-free pending getters, = TBD #4), B-S6 (`sendPing` future leak) recommended regardless
    * Part C: review of the open docs that touch the connection; `PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md` superseded if V3 goes ahead (D7)
    * Open decisions D1-D10
* V2_BUGS_FROM_CONNECTION_REVIEW.md
    * V2 bugs only, from the v3 connection review: `sendPing` pong-future leak, stale reader/writer thread stopping the current one (suspected). Header mutation after publish is a documented V2 limitation, not listed
* KV_OS_SERVICE_API_USAGE_AUDIT.md
    * 2026-10-07: what kv / os / service use from jetstream and core; the non-user-API part is `AbstractBucketFeature` (6 public fields), `NatsWatchSubscription`, `JsValidator`, constants. Decision: package access rule as for jetstream → core; only A3 applied (KV validators + tests moved to kv `KvValidator`)
* PROTECTED_ACCESS_AUDIT.md
    * 2026-10-07: 335 protected members (bytecode); 25 must stay (kv / os subclass jetstream bases across packages, plus `api.SubscribeBehavior` / `api.ApiResponse` extended from jetstream `impl`); 310 could be package-private (compile + javadoc verified in a copy). Decision: keep `protected` (users may subclass, e.g. `NatsServerPool`); nothing applied

## Plans / Audits TBD

1. ObjectStore line 107 / ObjectStore nullability

2. **Reader hardening against malformed incoming headers.** Raised by a code review, which called it "a separate, optional item" - recording it 2026-09-07 so it is at least considered rather than forgotten. The threat model is narrow: a bad or malicious server, or a broken/corrupted connection, feeding the reader header bytes that do not parse the way the client assumes. Not urgent and not known to be exploitable - the question to answer is what the reader currently does on malformed header input and whether that is acceptable, before deciding whether anything needs changing.

3. Create legacy project with items like:
  * Legacy pull subscription fetch, iterator, reader
  * Facade to more easily migrate from V2

4. **Port V2 PR #1632: lock-free `outgoingPendingMessageCount` / `outgoingPendingBytes`.** Recorded 2026-09-25 from nats.java issue #1631 (a user traced publish tail latency to these getters). V3 has the identical code: the two getters at the end of `NatsConnection` take `closeSocketLock`, which `closeSocket` holds through `reconnectImpl()`, so a read parks for the entire reconnect; the two getters at the end of `NatsConnectionWriter` take `writerLock`, which `sendMessageBatch` holds through every socket write, so a read parks behind a stalled write for up to the socket write timeout. Neither lock buys anything: `length` and `sizeInBytes` are `AtomicLong` in `MessageQueueBase`, no mutator (`push`, `accumulate`, `filter`, `clear`) holds either lock, `writer` is assigned once in the constructor and never reassigned, and `normalOutgoing` is `private final`. The V2 lock was added in #1416 to cover a writer swap on reconnect that no longer exists in either codebase. Fix is the same as V2: drop both locks and both null checks so the getters return the atomic reads directly, and port the regression test `testOutgoingPendingGettersDoNotBlockOnCloseSocketLock` into `NatsConnectionImplTests` (it holds `closeSocketLock` on the test thread and reads both getters from another thread under a timeout; it fails with `TimeoutException` on the current code). `canQueueDuringReconnect` already reads `normalOutgoing.sizeInBytes()` lock-free on the publish path, so this matches existing practice.

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
