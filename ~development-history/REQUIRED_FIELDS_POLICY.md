# Recommendation — Required-field handling policy for lazy readers

You raised the question while we were closing out PeerInfo / StreamSourceInfo: given how
much of the v3 reader layer is built on lazy JSON, and given that wire-level missing-data
is vanishingly rare in practice (basically "the connection broke mid-frame"), should we
just commit to "if the schema says it's there, assume it's there" everywhere — and for
strings return `""`, for durations return `Duration.ZERO` like `PeerInfo.getActive()`
already does?

My recommendation: **yes, lean optimistic.** Drop the throw-on-missing pattern for
required scalars and align the whole reader layer on the convention numerics and booleans
already follow. Required sub-objects are the one case where there is no sensible default,
and those should be `@Nullable` (or constructor-checked, the `PublishAck` shape).

Below is the reasoning, the failure mode of where we are now, and the concrete migration.

## What we actually have today

The current reader layer is **three inconsistent philosophies in one codebase**:

- **Required numerics / booleans → silent default.** `readLong(ljv, KEY, 0)`,
  `readBoolean(ljv, KEY, false)`. Established v3 convention, matches v2. Used in
  `AccountLimits`, `AccountTier`, `ApiStats`, `SequenceInfo`, `StreamState`,
  `PeerInfo.isCurrent()` / `getLag()`, `Error.getCode()`, etc. Documented in the
  required-fields audit under "Numeric required-field convention" and explicitly
  **not flagged** there.
- **Required strings → throw.** We added `LazyApiObject.getRequiredString(key)` this
  session, migrated `External`, `Republish`, `SubjectTransform`, `PriorityGroupState`,
  `PeerInfo.getName()`, `StreamSourceInfo.getName()` to it. Throws `IllegalStateException`
  on missing.
- **Required durations → coin flip.** `PeerInfo.getActive()` is non-`@Nullable` and
  returns `Duration.ZERO` on null. `StreamSourceInfo.getActive()` is `@Nullable` and
  returns null on null. Same schema requirement, two different reader contracts, in
  classes that live side-by-side and both extend `LazyApiObject`.

Reader code that walks across these objects has no predictable contract. A caller writing
`info.getName() + " is at " + info.getActive()` needs to know that `getName()` throws and
`getActive()` doesn't — for one class — but for the other class `getActive()` might be
null. That's not a contract, that's a quiz.

## The two coherent paths

Either philosophy is internally consistent if applied uniformly.

**Path A — strict everywhere.** Extend the throw-on-missing pattern to numerics, booleans,
durations, and timestamps. Add `getRequiredLong`, `getRequiredBoolean`,
`getRequiredDuration`, `getRequiredDate` siblings to `getRequiredString`. Migrate every
`readLong(K, 0)` for a schema-required field to the throwing variant.

**Path B — optimistic everywhere (recommended).** Drop `getRequiredString`'s throw and
return `""` instead. Mirror that for durations (`Duration.ZERO`) and timestamps (epoch or
`@Nullable` — see below). Numerics and booleans already do this; the work is mostly
"strings catch up to the established convention" plus a handful of duration getters.

The hybrid we have now is worst-of-both: callers can't predict which getter is dangerous.

## Why I recommend optimistic (Path B)

1. **The "missing required field" case is theoretical.** NATS transports framed JSON over
   a reliable connection. The cases where a required field is genuinely absent are: the
   server has a bug, the schema drifted relative to the server version, or the wire was
   truncated mid-frame. The last is the only "real" runtime case and it doesn't happen on
   a healthy connection. The throw-on-missing pattern optimizes for an event you'll
   essentially never see, at the cost of an API that everyone has to handle defensively.

2. **The "silent default masks bugs" argument doesn't really hold downstream.** If a
   `getName()` returned `""` on a missing required field, the next thing that consumes
   that name (a server lookup, a comparison, a log line) will fail or surface visibly
   anyway. The bug isn't masked, it just surfaces one layer down — in a location closer
   to actual use, often with more context than the raw "this getter threw" stack trace
   would give. For a debugging perspective, "empty name caused a server NOT_FOUND" is
   strictly more actionable than "IllegalStateException in `getRequiredString`."

3. **Numerics already won the argument.** Every `readLong(K, 0)` for a schema-required
   field is a vote for optimism. There are dozens of these in v3, audited and explicitly
   not flagged. Continuing to treat strings as the special-case loud one just makes the
   layer harder to reason about.

4. **`PeerInfo.getActive()` already does it for Duration.** That's the pattern I'd
   propagate, not retreat from. Its non-`@Nullable` `Duration.ZERO` default is the
   honest match for "schema says required, here's the sensible empty."

5. **It's less code to read at every call site.** A caller that knows "readers are
   optimistic, just use the getter" never has to wrap, catch, or null-check at scalar
   boundaries. The defensive code disappears.

The thing I'd give up by going optimistic is the **loud-failure-on-server-bug** property.
That's real but small — if a server starts dropping `name` from a `StreamSourceInfo`
response, we'd find out via the consumer's downstream NOT_FOUND or "stream " (empty
name) error rather than via a stack trace. I think that's a fine trade.

## Sub-objects follow the collection pattern

Required sub-objects (`AccountTier.getLimits()`, `StreamInfo.getConfiguration()` /
`getStreamState()`, `AccountStatistics.getLimits()` / `getApiStats()`,
`ConsumerInfo.getConsumerConfiguration()` / `getDelivered()` / `getAckFloor()`, etc.) use
the same **"never null, may be empty"** contract that lists and maps already follow
elsewhere in the codebase (e.g. `getReplicas()`, `getSubjects()`, `getTiers()`).

Mechanically that's:
- `new Sub(readMapObjectOrEmpty(ljv, KEY))` for direct `LazyApiObject` subclasses, or
- `valueRequired(KEY, Sub::new, Sub.EMPTY)` for `ApiResponse` subclasses, or
- an inline fallback to a documented `EMPTY` / `getDefaultInstance()` constant.

In every case the result is a non-null wrapper whose own required-field getters return
the per-type optimistic sentinels (`""`, `-1`, `Duration.ZERO`, `DateTimeUtils.DEFAULT_TIME`).
The "missing required" condition propagates down through the sub-object's getters rather
than being raised as a null pointer at the boundary. This is the same recursive composition
that makes the scalar policy work.

Initial draft of this doc proposed `@Nullable` or `PublishAck`-style construction-time
throw for sub-objects — that was wrong. The collection pattern composes cleanly and is
already the established codebase voice; defects #10, #13, #14, #15 in the required-fields
audit were closed as verified-OK once this was recognized.

`PublishAck`'s construction-time throw stays as a domain-specific exception for response
objects where the protocol contract requires it, not a generalized pattern.

## What was actually shipped (2026-06-09)

### Helpers live in `ApiUtils`, not on `LazyApiObject`

After we tried putting the helpers as instance methods on `LazyApiObject`, it turned
out `ApiResponse` doesn't extend `LazyApiObject` (different reader hierarchy with its
own `stringRequired` / `dateRequired` error-tracking helpers). To give both hierarchies
access without duplicating, the helpers landed as **static methods on
`io.synadia.client.utils.ApiUtils`** taking `LazyJsonValue` explicitly:

```java
public static String  readStringOrEmpty   (LazyJsonValue ljv, String key);  // ""
public static long    readLongOrMinusOne  (LazyJsonValue ljv, String key);  // -1L
public static int     readIntegerOrMinusOne(LazyJsonValue ljv, String key); // -1
public static Duration    readDurationOrZero(LazyJsonValue ljv, String key); // Duration.ZERO
public static ZonedDateTime readDateOrDefault(LazyJsonValue ljv, String key); // DateTimeUtils.DEFAULT_TIME
```

Call sites use static import:
```java
import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;
...
public String getApi() {
    return readStringOrEmpty(ljv, API);
}
```

Reads the same as `readLong(ljv, KEY, 0)` from `LazyJsonValueUtils` — fits the existing
v3 reader convention.

### Numeric default for required fields is `-1`, not `0`

`-1` was already the established sentinel in chunks of the codebase
(`Error.NOT_SET = -1`, `PublishAck.seq` defaults to `-1`, `StreamConfiguration` max-limits
default to `-1`). The new `readLongOrMinusOne` / `readIntegerOrMinusOne` formalize that
pattern for the rest of the schema-required numerics. The previous policy ("required
numerics keep `readLong(K, 0)`") is **superseded** — `0` is too easily confused with a
valid server value for fields like `messages`, `bytes`, `lag`, sequence numbers, etc.

There is one known conflation: for fields where the server uses `-1` to mean *unlimited*
(notably `StreamConfiguration.max_consumers` / `max_msgs` / `max_bytes`), `-1` from
this helper is indistinguishable from server-sent `-1`. That conflation already existed
pre-policy and is judged acceptable noise — the missing-required case is the rare-wire-
broken scenario that justifies any sentinel at all.

### Migrated this pass

- **Strings → `readStringOrEmpty`:** `External.getApi`,
  `Republish.getSource` / `getDestination`, `SubjectTransform.getSource` / `getDestination`,
  `PriorityGroupState.getGroup`, `PeerInfo.getName`, `StreamSourceInfo.getName`,
  `StreamSource.getStreamName`, `StreamAlternate.getName` / `getCluster`.
- **Durations → `readDurationOrZero`:** `PeerInfo.getActive` (already had the inline
  pattern, refactored to helper), `StreamSourceInfo.getActive` (flipped from `@Nullable`
  to non-`@Nullable`).
- **Longs → `readLongOrMinusOne`:** `AccountLimits` (max_memory / max_storage /
  max_streams / max_consumers), `AccountTier` (memory / storage / streams / consumers),
  `ApiStats` (total / errors), `SequenceInfo` (consumer_seq / stream_seq),
  `StreamState` (messages / bytes / first_seq / last_seq / consumer_count),
  `ConsumerInfo` (num_pending / num_waiting / num_ack_pending / num_redelivered),
  `StreamSourceInfo.getLag`.

### Not migrated (deliberate)

- **`Error.getDescription`** — schema marks `description` as **optional**, so the right
  fix is `@Nullable`, not a default. Tracked separately as audit defect #11.
- **`ApiResponse` subclass strings/dates** (`ConsumerInfo.getName`, `StreamInfo.getCreateTime`,
  etc.) — these already use `ApiResponse`'s parallel `stringRequired` / `dateRequired`
  helpers that thread `invalidJson()` error state via `hasError()`. That's a feature
  of response objects, not a defect. The optimistic helpers and the error-tracking
  helpers coexist within their respective domains.
- **Required sub-objects** (`AccountTier.getLimits`, `StreamInfo.getConfiguration` /
  `getStreamState`, `ConsumerInfo` sub-objects, `AccountStatistics.getLimits` /
  `getApiStats`) — these already follow the collection pattern (`readMapObjectOrEmpty`
  + non-null empty wrapper). Audit defects #10/#13/#14/#15 verified-OK and closed.
  See the "Sub-objects follow the collection pattern" section below.
- **Optional numerics** (e.g. `AccountLimits.getMaxAckPending`, `StreamState.getSubjectCount`,
  `PeerInfo.getLag`) — keep their existing `readLong(K, 0)` since schema says optional
  and `0` is the documented "absent" value.

### Timestamps — decided as `DateTimeUtils.DEFAULT_TIME`

Picked the existing codebase-wide sentinel (year-1 UTC), not Unix epoch, because
`JsonWriteUtils` already skip-emits `DEFAULT_TIME` on serialization — round-trip
preserved. No required-timestamp getters exist on `LazyApiObject` subclasses today
(all such getters are on `ApiResponse` subclasses using `dateRequired`); the helper
is in place for future use.

## What I'd keep open for your call

1. **Timestamps:** `Instant.EPOCH` sentinel vs `@Nullable`. I lean `@Nullable` but it
   breaks the symmetry.
2. **Helper naming:** `readStringOrEmpty` / `readDurationOrZero` / `readDateOrEpoch`,
   or shorter `getString` / `getDuration` / `getDate` on `LazyApiObject` that imply
   "the policy-correct version." I lean explicit names because the `Or...` suffix
   communicates the contract at the call site without needing to remember the policy.
3. **Whether to also tighten `Error.getDescription()`** — schema says `description`
   is **optional**, so this is the one place where `@Nullable` is the schema-honest
   answer regardless of policy. Mention only because it's tangled with the same audit
   defect (#11) and would close at the same time.

## TL;DR

The current mix of "strings throw, numerics default, durations are inconsistent" is the
worst answer because callers can't predict it. Picking either direction would be
internally consistent — I recommend optimistic ("if schema says it's there, return
the sensible empty when it's not") because that's already the established pattern for
numerics and `PeerInfo.getActive()`, and the loud-failure-on-server-bug benefit is mostly
theoretical given NATS's wire reliability. Required sub-objects are the one carve-out:
`@Nullable` or constructor-throw, because there's no sensible default sub-object.

Cost is half a day plus one timestamp call. Want me to draft the helper additions and
do the migration?
