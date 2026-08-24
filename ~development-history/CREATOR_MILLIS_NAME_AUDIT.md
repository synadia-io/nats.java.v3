# Creator-family `…Millis` param → `millis` audit

**Status: audit only — nothing changed yet.** This catalogs the `*Creator` (and KV/OS config-creator) builder-style setters that take a `long …Millis` parameter, as candidates for the same rename just applied to `OptionsBuilder` and the option-builders: when the **method name already names the setting**, the parameter is just the bare unit (`millis`); the descriptive `…Millis` prefix is redundant.

## Context that makes the Creators a bit different from the option-builders

- These setters **serialize to the wire as nanoseconds** (Group A — `addFieldAsNanos`), but the `long` overload itself takes **milliseconds** (`…Millis`). Renaming the param is purely cosmetic; it does not touch serialization.
- **Almost none of them have a `Duration` sibling overload anymore** — the consumer/stream creators are `long`-only (they still `import java.time.Duration` for other reasons, but have no `Duration` time-setter). The only `Duration` siblings left are in KV/OS (`ttl`, `limitMarker`). So renaming the `long` param to `millis` can't collide with a `Duration` overload.
- Several methods are also exposed as **protected `_`-prefixed helpers** (`ConsumerCreator._ackWait` etc.); those would move in lockstep with their public counterparts.

## The candidates (alphabetical by class)

| Class | Method (visibility) | Current param | → `millis`? | Notes |
|---|---|---|---|---|
| `AbstractEphemeralConsumerCreator` | `ackWait(long)` (public) | `timeoutMillis` | ✅ yes | method names the setting; `timeoutMillis` is generic anyway |
| `AbstractEphemeralConsumerCreator` | `flowControl(long)` (public) | `idleHeartbeatMillis` | ⚠️ **judgment call** | `flowControl` is a convenience that *enables flow control and sets the idle-heartbeat*; the param names the **effect** (`idleHeartbeat`), not the method. `flowControl(long millis)` reads as "millis of what?" — keeping `idleHeartbeatMillis` (or at least a very clear javadoc) is arguably better here |
| `ConsumerCreator` | `idleHeartbeat(long)` (public) | `idleHeartbeatMillis` | ✅ yes | name matches setting |
| `ConsumerCreator` | `inactiveThreshold(long)` (public) | `inactiveThresholdMillis` | ✅ yes | name matches setting |
| `ConsumerCreator` | `_ackWait(long)` (protected) | `timeoutMillis` | ✅ yes | internal mirror of `ackWait` |
| `ConsumerCreator` | `_idleHeartbeat(long)` (protected) | `idleHeartbeatMillis` | ✅ yes | internal |
| `ConsumerCreator` | `_flowControl(long)` (protected) | `idleHeartbeatMillis` | ⚠️ same as `flowControl` | internal mirror of the judgment call above |
| `ConsumerCreator` | `_maxExpires(long)` (protected) | `maxExpiresMillis` | ✅ yes | internal |
| `ConsumerCreator` | `_priorityTimeout(long)` (protected) | `priorityTimeoutMillis` | ✅ yes | internal |
| `ConsumerLimitsCreator` | `inactiveThreshold(long)` (public) | `inactiveThresholdMillis` | ✅ yes | name matches setting |
| `KeyValueConfigurationCreator` | `limitMarker(long)` (public) | `limitMarkerTtlMillis` | ⚠️ **judgment call** | param adds `Ttl`; the method is `limitMarker`, so `limitMarker(long millis)` drops the "this is a TTL" hint. Also the **one creator setter that still has a `Duration` sibling** (`limitMarker(Duration limitMarkerTtl)`) — no clash, but worth eyeballing the pair together |
| `PullConsumerCreator` | `maxExpires(long)` (public) | `maxExpiresMillis` | ✅ yes | name matches setting |
| `PullConsumerCreator` | `priorityTimeout(long)` (public) | `priorityTimeoutMillis` | ✅ yes | name matches setting |
| `PullOrderedConsumerCreator` | `maxExpires(long)` (public) | `maxExpiresMillis` | ✅ yes | name matches setting |
| `PullOrderedConsumerCreator` | `priorityTimeout(long)` (public) | `priorityTimeoutMillis` | ✅ yes | name matches setting |
| `StreamCreator` | `maxAge(long)` (public) | `maxAgeMillis` | ✅ yes | name matches setting |
| `StreamCreator` | `duplicateWindow(long)` (public) | `windowMillis` | ✅ yes | param already short; `duplicateWindow` carries the meaning |
| `StreamCreator` | `subjectDeleteMarkerTtl(long)` (public) | `subjectDeleteMarkerTtlMillis` | ✅ yes | name matches setting (and is a mouthful — `millis` is a real readability win here) |

## Not candidates (no `long …Millis` param to rename)

| Class | Method | Why it's here |
|---|---|---|
| `KeyValueConfigurationCreator` | `ttl(Duration ttl)` | **`Duration`-only** — no `long` overload exists. Nothing to rename. (Possible separate follow-up: add a `ttl(long millis)` overload for parity with the rest of the API.) |
| `ObjectStoreConfigurationCreator` | `ttl(Duration ttl)` | same — `Duration`-only, no `long` overload |

## Summary / recommendation

- **15 of 17** `long …Millis` params are clean ✅ renames (method name already names the setting). The two mouthfuls — `subjectDeleteMarkerTtlMillis`, `inactiveThresholdMillis` — benefit the most.
- **2 judgment calls** ⚠️: `flowControl(long idleHeartbeatMillis)` / `_flowControl` (param names the *effect*, not the method — bare `millis` is genuinely less clear) and `limitMarker(long limitMarkerTtlMillis)` (param adds `Ttl`, and it's the lone setter with a surviving `Duration` sibling). I'd lean **keep the descriptive name on `flowControl`** and rename the rest — but flagging both for a decision rather than deciding unilaterally.
- Separately: `KeyValueConfigurationCreator.ttl` and `ObjectStoreConfigurationCreator.ttl` are `Duration`-only; if the goal is API-wide consistency they're candidates to *gain* a `long millis` overload, which is a different task than this rename.
