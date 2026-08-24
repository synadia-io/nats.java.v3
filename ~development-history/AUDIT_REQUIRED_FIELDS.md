# Audit — Required-field handling across API objects and creators

Scope: every API DTO in `jetstream/src/main/java/io/synadia/client/api/` — lazy readers
(extending `LazyApiObject`), `ApiResponse` subtypes, and `*Creator` writers — checked against
the upstream schema at `/mnt/c/nats/jsm.go/schema_source/jetstream/api/v1/definitions.json`.

**Out of scope (handled elsewhere):** field-level *value* validation (numeric ranges, regex
patterns, enum membership). This audit checks **presence-handling** only: do required-field
getters fail (or throw) when the JSON is missing the value, and do creators validate
required inputs in constructors and setters?

The presence-handling policy this audit applies is documented in
`REQUIRED_FIELDS_POLICY.md` (adopted 2026-06-09). A separate round-trip audit lives in
`AUDIT_ROUND_TRIP_PLAN.md`.

---

## Methodology — the reference pattern

The audit predates the **optimistic required-field policy** documented in
`REQUIRED_FIELDS_POLICY.md` (adopted 2026-06-09). Under the policy, every required-scalar
getter on a `LazyApiObject` subclass returns a sensible empty value when the JSON is
absent rather than throwing. The reference pattern below reflects the post-policy state.

### Reader rules (e.g. `External`)

All optimistic helpers are static methods on `io.synadia.client.utils.ApiUtils` taking
a `LazyJsonValue` explicitly — that lets both `LazyApiObject` subclasses and
`ApiResponse` subclasses use them without duplication. Import them via static import:
`import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;`

1. **Required string getter returns non-`@Nullable`** and delegates to
   `ApiUtils.readStringOrEmpty(ljv, key)`, which returns `""` on missing JSON. New
   required-string getters should be one-liners: `return readStringOrEmpty(ljv, KEY);`
   Never inline a null-check / throw.
2. **Required Duration getter returns non-`@Nullable`** and delegates to
   `ApiUtils.readDurationOrZero(ljv, key)`, which returns `Duration.ZERO` on missing or
   negative input.
3. **Required date getter returns non-`@Nullable`** and delegates to
   `ApiUtils.readDateOrDefault(ljv, key)`, which returns `DateTimeUtils.DEFAULT_TIME`
   (the codebase-wide "absent timestamp" sentinel — `JsonWriteUtils` skip-emits it,
   preserving round-trip). (`ApiResponse` subclasses have a parallel `dateRequired`
   helper that additionally threads `invalidJson()` error state; both are fine within
   their domain.)
4. **Required long getter returns non-`@Nullable`** and delegates to
   `ApiUtils.readLongOrMinusOne(ljv, key)`, which returns `-1L` on missing JSON. `-1` is
   the codebase-wide "missing required numeric" sentinel (matches `Error.NOT_SET`,
   `PublishAck.seq`, `StreamConfiguration` max-limits).
5. **Required int getter** mirrors with `ApiUtils.readIntegerOrMinusOne(ljv, key)`.
6. **Required boolean getter** uses the existing `readBoolean(ljv, KEY, false)`
   convention — boolean has no separate "missing" sentinel, `false` is the default.
7. **Optional field getter is `@Nullable`** and silently returns null on absent JSON
   (uses `readString(ljv, KEY)` from `LazyJsonValueUtils`, or `readLong(ljv, KEY, 0)`
   for numerics where `0` is the documented "absent" value).
8. **`optionalInstance(LazyJsonValue v)` is `@Nullable`** and returns null when the input is null.
9. **Required sub-object getter** follows the **collection pattern** — never null, but
   can be empty. The sub-object is constructed from `readMapObjectOrEmpty(ljv, KEY)`
   (for direct `LazyApiObject` subclasses) or `valueRequired(KEY, Ctor::new, EMPTY)`
   (for `ApiResponse` subclasses), or returns a documented `EMPTY` /
   `getDefaultInstance()` sentinel on missing. This composes cleanly with the optimistic
   policy: the empty wrapper's own required-field getters then return their per-type
   sentinels (`""`, `-1`, `Duration.ZERO`, `DateTimeUtils.DEFAULT_TIME`). Same shape as
   `getReplicas()` / `getSubjects()` / `getTiers()` already use for lists and maps:
   "may be empty, but never null."
10. **`PublishAck`-style construction-time throw** is a domain-specific exception for
    response objects where the protocol requires construction to fail on missing fields.
    Not generalized — use the collection pattern above for normal sub-objects.

### Creator rules (e.g. `ExternalCreator`)
1. **Required field is non-`@Nullable`** on the private field and on the getter.
2. **Every constructor that takes a required field validates it** via
   `Validator.required(value, "FieldName")` (or an equivalent throw-on-null/empty helper such
   as `validateStreamName`).
3. **Every public setter for a required field validates non-null** the same way the
   constructor does — never accept `@Nullable` for a required field.
4. **Optional field is `@Nullable`** on field, constructor parameter, setter parameter, and getter.
5. **Optional String fields that should treat empty as absent use `Validator.emptyAsNull(value)`**
   for normalization. Never inline `s == null || s.isEmpty() ? null : s`.

### Schema-required fields used in this audit

| Schema definition | Required fields |
|---|---|
| `external_stream_source` | `api` |
| `stream_source` | `name` |
| `stream_consumer_source` | `name`, `deliver_subject` (no v3 reader yet) |
| `priority_group_state` | `group` |
| `stream_source_info` | `name`, `lag`, `active` |
| `lost_stream_data` | _(none)_ |
| `placement` | _(none)_ |
| `peer_info` | `name`, `current`, `active` |
| `cluster_info` | _(none)_ |
| `api_stats` | `total`, `errors` |
| `tier` | `memory`, `storage`, `streams`, `limits`, `consumers` |
| `account_stats` | `memory`, `storage`, `streams`, `limits`, `api`, `consumers` |
| `account_limits` | `max_consumers`, `max_memory`, `max_storage`, `max_streams` |
| `stored_message` | `subject`, `seq`, `time` |
| `iterable_response` | `total`, `offset`, `limit` |
| `error_response` | `error` |
| `api_error` | `code` |
| `sequence_info` | `consumer_seq`, `stream_seq` |
| `sequence_pair` | `consumer_seq`, `stream_seq` |
| `consumer_info` | `stream_name`, `name`, `config`, `created`, `delivered`, `ack_floor`, `num_ack_pending`, `num_redelivered`, `num_waiting`, `num_pending` |
| `consumer_configuration` | _(none — `"required":[]`)_ |
| `stream_info` | `config`, `state`, `created` |
| `stream_alternate` | `name`, `cluster` |
| `stream_state` | `messages`, `bytes`, `first_seq`, `last_seq`, `consumer_count` |
| `subject_transform` | `src`, `dest` |
| `republish` | `src`, `dest` |
| `stream_configuration` | `retention`, `max_consumers`, `max_msgs`, `max_bytes`, `max_age`, `storage`, `num_replicas` |

---

## Required-scalar convention (post-policy)

Under the optimistic policy adopted 2026-06-09 (see `REQUIRED_FIELDS_POLICY.md`), every
required scalar reader returns a "missing" sentinel value when the JSON is absent — no
throws, no `@Nullable` annotation lies:

```java
public long getMaxMemory() {
    return readLongOrMinusOne(ljv, MAX_MEMORY);  // schema-required: -1 on missing
}

public String getApi() {
    return readStringOrEmpty(ljv, API);          // schema-required: "" on missing
}

public Duration getActive() {
    return readDurationOrZero(ljv, ACTIVE);      // schema-required: ZERO on missing
}
```

Helpers all live in `io.synadia.client.utils.ApiUtils` as static methods taking
`LazyJsonValue` explicitly:

| Type | Helper | Sentinel |
|---|---|---|
| String | `readStringOrEmpty` | `""` |
| long | `readLongOrMinusOne` | `-1L` |
| int | `readIntegerOrMinusOne` | `-1` |
| Duration | `readDurationOrZero` | `Duration.ZERO` |
| ZonedDateTime | `readDateOrDefault` | `DateTimeUtils.DEFAULT_TIME` (year-1 UTC) |
| boolean | (no new helper) `readBoolean(ljv, KEY, false)` | `false` |

### `-1` collision note

For fields where `-1` is a meaningful server value (e.g. `StreamConfiguration.max_consumers`
= "unlimited"), the sentinel is indistinguishable from server-sent `-1`. That conflation
already existed pre-policy and is judged acceptable noise — the missing-required case is
the rare-wire-broken scenario.

### Sub-objects follow the collection pattern

Required sub-objects use the same "never null, may be empty" pattern that lists and maps
already follow elsewhere in the codebase. They're constructed via
`readMapObjectOrEmpty(ljv, KEY)` (or `valueRequired(..., EMPTY)` for `ApiResponse`
subclasses) and return a non-null empty wrapper when the JSON is absent. The empty
wrapper's own getters then propagate the per-type optimistic sentinels (`""`, `-1`,
`Duration.ZERO`, `DateTimeUtils.DEFAULT_TIME`). This composes recursively without
needing per-sub-object null guards. Audit defects #10/#13/#14/#15 were wrongly diagnosed
under the earlier methodology — they all already follow this pattern. **Closed
2026-06-09.**

---

## Per-pair findings

### Readers

**Bullet markers** (legend): `✓` already correct, no action; `✅` was a defect, now fixed; `❌` open defect; `⚠` open question / needs verification; `🔧` cleanup migration (informational, not a defect).

#### ✅ `External` — required: `api`
- ✓ `getApi()` → `return readStringOrEmpty(API);` (under optimistic policy 2026-06-09).
- ✓ `getDeliver()` is `@Nullable`.
- ✓ `optionalInstance` returns null for null input.

#### ✅ `Republish` / `RepublishCreator` — required: `src`, `dest` — **FIXED 2026-06-09**
- ✅ `Republish.getSource()` / `getDestination()` → `readStringOrEmpty(...)`.
- ✅ `RepublishCreator` validates both fields in both constructors via `Validator.required(...)`. No mutating setters.

#### ✅ `SubjectTransform` / `SubjectTransformCreator` — required: `src`, `dest` — **FIXED 2026-06-09**
- ✅ `SubjectTransform.getSource()` / `getDestination()` → `readStringOrEmpty(...)`.
- ✅ `SubjectTransformCreator` validates both fields in the only constructor; no mutating setters.

#### ✅ `Placement` / `PlacementCreator` — required: _(none)_ — **FIXED 2026-06-09**
- ✓ All fields optional; `Placement.getCluster()` / `getTags()` are `@Nullable`.
- ✅ `PlacementCreator.cluster(@Nullable)` uses `emptyAsNull` per the new norm.

#### ✅ `StreamSource` / `Mirror` / `Source` + `StreamSourceCreator` / `MirrorCreator` / `SourceCreator` — required: `name` — **FIXED 2026-06-09**
- ✅ `StreamSource.getStreamName()` → `return readStringOrEmpty(NAME);` — migrated from inline throw 2026-06-09.
- ✅ `StreamSourceCreator` primary and copy-rename constructors both validate via `validateStreamName(name, true)`.
- ✓ `MirrorCreator`, `SourceCreator` inherit both constructors.

#### ✅ `ConsumerLimits` / `ConsumerLimitsCreator` — required: _(none)_
- ✓ All fields optional; nullability annotations consistent throughout.

#### ✅ `ConsumerConfiguration` / `ConsumerCreator` hierarchy — required: `"required":[]`
- ✓ Schema is explicitly empty-required. All-optional setters across `ConsumerCreator`, `AbstractEphemeralConsumerCreator`, `AbstractOrderedConsumerCreator`, and the four leaf classes are consistent.
- ✓ Separate `ConsumerFieldsSetterCoverageTest` enforces a related invariant (every protected `_*` setter has a public counterpart).

#### ✅ `PriorityGroupState` — required: `group` — **FIXED 2026-06-09**
- ✅ `getGroup()` → `return readStringOrEmpty(GROUP);`.
- ✓ `getPinnedClientId()` / `getPinnedTime()` are `@Nullable`.

#### ✅ `PeerInfo` (abstract base for `Replica`) — required: `name`, `current`, `active` — **FIXED 2026-06-09**
- ✅ `getName()` → `return readStringOrEmpty(NAME);`.
- ✓ `isCurrent()` uses `readBoolean(ljv, CURRENT, false)` — required-scalar convention.
- ✅ `getActive()` → `return readDurationOrZero(ACTIVE);` — migrated to shared helper 2026-06-09.
- ✓ `isOffline()`, `getLag()` — optional fields, OK.

#### ✅ `StreamSourceInfo` (base for `MirrorInfo`, `SourceInfo`) — required: `name`, `lag`, `active` — **FIXED 2026-06-09**
- ✅ `getName()` → `return readStringOrEmpty(ljv, NAME);` — migrated from inline throw 2026-06-09.
- ✅ `getLag()` → `return readLongOrMinusOne(ljv, LAG);` — default flipped from `0` to `-1` per policy 2026-06-09.
- ✅ `getActive()` → `return readDurationOrZero(ljv, ACTIVE);` — flipped from `@Nullable` to non-`@Nullable` under the optimistic policy 2026-06-09.
- ✓ `getFilterSubject()`, `getExternal()`, `getError()` are `@Nullable`.
- ✓ `getSubjectTransforms()` returns a (possibly-empty) list.

#### ✅ `ClusterInfo` — required: _(none)_
- ✓ All getters `@Nullable` where appropriate.
- ✓ `getReplicas()` returns a (possibly-empty) list via `Replica.listOf(...)`.

#### ✅ `AccountLimits` — required: `max_consumers`, `max_memory`, `max_storage`, `max_streams` — **FIXED 2026-06-09**
- ✅ All four required fields migrated to `readLongOrMinusOne(ljv, KEY)` — default flipped from `0` to `-1` per the new policy.
- ✓ Optional fields (`isMaxBytesRequired`, `getMaxAckPending`, `getMemoryMaxStreamBytes`, `getStorageMaxStreamBytes`) keep `0` defaults (schema says optional, `0` is the documented "absent" value).

#### ✅ `AccountTier` — required: `memory`, `storage`, `streams`, `consumers`, `limits` — **FIXED 2026-06-09**
- ✅ Numeric fields (`getMemoryBytes`, `getStorageBytes`, `getStreams`, `getConsumers`) migrated to `readLongOrMinusOne(ljv, KEY)`.
- ✓ `getLimits()` uses the collection pattern: `new AccountLimits(readMapObjectOrEmpty(ljv, LIMITS))` — non-null empty wrapper on missing. Empty wrapper's own getters propagate `-1` sentinels under the optimistic policy. Defect #10 was wrongly diagnosed.
- ✓ Optional `getReservedMemoryBytes`, `getReservedStorageBytes` keep `0` defaults.

#### ✅ `ApiStats` — required: `total`, `errors` — **FIXED 2026-06-09**
- ✅ `getTotal()`, `getErrors()` migrated to `readLongOrMinusOne(ljv, KEY)`.
- ✓ `getLevel()`, `getInFlight()` are optional, keep existing defaults.

#### ✅ `SequenceInfo` — required: `consumer_seq`, `stream_seq` — **FIXED 2026-06-09**
- ✅ `getConsumerSequence()`, `getStreamSequence()` migrated to `readLongOrMinusOne(ljv, KEY)`.
- ✓ `getLastActive()` is `@Nullable`.

#### ✅ `StreamAlternate` — required: `name`, `cluster` — **FIXED 2026-06-09**
- ✅ `getName()` → `return readStringOrEmpty(NAME);` — migrated from inline throw 2026-06-09.
- ✅ `getCluster()` → `return readStringOrEmpty(CLUSTER);` — migrated from inline throw 2026-06-09.

#### ✅ `StreamState` — required: `messages`, `bytes`, `first_seq`, `last_seq`, `consumer_count` — **FIXED 2026-06-09**
- ✅ All five required numeric fields migrated to `readLongOrMinusOne(ljv, KEY)`.
- ✓ Optional `getFirstTime`, `getLastTime`, `getSubjectCount`, `getDeletedCount`, `getLostStreamData` — properly nullable or `0`-default per existing convention.

#### ✅ `LostStreamData` — required: _(none)_
- ✓ `getMessages()` returns list via `readLongListOrEmpty` — never null.
- ✓ `getBytes()` is `@Nullable`.

#### ⚠ `Error` — required: `code`
- ⚠ `getCode()` returns `int` from `readInteger(ljv, CODE, NOT_SET)` — sentinel default of `NOT_SET`. Numeric convention.
- ❌ `getDescription()` returns non-`@Nullable` String — schema says `description` is optional. **Open:** if missing from JSON, what does the constructor at line 34 do? Annotation should be `@Nullable` or the constructor should fall back to a sentinel — verify.

#### ⚠ `MessageInfo` — required: `subject`, `seq`, `time`
- ⚠ Not surveyed in depth. **Open question** for follow-up.

#### ✅ `PublishAck` — required (Java contract): `stream`, `seq` (server response)
- ✓ Constructor throws `IOException("Invalid JetStream ack.")` if `stream == null` or `seq == -1`. Correct pattern — checked at construction time, not getter time.
- ✓ Optional `domain`, `val`, `batchId` are `@Nullable`.

#### ✅ `PurgeResponse`
- ✓ Required by schema: none explicit. `success` and `purged` use defaults (`false`, `0`).

#### ✅ `StreamInfo` — required: `config`, `state`, `created` — **VERIFIED OK 2026-06-09**
- ✓ `getConfiguration()` → `valueRequired(CONFIG, StreamConfiguration::new, StreamConfiguration.EMPTY)` — collection pattern, returns `EMPTY` on missing. Sets `invalidJson()` so `hasError()` is true. Non-null.
- ✓ `getStreamState()` → same shape with `StreamState.EMPTY`.
- ✓ `getCreateTime()` → `dateRequired(CREATED)` — returns `DateTimeUtils.DEFAULT_TIME` on missing, sets `invalidJson()`. Optimistic-policy-compliant.

#### ✅ `ConsumerInfo` — required: `stream_name`, `name`, `config`, `created`, `delivered`, `ack_floor`, `num_ack_pending`, `num_redelivered`, `num_waiting`, `num_pending` — **FIXED 2026-06-09**
- ✓ `getName()`, `getStreamName()` use `ApiResponse.stringRequired(KEY)` — the parallel error-tracking helper. Returns `""` and sets `invalidJson()` if missing.
- ✓ `getConsumerConfiguration()` inline-checks for `null` and falls back to `ConsumerConfiguration.getDefaultInstance()` — collection pattern, non-null empty wrapper.
- ✓ `getDelivered()`, `getAckFloor()` → `new SequenceInfo(readMapObjectOrEmpty(ljv, KEY))` — collection pattern.
- ✓ `getCreationTime()` uses `dateRequired(KEY)` — returns `DEFAULT_TIME` on missing.
- ✅ The four `num_*` fields migrated to `readLongOrMinusOne(ljv, KEY)`.

#### ✅ `AccountStatistics` — required: `memory`, `storage`, `streams`, `limits`, `api`, `consumers` — **VERIFIED OK 2026-06-09**
- ✓ Numeric fields delegate to a private `getRollupTier()` (a new `AccountTier(ljv)`) — non-null, propagates the `-1` sentinels from `AccountTier`'s post-policy getters.
- ✓ `getLimits()` delegates to `getRollupTier().getLimits()` — collection pattern.
- ✓ `getApiStats()` → `new ApiStats(readMapObjectOrEmpty(ljv, API))` — collection pattern, non-null empty wrapper.
- ✓ `getTiers()` already explicitly documents "may be empty, but never null" — established codebase voice.

#### ✅ `ConsumerPauseResponse`
- ✓ All fields optional per schema (no required listed). `paused` defaults to `false`.

### Creators (writer side)

#### ✅ `ExternalCreator`, `RepublishCreator`, `SubjectTransformCreator`, `PlacementCreator`, `StreamSourceCreator`, `MirrorCreator`, `SourceCreator`, `ConsumerLimitsCreator`
- ✓ All audited above. All fixed.

#### ✅ `ConsumerCreator` hierarchy
- ✓ Schema `consumer_configuration` is `"required":[]`. All-optional setters; no defects.

#### ⚠ `StreamCreator` — schema requires `retention`, `max_consumers`, `max_msgs`, `max_bytes`, `max_age`, `storage`, `num_replicas` (#5 — still open)
- ✓ `StreamCreator(String name)` primary constructor: `validateStreamName(name, true)`.
- ❌ `StreamCreator(StreamCreator sc)` copy constructor at line 100: `this.name = sc.name;` — no re-validation. Functionally safe today (the source was validated when built) but defense-in-depth gap.
- ❌ `StreamCreator(StreamConfiguration sc)` from-reader constructor at line 148: same concern.
- ❌ Many setters for schema-required fields accept `@Nullable` (e.g. `retentionPolicy(@Nullable RetentionPolicy)` at line 616). Either each silently substitutes a sensible default (acceptable, needs Javadoc) or it's a latent hole producing invalid JSON. Per-setter audit deferred — list below in "Discussion items".

---

## Summary of defects

| # | File | Line(s) | Defect | Severity | Status |
|---|---|---|---|---|---|
| ~~1~~ | ~~`Republish.java`~~ | ~~30-42~~ | ~~required string getters silently return null~~ | ~~**High**~~ | ✅ Fixed 2026-06-09 |
| ~~2~~ | ~~`SubjectTransform.java`~~ | ~~37-49~~ | ~~required string getters silently return null~~ | ~~**High**~~ | ✅ Fixed 2026-06-09 |
| ~~3~~ | ~~`PlacementCreator.java`~~ | ~~55-58~~ | ~~`cluster(String)` setter nullability mismatch~~ | ~~**Medium**~~ | ✅ Fixed 2026-06-09 |
| ~~4~~ | ~~`StreamSourceCreator.java`~~ | ~~44-51~~ | ~~Copy-rename constructor doesn't validate `newName`~~ | ~~**Medium**~~ | ✅ Fixed 2026-06-09 |
| 5 | `StreamCreator.java` | 100, 148, 616+ | Copy constructors don't re-validate `name`; some schema-required-field setters accept `@Nullable` without documented null-handling | **Low-Medium** | Open |
| ~~6~~ | ~~`PriorityGroupState.java`~~ | ~~32-34~~ | ~~`getGroup()` silently returns null when JSON missing `group`; declared non-`@Nullable`~~ | ~~**High**~~ | ✅ Fixed 2026-06-09 |
| ~~7~~ | ~~`PeerInfo.java`~~ | ~~25-27~~ | ~~`getName()` silently returns null when JSON missing `name`; declared non-`@Nullable`~~ | ~~**High**~~ | ✅ Fixed 2026-06-09 |
| ~~8~~ | ~~`PeerInfo.java`~~ | ~~49-52~~ | ~~`getActive()` returns `Duration` non-`@Nullable`; behaviour when JSON missing~~ | ~~**Medium**~~ | ✅ Verified 2026-06-09 — `return d == null ? Duration.ZERO : d;` guard preserves non-null contract |
| ~~9~~ | ~~`StreamSourceInfo.java`~~ | ~~52-56~~ | ~~`getActive()` same shape as #8 — possible latent null on required field~~ | ~~**Medium**~~ | ✅ Verified 2026-06-09 — getter declared `@Nullable` with explicit null/negative guard; annotation matches behaviour |
| ~~10~~ | ~~`AccountTier.java`~~ | ~~~74~~ | ~~`getLimits()` non-`@Nullable` for required sub-object~~ | ~~**Medium**~~ | ✅ Verified OK 2026-06-09 — collection pattern via `readMapObjectOrEmpty` |
| 11 | `Error.java` | 34, 68 | `getDescription()` declared non-`@Nullable` String; description is **optional** per schema → annotation should be `@Nullable` | **Medium** | Open |
| 12 | `MessageInfo.java` | — | Not surveyed in depth; required `subject`, `seq`, `time` per schema | **TBD** | Open — needs survey |
| ~~13~~ | ~~`StreamInfo.java`~~ | ~~45+, 56+, 67+~~ | ~~`getConfiguration()`, `getStreamState()`, `getCreateTime()` non-`@Nullable` for required sub-objects/values~~ | ~~**Medium**~~ | ✅ Verified OK 2026-06-09 — `valueRequired(..., EMPTY)` + `dateRequired` |
| ~~14~~ | ~~`ConsumerInfo.java`~~ | ~~65, 73, 81, 89, 100~~ | ~~`getName`, `getStreamName`, `getCreationTime`, `getDelivered`, `getAckFloor` non-`@Nullable`~~ | ~~**Medium**~~ | ✅ Verified OK 2026-06-09 — `stringRequired` / `dateRequired` / collection pattern for sub-objects |
| ~~15~~ | ~~`AccountStatistics.java`~~ | ~~97, 114~~ | ~~`getLimits()`, `getApiStats()` non-`@Nullable` for required sub-objects~~ | ~~**Medium**~~ | ✅ Verified OK 2026-06-09 — collection pattern |

## Closed defects (12)

All optimistic-policy helpers shipped 2026-06-09 as static methods on
`io.synadia.client.utils.ApiUtils`:
`readStringOrEmpty` / `readLongOrMinusOne` / `readIntegerOrMinusOne` /
`readDurationOrZero` / `readDateOrDefault`. Both `LazyApiObject` and `ApiResponse`
subclasses can call them via static import.

- #1 `Republish` — `getSource()` / `getDestination()` → `readStringOrEmpty(ljv, ...)`. Fixed 2026-06-09.
- #2 `SubjectTransform` — `getSource()` / `getDestination()` → `readStringOrEmpty(ljv, ...)`. Fixed 2026-06-09.
- #3 `PlacementCreator` — `cluster(@Nullable)` uses `emptyAsNull`. Fixed 2026-06-09.
- #4 `StreamSourceCreator` — copy-rename constructor validates via `validateStreamName`. Fixed 2026-06-09.
- #6 `PriorityGroupState.getGroup()` — `readStringOrEmpty(ljv, GROUP)`. Fixed 2026-06-09.
- #7 `PeerInfo.getName()` — `readStringOrEmpty(ljv, NAME)`. Fixed 2026-06-09.
- #8 `PeerInfo.getActive()` — `readDurationOrZero(ljv, ACTIVE)`. Fixed 2026-06-09.
- #9 `StreamSourceInfo.getActive()` — flipped from `@Nullable` to non-`@Nullable` via `readDurationOrZero(ljv, ACTIVE)`; `getName()` migrated to `readStringOrEmpty`; `getLag()` migrated to `readLongOrMinusOne(ljv, LAG)`. Fixed 2026-06-09.
- #10 `AccountTier.getLimits()` — collection pattern: `new AccountLimits(readMapObjectOrEmpty(ljv, LIMITS))`. Never null, may be empty. Verified OK 2026-06-09.
- #13 `StreamInfo` sub-objects — `valueRequired(KEY, Ctor::new, EMPTY)` for `getConfiguration` / `getStreamState`; `dateRequired(CREATED)` for `getCreateTime`. Verified OK 2026-06-09.
- #14 `ConsumerInfo` sub-objects — `stringRequired` / `dateRequired` for scalar required fields; collection pattern (`readMapObjectOrEmpty` + `getDefaultInstance()` fallback) for `getConsumerConfiguration` / `getDelivered` / `getAckFloor`. Verified OK 2026-06-09.
- #15 `AccountStatistics` sub-objects — `getLimits()` delegates via private `getRollupTier()`; `getApiStats()` uses `new ApiStats(readMapObjectOrEmpty(ljv, API))`. Both collection-pattern. Verified OK 2026-06-09.

### Bonus closures (alongside the policy adoption, not separately numbered defects)

- **Inline-throw string getters** in `StreamSource.getStreamName`, `StreamAlternate.getName`,
  `StreamAlternate.getCluster` migrated to `readStringOrEmpty(ljv, KEY)`.
- **Numeric `0` → `-1` defaults** for schema-required fields: `AccountLimits` (all four
  required maxes), `AccountTier` (memory/storage/streams/consumers), `ApiStats`
  (total/errors), `SequenceInfo` (consumer_seq/stream_seq), `StreamState` (all five
  required counts/sequences), `ConsumerInfo` (all four required `num_*` fields),
  `StreamSourceInfo.getLag`. All now use `readLongOrMinusOne(ljv, KEY)`.

Reference patterns are now the `read…Or…(ljv, KEY)` family for required-scalar readers
and `Validator.required(...)` / `emptyAsNull(...)` for creators.

## Suggested ordering of remaining fixes

1. **#11 `Error.getDescription()`** — schema says description is optional, getter says non-null.
   Add `@Nullable` and adjust the field's value/check at line 34. One-line annotation + small
   constructor tidy.
2. **#12 `MessageInfo`** — needs a survey pass. Schema requires `subject`, `seq`, `time`.
   Likely already follows the `ApiResponse` patterns (`stringRequired` / `dateRequired`); just
   needs a read-through to confirm.
3. **#5 `StreamCreator`** — per-setter audit + copy-constructor revalidation. Deferred
   because the failure mode is server-rejection-on-publish rather than NPE-at-getter, so
   strictly less urgent than the reader-side issues.

## Discussion items (not flagged as defects)

### Required-scalar convention — closed

Resolved 2026-06-09 by adopting the optimistic policy across `String`, `long`, `int`,
`Duration`, `ZonedDateTime`, and `boolean` readers. The codebase-wide convention is now:

- String → `""` via `readStringOrEmpty(ljv, KEY)`
- long → `-1L` via `readLongOrMinusOne(ljv, KEY)`
- int → `-1` via `readIntegerOrMinusOne(ljv, KEY)`
- Duration → `Duration.ZERO` via `readDurationOrZero(ljv, KEY)`
- ZonedDateTime → `DateTimeUtils.DEFAULT_TIME` via `readDateOrDefault(ljv, KEY)`
- boolean → `false` via existing `readBoolean(ljv, KEY, false)`

All helpers live as static methods on `io.synadia.client.utils.ApiUtils`. See
`REQUIRED_FIELDS_POLICY.md`. The earlier "leave required numerics at `0`" policy is
**superseded** — `0` was too easily confused with valid server values for fields like
sequence numbers and counts.

### Inline-throw migrations — closed

`StreamSource.getStreamName()`, `StreamAlternate.getName()`, `StreamAlternate.getCluster()`
all migrated to `readStringOrEmpty(KEY)` 2026-06-09 alongside policy adoption.
`Error.getDescription()` deliberately deferred (see audit defect #11 — the description
field is **optional** per schema and needs `@Nullable` rather than a default; revisit
separately).

### `StreamCreator` `@Nullable` setters for required fields

Per-setter list to audit (line numbers from `StreamCreator.java`):
- `retentionPolicy(@Nullable RetentionPolicy)` line 616
- `storageType(@Nullable StorageType)` — likely similar shape
- `discardPolicy(@Nullable DiscardPolicy)` — similar
- `compressionOption(@Nullable CompressionOption)` line 626
- numeric setters for `maxConsumers`, `maxMessages`, `maxBytes`, `maxAge`, `replicas` —
  primitives or `Duration`; verify each handles null/sentinels correctly.

For each: confirm Javadoc says what null means. If the answer is "passing null resets to
the schema default," document that. If the answer is "we forgot," tighten.

---

## What this audit does NOT cover

- **Field-level value validation** (already handled by `ApiFieldsTest`).
- **`StreamSourceInfo`-derived `MirrorInfo` / `SourceInfo`** — these are thin extensions of
  `StreamSourceInfo`; the per-pair findings above apply via inheritance.
- **Round-trip equivalence** — separate plan in `AUDIT_ROUND_TRIP_PLAN.md`.
