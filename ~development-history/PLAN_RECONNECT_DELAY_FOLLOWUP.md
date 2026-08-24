# Plan — Reconnect delay: close out PR #1578 and clean up the redesign's leftovers

Started as "what is the state of `INTEGRATION_PLAN_PR_1578.md`, and was it superseded?" — answered in **Background** at the bottom. What came out of it is one real code change (item 3) plus a test gap (item 4). Items are numbered in execution order.

| | Item | State |
|---|---|---|
**COMPLETE.** Items 1-4 shipped in `8dee6cb5` (build green, 13m). Items 3f + 5 (tests only) shipped in `167c03d5` — and that build **hung for 6 hours and was cancelled**; root cause and rewrite are in item 6. Fixed in `acdeb8e6`, build green in 13m, matching the 12-13m baseline. No product code was ever at fault — `core/src/main` was byte-identical to `8dee6cb5` throughout.

| 1 | Documentation corrections (incl. 1a — enum property values) | Done 2026-08-12 |
| 2 | Doc bookkeeping | Done 2026-08-12 |
| 3 | Restore V2's call-site gating | **Done 2026-08-12** — 3a-3f all complete |
| 4 | Make `BeforeAllRounds` the default | **Reverted 2026-08-12** — default stays `BeforeSubsequentRounds` |
| 5 | Connection-side LDM wiring tests | Done 2026-08-12, rewritten 2026-08-13 (see 6) |
| 6 | Fix the CI hang the new tests caused | Done 2026-08-13 |

One item was investigated and dropped; it is recorded under **Dropped** rather than numbered.

---

## 1. Documentation corrections — DONE 2026-08-12

Four places named a default or a method that no longer existed.

Three javadoc blocks still said the default behavior was `BeforeSubsequentRounds`; it has been `LameDuckAware` since the redesign, and in `OptionsBuilder` the javadoc contradicted the code two lines below it. Fixed in `Options.java:634`, `OptionsBuilder.java:970-976` (both links), and `OptionsProperties.java:118-119` (added `LameDuckAware` to the constant list, fixed the fallback link). `DefaultReconnectDelayHandler` is linked by simple name in the builder block, since `OptionsBuilder.java:3` already imports it.

`MIGRATION_GUIDE.md:176` said the SAM goes to `long getWaitTime(long round, Options options, boolean secure, boolean lameDuckTriggered)`; the shipped name is `getWaitTimeMillis`. Fixed. The `Duration getWaitTime(long totalTries)` on the "from" side of that same sentence is the v2 name and correctly stays.

`:core:compileJava` and `:core:javadoc` both clean afterward — 0 warnings, 0 errors.

**Superseded in part.** Two of these three javadoc blocks were rewritten again by items 3d and 4 — the default moved on to `BeforeAllRounds`, and the `OptionsBuilder` block's claim that the enum has no effect on a custom handler became false once item 3 landed. The `OptionsProperties` constant list is the only part still standing as written here.

### 1a — Enum-valued properties: document the legal values — DONE 2026-08-12

Came out of asking whether `ReconnectDelayBehavior.get` should accept `Before_All_Rounds` / `Before.All.Rounds` / `"Before All Rounds"` by normalizing punctuation and case.

**Answer: no, this is a documentation gap, not a parsing gap** — and treating it as one would have cost more than it bought. All three enum factories (`ReconnectDelayBehavior`, `SubjectValidationType`, `HostnameResolveMode`) use an identical `equalsIgnoreCase` loop, as does V2's version of the same enum, so making one lenient means either an unexplained difference between siblings or a shared normalizer across all three. The strings only ever arrive from a properties file whose *keys* are already camelCase, so `BeforeAllRounds` is the natural thing to type. And leniency treats the symptom: the real hazard is that an unrecognized value silently becomes the default, so `reconnectDelayBehavior=BeforeAllRound` (missing s) gives you `BeforeSubsequentRounds` and you never find out. Normalization shrinks that hole without closing it. **If effort goes anywhere here, it goes to the silent fallback** — absent/null → default is right, present-but-unrecognized is a user error and deserves to be visible. That is a behavior change (V2 falls back silently, and `OptionsTests` asserts `"bogus"` → default), so it stays a maintainer decision, unmade.

What the question did surface is that the values were documented inconsistently, and one entry was flat wrong:

* **`README.md:33` claimed the `reconnectDelayBehavior` default was `LameDuckAware`.** Stale — it is `BeforeSubsequentRounds`, and was `LameDuckAware` only briefly. Fixed, and the row now lists all three legal values.
* **`README.md:39` documented none of `hostnameResolveMode`'s six values.** A reader had no way to discover `HappyEyeballs` from the property docs at all. Now lists all six.
* **`OptionsProperties.PROP_HOSTNAME_RESOLVE_MODE` javadoc** was the one enum constant not enumerating its values, while `PROP_SUBJECT_VALIDATION_TYPE` and `PROP_RECONNECT_DELAY_BEHAVIOR` both did. Now enumerates them, and records that this property alone *ignores* an unrecognized value rather than falling back — `HostnameResolveMode.get` returns `null` on no-match and `OptionsBuilder.java:261-266` skips the setter, so the current value survives. Verified in the code, not assumed.

Listing the exact spellings where the user reads them is what makes the leniency question moot.

**Also cross-checked the rest of the README table against the code** rather than assuming the one stale row was alone: 34 of 63 rows carry a checkable literal default, and all 34 now match `OptionsConstants` / the `OptionsBuilder` field initializers. The remaining rows are `(none)`/class-name/prose defaults with nothing to compare.

## 2. Doc bookkeeping — DONE 2026-08-12

* `INTEGRATION_PLAN_PR_1578.md` got a `**State: COMPLETE**` header (shipped, absorbed by the redesign, step 3a moot, step 6 gone) and moved to `z-claude-done/`. Its original text is preserved below that header unchanged, so the `Partially integrated` wording inside it is historical, not a live claim.
* `TODO.md`: entry dropped from `## Plans / Audits`, `## Recently Closed` line added.
* `z-claude-done/INTEGRATION_PLAN_OVERVIEW.md`: all six lines flipped — table row (now `**Integrated**`), the "Net:" tally (9 integrated, all of them), the `## PR 1578` heading plus a closed-out note, the "Missing (the gap)" lead-in marked as historical, the plan-file table row, and "Remaining integration" which now reads none-left instead of "Only 1578 is left".

## 3. Restore V2's call-site gating — **DONE 2026-08-12, the key change**

V2's behavior — **the enum decides *whether* the handler is called at all for round 1, and it decides that for every handler, custom included** — is the one v3 needs. v3's redesign moved that decision inside `DefaultReconnectDelayHandler`, which quietly demoted the enum to "a setting only the default handler reads". Undo that.

**The whole point of the option is that the user gets to set the behavior.** In v3 today they can set it — builder setter, property, `get(String)` factory all present — but the setting goes inert the moment they also supply their own handler, which is precisely the user most likely to be reaching for it. A setting that silently stops applying is worse than not having one.

The separation this lands on is cleaner than either predecessor, and is the reason to do it rather than only to match V2: **the connection decides *whether* to wait; the handler only says *how long*.** Today the handler answers both by returning `0`, which is why the enum had to leak into it.

### 3a — `NatsConnection.reconnectImplConnect()` gates round 1 — **DONE 2026-08-12 (with 3b)**

Landed as written, except the consume lives in `reconnectImplConnect` rather than inside the gate — the value is needed twice (to decide, and to pass to the handler), so reading it once at the call site and handing it to `delayBeforeFirstRound(boolean)` avoids a second field read. **3b came along necessarily**: with the gate consuming the flag, leaving the old read-and-clear in `invokeReconnectDelayHandler` would double-consume, so the handler would receive `false` on the very call the gate fired for, and `DefaultReconnectDelayHandler`'s `LameDuckAware` arm would then return `0` — silently defeating the delay. 3a alone is not a correct state; 3a+3b is the minimum unit.

`ReconnectTests` + `TLSConnectTests` green 44/44 after the change, including every entry those suites contribute to the flaky watch list (`testProxyTlsFirst`, `testReconnectFailsAfterCertExpires`, `testForceReconnectFailsAfterCertExpires`, `testTLSOnReconnect`, `testForceReconnectQueueBehaviorCheck`). `OptionsTests`, `DefaultReconnectDelayHandlerTests`, `InfoHandlerTests` also green.

Original plan below.

`core/src/main/java/io/synadia/client/impl/NatsConnection.java:456-470`. Today:

```java
if (first == null) {
    first = cur;
    invokeReconnectDelayHandler(++round);   // round becomes 1
}
```

Becomes:

```java
if (first == null) {
    first = cur;
    ++round;                                // round 1 happened; whether it waits is the gate's call
    if (delayBeforeFirstRound()) {
        invokeReconnectDelayHandler(round);
    }
}
```

with the gate — the only place the enum is read, and the only place the LDM flag is consumed:

```java
private boolean delayBeforeFirstRound() {
    boolean ldt = lameDuckTriggered;
    lameDuckTriggered = false;              // always consume, so a stale signal cannot leak into a later campaign
    switch (options.reconnectDelayBehavior()) {
        case BeforeAllRounds:
            return true;
        case LameDuckAware:
            return ldt;
        default:                            // BeforeSubsequentRounds
            return false;
    }
}
```

The wrap-around branch is unchanged — rounds 2+ always call, under every behavior.

Note the unconditional clear: only the `LameDuckAware` arm *uses* the flag, but if the other two arms left it set, an LDM signal received under `BeforeSubsequentRounds` would survive to whatever campaign ran next. Read once, clear once, branch after.

**This also settles the `lameDuckTriggered` consume contract**, which was tracked separately until now. `ReconnectDelayHandler`'s javadoc says the connection "consumes this flag on the `round == 1` call, so subsequent rounds within the same campaign always see `false`", while `invokeReconnectDelayHandler` (`:2556-2558`) reads and clears it on every call. The open question was whether to reword the javadoc or gate the consume. Gating it here makes the existing javadoc true as written, so no reword is needed. (There was never a V2 behavior to match: V2 does exactly two things with lame duck — `NatsConnection.java:1866-1867` emits the `LAME_DUCK` event, and `ServerInfo` parses the `ldm` field. Nothing sets a flag, nothing reaches the delay path, and V2's `Duration getWaitTime(long totalTries)` has no LDM channel. `lameDuckTriggered` is a v3 invention from the redesign.)

### 3b — `invokeReconnectDelayHandler` stops consuming — **DONE 2026-08-12 (with 3a)**

Done exactly as below. The wrap-around call site passes `false`, which is what the interface's javadoc already promises ("subsequent rounds within the same campaign always see `false`"). Body below the signature is untouched.

`:2556-2558`. Drop the read-and-clear; take the flag as a parameter instead so the handler still receives it:

```java
protected void invokeReconnectDelayHandler(long round, boolean lameDuckTriggered) {
```

Everything below that line stays as-is, including the unconditional `reconnectWaiter` allocation — that matches V2 (see **Dropped**).

### 3c — `DefaultReconnectDelayHandler` loses the behavior switch — **DONE 2026-08-12**

`getWaitTimeMillis` is now the single line below, and the class javadoc no longer claims to switch on the behavior — it states the opposite, that the connection reads `reconnectDelayBehavior` to decide *whether* to invoke a handler, and a handler is only ever asked *how long*. Behaviorally a no-op: the switch agreed with the gate on every reachable path, which is exactly why it was duplication rather than a bug. Its test table collapsed with it — see 3f.

`core/src/main/java/io/synadia/client/impl/DefaultReconnectDelayHandler.java:24-35` collapses to the one line the switch was wrapping:

```java
@Override
public long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered) {
    return computeWaitMillis(options, secure);
}
```

`computeWaitMillis` is untouched. The class javadoc's "switches purely on `Options.reconnectDelayBehavior()`" (`:9-10`) becomes wrong and must change — the connection switches on it now.

**Keep the `lameDuckTriggered` parameter** even though the default handler no longer reads it. A custom handler still wants to know "this reconnect was triggered by an LDM drain" so it can wait longer; dropping it is a second breaking signature change for no gain. Same for `options` and `secure`.

### 3d — Revise the builder javadoc from item 1 — **DONE 2026-08-12 (with item 4)**

The item 1 reword on `OptionsBuilder.reconnectDelayBehavior(...)` said the enum "has no effect when a custom `ReconnectDelayHandler` is supplied", which this item makes false — it applies to every handler again. Rewritten to say the enum controls whether the handler is invoked before the first round and that it applies to every handler including a custom one. Done as part of item 4, since that item had to edit the same block for the default anyway.

### 3e — Sub-decision: round numbering — **DECIDED 2026-08-12: leave it 1-based**

v3's round numbers stay one higher than V2's: V2 passes `0` for the pre-first-round call and `1` for the first wrap-around; v3 passes `1` and `2`.

**Maintainer's reason, which is better than the one this plan originally gave: it is a round, not an index.** Rounds are counted the way people count them — the first round is round 1. Starting at 1 was deliberate, not an off-by-one inherited from the redesign. V2's `0` is the artifact, arising from a pre-first-round call bolted onto a counter that started at the first wrap-around.

(The original argument here — that realigning would be a third breaking change for handler authors — still holds, but it is the weaker one. Correct naming does not need a migration-cost justification.)

### 3f — Tests — **DONE 2026-08-12**

Before this, the change had **no coverage at all**. The one test that puts a custom handler on a live connection (`ConnectTests.java:455`, `(l, o, s, d) -> 1000L`) silently changed behavior when 3a/3b landed — it used to impose a 1000 ms wait before the first reconnect attempt and now does not — and passed both ways, because it never asserts on timing. The suite could not tell a present gate from an absent one.

**Four new tests in `ReconnectTests`**, all driving a real disconnect/reconnect with a recording handler that returns `0` (so the reconnect is not slowed; the question is which rounds reach the handler at all), behind one helper, `roundsSeenAcrossReconnect(behavior, lameDucksOut)`:

| Test | Asserts |
|---|---|
| `testDelayBehaviorSkipsFirstRoundWhenBeforeSubsequentRounds` | round 1 never reaches the handler |
| `testDelayBehaviorDelaysFirstRoundWhenBeforeAllRounds` | round 1 does reach the handler |
| `testDelayBehaviorSkipsFirstRoundWhenLameDuckAwareWithoutLameDuck` | with no LDM signal, `LameDuckAware` behaves as `BeforeSubsequentRounds` |
| `testDelayHandlerSeesNoLameDuckOnOrdinaryReconnect` | every invocation of an ordinary reconnect reports `lameDuckTriggered == false`, first round and wrap-arounds alike |

**Verified in both directions, not just green.** Forcing the gate to a constant and re-running:

| Gate forced to | Result |
|---|---|
| always `true` (the pre-3a behavior) | both *skip* tests **FAIL**, `BeforeAllRounds` passes |
| always `false` | `BeforeAllRounds` **FAILS**, both *skip* tests pass |

So the three tests pin the gate from both sides — neither a stuck-open nor a stuck-closed gate survives. A test that passed both ways would not be covering this.

`DefaultReconnectDelayHandlerTests` — the nine-row behavior table collapsed into one test, `sameWaitForEveryBehaviorRoundAndLameDuckFlag`, which sweeps all three behaviors × rounds {1, 2, 17} × lameDuck {false, true} × secure {false, true} and asserts the answer is always the standard wait. That is now the handler's actual contract after 3c: it answers *how long*, never *whether*, so nothing but the secure flag may change what comes back. The old rows asserted behavior-specific returns that are unreachable from the connection now. `computeWaitMillis` and singleton tests are untouched.

`OptionsTests` — unaffected; it only asserts plumbing and defaults.

## 4. Make `BeforeAllRounds` the default — **TRIED, THEN REVERTED 2026-08-12**

Done, then undone the same day. The default is **`BeforeSubsequentRounds`**, matching v2, and that is where it stands.

**What the attempt bought was the evidence.** The proposal was that backing off before the first round is better because it spreads a reconnect storm. It does — but making it the *default* charges that cost to every deployment, including the single-client-single-blip case that just wants to retry now. The three broken tests below were the tell: they are ordinary reconnect tests with ordinary budgets, and they broke simply because a first-round delay appeared where none had been. Real applications with tight reconnect budgets would have hit the same wall, silently. So the behavior stays available and now carries a much fuller explanation of when to reach for it (see the enum javadoc), but the fast path stays the default and opting into herd protection is a deliberate act.

`LameDuckAware` also lost its brief tenure as the default in the process; it is now just another opt-in constant.

The revert restored `ReconnectDelayBehavior` (fallback + the "this is the default" note), `OptionsBuilder` (field, null-reset, javadoc), `Options`, `OptionsProperties`, `MIGRATION_GUIDE.md`, and the three accommodating test edits — those last are byte-identical to HEAD again, verified with `git diff --quiet`. Two things were deliberately *kept*: the strengthened `OptionsTests` probes (see below) and the expanded enum javadoc.

### 4a — Kept from the attempt: the enum javadoc, with the explanation at class level

`ReconnectDelayBehavior` now explains the actual trade rather than naming it, and the explanation lives on the **class**, not on a constant. The class comment carries the whole argument: failover latency against reconnect storms; a server going down disconnects every client attached to it at once, so a large population all retries in the same instant and arrives at the survivors as one spike; `BeforeAllRounds` spreads that across the wait plus jitter; **the jitter is what actually does the spreading** — a fixed wait moves the spike rather than flattening it — so `reconnectJitter` must not be zero; and scale is the deciding factor.

The three constant docs are back to a few lines each, saying only what that constant does plus a one-line characterization. An earlier draft had the entire thundering-herd essay hanging off `BeforeAllRounds`, which is the wrong altitude — a reader comparing the three constants had to read one of them in full to understand the other two.

### 4b — Settled: three states, not four

Considered splitting the enum into a 2×2 — `BeforeSubsequentRounds` / `BeforeAllRounds` × plain / `…LameDuckAware` — to separate the round-1-delay axis from the lame-duck axis, which the current three values conflate (`LameDuckAware` is welded to the `BeforeSubsequentRounds` base, so "BeforeAllRounds and also honor LDM" is unsayable).

**Rejected: the fourth cell is empty.** `BeforeAllRounds` already delays before the first round whatever caused the disconnect, so it covers a lame-duck drain by construction — `BeforeAllRoundsLameDuckAware` would behave identically at the gate and ship a constant that does nothing. Three states stand, and the class javadoc now says this explicitly so the question does not get re-opened: "there is no lame-duck variant of it because there would be nothing left for the variant to do."

### 4c — Kept from the attempt: stronger `OptionsTests` probes

Several assertions used `BeforeAllRounds` as the "explicit non-default" value. That was fine while the default was `LameDuckAware`, would have been *broken* while `BeforeAllRounds` was the default (passing even if the setter, the null-reset, or the copy-constructor did nothing), and is fine again now — but it was luck, not design. The explicit-setter, null-reset, and copy-constructor cases now probe with `LameDuckAware`, and the static factory gained real-match assertions for `BeforeAllRounds` and `LameDuckAware` so a genuine match is proven distinct from the fallback. Whatever the default becomes next, these probes stay honest.

### 4d — The fallout, and why it is the argument against defaulting to it

The full `core` suite went from green to **15 failures (3 distinct tests × 5 retries)**. Attribution was confirmed, not assumed — flipping only the field default back made all three pass, flipping it forward made them fail again:

| Test | Symptom | Cause |
|---|---|---|
| `TLSConnectTests.testReconnectFailsAfterCertExpires` | `expected: <true> but was: <false>` | Waits on a **2 s** error latch; the default delay is 2000 ms + up to 1000 ms *TLS* jitter, so it could not pass by construction |
| `ErrorListenerTests.testLastError_ClearError_AuthViolation` | `'Validate Received' Failed [nats: connection reconnected]` | 5 s validate budget, default 2000 ms wait now spent before the first attempt across a three-server list |
| `AuthTests.testThatAuthErrorIsCleared` | `expecting CONNECTED but was RECONNECTING` | 5 s `confirmConnected` budget, deliberate `reconnectWait(1000)` now also charged before round 1 |

None of the three is a test *of* reconnect delay. They are ordinary reconnect tests that broke purely because a first-round delay appeared where none had been — which is exactly what would have happened to application code with a tight reconnect budget, without the courtesy of a failing test. **That is the case against the default, and it is why the revert followed.** All three accommodations have been undone; the files are byte-identical to HEAD.

Full `core` suite green after the revert: **554 tests, 0 failures**, javadoc 0 warnings / 0 errors.

One observation worth keeping even though its edit was reverted: `TLSConnectTests.testReconnectFailsAfterCertExpires` is on the flaky watch list because of the tension between `CLIENT_CERT_VALIDITY_MILLIS` and its 2-second latches. Giving it a small explicit `reconnectWait` relieves that pressure and makes it deterministic regardless of what the default is. That is a candidate fix for the flaky list on its own merits — see `FLAKY_TESTS_ANALYSIS.md` — not something to smuggle in under a different change.

## 5. Connection-side LDM wiring tests — **DONE 2026-08-12**

`DefaultReconnectDelayHandlerTests` covered the handler as a pure function and `InfoHandlerTests.testLDM` covered the `LAME_DUCK` connection *event*, but nothing asserted that the flag reaches the handler — the one thing `LameDuckAware` exists to do.

Landed as a single test, `ReconnectTests.testLameDuckSignalDelaysFirstRoundThenIsConsumed`, covering all three points the redesign plan listed. One test rather than three because points 1 and 2 are sequential by nature: you cannot show the signal is consumed without first showing it was received.

**The fixture is the realistic shape of a drain, not a synthetic flag poke.** A `NatsServerProtocolMock` with `exitAfterCustom` writes `INFO {"server_id":"draining","ldm":true}` mid-connection and then returns, which exits the mock — so the client gets the announcement and the drop in the order a real draining server produces them. The pool is `[mock, realServer]` with `noRandomize`, so the reconnect lands on the real server.

* **Campaign 1** — asserts `rounds.get(0) == 1` and `lameDucks.get(0) == true`: the lame duck signal made round 1 invoke the handler under `LameDuckAware`, and the handler was told why.
* **Campaign 2** — the real server is dropped and restarted with no lame duck signal. Asserts round 1 does *not* appear and no invocation reports a lame duck, so the flag was consumed by campaign 1 rather than sticking.

Point 3's other half (plain non-LDM disconnect under `LameDuckAware` skips round 1) is already covered by `testDelayBehaviorSkipsFirstRoundWhenLameDuckAwareWithoutLameDuck` from 3f.

**Verified failing in both directions**, the same discipline as 3f — a test that passes against broken wiring is not covering it:

| Wiring broken | Result |
|---|---|
| `this.lameDuckTriggered = true` removed (signal never recorded) | **FAILS** — campaign 1 skips round 1 |
| `lameDuckTriggered = false` removed from the gate (never consumed) | **FAILS** — campaign 2 still delays round 1 |

`NatsConnection.java` was restored from a saved copy after each diagnostic; `git diff --quiet core/src/main` confirms it is identical to `8dee6cb5`, so nothing in this item changed product code — it is pure coverage of what already shipped.

## 6. Fix the CI hang the new tests caused — DONE 2026-08-13

`167c03d5` (the 3f + item 5 tests) **hung CI for exactly six hours** — 22:11:22Z to 04:11:40Z — and was cancelled at the 6h job ceiling. Green locally, hung on the runner. Product code was never involved: `git diff --quiet core/src/main` against `8dee6cb5` was clean then and is clean now.

### What actually happened

The log tail is a single pattern repeating every two seconds for six hours:

```
[04:11:37.165 exceptionOccurred] class java.net.ConnectException --> Connection refused
[04:11:37.165 connectionEvent] nats: connection disconnected
```

An endless reconnect loop. Three things combined, and all three were mine:

1. **`maxReconnects(-1)`** — unbounded. A campaign that cannot succeed never ends.
2. **The tests stopped a server and restarted it on the same port**, then waited for the client to find it. On a CI runner that restart does not reliably come back, so there was nothing to find.
3. **`managedConnect` does not register the connection for cleanup** (`ConnectionUtils.java:33`) — it is connect-with-retry, nothing more. So even after the test method gave up, the connection was still alive, still looping, and its threads are not daemons. The test JVM could not exit and Gradle waited.

Of the five new tests only `testDelayBehaviorSkipsFirstRoundWhenBeforeSubsequentRounds` ever reported; the other four never started.

### The rewrite

Three rules now, applied to every test in this group:

* **Nothing restarts.** Each test starts every server it needs up front and kills the one the client is sitting on, so the reconnect lands somewhere already listening. Nothing waits for a port to become reusable — the consume test uses three servers rather than reusing two.
* **`maxReconnects` is bounded** (10). A campaign that cannot succeed now ends the connection and fails the test instead of looping forever. The comment on `delayBehaviorOptions` says why, so nobody "helpfully" restores `-1`.
* **The connection is closed deterministically** — `try (NatsConnection ignored = managedConnect(options))` — rather than relying on a helper that does not do it.

Shared setup moved into `delayBehaviorOptions(behavior, rounds, lameDucks)` and `drainingServer(clientReady)`, so each test body is now a handful of lines. The old two-phase LDM test split into `testLameDuckSignalDelaysFirstRoundWhenLameDuckAware` and `testLameDuckSignalIsConsumedAfterTheCampaignItTriggered` — one assertion each, independently diagnosable.

Runtime for the group went from a 6-hour hang to **13 seconds**. Full `core` suite 3m07s, and all four broken-wiring diagnostics still catch:

| Wiring broken | Result |
|---|---|
| gate forced `true` | both *skip* tests **FAIL** |
| gate forced `false` | `BeforeAllRounds` **FAILS** |
| `lameDuckTriggered = true` removed | both LDM tests **FAIL** |
| the consume removed | the consume test **FAILS**, the delay test correctly still passes |

### The lesson worth keeping

**An unbounded `maxReconnects` in a test is a JVM-hang waiting to happen, not a failing test.** The connection outlives the test method, its threads are non-daemon, and nothing in the harness closes it for you. It cost a six-hour CI job to find, and it would have been invisible locally forever, because locally the server restart always works.

---

## Verification

* `./gradlew :core:compileJava :core:compileTestJava` after each step (green as of 2026-08-12).
* `./gradlew :core:test --tests "*OptionsTests*" --tests "*DefaultReconnectDelayHandlerTests*" --tests "*InfoHandlerTests*" --tests "*ReconnectTests*"`.
* Javadoc: capture with `> file 2>&1` (not `2>&1 > file`) and lift `-Xmaxwarns`, per `~/.claude/CLAUDE-JAVA.md`. Item 1 left it at 0 warnings / 0 errors; 3c/3d edit existing comments, so it should stay there.
* Baseline `ReconnectTests` and `TLSConnectTests` **before** starting item 3 — both are on the flaky watch list in `FLAKY_TESTS_ANALYSIS.md`, and a pre-existing flake must not read as a regression from this change.

## Risk

Reconnect loop, so real. But the change is small and the default path is provably identical: with no custom handler, `BeforeSubsequentRounds` today returns `0` from the handler and afterward skips the call — same zero wait, one fewer call. The behavior that actually moves is custom handlers under `BeforeSubsequentRounds` / `LameDuckAware`, which is the point of the change.

## Dropped

**Skip the throwaway future on zero-wait rounds.** The redesign plan had `if (currentWaitMillis <= 0) return;` before touching `reconnectWaiter`; the shipped code (`:2563`, `:2577`) always allocates a fresh `CompletableFuture` and completes it. **Checked V2 — it does the same** (`/mnt/c/nats/nats.java`, `NatsConnection.java:2314` and `:2327`, unconditional allocate and complete regardless of wait). v3 matches the reference implementation; the plan's early return would have been the divergence. One allocation per round is not worth a deliberate difference in critical reconnect code.

---

# Background

## PR #1578 is fully implemented; the doc just never got closed out

Every step of `INTEGRATION_PLAN_PR_1578.md` is in the tree and committed. The plan doc read as if nothing had been started, and `TODO.md` still listed it under `## Plans / Audits`, which was the only reason it looked open.

| 1578 step | State | Evidence |
|---|---|---|
| 1 — `SubjectValidationType.get(String)` | Done | `SubjectValidationType.java`, case-insensitive, `Lenient` default |
| 2 — `ReconnectDelayBehavior` enum | Done, **and extended** | `ReconnectDelayBehavior.java` — has a third constant `LameDuckAware`, which is now the `get(String)` default |
| 3a — `@Deprecated` the two legacy subject-validation props | **Moot** | `PROP_NO_SUBJECT_VALIDATION` / `PROP_STRICT_SUBJECT_VALIDATION` no longer exist anywhere in `core/src` — removed outright, not deprecated |
| 3b/3c — three new property constants | Done | `OptionsProperties.java:115` `PROP_RECONNECT_DELAY_HANDLER_CLASS`, `:121` `PROP_RECONNECT_DELAY_BEHAVIOR`, `:152` `PROP_SUBJECT_VALIDATION_TYPE` — camelCase, per the plan's recommendation |
| 4 — `OptionsBuilder` field/parsing/setter/copy-ctor | Done | `:102` field (defaulted `LameDuckAware`), `:204`/`:218`/`:219` parsing, `:979` setter |
| 5 — `Options` field/assign/getter | Done | `:98` field, `:638` `reconnectDelayBehavior()` — the no-`get`-prefix form the plan recommended |
| 6 — hook `BeforeAllRounds` into `reconnectImplConnect()` | Done, **then superseded** | The enum branch is gone from the loop (`NatsConnection.java:457-469`) — **this is what item 3 restores** |
| 7 — `CoverageReconnectDelayHandler` test helper | Done | `core/src/test/java/io/synadia/client/utils/CoverageReconnectDelayHandler.java` |
| 8 — tests | Done | `OptionsTests.testReconnectDelayBehavior` / `.testReconnectDelayHandler` / subject-validation property block, plus a whole new `DefaultReconnectDelayHandlerTests` |
| 9 — spot-checks + flip the overview row | Done 2026-08-12 (item 2) | Doc archived to `z-claude-done/` with a COMPLETE header; overview row flipped to **Integrated** |

Both "open decisions for review" in the 1578 doc were resolved in the code in the direction the plan recommended: camelCase property names, and `reconnectDelayBehavior()` without a `get` prefix. Decision 3 ("wait for upstream merge") is moot — v3 diverged into its own design rather than porting the upstream shape.

## `PLAN_FORCE_RECONNECT_READER_STOP.md` did not supersede it — different subject

It has nothing to do with reconnect *delay*. It fixed the `forceReconnectImpl` reader/writer stop race: stop i/o **before** the async port close, capture both stopped-futures, and join them with the full connection timeout instead of 100 ms. COMPLETE and committed (`e64ad979`); the code is in `NatsConnection.forceReconnectImpl` today with the explanatory comments intact. The two plans touch `NatsConnection.java` but never the same method — `forceReconnectImpl` vs `reconnectImplConnect` / `invokeReconnectDelayHandler`.

**The doc that actually superseded PR #1578 is `z-claude-done/PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`.** It absorbed 1578's enum and went further:

* `ReconnectDelayHandler` is now `long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered)` — was `Duration getWaitTime(long totalTries)`. Still SAM.
* New stateless `io.synadia.client.impl.DefaultReconnectDelayHandler` with a public `INSTANCE`; `OptionsBuilder.build()` falls back to it, so `Options.getReconnectDelayHandler()` is never null.
* `LameDuckAware` added and made the default — no round-1 delay unless the server sent `INFO {"ldm":true}`.
* The reconnect loop stopped branching on the enum; policy moved entirely into the handler. **This is the one item 3 reverses.**
* `NatsConnection` owns a `volatile boolean lameDuckTriggered` (`:58`), set at `:2091` when LDM arrives, passed to the handler as the fourth argument.

## V2 parity check (2026-08-12)

Is there a reconnect delay change in V2 that v3 is missing? **No — v3 has all of it, but not in the same form.** V2's own `z-storage/CONNECTION_CHANGELOG.md` names exactly one, under 2.26.0: PR #1578, commit `8ae9525f` (2026-06-03). Local V2 is level with `origin/main` at `d636a436` after a fetch, and the upstream PR list back to #1580 has no other reconnect-delay entry. The neighboring reconnect work is in v3 too: #1595 (count connect failure once per server, not per resolved IP — v3 has both `connectFailed` calls outside the resolved-IP loop under `if (!isConnected() && !isClosed())`, matching V2), #1601 (`e64ad979`), #1609 (`4351b510`).

The form difference is what item 3 is about. In V2 the enum gates the call site, so under `BeforeSubsequentRounds` no handler — custom or default — is called for the first round. In v3 the loop always calls and the default handler returns `0`, so `reconnectDelayBehavior` has no effect at all on a custom handler. For the default handler the two are behaviorally identical; the divergence only reaches users who supply their own, which is exactly the audience for the option.
