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

**`_createConsumerAndSubscription` — Under Review.** Working tree, uncommitted, `JetStream.java` only. `_createConsumer` + the delete-on-failed-subscribe cleanup folded into one package-private method, applied to all 9 creator-based `pushSubscribe`/`pullSubscribe` overloads. Reviewed in `REVIEW_CREATE_CONSUMER_AND_SUBSCRIPTION.md`; nothing open in the changed code. Three findings were raised and fixed: the method needed a `throws JetStreamException, InterruptedException` clause (without it the checked exceptions came out wrapped in `RuntimeException` while all 9 public overloads still declared them), the cleanup catch had to widen from `JetStreamException` to `Exception` so an `IllegalStateException` from a delete on a closing connection is suppressed rather than replacing the original failure, and `_createConsumer` was hoisted out of the try so `ci` is a plain non-null local. Compiles clean, not yet run against a server.

## Recently Closed


## Plans / Audits
* PLAN_CORE_JETSTREAM_BOUNDARY.md
    * **Absorbs the package/protected/private visibility question** — do not start a separate plan for it. §3a (added 2026-08-15) records the member-level pass done as a by-product of the naming audit: 11 cross-object internal accesses exist, **only 2 cross a module boundary**, both `JetStream` reaching into core (`_createSubscriptionByFactory`, `_subscribeByFactory`). The second is a new finding and matches what §8 predicted
    * Still outstanding for its Phase 0: `Headers`, `NatsMessage`, and inheritance-based reach (`JetStreamSubscription extends NatsSubscription`), which the qualified-call sweep cannot see
    * Make JetStream consume core only through public API; kill the split-package reach (`.impl`/`.api`/`.utils` shared by both jars, no `module-info`)
    * measured coupling map replaces the old estimate; 3 buckets (connection ops / value types / shared utils) + recommend distinct per-module internal packages + `module-info` as the enforcement ratchet
    * subsumes D4 (JS exception `.impl`→`.api`) and `OSGi_JPMS_TODO.md` O3; gated on [[project_connection_removal]]
* PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md
* UNDERSCORE_INTERNAL_NAMING_AUDIT.md
  * **Settled 2026-08-15: `_` means internal, and a delegate is an internal thing** — one marker, one meaning. Delegate-vs-impl, caller counts and module boundaries all stop mattering at naming time; the call shape already shows them
  * The one thing a name must keep is the **stem** of the public method it backs (`_nextMessage` <- `nextMessage`). Removing a marker is licence to change the marker, not to rename the method — three names were rewritten on that mistake and restored
  * `internal` survives only as an adjective (`executorIsInternal`, `internalArray`, `queueInternalOutgoing`), several of which are public API. `NatsConnection`'s lifecycle keeps `Impl` deliberately — see `NATSCONNECTION_NAMING_TABLE.md`
  * 14 methods renamed. All package-private or private except one deliberate API change: `NatsDispatcher.internalStart` -> **`startImpl`**, staying `protected` because it is real extension API — `nats-java-vertx-client`'s `VertxDispatcher` calls it with `threaded=false` to run the drain loop on the Vert.x event loop. `_` would have been wrong there; `Impl` names the implementation half of `start` without claiming anything about audience. **Breaks that repo, one line, not updated.** Also `ListRequestEngine.internalNextJson` (x2) -> `nextJson`
  * Three renaming traps recorded in the doc, each of which compiled cleanly: a method name that is a variable elsewhere, a parameter of the same name in another class, and a `protected` base method widened to `public` in an override. A fourth, from the `startImpl` finding: a parameter that looks dead in-repo (and carries `@SuppressWarnings("SameParameterValue")`) can be the whole point of the method for an out-of-tree subclass
  * **Complete, archived to `z-claude-done/`.** The follow-on visibility pass (package vs `protected` vs `private`) is `PLAN_CORE_JETSTREAM_BOUNDARY.md` §3a/§3b/§3c, not a separate plan
* INTERFACE_DEFAULT_METHODS_AUDIT.md
* FLAKY_TESTS_ANALYSIS.md
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
* PLAN_PENDING_QUEUE_LOOKUP_RACE.md
  * V3 port of nats.java PR #1615 (open, replaces #1614 - do not port both). `NatsMessageSink` reads the queue reference twice per call, so an `invalidate()` on another thread nulls it between the check and the dereference -> NPE on the reader thread, which `NatsConnectionReader:466` relabels `IOException("Gather Message Data")`, escalating a dropped message into a forced reconnect on a healthy connection
  * **Step 0 is re-verification, not editing.** Written against the 2026-08-17/18 tree with the naming work still uncommitted, which touches every file this plan edits except `NatsMessageSink.java` - all line numbers should be assumed stale. The doc carries the greps to re-run and the 4 claims the design leans on (dispatcher queue still `protected final`, limits still normalised to `Long.MAX_VALUE`, `cleanUpAfterDrain` still before `tracker.complete`, only one `== 0` comparison). Re-read the upstream PR too, it was open when this was written
  * 6 sites, all the same double-read shape: `NatsMessageSink:76,:84,:141`, `NatsSubscription:90,:180-184`, `NatsConnection:2137-2151`. Fix: read once and pass the queue in, via a new `getDeliverabilityState(queue)` returning `AVAILABLE`/`FULL`/`NOT_AVAILABLE`
  * **Simpler than the V2 patch** - V3 normalises unlimited to `Long.MAX_VALUE` in `setPendingLimits`, so V2's `ml > 0` / `bl > 0` guards are dead code here. **Narrower too** - `NatsDispatcher.incoming` is `protected final`, so only a `NatsSubscription` can reach the gone-queue path
  * **The one thing that must not be missed:** the getters start returning `-1` instead of `0`, and `isDrained()` must move `== 0` -> `<= 0`. `cleanUpAfterDrain()` invalidates *before* `tracker.complete(this.isDrained())`, so that comparison is evaluated with the queue already gone on every drain - leaving `== 0` makes every subscription drain future complete `false`
* REVIEW_CREATE_CONSUMER_AND_SUBSCRIPTION.md
  * Review of the working-tree `JetStream.java` fold of `_createConsumer` + `subscribeDeleteConsumerOnException` into `_createConsumerAndSubscription`
  * 2 real findings (checked exceptions wrapped in `RuntimeException`; cleanup catch narrowed so a failing delete replaces the original failure), 2 minor, plus the equivalence checks that came back clean — the ordered/non-ordered `instanceof` dispatch, the 8 converted sites, and the dropped `pmmInstance` parameter
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
