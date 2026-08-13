# Instructions

The single source of truth for the things currently in progress — so any session (or a fresh read) gets an accurate, current recap instead of a stale one.

- In the `# Recap` section...
  - Exactly **one** implementation item lives here at a time. `In progress` means a session is implementing it, or has implemented it and it's under review. I will usually do this myself.
      - Section is `## Current Implementation`
  - Whenever an audit or plan is written for the first time, add it to the TODO.md as the last entry under `## Plans / Audits`
- Nothing work in progress is tracked here. It's only tracked in the audit or plan as I ask you to mark things done.
- Not pushed live: re-read this when you begin or resume work.

**Implementation State** is one of:
- **In progress** — a session is actively writing the change.
- **Under Review** — implemented, not yet committed; the working tree is being reviewed.
- **Committed** — committed; the commit(s) are being built
- **Closed** — reviewed and accepted, build passed; ready to be replaced by the next item.

# Recap

## Current Implementation

**Reconnect Delay Behavior — Committed, build passed.** `8dee6cb5`, pushed to `main`; GitHub Actions "Build Main" green (run 31641993453). Full detail in `PLAN_RECONNECT_DELAY_FOLLOWUP.md`.

Three things landed together:

1. **Call-site gating restored (item 3).** `reconnectDelayBehavior` again governs *whether* the reconnect delay handler is invoked before round 1, and it governs it for **every** handler, custom ones included — the v3 redesign had moved that decision inside `DefaultReconnectDelayHandler`, which silently made the setting inert for anyone supplying their own handler. The connection now decides whether to wait (`NatsConnection.delayBeforeFirstRound`, the only place the enum is read and the only place the lame duck flag is consumed); the handler only says how long. `DefaultReconnectDelayHandler.getWaitTimeMillis` is a pure "how long" — one line.
2. **Default changed `LameDuckAware` → `BeforeSubsequentRounds`,** matching v2. `BeforeAllRounds` was tried as the default and reverted the same day: it broke three ordinary reconnect tests purely on their timing budgets, which is what application code with a tight reconnect budget would have hit silently. Herd protection stays opt-in.
3. **Docs.** `ReconnectDelayBehavior`'s class javadoc now carries the thundering-herd rationale (and that jitter, not the wait, is what spreads the storm); enum-valued properties list their legal values in `README.md` and `OptionsProperties`; a stale `README` default was fixed.

Round numbers stay 1-based — it's a round, not an index. A 4-state enum (adding `…LameDuckAware` variants) was considered and rejected: `BeforeAllRounds` already covers lame duck, so the fourth cell would ship a constant that does nothing.

**`167c03d5` (the tests) HUNG the CI build for 6 hours and was cancelled — fixed, uncommitted.** My tests, not product code (`git diff --quiet core/src/main` clean). `maxReconnects(-1)` + a test that stopped a server and restarted it on the same port = an endless reconnect loop on a runner where the restart never came back; and `managedConnect` does not close the connection for you, so its non-daemon threads kept the test JVM alive past the 6h job ceiling. Rewritten so nothing restarts (all servers up front, kill the one the client is on, land on one already listening), `maxReconnects` bounded at 10, and the connection closed via try-with-resources. Group runtime 6h hang → 13s; full `core` 3m07s; all four broken-wiring diagnostics still catch. **Lesson: an unbounded `maxReconnects` in a test is a JVM hang, not a failing test — and it is invisible locally because the restart always works there.**

**Item 5 done, uncommitted (test-only).** `ReconnectTests.testLameDuckSignalDelaysFirstRoundThenIsConsumed` closes the last gap: a `NatsServerProtocolMock` announces `ldm:true` mid-connection then exits, so the client sees announcement-then-drop in the real order. Campaign 1 asserts round 1 invoked the handler with `lameDuckTriggered == true`; campaign 2 (real server bounced, no signal) asserts round 1 is skipped again, proving the flag is consumed and not sticky. Verified failing both ways — remove the `= true` and campaign 1 fails, remove the `= false` consume and campaign 2 fails. `git diff --quiet core/src/main` confirms no product code changed. **`PLAN_RECONNECT_DELAY_FOLLOWUP.md` is now complete, nothing open.**

---

`testConnectPendingCountCoverage` resolved 2026-08-11, both repos — details in the "Third pass" and "Resolution" sections of `FLAKY_TESTS_ANALYSIS.md`. The ported V2 fix closes the reconnect-buffer overflow but not the fast-box direction, and shrinking the flood made that worse: publishing takes 12-27 ms against a `sleep(1)` sampler, so 1 to 18 samples total, measured **4 failures in 100 runs**. It also tears its own pair — the two maxima are accumulated by separate reads, so `maxBytes` can come from a drained instant while `maxCount` came from a full one.

* **v3** — deleted; `ConnectTests.java` is byte-identical to HEAD again. `NatsConnectionImplTests.testOutgoingPendingCountCoverage` already covered both getters deterministically.
* **V2 (`nats.java`, uncommitted in that working tree)** — fixed the same way rather than patching the sampler, since `getWriter()` is already `protected // For testing` and `NatsConnectionImplTests` already exists in `io.nats.client.impl`. Test removed from `ConnectTests` (plus three unused imports, two of which were already stale) and `testOutgoingPendingCountCoverage` added. Green 5/5, and verified **under a real Java 8 JDK**, not just `sourceCompatibility = 1.8`.

## Open / carried forward

**Cross-platform test run (2026-08-04), both on nats-server v2.14.4** — WSL had been on a `v2.14.0-dev` build, so all prior flaky observations were against a dev server. **WSL: 931 tests, 0 failures** (first fully clean full run of the session; no watch-list test failed). **Windows: 932 tests, 1 failure** — `AuthTests.testToken`, new to the watch list, passed 3/3 on rerun. **This inverts `FLAKY_TESTS_ANALYSIS.md`'s opening premise** that these pass on Windows and flap on Linux; one run each does not overturn it but it is no longer established. The 932/931 gap was `AuthTests.testNeedsJsonEncoding` being `@EnabledOnOs({ WINDOWS })` — **fixed since in `c6e0cdd8`**, both platforms now run the same 25 `AuthTests`. (A Windows run may still *report* 26: the retry plugin logs a failed attempt and its retry as two testcases of the same name.)

**`testToken` is now flagged PRIORITY in `FLAKY_TESTS_ANALYSIS.md` — the one entry that is a product-code race rather than test robustness.** The exception type is API surface: an app that catches `AuthenticationException` (bad credentials, stop) separately from `IOException` (network, retry) gets routed down the wrong branch whenever the race is lost, so bad credentials get retried as a transient fault. The doc records the mechanism, four things to check when picking it up, and an explicit "do not fix this by widening the test's expected exception type" — that would hide the non-determinism rather than resolve it.

**Mechanism:** it fails `assertThrows(AuthenticationException.class, ...)` with an `IOException`, which reads as a logic bug. `connectImpl` only throws `AuthenticationException` when `connectError` already holds the server's auth text — if the `-ERR 'Authorization Violation'` is not read before the socket closes, it falls through to the generic `IOException`. Pure timing. Recorded in the flaky doc so it is not re-diagnosed as structural.

**Two V2 KV tests parked for the KV work — `KeyValueTests.testJustLimitMarkerCreatePurge` and `.testJustTtlForDeletePurge`.** Flapping in V2; a V2 session is investigating there and its notes will come back here. Both already sit in the `tdb/` staging copy of `KeyValueTests`, so they arrive with the KV port — read the notes before porting. Parked section at the end of `FLAKY_TESTS_ANALYSIS.md`.

**Flaky tests — OPEN, monitoring, nothing changed.** Watch rather than act on an unreproduced hypothesis; recent runs pass, so the rate is low and does not justify changing tests on a theory. On the list: `TLSConnectTests.testProxyTlsFirst` / `.testReconnectFailsAfterCertExpires` / `.testForceReconnectFailsAfterCertExpires`, `WebsocketConnectTests.testTLSOnReconnect`, `AuthTests.testJWTAuthWithCredsFileAlso`, `JetStreamPushTests.testDeliveryPolicy` / `.testAcks`, `SimplificationTests.testFetchOrdered` / `.testFetchDurable` / `.testReconnectOverOrdered` (added 2026-08-11 — which of the two assertions in `validateOverOrdered` failed was not recorded; `count > 0` is a budget flake, `allInOrder` would be a product issue, so capture that before diagnosing), `ReconnectTests.testForceReconnectQueueBehaviorCheck` (added 2026-08-04 — flaked once during the javadoc pass, passed on retry and clean rerun; ruled out as caused by it since that diff had zero non-comment lines; ~29s and deliberately timing-sensitive via `ForceReconnectQueueCheckDataPort.DELAY = 75`). **No test or product code has been changed for these:** `CLIENT_CERT_VALIDITY_MILLIS` is still `5000` and the WAIT ladder / `connectionTimeout` are untouched, so the tension in `FLAKY_TESTS_ANALYSIS.md` ("Concrete lead") is still live. Revisit if the rate rises or one starts failing *consistently* — `testConnectPendingCountCoverage` is the precedent for "fails every time = real signal, not a flake".

## Recently Closed

Newest first. Detail lives in each plan/audit doc.

* **PLAN_RECONNECT_DELAY_FOLLOWUP.md items 1-4** — `8dee6cb5`, build green. Call-site gating restored so `reconnectDelayBehavior` applies to custom handlers too; default back to `BeforeSubsequentRounds`; enum/property docs filled in. Item 5 (LDM wiring tests) still open — see `## Current Implementation`.
* **INTEGRATION_PLAN_PR_1578.md** — closed out 2026-08-12, doc archived to `z-claude-done/`. It had been fully implemented and committed for a while; only the bookkeeping was outstanding. Absorbed and extended by `z-claude-done/PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md` (handler is now `getWaitTimeMillis(round, options, secure, lameDuckTriggered)`, never-null `DefaultReconnectDelayHandler.INSTANCE`, `LameDuckAware` default, no enum branch in the reconnect loop). That redesign's leftover drift is tracked in `PLAN_RECONNECT_DELAY_FOLLOWUP.md`.
* **INTEGRATION_PLAN_PR_1609.md** — `4351b510`, `d6914f41`. Upstream #1608/#1609 ported: `updateStatus(DISCONNECTED)` moved ahead of `closeSocketImpl`, plus the two PR #1547 `updateStatus` items (event chosen from the transition made, not a re-read after unlocking; `status` now `volatile`). Step 4 (`currentServer` not volatile) deliberately not taken, still open.
* **`AuthTests` special-character test made cross-platform** — `c6e0cdd8`. Dropped a Windows-only skip that was masking a harness quoting bug; renamed to `testUserPassWithSpecialCharacters`.
* **`ListenerIdTests`** — `900bed22`.
* **PUBLIC_API_MISSING_JAVADOC_AUDIT.md** — `2f03d342`, `998d5752`, `dc10cb9e`, `c697d7f8`. Every public declaration in `core`/`jetstream`/`service` documented; 0 javadoc errors, only the gitignored `DebugJs` left.
* **RESOURCEUTILS_V3_PLAN.md** — `c68ca6cb`. Dropped a vestigial `@SuppressWarnings` and added `ResourceUtilsTests`.
* **PLAN_FORCE_RECONNECT_READER_STOP.md** — `e64ad979`. `forceReconnectImpl` now stops i/o before closing and joins with the full connection timeout, so a stale reader can't fire on the healthy connection.
* **`Nats.connectAsynchronously` returns a future** — `5f754250`. `CompletableFuture<NatsConnection>` plus an `Executor` overload.
* **LISTENERS_ON_THE_FLY_PLAN.md** — `7e203856`. `ErrorListener`/`ConnectionListener` are multi-listener, add/remove live.
* **`package-info.java` javadoc fix** — `1680430c`. (Commit message says "unflaking tests" but it holds only that file.)
* **EXCEPTIONS_AUDIT** — through `1f21ac26`, 5 parts. D4 subsumed into `PLAN_CORE_JETSTREAM_BOUNDARY.md`.

*(`TODO.md` is tracked as of `c86b0387`. Every other working doc in the repo root is untracked and transient by design. Back an untracked doc up to `<name>.md.bak` before editing it.)*


## Plans / Audits
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
* RESOURCEUTILS_V3_PLAN.md
  * ResourceUtils missing-resource diagnostics (V3) cleanup
* PLAN_RECONNECT_DELAY_FOLLOWUP.md
  * **PR #1578 is fully implemented and committed** — the plan doc just never got closed out; not superseded by `PLAN_FORCE_RECONNECT_READER_STOP.md` (different subject), but absorbed and extended by `z-claude-done/PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`
  * what's left is that redesign's drift: 3 javadoc blocks + `MIGRATION_GUIDE.md` still name the old default/method, the `lameDuckTriggered` consume contract doesn't match the code, and the connection-side LDM wiring tests never landed
  * **Item 3 DONE (3a-3f)** — V2's call-site gating restored: `reconnectDelayBehavior` governs *whether* the handler is called for round 1, custom handlers included. Connection decides whether to wait, handler decides how long, and `DefaultReconnectDelayHandler` is now a pure "how long". Round numbering stays 1-based — **it's a round, not an index**. Four new `ReconnectTests` pin the gate, verified failing in both directions (gate forced true → the skip tests fail; forced false → the BeforeAllRounds test fails)
  * **Item 4 tried then reverted — the default is `BeforeSubsequentRounds`**, matching v2. Making `BeforeAllRounds` the default broke 3 ordinary reconnect tests on their timing budgets (attribution confirmed by flipping the default back and forth); that is what application code with a tight reconnect budget would have hit silently, so herd protection stays opt-in. Kept from the attempt: a much fuller `ReconnectDelayBehavior` javadoc (thundering-herd rationale, jitter is what does the spreading) and stronger `OptionsTests` probes. Full `core` green, 554 tests, javadoc clean
  * 4-state `ReconnectDelayBehavior` (adding `…LameDuckAware` variants) considered and **rejected** — `BeforeAllRounds` already covers lame duck by construction, so the fourth cell would ship a constant that does nothing. Three states stand; the enum class javadoc now says so explicitly. That javadoc also moved the thundering-herd rationale up to class level, where a reader comparing the constants can see it
  * Item 1a done — enum-valued properties now list their legal values in `README.md` and the `OptionsProperties` javadoc. Found a stale README default (`reconnectDelayBehavior` said `LameDuckAware`) and that `hostnameResolveMode` documented none of its six values. Decided against making `get(String)` lenient about punctuation/spacing: docs were the gap, and the real hazard is the silent fallback on an unrecognized value — still an open maintainer decision
  * Items 1-2 done (documentation corrections; 1578 doc archived + overview flipped). Item 5 (LDM wiring tests) follows item 3. The zero-wait-future tweak was dropped — v3 already matches V2 there

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
