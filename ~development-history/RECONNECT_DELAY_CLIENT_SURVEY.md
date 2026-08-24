# Reconnect-delay survey across NATS clients

Surveyed each official client at HEAD to inform the decision about whether `ReconnectDelayBehavior.BeforeAllRounds` should be the default in JNats v3.

For the JNats taxonomy:
- **BeforeSubsequentRounds** (current default): on disconnect, immediately try every server in the pool one after the other (no delay). Only after a full round (cycled back to the first server) do we apply the reconnect delay between rounds.
- **BeforeAllRounds** (the new opt-in): apply the delay even before round 0 — wait before the very first reconnect attempt.

## Findings per client

### nats.go — BeforeSubsequentRounds
- Disconnect handler `processOpErr` (`nats.go:3431`) immediately spawns `doReconnect` (line 3454); the loop at line 3213 starts trying servers right away.
- Sleep gate: `doSleep = i+1 >= len(nc.srvPool) && !forceReconnect` (line 3270) — only fires when the loop has tried every server once.
- On the sleep branch (lines 3299-3326) `i = 0` resets and the goroutine sleeps `ReconnectWait + jitter` (or `crd(wlf)` if a `CustomReconnectDelay` is set).
- Defaults (`nats.go:56-58`): `ReconnectWait = 2s`, `ReconnectJitter = 100ms`, `ReconnectJitterTLS = 1s`.
- Custom callback: `CustomReconnectDelayCB` receives a wrap-around count `wlf` and fully overrides `ReconnectWait`/jitter. Doc (line 382): *"invoked after the library tried every URL in the server list and failed to reconnect."*
- **Verdict:** pure BeforeSubsequentRounds. No wait before round 0, no wait between servers in a round, wait only between full pool cycles.

### nats.c — BeforeSubsequentRounds
- `_processOpError` (`src/conn.c:2089`) spawns `_doReconnect` in a new thread (lines 2152-2153); the loop starts with `i=0` and takes the no-sleep branch immediately.
- Sleep gate: `doSleep = (i+1 >= natsSrvPool_GetSize(pool))` (`src/conn.c:1549`). Intermediate servers just call `natsThread_Yield()` (no real sleep).
- On the sleep branch (lines 1551-1570) the thread does `natsCondition_TimedWait(... sleepTime)` where `sleepTime = reconnectWait + rand()%jitter` (or the custom callback's return).
- Defaults (`src/opts.h:29, 35-36`): `NATS_OPTS_DEFAULT_RECONNECT_WAIT = 2000ms`, `NATS_OPTS_DEFAULT_RECONNECT_JITTER = 100ms`, `NATS_OPTS_DEFAULT_RECONNECT_JITTER_TLS = 1000ms`.
- Doc (`src/nats.h:2257-2268`): *"if you have a list with S1,S2 and are currently connected to S1, and get disconnected, the library will immediately attempt to connect to S2. If this fails, it will go back to S1, and this time will wait for `reconnectWait`."*
- **Verdict:** pure BeforeSubsequentRounds. Identical model to nats.go (Go and C share the original NATS-team design).

### nats.rs (async-nats) — Per-attempt model; first attempt effectively immediate
- `Connector::try_connect` (`async-nats/src/connector.rs`) sorts the pool by `failed_attempts` (line 340), then for each candidate: `self.attempts += 1` (344) → compute `duration = (reconnect_delay_callback)(self.attempts)` (359) → `sleep(duration)` (367) → `try_connect_to_server` (369).
- There is no "round" concept. `self.attempts` is monotonic across the entire reconnect campaign and only resets on a successful connect (line 424) or pool replacement (line 240).
- Default callback `reconnect_delay_callback_default` (connector.rs:173-181): `attempts <= 1` → **0 ms**; otherwise `min(2^(attempts-1) ms, 4s)`. So the very first attempt has a no-op `sleep(Duration::ZERO)`.
- Configurable: `ConnectOptions::reconnect_delay_callback` (options.rs:778) accepts any `Fn(usize) -> Duration` closure. Also `reconnect_to_server_callback` can override per-attempt (connector.rs:297-303).
- Doc (options.rs:762): *"Registers a callback for a custom reconnect delay handler that can be used to define a backoff duration strategy."* Frames the knob as per-attempt backoff, not per-round.
- **Verdict:** structurally different from both Java modes — delay is per-attempt, monotonic, exponential. At default settings the *observable* behavior on the first reconnect attempt matches BeforeSubsequentRounds (0 ms = immediate), but subsequent same-round server attempts also get a (small, growing) delay rather than firing back-to-back with zero wait.

### nats.js — Closer to BeforeAllRounds (per-server cooldown)
- Disconnect handler `disconnected` (`core/src/protocol.ts:539`) calls `dialLoop()` → `dodialLoop` at line 650.
- Each iteration: compute `wait = reconnectDelayHandler()`, then `selectServer()` (`core/src/servers.ts:277` — shifts current to tail), then gate per-server on `lastConnect` (`protocol.ts:674`):
  ```ts
  if (srv.lastConnect === 0 || srv.lastConnect + wait <= now) { ...dial... }
  else { await delay(srv.lastConnect + wait - now) }
  ```
- The just-disconnected server has a recent `lastConnect`, so the loop waits `reconnectTimeWait + jitter` before redialing it — i.e. there IS a delay before the very first attempt.
- Untried servers (`lastConnect === 0`) are dialed immediately, back-to-back.
- Defaults (`core/src/options.ts:29-36`): `reconnectTimeWait = 2000ms`, `reconnectJitter = 100ms`, `reconnectJitterTLS = 1000ms`, `maxReconnectAttempts = 10`.
- Configurable: `reconnectTimeWait`, `reconnectJitter`, `reconnectJitterTLS`, plus full override `reconnectDelayHandler: () => number` (`core/src/core.ts:947`).
- Doc (`core/src/core.ts:944-967`): *"number of milliseconds between reconnect attempts"* / *"wait for the next reconnect attempt."*
- **Verdict:** uses a per-server cooldown rather than a round boundary, but because the just-disconnected server is retried first and is gated by its own `lastConnect`, the observable behavior at defaults includes a wait before round 0 — closer to BeforeAllRounds than to BeforeSubsequentRounds.

### nats.net (NATS.Net v2) — Per-failed-attempt; first attempt immediate
- `ReconnectLoop` (`src/NATS.Client.Core/NatsConnection.cs:708-844`) builds a URL list (seeds + server-reported, shuffled unless `NoRandomize`), then jumps to label `CONNECT_AGAIN` (line 772). Each iteration `MoveNext()` to the next URL and `ConnectSocketAsync`.
- The wait lives in the failure `catch` block: `WaitWithJitterAsync` (line 821) runs *before* going back to `CONNECT_AGAIN`. When the enumerator is exhausted (lines 792-797) it resets the enumerator and `goto`s back with no extra wait.
- Defaults (`NatsOpts.cs:117, 122, 211, 221`): `ReconnectWaitMin = 2s`, `ReconnectWaitMax = 5s`, `ReconnectJitter = 100ms`, `MaxReconnectRetry = -1` (unlimited).
- Doubles each attempt up to max (NatsConnection.cs:982-1004); uniform jitter (line 1022). No strategy callback.
- Doc (`NatsOpts.cs:217-219`): *"When the connection is lost, the client will wait for ReconnectWaitMin before attempting to reconnect."* (The *"before attempting"* wording is misleading — code waits *after* each failure, not before the first.)
- **Verdict:** first attempt is immediate (BeforeSubsequentRounds-like), but unlike Java BSR it then waits between every server in a round with exponential backoff. Effectively "delay per failed attempt," not "delay between rounds."

## Comparison matrix

| Client | Wait before round 0? | Wait between servers in a round? | Wait between rounds? | Default initial wait | Custom callback? |
|---|---|---|---|---|---|
| nats.go | no | no | yes | 2 s + 100 ms jitter | yes — `CustomReconnectDelayCB(wlf)` |
| nats.c | no | no | yes | 2 s + 100 ms jitter | yes — `natsOptions_SetCustomReconnectDelay` |
| nats.rs | effectively no (default 0 ms for attempt 1) | yes — small growing | same monotonic backoff | 0 ms → exp 2^(n-1) capped at 4 s | yes — `reconnect_delay_callback` |
| nats.js | **yes** — `reconnectTimeWait` if just-lost server is the first candidate | no (other servers have `lastConnect=0`) | yes (cooldown per server) | 2 s + 100 ms jitter | yes — `reconnectDelayHandler` |
| nats.net | no | yes — exponential | same backoff | 2 s → 5 s + 100 ms jitter | no — only min/max knobs |
| JNats v2/v3 (current default) | no | no | yes | 2 s + 100 ms jitter | yes — `ReconnectDelayHandler.getWaitTime(totalTries)` |

## How the two original NATS team clients (Go and C) describe it

Both Go and C — the two clients written by the original NATS team — use identical language and identical mechanics:

> nats.go:382 / nats.c (`nats.h:2299`): *"the library tried every URL in the server list and failed to reconnect"* — the delay applies only after a full pool cycle.

That is the model that JNats has historically matched and that its current default (`BeforeSubsequentRounds`) preserves.

## Recommendation: keep `BeforeSubsequentRounds` as the v3 default

1. **Alignment with the canonical clients.** nats.go and nats.c are the reference implementations from the NATS team and they both delay only between rounds, never before round 0 or between same-round server attempts. JNats has matched this model for a decade. Changing the default would make JNats the only "team-original-style" client (Go / C / Java) that pauses before round 0.

2. **Regression for the common cluster-failover case.** In a healthy 3-node cluster, the most common disconnect cause is a single server going down (rolling deploy, crash, network blip). With BeforeSubsequentRounds today, the client tries server 2 within milliseconds — typically reconnecting before the application notices. With BeforeAllRounds as default, every such failover would pay an extra ~2 s of `ReconnectWait` for no benefit, even when the cluster is perfectly healthy.

3. **The `BeforeAllRounds` model is not actually consensus among clients.** Only nats.js exhibits BeforeAllRounds-like behavior at defaults, and it's a side effect of the per-server `lastConnect` cooldown model rather than a deliberate design choice. nats.rs and nats.net both have per-attempt backoff but the first attempt is immediate. So the design space is really three camps:
   - Pure round-based (Go, C, JNats today): immediate first try, delay between rounds.
   - Per-attempt monotonic (Rust, .NET): delay grows with each failure; first attempt effectively immediate.
   - Per-server cooldown (JS): just-disconnected server gets a cooldown before retry.

   Only the third camp ends up with an observable "wait before round 0," and it's a one-client camp.

4. **`BeforeAllRounds` is still a legitimate user-controlled choice.** Some applications (e.g., a service that wants to back off to avoid thundering-herd on a known-unhealthy cluster) genuinely want a pre-round-0 delay. Keeping it as a non-default opt-in via `OptionsBuilder.reconnectDelayBehavior(BeforeAllRounds)` and `PROP_RECONNECT_DELAY_BEHAVIOR=BeforeAllRounds` covers that case without surprising the majority of users who upgrade from v2.

5. **Migration ergonomics.** Anyone porting from v2 to v3 expects the same reconnect timing they had before. Changing the default would be a silent behavioral change — the kind that doesn't surface in tests but shows up later as "why are my reconnects 2 s slower in v3?" tickets.

### What if you wanted to challenge this conclusion

The strongest argument *for* `BeforeAllRounds` as default would be: "the historical no-pre-delay design is a leftover from when the typical NATS deployment was a single server and the typical disconnect was a true failure, not a rolling deploy. Modern clusters and rolling deploys make `BeforeSubsequentRounds` an antipattern that hammers a healthy cluster." That's the same argument nats.js's behavior makes implicitly. It would be worth raising on the nats-io maintainer channel — but probably as a shared discussion across clients, not as a Java-only default flip.

### Action

- **Keep `ReconnectDelayBehavior.BeforeSubsequentRounds` as the default value in `OptionsBuilder` and `Options`.** No code change needed beyond what's already in this branch.
- **Consider** mentioning the trade-off in the Javadoc for `OptionsBuilder.reconnectDelayBehavior(...)` so users can make an informed choice. The current Javadoc just says "defaults to BeforeSubsequentRounds" without explaining when to prefer the other.
