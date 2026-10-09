# Test tracking — ports, suggestions, improvements, flaky tests

The single place for anything about the test suite: what still needs porting, where coverage is missing, tests worth improving, and the flaky-test investigation history.

**This file was `FLAKY_TESTS_ANALYSIS.md` until 2026-08-28**, when the test-suggestions tracker was folded into it. Several archived docs under `~development-history/` still cite the old name — they mean this file. Entries that were complete were removed on 2026-10-06; their history is in git.

## How this file is maintained

- If Claude notices missing coverage or a test worth improving while doing something else, it gets **noted here rather than acted on**. Scott decides what gets done and when.
- If Claude adds or changes a test, Claude updates the matching entry here in the same change.
- If Scott adds a test, Scott says so and Claude updates the entry.
- Coverage reports to check against: `core/build/reports/jacoco/test/html/index.html` and the matching `jetstream` / `service` paths. They reflect the last local run, so re-run before trusting a number.
- **Do not run the full suite to populate this file.** Ask first; Scott runs it, often on Windows, and a concurrent WSL run collides on the `.gradle` locks.

## Rule: no test should leave localhost — with one deliberate exception

Established 2026-08-28. A test should not open a connection to anything outside this machine; it makes the suite depend on someone else's uptime and fails on an offline or firewalled box.

**The rule is about connections, not name resolution.** Testing ip resolution requires a real DNS, so a test that only *resolves* an external name is fine. These stay as they are: `NatsProvidersAndImplementationsTests` lines 27/28/31/32/35/36 (`validateNatsInetAddress("synadia.io")` / `("synadia.com")`), `ServerPoolTests:23` (`HOST_THAT_CAN_BE_RESOLVED_TO_ONE_IP = "demo.nats.io"`, resolved but never connected to), and `ConnectTests:329` / `MaxReconnectResolvedIpsTest:71,103` which use names that deliberately cannot resolve (`.invalid` is RFC 2606 reserved; `.notnats` is not a real TLD).

**The one exception, kept on purpose: `ConnectTests.testConnectWithHappyEyeballsShortCircuitCoverage` connects to `demo.nats.io`.** Scott's call, 2026-08-28 — it is worth having one connect test that actually goes out over the wire. It was briefly changed to a local `NatsTestServer` on `nats://127.0.0.1:<port>` and then put back. If it flaps, **suspect demo.nats.io before suspecting the client** — demo has been reported flaky recently, and that is the likely cause of the local failures seen on 2026-08-28. Do not "fix" this one by making it local.

## Tests still to port — the `tdb/` directory

`tdb/` is untracked and holds V2 tests not yet ported to V3. Scott is porting these by hand deliberately, to build familiarity with the API and to drive the coverage numbers — so **do not offer to bulk-port them.**

| File | @Test count | Lines | Notes |
|---|---|---|---|
| `os/ObjectStoreApiTests.java` | 5 | 409 | already uses `dataAsString` |
| `os/KeyValueConfigurationTests.java` | 2 | 106 | |

## TLS hostname tests — added 2026-10-09

- `impl/TlsHostnameTests` (43 executions), ported from nats.java PR #1638 with `utils/ssl/SniTestServer`: SNI in every `HostnameResolveMode` with and without TLS first, `Unresolved` through a loopback HTTP CONNECT proxy, ip literals, the unresolved-uri `DataPort` overload, default-on hostname verification (wrong name, ip SAN, trust-all context), discovered-ip hostname carry-over, and the pool's per-entry TLS names. `ExpiringClientCertUtil.generateKeyPair` / `generateCertificate` are package-private now, with a subject-alternative-name overload.
- `opentls` removal: `TLSConnectTests` `*OpenTLS*` tests became `*TrustAll*` tests with a supplied `SSLUtils.createTrustAllTlsContext()`; their property halves were dropped. `WebsocketConnectTests.testWsOpenTLS` and `testWssVerifyOpenTLS` were removed, `testWssOpenTLS` became `testWssTrustAllContext`. `ReconnectTests` `tls_noip.conf` case connects by `localhost` and its reconnect to the gossiped ip passes only through the hostname carry-over.
- `OptionsTests.testTlsVerifyHostname` added; `testSslContextIsProvided` and `testNatsUri` assert that `opentls` is rejected.

## Coverage gaps worth a test

### `HappyEyeballsConnector` — only the short circuit is covered
Per the jacoco report, lines 58-104 are entirely uncovered: the staggered-delay task construction, `executor.invokeAny`, the winner/loser socket handling in `closeAllExcept`, and the `No responsive IP found` throw. Covering it needs a hostname resolving to 2+ addresses that are all local. Possible approach: `NatsInetAddress` goes through a pluggable `PROVIDER`, so a test provider could return two loopback addresses (`127.0.0.1` and `127.0.0.2`, both local) for a fake name — unverified, and worth checking whether the provider is swappable from a test.

### Null-argument validation on the push subscribe overloads
Every `MessageHandler` / `SubscribeBehavior` overload calls `Validator.required(...)` and the javadoc promises `IllegalArgumentException`; nothing asserts it.

### `SubscribeBehavior.messageAlarmTime`
Never set in any test under `client/impl`.

### Creator hierarchy: four commented-out tests in `StreamCreatorConfigurationTests`

`testPlacement` (`:627`), `testRepublish` (`:691`), `testSubjectTransform` (`:708`), `testConsumerLimits` (`:721`). Their *validation* content is covered by `StreamCreatorConfigurationTests.testConstructionInvalidsCoverage`; their round-trip and getter/setter content is not.

Two notes worth keeping:

- **`validateSubjectTermStrict` validates a whole subject, not a single term.** `HAS_DOT` ("has.dot") is a valid two-segment subject and does *not* throw; the name misleads. The subject cases that do throw are whitespace, a leading dot, an empty segment, a trailing dot, and misplaced wildcards. `null` and `""` never reach the validator at all — `replaceAllStrings` skips empty entries before calling it.
- **The consumer/stream replica asymmetry is real and is covered on both sides.** `ConsumerCreator` reads `numReplicas < 1 ? UNSET : validateNumberOfReplicas(numReplicas)`, so `numReplicas(0)` silently means "unset"; `StreamCreator.replicas` calls the validator directly, so `replicas(0)` throws. Easy to "fix" wrongly later if the asymmetry is not noticed.

### ObjectStore `ClientError`: the digest / chunks / size mismatch trio

`OsGetDigestMismatch`, `OsGetChunksMismatch` (`ObjectStore.get`) and `OsGetSizeMismatch` are asserted nowhere; the other seven `ObjectStoreClientError` constants are covered in `os/src/test/.../ObjectStoreTests.java`. The trio has never been tested, in v2 either. It needs a corrupted or truncated download — a stored object whose chunks, size or digest disagree with its `ObjectInfo` — which means writing chunk messages directly rather than going through `put`.

## Test improvements

### `ConnectTests.testConnectWithHappyEyeballsShortCircuitCoverage` — worth improving, but keep it on demo
Added 2026-08-28. The test stays pointed at `demo.nats.io` on purpose (see the rules above), so these are improvements *within* that constraint, not attempts to make it local.

- **It does not actually assert the short circuit was taken.** The only assertion is `assertConnected(nc)`. If `demo.nats.io` ever returned two or more addresses, the test would silently stop covering `ips.length == 1` and start covering the racing path instead, and still pass. Nothing anywhere would catch that. Some assertion that pins which branch ran would make the test's name true.
- **Its premise is an assumption about someone else's DNS.** `ServerPoolTests:23` documents `demo.nats.io` as "the host that can be resolved to one ip". That is outside our control and can change without warning. At minimum the assumption deserves an explicit check with a clear failure message, so a second A record on demo reads as "the premise changed", not as a mysterious connect failure.
- **Demo being down should probably skip, not fail.** A JUnit `Assumptions.assumeTrue(...)` on reachability would keep the real-over-the-wire coverage when demo is up and stop demo's downtime reddening a build that says nothing about the client. Needs a decision — a skipped test is also a test that stops telling you anything.
- **The multi-ip racing path has no test at all.** See the `HappyEyeballsConnector` entry under coverage gaps; that one can and should be local.

---

# Part 2 — Flaky tests: investigation history

Everything below comes from the original `FLAKY_TESTS_ANALYSIS.md`, with resolved entries removed on 2026-10-06. It is a running log of observation passes, so read the dates; later passes correct earlier ones.

Context: passes locally on Windows (individually and full-suite), flaps on the GitHub (Linux) runners — some attempts of the same test fail (x2/x4) then pass, one fails all 5 (x5). GH runners are slower, share CPU, and have smaller/oddly-tuned socket buffers vs a dev box, so anything that races the clock or floods a socket surfaces there. Below is the concrete mechanism for each, why it shows up on CI and not Windows, and ideas. None of these point to a wrong assertion that should be "fixed" by weakening it — they're timing/resource races; the fixes are about making the test tolerant of a slow box (and one looks like a genuine behavior issue worth a closer look).

## Shared root causes

1. **Fixed wall-clock budgets vs a slow CPU.** `ConnectionUtils.waitUntilStatus` polls every 100ms up to `millis` (`DEFAULT_WAIT`), `managedConnect` retries connect 10× — but only retries on `IOException`. A connection that ends in **CLOSED** throws `AssertionFailedError`, which is *not* retried. So any connect that quietly closes (slow handshake + `maxReconnects(0)`) fails immediately instead of getting the 10 retries the helper appears to promise.

## Per-test

### `nkill.bat` cannot see WSL — a cross-boundary blind spot

`nkill.bat` is `taskkill /F /IM nats-server.exe`, which only reaches **Windows** processes. A `nats-server` left running under WSL survives it completely.

That matters because WSL2 forwards localhost: a WSL server holding 4222 is invisible to `nkill` and will break a Windows run with the exact error above, with nothing in the Windows environment to explain it. When this was checked, **three `nats-server` processes were still running in WSL** from earlier runs (ports 51019, 50557, 50845 — so not the cause on this occasion, but they could have been), alongside **3596 accumulated `/tmp/nats_java_test*.conf` files**.

Before a Windows run, kill on both sides:

```
pkill -9 nats-server          # WSL
rm -f /tmp/nats_java_test*.conf
```

WSL still has no `nclean` equivalent of its own.

### AuthTests.testWssJWTAuthWithCredsFile — flaps

> **Superseded in part (2026-08-15, updated 2026-08-17).** On Windows this test failed because port 4222 was taken — **resolved 2026-08-17** (`NatsServerRunner` 4.0.0 plus `port: 0` in the ws confs) — not for the handshake-timing reason below. The timing analysis may still explain earlier CI observations, but check the server actually started before pursuing it: a `Failed to run [nats-server ...]` is a bind failure, not slow TLS. Re-measure before treating anything below as live, since every observation predates the conf fix.

- `managedConnect` with `maxReconnects(0)` over **wss + TLS + JWT/creds** — the heaviest, slowest handshake path. Ends CLOSED (status), so `waitUntilStatus` throws `AssertionFailedError`.
- Critical detail: `managedConnect`'s 10× retry loop only catches `IOException` (ConnectionUtils.java:53). A handshake that overruns the connection timeout with `maxReconnects(0)` lands in **CLOSED**, surfaced as `AssertionFailedError` — which the loop does **not** retry. So this "retrying" helper gives the slowest connect path exactly one shot.
- Why not Windows: the wss upgrade + TLS negotiation completes inside the timeout locally; on a loaded CI runner it occasionally doesn't.
- Ideas: raise the connection timeout for the wss/TLS cases, and/or make `managedConnect` treat a CLOSED-result (AssertionFailedError) as retryable like an IOException so the 10 retries actually apply to handshake slowness. (Bumping `maxReconnects` would change semantics the test may rely on, so prefer the timeout/retry-helper route.)

### JetStreamManagementTests bulk tests — open follow-ups from the `max_consumers` server change

The timeouts and the `maximum consumers limit reached [10026]` failure are closed (`~development-history/NATS_SERVER_MAX_CONSUMERS_REGRESSION.md`). What is still open:

- **v3 cannot send `max_consumers` at all unless it is positive, so "unlimited" is only expressible by omission.** `StreamCreator.maxConsumers(long)` normalizes anything below 1 to `UNSET` (`-1`), and `addField(sb, MAX_CONSUMERS, maxConsumers)` (`StreamCreator:229`) drops the field when negative. jnats-json has `addFieldWhenGteMinusOne` for this case and it is not used. The same omission applies to `maxMessages`, `maxMessagesPerSubject`, `maxBytes`, `maxMessageSize`.
- **Forward risk:** nats-server's `// Max asset limits` section holds only `DefaultMaxConsumers` today. If a `DefaultMaxStreams` lands, `testGetStreamInfoOrNamesPaginationFilter` (1400 streams) breaks the way `testGetConsumers` did.
- Carry forward: **any v3 test or example that wants more than 1000 consumers on a stream must say so explicitly** with a positive `maxConsumers`; `-1` will not do it.

## Open idea from the first pass

`managedConnect` retries only `IOException` (`ConnectionUtils.java:53`); a CLOSED outcome surfaces as `AssertionFailedError` and is not retried. Making it retry a CLOSED result was left unimplemented by Scott's call. Also left open: whether the JS request timeout inheriting the connection timeout (2000ms product default) is too aggressive — consistent with jnats today.

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
| `JetStreamPushTests.testDeliveryPolicy` | **Failure detail captured 2026-08-28** (local WSL, full `:jetstream:test` with parallel forks; passed 6/6 isolated reruns after). It is the `DeliverPolicy.ByStartTime` block, line 442 `assertMessage(m, 3)`, and it threw at `assertMessage` line 473 (`assertEquals`) with `expected: <data-3> but was: <data-1>` — **not** at line 472 `assertNotNull`. That distinction matters: a message did arrive inside the 1000ms `nextMessage` window, so this is not slow delivery, it is the consumer starting at sequence 1. The start time is `m3.metaData().timestamp().minusSeconds(1)`, and the setup publishes data-1/data-2, `sleep(1500)`, then data-3 — so data-1 should sit ~500ms below the cutoff and the server should not deliver it. Getting it back means the server saw a start time at or below T1. The timestamp itself is not a suspect: `JetStreamMetaData` splits the `$JS.ACK...` reply subject and parses the nanos token, which is deterministic for a given subject. Mechanism still unexplained. |
| `JetStreamPushTests.testAcks` | |
| `SimplificationTests.testFetchOrdered` | serial fetch sizing/expiry assertions. **Mechanism identified 2026-08-13** — same `_testFetch` helper as `testFetchDurable`, see the note below the table |
| `SimplificationTests.testFetchDurable` | added 2026-08-04 — sibling of the above, same `_testFetch` helper, flaked once during the forceReconnect work and passed on rerun. **Mechanism identified 2026-08-13, see the note below the table** |
| `SimplificationTests.testReconnectOverOrdered` | added 2026-08-11. **Which assertion failed was not recorded**, and it matters — see below. The test is built entirely out of fixed sleeps around three server stop/start cycles (`sleep(500)`, then `sleep(3500)` × 3) with `expiresIn(1000)`, so a 500ms idle heartbeat and an alarm at 3× it. `validateOverOrdered` asserts two independent things and either can be the failure: `allInOrder` (no gap and no repeat in the stream sequence) and `count > 0` (some message arrived in the window). They point in completely different directions — `count > 0` is a pure budget problem on a loaded box, `allInOrder` would be an ordered-consumer reset landing on the wrong sequence, which is a product question. Structural details that make it timing-sensitive: the handler `sleep(50)`s per message against `batchSize(100)`, so a large client-side buffer builds up ahead of the handler while the server is killed; `messageCount` is reset before each window but `nextExpectedSequence` is **not**, so `allInOrder` has to hold continuously across all three reconnects; and it is the one test on this list that connects via `ConnectionUtils.managedConnect`, so the un-retried-CLOSED gap discussed under "One hypothesis checked and rejected" **does** apply here, unlike the five connect-side tests where it was ruled out. Mechanism unverified — nothing below is a diagnosis |
| `AuthTests.testToken` | **SEE THE PRIORITY SECTION ABOVE — product-code race, not test robustness.** Added 2026-08-04, WINDOWS, failed once in a full run, passed 3/3 on rerun. **The one flake here that does not look like a flake**, so read the mechanism before re-diagnosing it: it fails `assertThrows(AuthenticationException.class, ...)` with an `IOException` instead. That reads as a structural/logic bug, but `connectImpl` only throws `AuthenticationException` when `connectError` already holds the server's auth text; if the `-ERR 'Authorization Violation'` has not been read before the socket closes, it falls through to the generic `IOException`. Whether that read wins is pure timing |
| `ReconnectTests.testForceReconnectQueueBehaviorCheck` | added 2026-08-04 — flaked once during the javadoc pass, passed on retry and on a clean rerun. **Ruled out as caused by that work: the diff had zero non-comment lines.** The longest test in the suite at ~29s, and deliberately timing-sensitive — it drives `ForceReconnectQueueCheckDataPort` with `DELAY = 75` on every matching write. Measured at 29.098/29.302s before the forceReconnect fix and 28.811/29.778s after, so its duration is inherent, not a regression |

### The `_testFetch` mechanism — identified 2026-08-13, not fixed

`SimplificationTests.testFetchDurable` and `.testFetchOrdered` (and `.testFetchEphemeral`, not yet on this list) all run the same `_testFetch` helper. The flake is **`assertTrue(elapsed < 100)`** — a 100 ms wall-clock budget, at `SimplificationTests.java:286` for cases 1A/1B/2B and again at `:296` for case 2A.

**MECHANISM CORRECTED 2026-09-01: it is the filesystem, and the "clean worktree passes" control was confounded.** The working tree lives on `/mnt/c` — a **9p** mount (the Windows drive through WSL). A `git worktree` made under `/tmp` is on native **ext4**. A 100 ms wall-clock budget does not survive that difference, so every "reverted source still fails here but the worktree passes" observation below was measuring the mount, not the tree. Controlled run, source held at `HEAD`, only the mount varied:

| tree | source | filesystem | result |
|---|---|---|---|
| worktree under `/tmp` | `HEAD` | ext4 | **PASSED** |
| worktree under `/mnt/c` | `HEAD` | 9p | **FAILED 5/5** |
| the working tree | HEAD + the whole `ClientError` change set | 9p | **FAILED 5/5** |

Same source, different mount, opposite result; same mount, different source, identical result. **So: put the control worktree on the same filesystem as the tree you are testing, or the comparison is worthless.** `/mnt/c/nats/temp/` works. This does not contradict the load sensitivity below — 9p is a large standing handicap that load then tips over. It is not deterministic: the same working tree on the same 9p mount failed `testFetchDurable` 5/5 twice, then passed it outright on a full-module run ~20 minutes later (that run was green overall, with `JetStreamPushTests.testAcks` flaking once and clearing on retry). So on 9p, expect *some* member of the timing family to tip on any given run.

**It is load, not code.** Found while verifying the `z-claude-done/ISSUE_1596_REVIEW.md` work, where it failed 5/5 in a full run and looked exactly like a regression. It is not: with the source reverted byte-identical to `HEAD`, this working tree still failed **3/3** while a `git worktree` at the same commit passed **3/3** concurrently on the same machine. Cleaning the build directories changed nothing. On a later run `testFetchDurable` passed and its sibling `testOverflowFetch` flaked instead and passed on retry — that wandering between tests of the same class is the signature.

**The assertion is load-bearing, so it must not simply be deleted.** The fetch is configured `expiresIn(2000)`, and the two branches assert opposite behaviors:

| cases | meaning | assertion |
|---|---|---|
| 1A / 1B / 2B / 2A | the fetch was satisfied, so it returns immediately | `elapsed < 100` |
| 1C / 1D / 2C | the fetch could not be filled, so it waits for expiry | `elapsed >= 1500` |

Removing the fast-side assertion would lose the coverage that a satisfied fetch returns early rather than sitting until expiry.

**Proposed fix when flaky work is picked up: widen both `elapsed < 100` to `< 750`.** The discriminator only has to separate "immediate" from a 2000 ms expiry, and the slow branch already uses 1500 as its floor, so 750 leaves a clean dead band. A regression where the fetch stops returning early lands near 2000 and still fails. This is not the `testToken` situation — there is no product race being hidden here, only a measurement budget far tighter than the behavior it is discriminating. Two characters, and it covers two entries on this list at once.

### Before trusting ANY full-run result: clean the environment first

A full run must start from a clean slate, and none of the runs recorded in the section below did. On Windows the workflow is `C:\Programs\nt3.bat`, which is the authority:

```
nt3.bat   -> call nclean
             gradlew clean test
             taskkill /F /IM nats-server.exe
             nreport3

nclean.bat -> call nkill                                  (taskkill /F /IM nats-server.exe)
              rd /s /q C:\nats\z-jetstream-storage
              rd /s /q C:\temp\jetstream-storage
              rd /S /Q %LOCALAPPDATA%\Temp\nats
              rd /S /Q %LOCALAPPDATA%\Temp\jetstream
              del %LOCALAPPDATA%\Temp\nats_java_test*.conf
              del %LOCALAPPDATA%\Temp\nats_net_test*.conf
```

Three things it does that a bare `gradlew test` does not: **kill stray `nats-server` processes**, **delete JetStream storage directories**, and **delete accumulated per-test conf files**. A stray server holds a port; leftover JetStream storage means a "new" stream may not be empty.

**This is not hypothetical.** At the point the runs below were recorded, the Windows environment held **443 leftover `nats_java_test*.conf` files** and a populated `%LOCALAPPDATA%\Temp\nats`. Every result in the next section — WSL and Windows alike — was measured against a dirty environment and should be treated as suggestive only, not as evidence about any particular test.

The WSL side has no equivalent script yet. At minimum: `pkill -9 nats-server`, and clear the `/tmp/nats_java_test*.conf` accumulation.

### Connect-under-load: one pattern, a different test each run — observed 2026-08-15

Four consecutive full `:core:test` runs on the same rename-only working tree (diff verified as names, whitespace and comments — zero behavior change) failed a **different** test each time, and every failure was connection establishment:

| Run | Failed | Build |
|---|---|---|
| A | `RequestTests.testSafeRequest` | green on retry |
| B | `testSafeRequest`, `TLSConnectTests.testForceReconnectFailsAfterCertExpires` | green on retry |
| C | `WebsocketConnectTests` ×4 — `testWssVerify`, `testWssVerifyOpenTLS`, `testWssVerifyInterceptor`, `testWssVerifyTlsFirstIgnored` | **BUILD FAILED** |
| D | `ReconnectTests.testReconnectWait` | green on retry |

Every one passes in isolation — the four websocket tests passed 7/7 as a class immediately after failing in the full run. The messages are all connect-time: `Unable to connect to NATS servers: [nats://127.0.0.1:PORT]` and `Unable to make a connection` (the latter is `ConnectionUtils.managedConnect` giving up **after its 10 retries**).

**This is not four flaky tests, it is one condition.** A full run starts and stops a large number of `nats-server` instances; under that load a fresh server is not always accepting when the client dials. Which test loses the race is close to arbitrary, which is why the watch list keeps growing one entry at a time — each new name is a symptom of the same thing rather than a new defect.

Two consequences worth keeping in mind:

* **A green build does not mean a clean run.** Runs A, B and D were all `BUILD SUCCESSFUL` because the retry plugin re-ran and passed. Only run C tipped over into a real failure. Anyone judging "did my change break something" from the exit code alone will miss this entirely.
* **`managedConnect` failing is the strong signal.** It already retries 10 times with increasing backoff. When *that* gives up, the machine is genuinely saturated, not merely slow — which is what run C shows and what separates it from the single-attempt cases like `testSafeRequest`.

Before diagnosing any single entry on this list as a product defect, reproduce it in isolation first. So far nothing on the list has survived that step.

### `RequestTests.testSafeRequest` — new 2026-08-15, mechanism identified, not fixed

Failed in **two consecutive full `:core:test` runs** while passing **3/3 in isolation** with forced recompiles. Passed on the retry plugin's rerun both times, so the build stayed green and it is easy to miss.

**It is not the request logic — it is the connect.** The failure is at `RequestTests.java:139`:

```
java.io.IOException: Unable to connect to NATS servers: [nats://127.0.0.1:50261]
```

which is the `Nats.connect(...)` inside the try-with-resources, before any request is made:

```java
try (NatsTestServer ts = new NatsTestServer();
     NatsConnection nc = Nats.connect(optionsBuilder(ts).maxReconnects(0).build())) {
```

`maxReconnects(0)` gives the connect **no retry budget at all**, and it runs immediately after the server is constructed. Under full-suite load the server is not always accepting yet, and there is no second attempt. Alone on an idle machine it always wins the race; in a full run it sometimes does not.

**Note the contrast with the harness that exists for exactly this.** `ConnectionUtils.managedConnect` retries the connect up to 10 times with an increasing delay. These tests use raw `Nats.connect`, so they opt out of it.

**Proposed fix when flaky work is picked up:** use `managedConnect` here, or drop `maxReconnects(0)` where the test is not actually asserting anything about reconnect behavior — `testSafeRequest` is not. The pattern is not isolated: `maxReconnects(0)` appears throughout `RequestTests`, so the same race is latent in its siblings and `testSafeRequest` may simply be the one that lost first.

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

---

# Two KV tests — V2 findings and the v3 plan (2026-08-11)

Was parked awaiting a V2 investigation. Those notes are now in, below, along with what v3's staging copies do differently and what to change when the KV tests are ported.

| Test | V2 | v3 |
|---|---|---|
| `KeyValueTests.testJustLimitMarkerCreatePurge` | `src/test/java/io/nats/client/impl/KeyValueTests.java:1969` | `kv/src/test/java/io/synadia/client/kv/KeyValueTests.java:1907` |
| `KeyValueTests.testJustTtlForDeletePurge` | `src/test/java/io/nats/client/impl/KeyValueTests.java:2079` | `kv/src/test/java/io/synadia/client/kv/KeyValueTests.java:2001` |

Both are `atLeast2_12`-gated, use a 1-second TTL (`limitMarker` / bucket `ttl`), assert the exact operation sequence of the raw stream messages from a dispatcher, and poll `getStreamInfo` for the message count to reach zero while asserting the elapsed wall clock is `>= 1000`ms.

Both tests are ported into `kv/src/test` and run. None of the plan below has been applied yet: `waitForPurge` still sleeps before the first check, and the TTL test still takes its mark after `kv.delete(key)` (`:2053-2054`).

## What the V2 investigation found

Neither failure was ever reproduced on the V2 investigator's machine — both passed repeatedly there. What was done instead was to instrument every timing-sensitive assertion and measure its actual margin. Two structural fragilities came out of that, both measured, neither confirmed as *the* cause.

**1. The poll budget was iterations, not time.** V2's loop was `while (++safety < 10000 && errorLatch.getCount() > 0)` with no sleep. How much wall time 10000 unthrottled `getStreamInfo` round trips buys depends entirely on the machine. Measured on the V2 box: 0.70–0.88ms per poll, so the budget was worth 7–9 seconds against a 1.2–2.1 second need. Exhaust it and `gotZero` stays `-1`, and `assertTrue(gotZero - mark >= 1000)` fails **with no message**. Needs roughly a 4–6x faster poll rate to bite — plausible on fast hardware, and it would hit both tests, which is the only finding that explains both flapping together.

**2. The TTL test measured from the wrong side of the write.** In `testJustTtlForDeletePurge`, `mark` was taken *after* `kv.delete(key)` returned. The message's 1-second clock starts when the **server** writes it, which is strictly before the client takes `mark`, so the interval being measured is shorter than the TTL by construction. Measured margins: **+249ms and +251ms**, against +1084ms and +2105ms for the marker test. That margin is supplied by the server's `max_age` expiry sweep lagging ~250ms behind the true expiry — not by anything the test controls. A server that expires more promptly fails it outright.

V2 applied two fixes: time-bounded loops with a `sleep(10)`, and moving `mark` above the write. Both tests pass 4/4 there afterwards. Note the second fix is a *structural* correction, not a margin increase — moving `mark` gains only one round trip in absolute terms, but it changes elapsed from `1000 + sweepLag − roundTrip` to `1000 + sweepLag + roundTrip`, which is above 1000 unconditionally instead of conditionally.

## What v3's copies do differently

v3 has already factored the poll loop into a helper, which changes the picture:

```java
private static long waitForPurge(JetStreamTestingContext ctx, String rawStream) throws IOException, JetStreamApiException {
    for (int tries = 0; tries < 20; tries++) {
        sleep(500); // it takes a bit of time for the purge to happen, depends on the server load
        StreamInfo si = ctx.jsm.getStreamInfo(rawStream);
        if (si.getStreamState().getMessageCount() == 0) {
            return System.currentTimeMillis();
        }
    }
    return -1;
}
```

**Finding 1 does not apply to v3.** 20 tries × 500ms is a wall-clock budget of ~10 seconds regardless of machine speed. That fragility was already designed out.

**Finding 2 does apply**, unchanged — `KeyValueTests.java:2053-2054` is still `kv.delete(key); long createdTimeMark = System.currentTimeMillis();`.

And the helper introduces two problems of its own:

* **It sleeps before the first check.** The returned timestamp can be up to 500ms later than the moment the stream actually emptied, so every elapsed measurement is inflated by 0–500ms. That is currently *masking* finding 2 — the ~250ms deficit is hidden inside the sleep granularity. Fixing the mark without fixing this leaves the measurement coarse; fixing this without fixing the mark could newly expose finding 2.
* **`-1` flows into arithmetic.** On timeout the caller computes `purgedTimeMark - createdTimeMark >= 1000` against `-1`, producing a large negative and an `assertTrue` failure with no message — the same no-diagnostic failure mode as V2's.

## Additional: a stale mark in the marker test

`testJustLimitMarkerCreatePurge` sets `createdTimeMark` once at line 58 and never reassigns it, so the *second* `assertTrue(purgedTimeMark - createdTimeMark >= 1000)` at line 82 measures the whole test rather than the purge TTL, and is trivially true. Present identically in V2, where it was deliberately left alone because re-marking turns a passing no-op assertion into a real one that could newly fail.

In v3 that argument does not hold — the test is not running, so there is nothing to regress. Fix it here and find out what it actually asserts.

## Plan

Order matters: do 1 and 2 together, because 1 alone can expose 2.

1. **`waitForPurge`: check first, then sleep, and shorten the interval.**

```java
private static long waitForPurge(JetStreamTestingContext ctx, String rawStream) throws IOException, JetStreamApiException {
    long timeoutAt = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < timeoutAt) {
        if (ctx.jsm.getStreamInfo(rawStream).getStreamState().getMessageCount() == 0) {
            return System.currentTimeMillis();
        }
        sleep(50);
    }
    return -1;
}
```

Same ~10 second bound, but the returned timestamp is within ~50ms of the real event instead of ~500ms, so the elapsed assertions measure what they claim to.

2. **Move the mark above the write** in `testJustTtlForDeletePurge`:

```java
long createdTimeMark = System.currentTimeMillis();
kv.delete(key);
```

3. **Re-mark before the purge** in `testJustLimitMarkerCreatePurge`'s second block, so the assertion stops being trivially true. Expect this one to need a look — it has never actually been evaluated in either repo.

4. **Assert the timeout explicitly** rather than letting `-1` reach the arithmetic:

```java
long purgedTimeMark = waitForPurge(ctx, rawStream);
assertTrue(purgedTimeMark > 0, "stream never emptied");
assertTrue(purgedTimeMark - createdTimeMark >= 1000);
```

5. **Lower priority — the delivery race.** `assertEquals(2, messages.get())` reads a counter incremented by the dispatcher thread, immediately after a loop that polls *stream state*. Stream state reaching zero says the server removed the messages; it says nothing about the client having delivered them. V2 measured roughly a second of slack here (the marker is delivered at ~1s, the stream does not empty until ~2s), and a deliberate 200ms handler stall did **not** break it. It needs a stall over a second — a long GC pause, a loaded runner — so it is real but well down the list. If it does surface, wait for the count before asserting on it rather than widening the sleep.

## Verification

Whatever is changed, confirm the assertions still mean something by instrumenting once and reading the margins, as V2 did:

```java
System.out.println("PROBE elapsed=" + (purgedTimeMark - createdTimeMark)
    + "ms (needs >=1000, margin " + (purgedTimeMark - createdTimeMark - 1000) + "ms)"
    + "  messagesDelivered=" + messages.get() + " ops=" + ops);
```

A margin in the low hundreds of milliseconds means the assertion is riding on server expiry-sweep granularity and will flap somewhere else. V2's marker test showed +1084ms; its TTL test showed +249ms before the fix.

## New candidate, seen 2026-08-23: `SimplificationTests._testFetch` — `assertTrue(elapsed >= 1500)`

Two full-suite runs on the same day each failed one of the sibling tests that share the `_testFetch` helper (`SimplificationTests.java:293`), and neither failed when the class was run alone: `testFetchOrdered` on a working tree, `testFetchDurable` on a clean `b4e15f4a` worktree. Different method, same assertion, only under whole-suite load — so it is the helper, not either test.

The assertion is a timing **lower** bound: cases 1C, 1D and 2C ask for more messages than the stream holds, so the fetch is supposed to sit until the pull request expires, and the test requires at least 1500ms of that. A failure means the fetch ended *early*, which load does not cause directly — the suspect is the idle-heartbeat alarm firing on a loaded box and terminating the fetch, so `nextMessage` returns null before expiry. Worth an instrumented probe of `elapsed` (as under **Verification** above) before changing anything.

Also failed once in the same clean-worktree run, unrelated and not investigated: `TLSConnectTests.testReconnectFailsAfterCertExpires()`.

**Later the same day it spread, and the machine state is what moves it.** Six more runs, alternating a working tree against a clean `b4e15f4a` worktree, at a point where the whole module ran in ~2.5 minutes instead of ~6 - so more forks live at once. Both trees failed, and the failing test moved every run: `testFetchDurable`, `testFetchEphemeral`, `testFetchOrdered`, `testOverflowFetch`, and `JetStreamPushTests.testDeliveryPolicy`, which is outside the `_testFetch` family. Sometimes the retry cleared it and the build passed; sometimes all five attempts failed and it did not. The same tree gave a fully green 937 earlier in the day. Treat a `SimplificationTests` or `JetStreamPushTests` timing failure as a machine-load reading until it reproduces on an idle box, and confirm any suspect change against a clean worktree run taken back to back with it - a single green or red run on one tree proves nothing here.

## New candidate, seen 2026-08-28: `ConnectTests.testConnectWithHappyEyeballsShortCircuitCoverage` — demo.nats.io, and it stays that way

Local WSL, `:core:test`. The class ran 30 tests with 2 failures; this one flapped 2 of its 3 retry attempts (failed 2.120s, passed 2.476s, failed 2.011s), so the retry plugin cleared the build.

```
java.io.IOException: Unable to connect to NATS servers: [nats://demo.nats.io:4222]
    at io.synadia.client.impl.NatsConnection.connectImpl(NatsConnection.java:295)
    at io.synadia.client.ConnectTests.testConnectWithHappyEyeballsShortCircuitCoverage(ConnectTests.java:576)
```

**Different root cause from everything else on this page — do not file it with the load/timing group.** `ConnectTests.java:573` builds `Options.builder().server("demo.nats.io").hostnameResolveMode(HostnameResolveMode.HappyEyeballs)`, so it is the one test in the repo that opens a connection to a third-party server over the public internet. (`ServerPoolTests:23` names the same host but only resolves it.) The likely cause here is simply that **demo.nats.io has been reported flaky recently** — suspect the server, not the client, when this one goes red.

**This is intentional and is not to be "fixed".** Scott's call, 2026-08-28: it is worth having one connect test that actually goes out over the wire. It was briefly pointed at a local `NatsTestServer` and then put back. See the localhost rule in Part 1 above for the exception and the reasoning.

**Recurred on CI, 2026-08-31** — Windows run [33420052954](https://github.com/synadia-io/nats.java.v3/actions/runs/33420052954), `:core:test`, 569 tests / 5 failed: this test alone, failing all 5 attempts and taking the build down. `Build Main` for the same commit failed too, so it was not a Windows-specific effect. Consistent with demo.nats.io being unreachable at the time rather than anything in the client — which is exactly the reading this section calls for, now with a data point behind it.

Separately, and unrelated to the flake: per the jacoco report, `HappyEyeballsConnector` lines 58-104 — the whole multi-ip racing path — are uncovered. Only the `ips.length == 1` short circuit is exercised. Tracked under coverage gaps in Part 1.

## `ServiceTests.EchoHandler` double-counts

The 2026-09-01 publish-beats-SUB race in `testQueueGroup` / `testResponsesFromAllInstances` is closed: both now gate on `isStarted(1000)`. Still open: `EchoHandler.onMessage` increments `counter` once directly (`ServiceTests.java:397`) and once again inside the default constructor's `responder` lambda (`:385`). Nothing reads `counter` today, so it breaks nothing — but it is a landmine for the first test that does assert on it.

## New candidate, seen 2026-09-03: `AuthTests.testEncodedPassword` — the auth failure sometimes arrives as a generic connect failure

Scott's full Windows suite, which **passed overall** — this flapped 2 of its retry attempts, so the retry plugin cleared it. Report: `core/build/reports/tests/test/classes/io.synadia.client.AuthTests.html`.

```
org.opentest4j.AssertionFailedError: Unexpected exception type thrown,
    expected: <io.synadia.client.AuthenticationException> but was: <java.io.IOException>
    at io.synadia.client.AuthTests.lambda$testEncodedPassword$4(AuthTests.java:105)
Caused by: java.io.IOException: Unable to connect to NATS servers: [nats://uspace+space:pspace+space@127.0.0.1:4262]
    at io.synadia.client.impl.NatsConnection.connectImpl(NatsConnection.java:295)
    at io.synadia.client.AuthTests.assertEncoded(AuthTests.java:112)
```

**What the assertion is for.** `AuthTests.java:105` is the last line of the test and the only negative case in it: `assertThrows(AuthenticationException.class, () -> assertEncoded("space+space", port))`. Every line above it asserts that a percent-encoded user/pass round-trips and authenticates; this one asserts that a **literal `+` is a plus, not a space**, so the credentials must be rejected. The connection does fail every time — what varies is *which* exception comes out.

**Scott's read, 2026-09-03: a timing issue or something in the exception handling.** Not diagnosed further. The observable is that the specific `AuthenticationException` is only surfaced sometimes, and otherwise the attempt ends in the generic `"Unable to connect to NATS servers"` `IOException` from `connectImpl` (`NatsConnection.java:295`) — the same shape of problem as [[REQUEST_BEHAVIOR_IMPROVEMENT]] is fixing elsewhere: a specific, known cause collapsing into a generic `IOException` that tells the caller nothing. Worth checking whether this one is a *client* defect rather than a test defect before changing the assertion — a connect that knows it was rejected for auth and reports "unable to connect" is losing information a user would want too.

**Do not "fix" this by widening the assertion to accept either exception** until that question is answered. A negative test that passes for the wrong reason proves nothing — the four negative wss tests passed all through the 2026-08-17 port-4222 breakage because the port was unreachable, not because of the TLS mismatch they exist to exercise.
