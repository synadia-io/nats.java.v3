# Flaky tests on GitHub CI — analysis & ideas

Context: passes locally on Windows (individually and full-suite), flaps on the GitHub (Linux) runners — some attempts of the same test fail (x2/x4) then pass, one fails all 5 (x5). GH runners are slower, share CPU, and have smaller/oddly-tuned socket buffers vs a dev box, so anything that races the clock or floods a socket surfaces there. Below is the concrete mechanism for each, why it shows up on CI and not Windows, and ideas. None of these point to a wrong assertion that should be "fixed" by weakening it — they're timing/resource races; the fixes are about making the test tolerant of a slow box (and one looks like a genuine behavior issue worth a closer look).

## Shared root causes

1. **Fixed wall-clock budgets vs a slow CPU.** `ConnectionUtils.waitUntilStatus` polls every 100ms up to `millis` (`DEFAULT_WAIT`), `managedConnect` retries connect 10× — but only retries on `IOException`. A connection that ends in **CLOSED** throws `AssertionFailedError`, which is *not* retried. So any connect that quietly closes (slow handshake + `maxReconnects(0)`) fails immediately instead of getting the 10 retries the helper appears to promise.
2. **Async callbacks read synchronously.** Error/slow-consumer listeners run on `callbackExecutor` (`makeCallback`, NatsConnection.java:1974). Tests assert on counters those callbacks increment, but the producing side (e.g. `getDroppedCount`) is incremented synchronously — so the assert can win the race on a fast box and lose it on CI.
3. **Socket flooding with no reader.** Two tests push tens of MB with nothing draining. On Linux the kernel send buffer / write-timeout interplay differs from Windows, so the writer stalls, ping/pong lapses, and the connection bounces into reconnect mid-test.
4. **Heavy JetStream provisioning vs a single request timeout.** The JS management tests create 1100–1400 entities one request at a time, each bounded by the JS request timeout (which defaults to the *connection timeout* — see JetStreamImpl.java:50). One slow round-trip in a long serial loop = timeout.

## Per-test

### ConnectTests.testConnectPendingCountCoverage — x5 (fails *every* attempt; treat as a real issue, not a flake)
- Publishes `5000 × 8KB = ~40MB` with no consumer and no flush, while a watcher thread samples `outgoingPendingMessageCount/Bytes`. Fails at `_publish` (NatsConnection.java:1036) with *"Unable to queue any more messages during reconnect, max buffer is 8388608"*.
- That branch (NatsConnection.java:1033) only fires when `status == RECONNECTING || DISCONNECTED`. So on CI the connection **drops mid-publish**: 40MB can't be written fast enough, the OS send buffer fills, the writer stalls, ping/pong lapses → disconnect → reconnect → now publishes land in the 8MB reconnect buffer → overflow → ISE.
- Why not Windows: different socket send-buffer sizing/timing keeps the writer ahead, so it stays CONNECTED and the messages sit in the normal (unbounded-ish) outgoing queue.
- Ideas: the test only needs a backlog big enough to observe `pending > 0`. Either (a) cut the volume / payload so it can't trigger a disconnect, (b) periodically `nc.flushBuffer()` or read, (c) bump `reconnectBufferSize`, or (d) the test should tolerate a transient disconnect (catch the ISE once pending was already observed > 0). Worth confirming whether the disconnect itself is expected under load — if the writer stall → disconnect is "correct," this is purely a test-design problem; if not, it's a writer/heartbeat bug.

### ConnectTests.testFlushBufferThreadSafety — x2
- Publisher thread sends 50000 × 5B; main thread loops `while (t.isAlive()) nc.flushBuffer();` at line 358 — **not** wrapped in try/catch. `flushBuffer()` throws `IllegalStateException("NatsConnection is not active.")` whenever `!isConnected()` (NatsConnection.java:2530).
- On CI a transient disconnect/reconnect (same flooding/stall story, smaller scale) makes `isConnected()` momentarily false; the bare `flushBuffer()` in the main loop throws ISE and fails the test. The publisher's own `flushBuffer` (line 332) is guarded for `IOException` but **not** for the unchecked ISE either.
- Ideas: tolerate the not-active window in the loop (catch ISE/IOException and continue while `t.isAlive()`), or gate the flush on `nc.getStatus() == CONNECTED`. This is a test robustness gap, not an API bug — though note `flushBuffer` mixes a checked `throws IOException` declaration with an unchecked ISE for the not-connected case, which is easy to miss.

### ErrorListenerTests.testExceptionInSlowConsumerHandler — flaps
- Fails at line 128 `assertTrue(getExceptions() > 0)` — line 124 `assertEquals(3, getDroppedCount())` already passed, so the slow consumer *was* detected.
- Mechanism: `processSlowConsumer` → `makeCallback` → `callbackExecutor.execute(...)` (async). `BadHandler.slowConsumerDetected` throws, caught at NatsConnection.java:1981 → `incrementExceptionCount()`. But `getDroppedCount()` is bumped synchronously at drop time. So on CI the callback task hasn't run yet when `getExceptions()` is read. Worse: `nc.close()` shuts down `callbackExecutor`; if the task is still queued it can hit the `RejectedExecutionException` swallow (NatsConnection.java:1986) and the exception is **never** counted.
- Why not Windows: the callback executor drains the task before/within `close()`; CI loses that race.
- Ideas: after `close()`, poll for `getExceptions() > 0` with a short timeout instead of reading once; or flush/await the callback executor before asserting. The comment "should force the exception listener through" assumes close synchronously drains callbacks — it doesn't guarantee that for already-dropped messages.

### AuthTests.testWssJWTAuthWithCredsFile — flaps
- `managedConnect` with `maxReconnects(0)` over **wss + TLS + JWT/creds** — the heaviest, slowest handshake path. Ends CLOSED (status), so `waitUntilStatus` throws `AssertionFailedError`.
- Critical detail: `managedConnect`'s 10× retry loop only catches `IOException` (ConnectionUtils.java:53). A handshake that overruns the connection timeout with `maxReconnects(0)` lands in **CLOSED**, surfaced as `AssertionFailedError` — which the loop does **not** retry. So this "retrying" helper gives the slowest connect path exactly one shot.
- Why not Windows: the wss upgrade + TLS negotiation completes inside the timeout locally; on a loaded CI runner it occasionally doesn't.
- Ideas: raise the connection timeout for the wss/TLS cases, and/or make `managedConnect` treat a CLOSED-result (AssertionFailedError) as retryable like an IOException so the 10 retries actually apply to handshake slowness. (Bumping `maxReconnects` would change semantics the test may rely on, so prefer the timeout/retry-helper route.)

### JetStreamManagementTests.testGetStreamInfoOrNamesPaginationFilter — x4
### JetStreamManagementTests.testGetConsumers — x5
- Both fail with *"Timeout or no response waiting for NATS JetStream server"* (`responseRequired`, JetStreamImpl.java:209) while bulk-creating entities (`addStreams` 300+1100=1400; `addConsumers` 600+500=1100) — one `createOrUpdateConsumer`/stream-create per iteration, serially.
- Each request is bounded by the JS request timeout, which **defaults to the connection timeout** when unset (JetStreamImpl.java:50). On a loaded runner, a single round-trip in that long serial loop exceeds the budget → IOException → whole test fails. `testGetConsumers` also runs on the **shared** server (`runInShared`), so it competes with other tests' load.
- Why not Windows: the per-request round-trips stay well under timeout locally; CI contention pushes one over.
- Ideas: give these provisioning-heavy tests an explicit, generous `JetStreamOptions.requestTimeout` (don't inherit the connection timeout); consider running the consumer test on its own server rather than the shared one; or retry the individual create on timeout. These are stress tests of pagination — the timeout is environmental, not a correctness signal.

## Summary of ideas (cheapest → most involved)
1. Wrap the bare `flushBuffer()` loop (testFlushBufferThreadSafety) to tolerate transient not-connected.
2. Poll-with-timeout for `getExceptions() > 0` (testExceptionInSlowConsumerHandler) instead of a single read after close.
3. Give the JS bulk tests an explicit large `requestTimeout` (and move testGetConsumers off the shared server).
4. Make `managedConnect` retry on a CLOSED result, and/or raise the timeout for the wss/TLS auth test.
5. Investigate testConnectPendingCountCoverage's mid-publish disconnect specifically — decide whether the writer-stall→disconnect under a 40MB flood is expected (then fix the test volume/handling) or a heartbeat/writer bug (then fix the client). This is the one failing 5/5, so it's the least "flaky" and most likely a real signal.

All of the above are environment/timing robustness changes on the test side except item 5, which needs a decision on intended behavior first.

---

# Follow-up: implementation-vs-test verdicts (verified)

Re-examined under the rule: **don't change behavior, fix implementation that's genuinely wrong, don't "cheat" tests — make tests exercise real behavior.** Verified the mechanisms against the code; ran `testConnectPendingCountCoverage` on WSL Linux once → it **passed**, confirming these are load-dependent, not deterministic product bugs.

## Verified root-cause facts
- Default data port = `SocketDataPortWithWriteTimeout`, **60s** write timeout → on expiry calls `forceReconnect(FORCE_CLOSE)` (SocketDataPortWithWriteTimeout.java:42-59). Real disconnect trigger under a stalled write.
- `DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE = 5000`; `testConnectPendingCountCoverage` publishes exactly 5000 — right on the saturation boundary (`normalOutgoing` blocks/`getWriteQueuePushTimeout` once full, discard defaults false).
- JS request timeout defaults to the **connection** timeout = **2000ms** (JetStreamImpl.java:50, DEFAULT_CONNECTION_TIMEOUT). Each of 1100–1400 serial creates gets a 2s budget.
- `flushBuffer()` signals not-connected via **unchecked `IllegalStateException`** though it declares `throws IOException`; ISE-when-not-connected is intended and is asserted for the CLOSED case (ConnectTests.java:302). `processSlowConsumer` fires once (first drop, guarded by `markSlow`); `shutdownExecutors` drains the callback executor with `shutdown()`+`awaitTermination` (Options.java:423-434).
- `managedConnect` retries only `IOException` (ConnectionUtils.java:53); a CLOSED outcome surfaces as `AssertionFailedError` and is **not** retried.

## Verdicts

| Test | Verdict | Why / action |
|---|---|---|
| ConnectTests.testConnectPendingCountCoverage (x5) | **Test exercises a real behavior wrongly** | Reconnect-under-write-timeout (or server-side close) is correct behavior — keep it. The test only needs to observe `outgoingPendingMessageCount/Bytes > 0`, and the sampler already takes the running max — it does **not** need a 40MB flood sitting exactly on the 5000-msg queue limit. Make it create an observable backlog **within** `DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE` so it can't tip into a reconnect. Not a product bug; not cheating (assertions unchanged). |
| ConnectTests.testFlushBufferThreadSafety (x2) | **Test must respect flushBuffer's contract** | `flushBuffer()` legitimately throws ISE when the connection isn't active; a transient reconnect window is a valid state. The publisher loop already guards flush (but only catches `IOException`, line 332). The bare main-thread loop (line 358) must accept the not-active window. No product change. (Latent smell worth noting separately: flushBuffer mixes a declared checked `IOException` with an unchecked ISE for not-connected.) |
| ErrorListenerTests.testExceptionInSlowConsumerHandler | **Likely not a real bug** | Callback is submitted on the reader thread before `close()` (guaranteed by `dropped==3` passing at line 124), and `close()` drains it via `awaitTermination`. Should be deterministic. If it recurs, investigate reader-delivery vs assert ordering — but leave as-is for now. |
| AuthTests.testWssJWTAuthWithCredsFile | **Test-helper fix (legitimate)** | `managedConnect`'s whole job is "keep trying to connect"; it should retry a CLOSED outcome (slow wss/TLS handshake) the same way it retries `IOException`. Fixing the helper to do what it's named for is not cheating and changes no product behavior. |
| JetStreamManagementTests.testGetStreamInfoOrNamesPaginationFilter (x4) | **Test workload config (legitimate)** | Give the bulk-provisioning setup an explicit, generous `JetStreamOptions.requestTimeout` instead of inheriting the 2s connection timeout. Setting a request timeout appropriate to a 1400-entity loop is correct API usage, not an assertion change. |
| JetStreamManagementTests.testGetConsumers (x5) | **Test workload config (legitimate)** | Same as above; also consider running it on its own server rather than the shared one to remove cross-test contention. |

## Implemented (this pass)

Corrected two earlier facts: tests set `.socketWriteTimeout(0)` (OptionsUtils:99-101) so the 60s write-timeout port does **not** apply in tests; and the JS request timeout in these tests = the connection timeout set at OptionsUtils:91 (was 4000ms), not the 2000ms product default — because the tests never set a `JetStreamOptions.requestTimeout`. (`JetStreamOptions` *does* have a separate `requestTimeout`; when unset/≤0 it falls back to the connection timeout — JetStreamImpl.java:50. So a per-context override exists; bumping the connection timeout simply moves the inherited default.) The real slow-consumer mechanism is that the test's callback executor is **user-supplied**, so `close()` doesn't drain it (`shutdownExecutors` only drains internal executors, Options.java:423) — `close()` cannot "force the listener through" for a user executor, so the assert raced the async callback.

Changes (all behavior-preserving, no assertion weakened; all six pass locally):
- **ErrorListenerTests.testExceptionInSlowConsumerHandler**: after `close()`, poll up to `DEFAULT_WAIT` for `getExceptions() > 0` instead of reading it once — correctly synchronizing with the listener that runs on the (undrained) user callback executor.
- **testConnectPendingCountCoverage → testOutgoingPendingCountCoverage** (moved to `NatsConnectionImplTests`): made deterministic instead of volume-based. Racing a live writer is unwinnable in both directions — a fast box drains the queue so the count is 0; a slow box backs the queue past the reconnect buffer and `publish` throws (the original 40MB flood). The counts also can't be read during reconnect at all: the getters take `closeSocketLock`, and `closeSocket` holds it for the *entire* reconnect (it calls `reconnectImpl` inside the lock, NatsConnection.java:755). So the test now stops the writer (`getWriter().stop()`, a test hook) so nothing drains, publishes a fixed batch into `normalOutgoing`, and reads the getters while CONNECTED (locks free). Same assertions as before, now deterministic; moved to the impl package because it needs `getWriter()`. Runs in ~1.5s.
- **ConnectTests.testFlushBufferThreadSafety**: tolerate `IllegalStateException` from `flushBuffer()` in both the publisher and the main flush loop — a transient not-active window is a valid state and isn't what this concurrency test targets. flushBuffer's not-active→ISE contract is unchanged.
- **OptionsUtils:91**: test default `connectionTimeout` 4000 → 10000. Because the JS request timeout inherits the connection timeout, this gives every JS management request a 10s budget on a loaded runner, fixing both JetStreamManagementTests timeouts without per-test config.

Not changed: `managedConnect` (per your call — other ConnectionUtils helpers exist for other needs).

## ConnectionUtils WAIT ladder (resolved)
With `connectionTimeout` now 10s, `DEFAULT_WAIT` (the connect/close status-poll budget) must be ≥ the connection timeout, or a slow-but-successful connect can be abandoned before it reaches CONNECTED. Bumped the whole ladder to preserve ordering and gaps: `DEFAULT_WAIT` 5000→11000, `MEDIUM_WAIT` 8000→14000, `LONG_WAIT` 12000→18000, `VERY_LONG_WAIT` 20000→26000.

## Bottom line (first pass)
No clear product **behavior** bug surfaced — these are load-sensitivity issues. The legitimate, behavior-preserving, non-cheating changes are: (a) `managedConnect` retries a CLOSED result, (b) the two JS bulk tests set an adequate `requestTimeout`, (c) the two ConnectTests exercise their target API (pending-count reporting; concurrent flush) within the connection's real operating envelope instead of flooding/hammering into a reconnect. Item 5 from the first section (whether a 2s default JS request timeout inherited from the connection timeout is too aggressive) remains the one genuine **behavior** question for you to decide — it is consistent with jnats today, so I'd leave it unless you want to revisit the default.

---

# Second observation pass — local WSL Linux, 2026-08-03

> **CROSS-PLATFORM RESULT (2026-08-04).** Both environments were levelled to **nats-server v2.14.4** (WSL was on a `v2.14.0-dev` build until then, so every observation above this line was gathered against a dev server). Full suite, same commit, one platform at a time:
> * **WSL Linux — 931 tests, 0 failures.** The first fully clean run of the session, and **none of the watch-list tests failed**.
> * **Windows — 932 tests, 1 failure** (`AuthTests.testToken`, new to the list; passed 3/3 on rerun).
>
> **This inverts the premise at the top of this document.** The original framing was "passes locally on Windows, flaps on the GitHub Linux runners". In this run the opposite happened. One run each is not enough to overturn it, but the framing should no longer be treated as established.
>
> The 932-vs-931 count was not a discrepancy: `AuthTests.testNeedsJsonEncoding` was `@EnabledOnOs({ WINDOWS })`, so it ran on Windows and was skipped on Linux. **Fixed since, in `c6e0cdd8`** - the skip is gone and the test (now `testUserPassWithSpecialCharacters`) runs on both. Both platforms now execute the same 25 `AuthTests`. Note that a Windows run can still *report* 26: the retry plugin logs a failed attempt and its retry as two testcases with the same name, so count unique names before concluding the platforms differ.
>
> **STATUS: OPEN — monitoring, nothing changed. Builds have been green since.** Scott's call: watch these rather than act on an unreproduced hypothesis, and the runs after this pass have all passed — so the observed rate is low and does not justify changing tests on a theory. **No test or product code was changed for any of the nine below.** In particular `CLIENT_CERT_VALIDITY_MILLIS` is still `5000` and the WAIT ladder / `connectionTimeout` are untouched, so the tension described under "Concrete lead" is still live and these are expected to keep flapping. The only thing that shipped alongside this pass was a `package-info.java` javadoc fix (`1680430c`, despite its "unflaking tests" message — it contains no test changes). Revisit if the frequency rises or one starts failing consistently; the first pass's `testConnectPendingCountCoverage` is the precedent for "fails every time = real signal, not a flake".

A different set of tests flapped during the `Nats.connectAsynchronously` work (`5f754250`). Worth recording because **none of them overlap with the first pass**, they were seen on a dev box rather than a GH runner, and — most importantly — **they flap despite the load-sensitivity work already done above.** That pass globally raised the budgets: `connectionTimeout` 4000→10000 (OptionsUtils:91) and the whole WAIT ladder `DEFAULT_WAIT` 5000→11000, `MEDIUM_WAIT` 8000→14000, `LONG_WAIT` 12000→18000, `VERY_LONG_WAIT` 20000→26000. So "the box was slow, give it more time" has already been applied and is **not** the remaining explanation. For at least one cluster below, the bump looks like part of the problem rather than the fix.

## PRIORITY — come back to this one: `AuthTests.testToken`

**Every other entry on this list is a test-robustness problem. This one is a race in product code with a user-visible consequence, and that is why it is called out separately.**

The test asserts that connecting with a bad token throws `AuthenticationException`. On Windows it threw `IOException` instead. The mechanism, in `NatsConnection.connectImpl`:

```java
this.close(true, false);
String err = connectError.get();
if (this.isAuthenticationError(err)) {
    throw new AuthenticationException("Authentication error connecting to NATS server: " + err);
}
throw new IOException("Unable to connect to NATS servers: " + failList);
```

`AuthenticationException` is thrown **only if `connectError` already holds the server's auth text** by the time the connect attempt gives up. The server sends `-ERR 'Authorization Violation'` and then closes the socket. If the read loop has not processed that `-ERR` before the close lands, `connectError` is empty and the caller gets a generic `IOException`.

**Why this matters beyond the test.** The exception type is API surface. An application doing:

```java
try { Nats.connect(opts); }
catch (AuthenticationException e) { /* bad credentials - stop, alert, do not retry */ }
catch (IOException e)             { /* network problem - back off and retry */ }
```

gets routed down the wrong branch whenever it loses that race. Bad credentials would be retried as if they were a transient network fault. The test is not wrong and should not be relaxed; it is reporting a real non-determinism in which exception the client raises.

**What to check when picking this up:**
1. Is the `-ERR` guaranteed to be readable before the socket close is observed, or is that inherently racy on the wire? If inherently racy, the fix is client-side, not test-side.
2. Does `close(true, false)` discard buffered-but-unparsed input that might contain the `-ERR`? If so, drain before closing.
3. `isAuthenticationError(err)` on an empty/null `err` returns false silently — consider whether an unknown-cause connect failure after an auth-configured connection should default differently.
4. Reproduce by loading the machine, or by delaying the reader, rather than by rerunning and hoping.

**Do not "fix" this by widening the test's expected exception type.** That would hide the non-determinism rather than resolve it.

Observed 2026-08-04 on Windows, nats-server v2.14.4, once in a full suite; passed 3/3 on targeted rerun. Not seen on WSL Linux in the same session.

## What was seen

Across roughly six full `:core:test :jetstream:test` runs, each of these failed in **at least one** run and passed in others. No run failed more than two.

| Test | Notes |
|---|---|
| `TLSConnectTests.testProxyTlsFirst` | tls-first through a `ProxyConnection`, 3 sequential connect/close cycles |
| `TLSConnectTests.testReconnectFailsAfterCertExpires` | `maxReconnects(1)`, asserts reconnect **fails** once a cert expires |
| `TLSConnectTests.testForceReconnectFailsAfterCertExpires` | as above via `nc.forceReconnect()` |
| `WebsocketConnectTests.testTLSOnReconnect` | `maxReconnects(-1)`, TLS re-handshake on reconnect |
| `AuthTests.testJWTAuthWithCredsFileAlso` | plain `Nats.connect(url, credentials)` |
| `JetStreamPushTests.testDeliveryPolicy` | |
| `JetStreamPushTests.testAcks` | |
| `SimplificationTests.testFetchOrdered` | serial fetch sizing/expiry assertions |
| `SimplificationTests.testFetchDurable` | added 2026-08-04 — sibling of the above, same `_testFetch` helper, flaked once during the forceReconnect work and passed on rerun |
| `SimplificationTests.testReconnectOverOrdered` | added 2026-08-11. **Which assertion failed was not recorded**, and it matters — see below. The test is built entirely out of fixed sleeps around three server stop/start cycles (`sleep(500)`, then `sleep(3500)` × 3) with `expiresIn(1000)`, so a 500ms idle heartbeat and an alarm at 3× it. `validateOverOrdered` asserts two independent things and either can be the failure: `allInOrder` (no gap and no repeat in the stream sequence) and `count > 0` (some message arrived in the window). They point in completely different directions — `count > 0` is a pure budget problem on a loaded box, `allInOrder` would be an ordered-consumer reset landing on the wrong sequence, which is a product question. Structural details that make it timing-sensitive: the handler `sleep(50)`s per message against `batchSize(100)`, so a large client-side buffer builds up ahead of the handler while the server is killed; `messageCount` is reset before each window but `nextExpectedSequence` is **not**, so `allInOrder` has to hold continuously across all three reconnects; and it is the one test on this list that connects via `ConnectionUtils.managedConnect`, so the un-retried-CLOSED gap discussed under "One hypothesis checked and rejected" **does** apply here, unlike the five connect-side tests where it was ruled out. Mechanism unverified — nothing below is a diagnosis |
| `AuthTests.testToken` | **SEE THE PRIORITY SECTION ABOVE — product-code race, not test robustness.** Added 2026-08-04, WINDOWS, failed once in a full run, passed 3/3 on rerun. **The one flake here that does not look like a flake**, so read the mechanism before re-diagnosing it: it fails `assertThrows(AuthenticationException.class, ...)` with an `IOException` instead. That reads as a structural/logic bug, but `connectImpl` only throws `AuthenticationException` when `connectError` already holds the server's auth text; if the `-ERR 'Authorization Violation'` has not been read before the socket closes, it falls through to the generic `IOException`. Whether that read wins is pure timing |
| `ReconnectTests.testForceReconnectQueueBehaviorCheck` | added 2026-08-04 — flaked once during the javadoc pass, passed on retry and on a clean rerun. **Ruled out as caused by that work: the diff had zero non-comment lines.** The longest test in the suite at ~29s, and deliberately timing-sensitive — it drives `ForceReconnectQueueCheckDataPort` with `DELAY = 75` on every matching write. Measured at 29.098/29.302s before the forceReconnect fix and 28.811/29.778s after, so its duration is inherent, not a regression |

## What was verified — and what was not

Verified, because the concern at the time was "did the change break these":
- none of the original eight reference `connectAsynchronously` (grep, 0 occurrences in each file), so they cannot be touched by that change;
- each passed on a targeted rerun of its own class;
- the directly affected classes were green throughout — `ConnectTests` 28/28, `ErrorListenerTests` 14/14, `OptionsTests`, `ListenerIdTests`, `ConnectionListenerTests`, `NatsConnectionReaderRepointTests`.

**Not** verified: the per-test mechanism. Unlike the first pass above, no failure was traced to a line and a cause. Treat the table as an observation log, not a diagnosis.

## One hypothesis checked and rejected

The obvious guess was that these share the first pass's `managedConnect` root cause — it retries only `IOException`, so a CLOSED outcome from a slow TLS/wss handshake surfaces as `AssertionFailedError` and is never retried (ConnectionUtils.java:53), and that fix was deliberately left unimplemented ("Not changed: `managedConnect` (per your call)").

**That is not the explanation.** None of the five connect-side tests go through `managedConnect` — they call `Nats.connect(...)` directly (`TLSConnectTests:428`/`:24`, `WebsocketConnectTests:26`, `AuthTests`), or drive a `ProxyConnection` (`TLSConnectTests:406-417`). So the unimplemented helper fix would not have prevented any of them, and implementing it should not be expected to.

## Concrete lead: the raised timeouts are wider than the cert-expiry tests' own window

The two `test*ReconnectFailsAfterCertExpires` tests build a client cert that is valid for exactly **`CLIENT_CERT_VALIDITY_MILLIS = 5000`** (TLSConnectTests.java:485), connect inside that window, then `sleep(CLIENT_CERT_VALIDITY_MILLIS)` to let it expire and assert the reconnect fails (`:518`, `:557`). Their entire premise is a 5-second wall-clock window.

The first pass then raised the global budgets to **2× that window**:

| Budget | Before | After | vs. 5000ms cert validity |
|---|---|---|---|
| `connectionTimeout` (OptionsUtils:91) | 4000 | 10000 | 0.8× → **2.0×** |
| `DEFAULT_WAIT` (status-poll budget) | 5000 | 11000 | 1.0× → **2.2×** |

Before the bump, a connect that could not finish inside the cert's life failed fast — the budget expired at or before the cert did. After it, the setup phase is allowed to run for up to ~10-11s against a credential that dies at 5s. On a loaded box a slow initial connect or status-wait can now straddle the expiry boundary, so the cert expires *during setup* rather than at the deliberate `sleep`, and the test asserts from a state it was never written for.

This is a hypothesis — supported by the numbers, **not** yet confirmed by reproduction. But it fits the pattern that these appeared during a period when the global budgets had been widened, and it is the one mechanism here where the earlier load-sensitivity fix plausibly made things worse instead of better.

Cheap next step: loop the cert-expiry pair to get a reproduction rate, then either (a) scale `CLIENT_CERT_VALIDITY_MILLIS` so it comfortably exceeds `DEFAULT_WAIT`/`connectionTimeout` rather than sitting at half of them, or (b) give these two tests a locally reduced connection timeout so the budget is again inside the cert's life. (a) is likelier correct — the test wants "cert expires while connected", not "cert expires during connect".

The general lesson for the ladder: a global timeout bump is only safe for tests that assert something *succeeds within* a budget. Tests that assert something *fails after* a deadline have the opposite polarity, and widening shared budgets can silently invalidate their setup. Worth a scan for other deadline-polarity tests before the ladder is raised again.

The remaining three (`JetStreamPushTests.testDeliveryPolicy`, `testAcks`, `SimplificationTests.testFetchOrdered`) are JetStream delivery/ack/fetch timing and were not investigated. `SimplificationTests.testReconnectOverOrdered` (added 2026-08-11) is in the same untouched group.

Cheap next step for `testReconnectOverOrdered`, worth doing before anything else on it: capture **which** of the two assertions in `validateOverOrdered` failed, and at which of the four call sites. `assertTrue(count > 0)` is another fixed-budget flake and belongs with the rest of this list. `assertTrue(allInOrder.get())` is not — a gap or a repeat in the stream sequence across an ordered consumer's reconnect reset would be the second product-code entry on this page after `AuthTests.testToken`, and would deserve the same treatment. Splitting the helper into two messages, or simply asserting with a message that names the call site, costs nothing and removes the ambiguity the next time it flaps.
