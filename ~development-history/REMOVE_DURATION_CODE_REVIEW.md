# Code review — Duration → millis (and adjacent changes)

Pre-commit review of the working tree vs the last commit: **57 main-source files changed (~2700 lines)**, plus tests and docs. Mostly the `Duration`→`long`/`Long` millis migration, with some unrelated reformatting bundled in. **All three modules compile** (`:core` `:jetstream` `:service`).

Verdict: **close to commit-ready.** One real consistency bug to decide on (`socketWriteTimeout` disable), two trivial cleanups, and one observation about reformatting inflating the diff.

## ✅ Verified correct

- **Compiles clean** across all modules (the earlier `nextMessageInternal`/`_nextUnmanaged` WIP is resolved).
- **Nanos preserved in the timing loops** (the thing that bit us before). Every `nanoTime()`-based read loop now converts millis→nanos *once at the top* and stays nanos:
  - `JetStreamPullSubscription._fetch` — `maxWaitNanos = maxWaitMillis * NANOS_PER_MILLI`, loop on `timeLeftNanos`, `nextMessage(timeLeftNanos, NANOSECONDS)`, `timeLeftNanos = maxWaitNanos - (nanoTime - start)`. ✓
  - `NatsFetchMessageConsumer` — `maxWaitNanos`/`startNanos`, `timeLeftNanos = maxWaitNanos - (nanoTime - startNanos)`, `_nextUnmanaged(timeLeftNanos, NANOSECONDS, …)`. ✓
  - `JetStreamSubscription._nextUnmanaged(long, TimeUnit, String)` — `timeoutNanos = unit.toNanos(timeout)`, loop on `timeLeftNanos`. ✓ (good: the unit is taken at the boundary, loop is nanos.)
  - `NatsConnection.tryToConnect` — nanos `end`, `timeCheck` in nanos, `.get(timeLeftNanos, NANOSECONDS)`. ✓
- **`PullRequestOptions` Duration removal** — fields/getters → `long` millis, both `Duration` setter overloads dropped, `toJson` serializes `addFieldWhenGtZero(…, millis * NANOS_PER_MILLI)`, `build()` validation rewritten to `> 0`/`<= 0`. ✓
- **`PublishOptions.streamTimeout`** — `@Nullable Long`, `null → DEFAULT_TIMEOUT`, faithful to the old `validateDurationNotRequiredGtOrEqZero(timeout, DEFAULT_TIMEOUT)`. ✓
- **Sentinels documented** in the migration guide runtime-timeouts table (`null`=poll-once, `<=0`=forever, etc.).

## ⚠️ Issue 1 (decide before commit) — `socketWriteTimeout` can no longer be disabled, and the docs/branch lie about it

The setter now **clamps to the minimum** (`OptionsBuilder:737`):
```java
this.socketWriteTimeout = millis <= MINIMUM_SOCKET_WRITE_TIMEOUT ? MINIMUM_SOCKET_WRITE_TIMEOUT : millis; // ... "disables"
```
So the field is **always ≥ 1, never ≤ 0** — but three places still assume it can be disabled:
- `OptionsBuilder:737` trailing comment says *"below the minimum (incl. `<= 0`) disables"* — wrong; it clamps, never disables.
- `Options:749` getter javadoc: *"`<= 0` means disabled"* — wrong; the value can never be `<= 0`.
- `Options:552` `if (socketWriteTimeout <= 0) { dp = new SocketDataPort(); }` — **dead branch.** The plain (no-write-timeout) `SocketDataPort` can never be selected here, so the write timeout is effectively **always on** for the default data-port type.

Decide one:
- **(a) always-on is intended** → delete the dead `<= 0` branch in `buildDataPort()` (always `SocketDataPortWithWriteTimeout`) and fix the two "disables" doc lines to say "clamped to a minimum; cannot be disabled."
- **(b) disabling should still be possible** → give the setter a carve-out, e.g. `millis <= 0 ? 0 : Math.max(MINIMUM_SOCKET_WRITE_TIMEOUT, millis)`, so `0` disables and the branch + docs become true again.

This was left open from the earlier discussion and is still in the inconsistent state.

## ⚠️ Issue 2 (trivial) — two dead `Duration` imports

`import java.time.Duration;` with no remaining usage:
- `core/.../impl/SocketDataPort.java:20`
- `service/.../service/Discovery.java:8`

(`KeyValuePurgeOptions` still imports it legitimately — used at line 17, `Duration.ofMinutes(30).toMillis()`.)

## ⚠️ Issue 3 (trivial) — one stale comment

`NatsFetchMessageConsumer:128`:
```java
Message msg = sub._nextUnmanaged(timeLeftNanos, TimeUnit.NANOSECONDS, pullSubject); // _nextUnmanaged takes millis; guarded >= 1ms above
```
The comment says *"takes millis"* but it now passes **nanos** + `NANOSECONDS`. Reword to "passes nanos; guarded `>= 1ms` above," or drop it.

## 👀 Observations (not blockers)

- **Reformatting is inflating the diff.** `NatsMessageBuilder` (+105/-91) and `IncomingHeadersProcessor` (+101/-85) are **100% whitespace** (they don't appear in `git diff -w` at all); `NatsDispatcher` (+380/-315) is **~99% whitespace** — only ~15 real lines (the `WAIT_FOR_MESSAGE_MILLIS` constant). None of this is `Duration`-related. Consider committing the reformatting separately so the migration commit is reviewable, or at least know the real review surface is far smaller than ~2700 lines.
- **Coercion / reset inconsistencies remain** (catalogued in `BUILDER_CREATOR_COERCION_AUDIT.md`): the raw `OptionsBuilder` timing setters (`pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout`, `reconnectWait`, `reconnectJitter`, `reconnectJitterTls`) and `JetStreamOptions.requestTimeout` neither coerce nor offer an explicit reset. These are documented design decisions, not regressions — fine to defer, but they're the obvious "do it in the same pass" candidates.
- **`@Nullable Long` literal friction.** `streamTimeout` and `deleteMarkersThreshold` take boxed `Long`, so integer literals need the `L` suffix (`streamTimeout(2000L)`); `streamTimeout(2000)` won't compile. Acceptable (it's the price of the `null`-reset), and now noted in the migration guide.

## Migration guide status

Updated this pass:
- `streamTimeout` row → `.streamTimeout(2000L)` (boxed `Long`) + a behavior note that `null` resets to default and literals need `L`.

Already covered and accurate: the runtime-timeouts table (`nextMessage`/`request`/`flush`/`drain`/`ackSync`/`nakWithDelay`/`next`/`fetch`/`iterate` with sentinels), the JetStream `requestTimeout`/`jsRequestTimeout` rows, and Options §7 (`connectionTimeout`/`socketReadTimeout`/`socketWriteTimeout`, property formats).

Possible small additions (optional — advanced/secondary APIs not individually called out): `PullRequestOptions.expiresIn`/`idleHeartbeat` `Duration`→`long`, and `KeyValuePurgeOptions.deleteMarkersThreshold` now `@Nullable Long`.
