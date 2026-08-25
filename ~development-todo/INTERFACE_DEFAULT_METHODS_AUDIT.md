# Audit: interface methods with default implementations

Scope: all `main` source sets (`core`, `jetstream`, `kv`, `os`, `service`, `examples`). Test sources excluded. Switch-expression `default ->` / `case default:` arms excluded.

**11 interfaces** declare **63 `default` methods**. They fall into four intent buckets.

## 1. Listener / callback interfaces — no-op stubs

Defaults let an implementer override only the events they care about. All bodies are empty except where noted.

| Interface | File | Default methods |
|---|---|---|
| `ErrorListener` | `core/.../ErrorListener.java` | `errorOccurred` (33), `exceptionOccurred` (45), `slowConsumerDetected` (61), `messageDiscarded` (69), `heartbeatAlarm` (80), `unhandledStatus` (89), `pullStatusWarning` (99), `pullStatusError` (109), `flowControlProcessed` (129), `socketWriteTimeout` (136) — all no-op; **`supplyMessage` (148) carries real logic** (builds the message string). 11 total. |
| `ReadListener` | `core/.../ReadListener.java` | `protocol` (14), `message` (21) — both no-op. |
| `StatisticsCollector` | `core/.../StatisticsCollector.java` | **34 defaults**: `setAdvancedTracking` + all `increment*`/`decrement*`/`register*` (no-op), and all `get*` returning `0`. The whole interface is defaulted, so an implementer can supply a partial collector. |

## 2. Provider / SPI interfaces — defaults delegate to the JDK

The default re-creates the standard behavior; override to inject test doubles or custom resolution.

| Interface | File | Default methods |
|---|---|---|
| `NatsInetAddressProvider` | `core/.../global/NatsInetAddressProvider.java` | `getByAddress(String,byte[])` (17), `getByName` (31), `getAllByName` (46), `getLoopbackAddress` (54), `getByAddress(byte[])` (64), `getLocalHost` (74) — each delegates to `InetAddress.*`. |
| `NatsSystemClockProvider` | `core/.../global/NatsSystemClockProvider.java` | `currentTimeMillis` (11) → `System.currentTimeMillis()`, `nanoTime` (17) → `System.nanoTime()`. |

## 3. Capability / optional-feature defaults — sentinel or delegate

A single default on an otherwise-abstract interface, giving a safe fallback so not every implementation must supply the method.

| Interface | File | Default method | Default behavior |
|---|---|---|---|
| `Message` | `core/.../Message.java` | `consumeByteCount` (152) | returns `-1` ("not supported / not a JetStream message"). |
| `Subscription` | `core/.../Subscription.java` | `getConsumerName` (57) | returns `null` ("subscription has no consumer name"). |
| `DataPort` | `core/.../impl/DataPort.java` | `afterConstruct` (24) | no-op hook. |
| `DataPort` | `core/.../impl/DataPort.java` | `forceClose` (50) | delegates to `close()`. |
| `Watcher<T>` | `jetstream/.../api/Watcher.java` | `getConsumerNamePrefix` (32) | returns `null` (no prefix). |

## 4. Debug utility inner interface

| Interface | File | Default methods |
|---|---|---|
| `Debug.Overrides` (inner) | `core/.../utils/Debug.java` | `sPrepare` (358), `fPrepare` (362), `customGetString` (366) — identity / null defaults; an empty `Overrides(){}` instance is the no-op override point. Debug-only helper. |

## Observations / minor flags

- **`ReadListener` (lines 14, 21):** each default body is `{};` — the trailing `;` is a stray empty statement after the method body. Harmless but inconsistent with the rest of the codebase (no other default uses it). Cosmetic.
- **`ErrorListener.supplyMessage` (148)** is the only logic-bearing default in an otherwise all-no-op listener. It reads `sub.getConsumerName()` (itself a `Subscription` default returning null) — fine, just noting the coupling.
- **`StatisticsCollector`** being 100% defaulted means an implementer can accidentally implement *nothing* and silently get a do-nothing collector (all getters return 0). That's intentional for partial collectors but worth knowing when debugging "stats are all zero."
- **`DataPort.forceClose` → `close`** is a reasonable default; the write-timeout/socket ports override it. No issue.
- All `default` methods are genuine interface defaults (not abstract-class methods); `default` is only legal in interfaces, so the list is complete.

No correctness problems found — every default is a sensible no-op, JDK delegation, or sentinel. The only nit is the `{};` in `ReadListener`.
