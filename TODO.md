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

**SLOT OPEN — no implementation in progress.** Everything through `c6e0cdd8` is pushed; `main` == `origin/main`, working tree clean apart from this file.

## Open / carried forward

**Cross-platform test run (2026-08-04), both on nats-server v2.14.4** — WSL had been on a `v2.14.0-dev` build, so all prior flaky observations were against a dev server. **WSL: 931 tests, 0 failures** (first fully clean full run of the session; no watch-list test failed). **Windows: 932 tests, 1 failure** — `AuthTests.testToken`, new to the watch list, passed 3/3 on rerun. **This inverts `FLAKY_TESTS_ANALYSIS.md`'s opening premise** that these pass on Windows and flap on Linux; one run each does not overturn it but it is no longer established. The 932/931 gap was `AuthTests.testNeedsJsonEncoding` being `@EnabledOnOs({ WINDOWS })` — **fixed since in `c6e0cdd8`**, both platforms now run the same 25 `AuthTests`. (A Windows run may still *report* 26: the retry plugin logs a failed attempt and its retry as two testcases of the same name.)

**`testToken` is now flagged PRIORITY in `FLAKY_TESTS_ANALYSIS.md` — the one entry that is a product-code race rather than test robustness.** The exception type is API surface: an app that catches `AuthenticationException` (bad credentials, stop) separately from `IOException` (network, retry) gets routed down the wrong branch whenever the race is lost, so bad credentials get retried as a transient fault. The doc records the mechanism, four things to check when picking it up, and an explicit "do not fix this by widening the test's expected exception type" — that would hide the non-determinism rather than resolve it.

**Mechanism:** it fails `assertThrows(AuthenticationException.class, ...)` with an `IOException`, which reads as a logic bug. `connectImpl` only throws `AuthenticationException` when `connectError` already holds the server's auth text — if the `-ERR 'Authorization Violation'` is not read before the socket closes, it falls through to the generic `IOException`. Pure timing. Recorded in the flaky doc so it is not re-diagnosed as structural.

**Flaky tests — OPEN, monitoring, nothing changed.** Watch rather than act on an unreproduced hypothesis; recent runs pass, so the rate is low and does not justify changing tests on a theory. On the list: `TLSConnectTests.testProxyTlsFirst` / `.testReconnectFailsAfterCertExpires` / `.testForceReconnectFailsAfterCertExpires`, `WebsocketConnectTests.testTLSOnReconnect`, `AuthTests.testJWTAuthWithCredsFileAlso`, `JetStreamPushTests.testDeliveryPolicy` / `.testAcks`, `SimplificationTests.testFetchOrdered` / `.testFetchDurable`, `ReconnectTests.testForceReconnectQueueBehaviorCheck` (added 2026-08-04 — flaked once during the javadoc pass, passed on retry and clean rerun; ruled out as caused by it since that diff had zero non-comment lines; ~29s and deliberately timing-sensitive via `ForceReconnectQueueCheckDataPort.DELAY = 75`). **No test or product code has been changed for these:** `CLIENT_CERT_VALIDITY_MILLIS` is still `5000` and the WAIT ladder / `connectionTimeout` are untouched, so the tension in `FLAKY_TESTS_ANALYSIS.md` ("Concrete lead") is still live. Revisit if the rate rises or one starts failing *consistently* — `testConnectPendingCountCoverage` is the precedent for "fails every time = real signal, not a flake".

## Recently Closed
* **`AuthTests` special-character test made cross-platform** — `c6e0cdd8`. `testNeedsJsonEncoding` was `@EnabledOnOs({ WINDOWS })`, the only OS-conditional test in the repo, inherited from the bulk `dc20c1b2` restructure. **The skip was masking a test-harness bug, not a platform difference in the code under test.** The test hand-rolled shell quoting into the argument *value* (`"\"" + user + "\""`), and `NatsServerRunner` spawns with `ProcessBuilder(List)`: on unix that argv list goes straight to `exec` with no shell, so the quotes became part of the credential; on Windows `ProcessBuilder` must flatten to one command line, where they act as quoting and get stripped. Confirmed by temporarily enabling it on Linux — it fails `Unable to make a connection`. Fixed with a `quoteCredentialForOs` helper so the platform difference sits on the one line that is genuinely platform-specific. **Also renamed twice**: `testNeedsJsonEncoding` → `testUserPassNeedingJsonEncoding` → **`testUserPassWithSpecialCharacters`**, on Scott's steer that naming a test after the implementation mechanism (`jsonEncode`) is a misdirection — the test asserts `assertCanConnect` and never asserts anything about JSON, so the name would have gone stale if the encoding changed while the guarded behavior did not. The *why* moved into a comment: the CONNECT frame is JSON **and** NATS is line-oriented, so a raw newline both breaks the JSON and truncates the protocol message. Verified on both platforms, 25 tests / 0 skipped each. **Known gap, not fixed:** the character set omits `"`, which the encoder also escapes and is the most consequential (an unescaped quote terminates the JSON string mid-frame), and sub-0x20 characters outside the named ones that take the `\u%04x` path.
* **PUBLIC_API_MISSING_JAVADOC_AUDIT.md** — pushed across `2f03d342`, `998d5752`, `dc10cb9e`, `c697d7f8`. **Every public declaration in `core`, `jetstream` and `service` is documented.** End state 11 javadoc warnings / 0 errors, and those 11 are `DebugJs.java`, the gitignored floor CI never sees. Scope grew twice from the original tier-1 142: Scott added the validators + `JetStreamClientError` (97), then ruled that public means in scope regardless of package, which pulled in `.impl` (the audit had mis-tiered `JetStream.java` — 64 public methods, constructed directly by users — as internal on a package technicality). Final 492 done by six parallel agents on disjoint file sets. **Non-comment changes: 11 explicit constructors** (a `use of default constructor` warning cannot be fixed by a comment) — 3 private on pure static holders, each verified at 0 `new` calls / 0 subclasses — plus **3 enums reformatted one-line→multi-line**, unavoidable since a one-line enum has nowhere to attach per-constant javadoc. Validated on **both platforms**: WSL 931/931, Windows 932 with one unrelated flake.
* **RESOURCEUTILS_V3_PLAN.md** — `c68ca6cb`. Removed the vestigial `@SuppressWarnings("DataFlowIssue")` (the unguarded deref it silenced is long gone; `open()` already throws `FileNotFoundException` naming the file) and ported the regression test as `core/.../utils/ResourceUtilsTests.java`. **The test was verified to actually pin the behavior** — temporarily removing the guard makes it fail with `Unexpected null value, expected: <FileNotFoundException>`, then pass again once restored. Context: the V2 equivalent left a PR red in CI from June 16 to Aug 4 with only `NullPointerException at ResourceUtils.java:38` as a clue.
* **`ListenerIdTests`** — `900bed22`. Also renamed its eight test methods off the stale `ClId`/`ElId` abbreviations, which no longer matched any method after the `getConnectionListenerId`/`getErrorListenerId` rename.
* **PLAN_FORCE_RECONNECT_READER_STOP.md** — `e64ad979`. `forceReconnectImpl` stops reader/writer **first**, then closes, then joins with the full connection timeout instead of 100 ms. Covered by `ReconnectTests.testForceReconnectWaitsForStaleReaderToStop`, **verified to fail 5/5 without the fix** via a worktree. Costs ~+0.6s in one scenario — that is the fix working, not a regression.
* **`Nats.connectAsynchronously` returns a future** — `5f754250`. `CompletableFuture<NatsConnection>` completing only on successful connect, plus an `Executor` overload.
* **LISTENERS_ON_THE_FLY_PLAN.md** — `7e203856`. `ErrorListener` + `ConnectionListener` are multi-listener via `getErrorListenerId()` / `getConnectionListenerId()` defaults and keyed maps.
* **`package-info.java` javadoc fix** — `1680430c` (message says "unflaking tests" but the commit holds only that file).
* **EXCEPTIONS_AUDIT** — A1–A23 + A20/A21, pushed in 5 parts, last `1f21ac26`. D4 is subsumed into `PLAN_CORE_JETSTREAM_BOUNDARY.md`.

*(`TODO.md` is tracked as of `c86b0387`. Every other working doc in the repo root is untracked and transient by design — including the plan/audit files listed below. Their detail lives only in the working tree, so anything that must survive belongs in a commit or in this file. Per convention, back an untracked doc up to `<name>.md.bak` before editing it.)*

## Last Plan Made

**Item:** RESOURCEUTILS_V3_PLAN.md — written, verified against the tree, and now **complete** (`c68ca6cb`). All five of its claims held on re-check: the `@SuppressWarnings` was still present and genuinely vestigial, `open()` already guarded, zero unguarded `getResource(` repo-wide, Java 21 so `readAllBytes()` stays (do **not** port V2's Java-8 buffer loop), and only one `ResourceUtils` in the whole multi-module repo. The plan deliberately left the test's module/package unresolved; `core` alongside `ResourceUtils` is the only sensible home.

*(Older `Last Plan Made` entries were pruned — those plans are either in `## Recently Closed` above or still listed under `## Plans / Audits` below, and their full detail lives in their own docs. This section holds the most recent plan, not an append-only log.)*


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
  * **TIERING FLAW FOUND — `.impl` is not all internal.** The audit assigned tiers by **package**, but this codebase puts primary user-facing types in `.impl` (the same fact `PLAN_CORE_JETSTREAM_BOUNDARY.md` documents). `impl/JetStream.java` had 72 warnings, 64 public methods, is constructed directly by users and carries usage examples — tier 1 in substance, filed tier 3 on a naming technicality. **`JetStream.java` is now DONE (72 → 0);** overall 575 → 503, 0 errors
  * **DOC RESTRUCTURED:** all work items now live in a single **`# Work Items`** section at the END of the audit (it had grown to 489 lines with checklists scattered through it). Narrative/history stays above; actionable stays below
  * **SCOPE WIDENED (Scott): "if they are public, regardless of package, they must be fixed."** The old "still out of scope" list was public, so it is now work. **Open = 492 of 503 warnings.** Groups: **B** 63 (`core/impl` user-facing), **J** 43 (`jetstream/impl` user-facing, includes SF's 11 serialized-form fields), **K** 148 (the two protocol-constants walls `NatsConstants` 90 + `JetStreamConstants` 58), **R** 238 (remaining public internals, ~58 files), plus VB verify. Arithmetic reconciles: 63+43+148+238+11 = 503
  * **`DebugJs.java` (11) is NEVER in scope** — matches gitignored `**/Debug*.java`, `git ls-files` returns nothing. It inflates the local count only; CI sees 492 where this tree sees 503
  * **R6 is the one non-comment item anywhere:** the 10 `use of default constructor` warnings need an explicit public no-arg constructor, i.e. a real code change, so that subset needs a test run
  * **FOLLOW-UP: incomplete-javadoc warnings (263)** — a category distinct from the missing-comment work. Comments that exist but lack tags. **161 are in tier 1**, so "tier 1 = 0" covers missing comments only. Types: `no main description` 151, `no @param` 73, `no @return` 18, `use of default constructor` 11, `no @throws` 10. Concentrated in `impl/JetStream.java` 62, `api/ConsumerConfiguration.java` 35, `api/ConsumerCreator.java` 34, `client/OptionsProperties.java` 20, the kv/os Configuration+Creator pairs ~43, `impl/NatsConnection.java` 15
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
* RESOURCEUTILS_V3_PLAN.md
  * ResourceUtils missing-resource diagnostics (V3) cleanup

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
