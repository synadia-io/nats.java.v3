# Plan — Reconnect-delay fields: `Duration` → `long` milliseconds

Goal: (1) fix `DefaultReconnectDelayHandler.computeWaitMillis` so a zero base wait still receives jitter, and (2) convert the three reconnect-delay `Duration` fields on `Options` / `OptionsBuilder` / `OptionsConstants` to `long` milliseconds. The two are coupled — once the Options field is already `long`, the `computeWaitMillis` body simplifies into the correct shape naturally. (Note: `computeWaitMillis` lives on `DefaultReconnectDelayHandler` as a `public static` helper — see `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`.)

Scope is limited to the three reconnect-delay fields. The other five `Duration` fields on `Options` are tracked separately in `todo.md` under "Other `Duration` → `long` ms conversions on `Options` (deferred)" and are out of scope here.

## Motivation

- **`computeWaitMillis` early-return is wrong.** Today the method does `if (wait == null) return 0L;`, which silently drops any configured jitter. Users who want "no fixed base wait but please add 0..100ms of jitter" can't express that — perfectly reasonable config, currently impossible.
- **Allocation cost on the reconnect hot path.** Each `Duration` getter returns a heap-allocated object that the handler immediately dumps via `.toMillis()` — never used in its richer form. Replacing with primitive `long` removes the allocation per `getWaitTimeMillis` call.
- **Property file format already supports both formats.** `OptionsProperties.durationProperty` (line 359) parses ISO-8601 first, then falls back to integer milliseconds. Switching the internal type to `long` ms is invisible to existing `.properties` files.

## Scope

Three `Duration` fields on `OptionsBuilder` (and the matching `Options` getters and `OptionsConstants` defaults):

| Field | Builder line | Current default constant |
|---|---|---|
| `reconnectWait` | 69 | `DEFAULT_RECONNECT_WAIT` |
| `reconnectJitter` | 70 | `DEFAULT_RECONNECT_JITTER` |
| `reconnectJitterTls` | 71 | `DEFAULT_RECONNECT_JITTER_TLS` |

## Fixed `computeWaitMillis`

After the conversion, the body becomes:

```java
public static long computeWaitMillis(Options options, boolean secure) {
    long wait = options.getReconnectWaitMillis();
    long jitter = secure
        ? options.getReconnectJitterTlsMillis()
        : options.getReconnectJitterMillis();
    if (jitter > 0) {
        wait += ThreadLocalRandom.current().nextLong(jitter);
    }
    return wait < 0 ? 0L : wait;
}
```

Behaviour matrix:

| `wait` | `jitter` | result |
|---|---|---|
| 0 | 0 | 0 (no wait) |
| 0 | >0 | `[0, jitter)` random |
| >0 | 0 | exactly `wait` |
| >0 | >0 | `[wait, wait+jitter)` random |
| <0 | any | 0 (safety clamp) |

No null handling — the field is now primitive `long`, so the case can't arise.

## API surface changes

### `Options` getters — rename and retype

| Today | After |
|---|---|
| `Duration getReconnectWait()` | `long getReconnectWaitMillis()` |
| `Duration getReconnectJitter()` | `long getReconnectJitterMillis()` |
| `Duration getReconnectJitterTls()` | `long getReconnectJitterTlsMillis()` |

Each picks up the `Millis` suffix so the unit is visible at the call site. No `Duration` overloads — adding them would re-allocate per call, defeating the point.

### `OptionsBuilder` setters — long-only

For each of the three setters (`reconnectWait` at line 614, `reconnectJitter` at line 626, `reconnectJitterTls` at line 639):

```java
public OptionsBuilder reconnectWait(long millis) {
    this.reconnectWait = millis;
    return this;
}
```

No `Duration` convenience overload. Users that previously called `reconnectWait(Duration.ofSeconds(2))` translate to `reconnectWait(2_000L)` — one-line mechanical change per call site.

(Method names stay `reconnectWait`, not `reconnectWaitMillis` — the existing setter name is well-known and the `long` parameter is unambiguous.)

### `OptionsBuilder` fields — change type

```java
long reconnectWait = DEFAULT_RECONNECT_WAIT_MILLIS;
long reconnectJitter = DEFAULT_RECONNECT_JITTER_MILLIS;
long reconnectJitterTls = DEFAULT_RECONNECT_JITTER_TLS_MILLIS;
```

### `OptionsConstants` constants — rename and retype

```java
// before:
public static final Duration DEFAULT_RECONNECT_WAIT = Duration.ofSeconds(2);
// after:
public static final long DEFAULT_RECONNECT_WAIT_MILLIS = 2_000L;
```

…and likewise for `DEFAULT_RECONNECT_JITTER` and `DEFAULT_RECONNECT_JITTER_TLS`. `_MILLIS` suffix matches the `Options` getter naming.

### Properties parsing — keep the existing `durationProperty` helper

`OptionsProperties.durationProperty` (line 359) accepts both ISO-8601 and plain integer milliseconds. The other five `Duration` fields keep using it. For the three reconnect properties we switch to a new `longGtEqZeroProperty` helper (parallel to the existing `intGtEqZeroProperty`) — plain integer parsing with the same negative-skip-silently semantic the old `durationProperty` had. The three call sites in `OptionsBuilder.properties(...)` change shape:

```java
// before
durationProperty(props, PROP_RECONNECT_WAIT,     d -> this.reconnectWait     = d);
durationProperty(props, PROP_RECONNECT_JITTER,   d -> this.reconnectJitter   = d);
durationProperty(props, PROP_RECONNECT_JITTER_TLS, d -> this.reconnectJitterTls = d);

// after
longGtEqZeroProperty(props, PROP_RECONNECT_WAIT,     l -> this.reconnectWait     = l);
longGtEqZeroProperty(props, PROP_RECONNECT_JITTER,   l -> this.reconnectJitter   = l);
longGtEqZeroProperty(props, PROP_RECONNECT_JITTER_TLS, l -> this.reconnectJitterTls = l);
```

**This is a properties-file format change for these three keys.** `io.nats.client.reconnectWait=2000` (plain ms) keeps working; `io.nats.client.reconnectWait=PT2S` (ISO-8601) no longer parses and now throws `NumberFormatException` at build time. Negative values continue to be silently skipped, so existing files setting `-1` to fall back to the default still behave the same. The other five duration-typed properties keep ISO-8601 support since they still go through `durationProperty`.

### Internal consumers

`NatsConnection.invokeReconnectDelayHandler` (the only internal reader of these three fields) is already being rewritten in `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md` to use the long-ms handler. After this change it reads `options.getReconnectWaitMillis()` etc. directly — no more `.toMillis()` on the way in. Spot-check via `grep "getReconnectWait\|getReconnectJitter"` in `NatsConnection.java` after the conversion to make sure no `Duration` users remain.

## File-by-file change list

| File | What changes |
|---|---|
| `OptionsConstants.java` | Three constants renamed + retyped to `long DEFAULT_RECONNECT_*_MILLIS`. |
| `OptionsBuilder.java` (lines 69-71) | Three fields retyped to `long`. |
| `OptionsBuilder.java` (lines 614, 626, 639) | Three setters retyped to take `long millis`. |
| `OptionsBuilder.java` (lines 217-219) | Three property-parser call sites change consumer body to `d.toMillis()`. |
| `OptionsBuilder.java:1338+` (copy constructor) | Field copies pick up the new `long` types automatically — no diff except the field types. |
| `Options.java` (the three corresponding fields) | `final Duration` → `final long`. |
| `Options.java` (constructor body, ~lines 201-203) | Straight copies; field types change. |
| `Options.java` (the three getters) | Renamed and retyped per the table above. |
| `DefaultReconnectDelayHandler.java` (the new file from the redesign plan) | `computeWaitMillis` body becomes the simplified shape above. |
| `NatsConnection.java` | The three `options.getXxx()` reads (consumed by the reconnect-delay path) switch to the new `…Millis` getters. |
| `OptionsTests.java` | Assertions on the three fields retyped from `Duration.ofSeconds(2)` → `2_000L`. |
| `MIGRATION_GUIDE_OPTIONS.md` | New subsection listing the three renamed methods and recommended call-site translation. |

## Migration story

Breaking against any user code that calls the three `Options` getters or the three `OptionsConstants` constants. v3 is unreleased so we don't owe API stability, but a clean migration table in `MIGRATION_GUIDE_OPTIONS.md` is the minimum:

```
Old call                                      New call
options.getReconnectWait()                    options.getReconnectWaitMillis()
options.getReconnectWait().toMillis()         options.getReconnectWaitMillis()
options.getReconnectWait().toNanos()          options.getReconnectWaitMillis() * 1_000_000L
DEFAULT_RECONNECT_WAIT                        DEFAULT_RECONNECT_WAIT_MILLIS
builder.reconnectWait(Duration.ofSeconds(2))  builder.reconnectWait(2_000L)
```

(Same three rows for `reconnectJitter` and `reconnectJitterTls`.)

Properties files that already use plain integer milliseconds (e.g. `2000`) don't need any changes. Files that used ISO-8601 duration strings (e.g. `PT2S`) for the three reconnect keys must convert to milliseconds — `longGtEqZeroProperty` is a plain parser, so `Long.parseLong("PT2S")` throws.

## Implementation order

Each step compiles on its own.

1. **`OptionsConstants` — rename + retype the three constants.** Add the new `…_MILLIS` constants, keep the old `Duration` ones temporarily as deprecated forwarders for the next steps.
2. **`OptionsBuilder` — retype the three fields to `long`.** Field-decl defaults point to the new `…_MILLIS` constants. Setters take `long`. Property parsers convert via `.toMillis()` inline.
3. **`Options` — retype the three fields and add the new `…Millis` getters.** Keep the old `Duration` getters as deprecated forwarders.
4. **Internal consumers** — `NatsConnection` (only `invokeReconnectDelayHandler` path) switches to the new `…Millis` getters.
5. **Remove the deprecated `Duration` getters and `Duration`-typed constants.** Single mechanical pass.
6. **Tests** — replace `Duration.ofSeconds(...)` with `... * 1_000L` literals. Add the "zero wait + non-zero jitter" jitter-only test.
7. **Migration guide** update.

Alternative: do steps 1-5 as one big bang. Saves the deprecation churn but the tree is broken between sub-steps. For an unreleased v3 this is probably fine; pick whichever the reviewer prefers.

## Tests to add specifically

- **Jitter-only wait**: `reconnectWait = 0`, `reconnectJitter = 100` → 100 calls to `computeWaitMillis` should produce values in `[0, 100)` with reasonable variance (or just assert `< 100` and `>= 0`).
- **Negative wait clamp**: nonsensical inputs (someone calling `.reconnectWait(-5L)` shouldn't crash) get clamped to 0.
- **TLS jitter picks the right pool**: same `reconnectWait = 0`, jitter values diverge by 10x, `secure=true` picks the bigger pool, `secure=false` picks the smaller.
- **Default values** after `new OptionsBuilder().build()` — assert the three `…Millis` getters return the constants they should.
- **Properties round-trip**: `2000` in a properties file produces `getReconnectWaitMillis() == 2000L`; `PT2S` throws `NumberFormatException`; `-1` is silently skipped, leaving the default.

## Open decisions for review

1. **Setter method names — `reconnectWait(long)` or `reconnectWaitMillis(long)`?** Plan recommends keep the existing name. The `long` parameter type is unambiguous given there's no `Duration` overload, and call sites that previously read `nc.reconnectWait(...)` keep reading naturally.
2. **Constant suffix — `_MILLIS` or `_MS`?** Plan picks `_MILLIS` to match `Options.getReconnectWaitMillis()` and the existing `socketReadTimeoutMillis` precedent. `_MS` is shorter but inconsistent.
3. **`computeWaitMillis` location.** Lives as a `public static` method on `DefaultReconnectDelayHandler` (decided in the redesign plan — the interface holds only the contract). Custom handlers reuse it via `DefaultReconnectDelayHandler.computeWaitMillis(options, secure)`.
