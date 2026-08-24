# API Package - Overloaded Setter Analysis

All overloaded setters in the `api` package follow the **Duration + long (millis)** pattern.
No boxed/primitive (`Long`/`long`, `Integer`/`int`) overloads were found.

## ConsumerCreator.java (6 pairs)

| Method | Overloads |
|---|---|
| `ackWait` | `Duration` (L234) / `long` (L245) |
| `idleHeartbeat` | `Duration` (L387) / `long` (L412) |
| `flowControl` | `Duration` (L431) / `long` (L442) |
| `maxExpires` | `Duration` (L453) / `long` (L464) |
| `inactiveThreshold` | `Duration` (L475) / `long` (L486) |
| `priorityTimeout` | `Duration` (L732) / `long` (L743) |

## ConsumerLimits.java (1 pair)

| Method | Overloads |
|---|---|
| `inactiveThreshold` | `Duration` (L89) / `long` (L99) |

## KeyValueConfiguration.java (1 pair)

| Method | Overloads |
|---|---|
| `limitMarker` | `Duration` (L371) / `long` (L382) |

## KeyValuePurgeOptions.java (1 pair)

| Method | Overloads |
|---|---|
| `deleteMarkersThreshold` | `Duration` (L57) / `long` (L70) |

## StreamConfiguration.java (3 pairs)

| Method | Overloads |
|---|---|
| `maxAge` | `Duration` (L800) / `long` (L810) |
| `duplicateWindow` | `Duration` (L883) / `long` (L894) |
| `subjectDeleteMarkerTtl` | `Duration` (L1112) / `long` (L1123) |

## Summary

**Total: 12 overloaded setter pairs across 5 files**, all Duration/long.
