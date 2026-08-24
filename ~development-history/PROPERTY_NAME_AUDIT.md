# Property-name audit (README ↔ code)

Cross-checked every `Name` in the README properties table against the actual property keys in `OptionsProperties` (the `PROP_*` constant string values, minus the `io.nats.client.` prefix), the builder setter it routes to in `OptionsBuilder.properties(...)`, and the getter in `Options`. Grouped by what needs fixing.

**Status:** **A, B, C, and D are FIXED.** The only thing deliberately left is `supportUtf8Subjects` (moved to **E. Deferred** — being handled separately) and `secure` (intentionally untouched).

## A. Stale README names — no such property exists — ✅ FIXED (rows removed)

These appeared in the README table but had **no `PROP_*`** in `OptionsProperties` (verified: 0 occurrences) — leftovers from v2 jnats. All five rows have been removed from the README (`noSubjectValidation`/`strictSubjectValidation` are superseded by the new `subjectValidationType` row added in C).

| README name | Resolution |
|---|---|
| `timeTraceLoggerClass` | Removed — no TimeTraceLogger in v3. |
| `noSubjectValidation` | Removed — folded into `subjectValidationType` (value `None`). |
| `strictSubjectValidation` | Removed — folded into `subjectValidationType` (value `Strict`). |
| `reportNoResponders` | Removed — no property in v3. |
| `useOldRequestStyle` | Removed — no property in v3 (the related knob is `forceFlushOnRequest`). |

## B. README name (= property key) ≠ getter / builder API name — ✅ FIXED

Resolved per your direction: for `maxPings`/`cleanupInterval`/`reconnectBufSize` the **property key was renamed to match the existing setter/getter**; for the two socket buffer sizes the **setter/getter were renamed to match the existing property key**. The `PROP_*` constants, the property loader routing, the README table, the two test `.properties` resources, and the migration guide (§4 + new §9) were all updated. `OptionsTests` passes.

| Was (property key) | Was (API) | Now — key + API agree |
|---|---|---|
| `maxPings` | `maxPingsOut(int)` / `getMaxPingsOut()` | key → `maxPingsOut` (`PROP_MAX_PINGS_OUT`) |
| `cleanupInterval` | `requestCleanupInterval(long)` / `getRequestCleanupInterval()` | key → `requestCleanupInterval` (`PROP_REQUEST_CLEANUP_INTERVAL`) |
| `reconnectBufSize` | `reconnectBufferSize(long)` / `getReconnectBufferSize()` | key → `reconnectBufferSize` (`PROP_RECONNECT_BUFFER_SIZE`) |
| `socketReceiveBufferSize` | `receiveBufferSize(int)` / `getReceiveBufferSize()` | API → `socketReceiveBufferSize(int)` / `getSocketReceiveBufferSize()` |
| `socketSendBufferSize` | `sendBufferSize(int)` / `getSendBufferSize()` | API → `socketSendBufferSize(int)` / `getSocketSendBufferSize()` |

## C. Properties that exist in code but were missing from the README table — ✅ FIXED (rows added)

These have a `PROP_*` and are loaded, but had no README row. All three were added to the README table:

| Property key | Loads into | Default |
|---|---|---|
| `subjectValidationType` | `subjectValidationType(SubjectValidationType)` (case-insensitive `None`/`Lenient`/`Strict`) | `Lenient` |
| `reconnectDelayHandlerClass` | `reconnectDelayHandler(ReconnectDelayHandler)` (class name) | `(none)` |
| `reconnectDelayBehavior` | `reconnectDelayBehavior(ReconnectDelayBehavior)` (case-insensitive enum name) | `LameDuckAware` |

## D. Other naming inconsistencies — ✅ FIXED (except deferred, see E)

- **`maxReconnect`** — ✅ made plural everywhere: property key `maxReconnect` → `maxReconnects`, field `maxReconnect` → `maxReconnects`, getter `getMaxReconnect()` → `getMaxReconnects()` (the setter `maxReconnects(int)` was already plural). `PROP_MAX_RECONNECT` → `PROP_MAX_RECONNECTS`.
- **`openTls`** — ✅ method casing fixed: `opentls()` / `opentls(boolean)` → `openTls()` / `openTls(boolean)` (the `openTls` property key was already correct). The URL scheme `opentls://` and `OPENTLS_PROTOCOL` are intentionally unchanged — those are wire/scheme strings, not the method.
- **`secure`** — left as-is intentionally (you'll address separately if needed). It loads into `useDefaultTls` and has no direct round-trip getter (state via `isTLSRequired()` / `getSslContext()`).

## E. Deferred — needs separate handling

- **`supportUtf8Subjects`** — property key `supportUtf8Subjects` (`Utf8`) vs the builder method/field `supportUTF8Subjects` (`UTF8`). **Not fixed here** — pulled out of the rename batch because it's being addressed separately (likely removed/reworked rather than just re-cased).

## Notes

- The `…Class` suffix on class-name properties (`connectionListenerClass`, `errorListenerClass`, `dispatcherFactoryClass`, the executor/thread-factory ones, etc.) is intentional convention — the property names a class to instantiate, the getter returns the instance (`getConnectionListener()`), so those are **not** flagged as mismatches.
- `url` is a config-only key (no `getUrl()`); it populates the server list read via `getServers()`. Intentional, not flagged.
