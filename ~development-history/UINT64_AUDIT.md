# uint64 Audit — server truth vs. schema vs. Java API objects

**Question being answered:** which API-object fields are *unsigned 64-bit* (`uint64`) on the
server, and therefore must default to **0** (not **-1**) when absent from the JSON?

**Why it matters:** the read-side "missing required numeric" sentinel in this codebase is `-1`
(`readLongOrMinusOne`). For a `uint64` that is wrong — the natural empty value is `0`
(`readLongOrZero` / `ULONG_UNSET`). A `uint64` can never legitimately be `-1`, so handing `-1`
back to a caller is both impossible-on-the-wire and a sentinel collision with the signed fields.

**Sources of truth (in priority order):**
1. **Server Go structs** — the real types. `C:\nats\nats-server\server\` → `stream.go`,
   `consumer.go`, `jetstream.go`, `store.go`.
2. **Schema** — `C:\nats\jsm.go\schema_source\jetstream\api\v1\definitions.json`. *Unreliable
   for this purpose* (see Part A).
3. **Java API objects** — `jetstream/src/main/java/io/synadia/client/api/`.

---

## Part A — Why the schema can't be trusted to report uint64

The schema *has* a precise marker for unsigned values:

```json
"golang_uint64": { "type": "integer", "minimum": 0, "maximum": 18446744073709551615 }
```

…but it is applied **inconsistently**. Three patterns appear in the schema for what is, on the
server, a `uint64`:

| Pattern in schema | Machine-detectable as uint64? | Example fields |
|---|---|---|
| `"$ref": ".../golang_uint64"` | **Yes** | `stream_state.messages/bytes/first_seq/last_seq`, `sequence_info.consumer_seq/stream_seq`, `consumer_info.num_pending`, `stream_source.opt_start_seq`, `stream_source_info.lag`, `rate_limit_bps`, `lost_stream_data.bytes`, `first_seq` |
| inline `"type":"integer","minimum":0` (no ref) | **No** — looks identical to a signed count | `api_stats.total/errors/inflight`, `tier.memory/storage/reserved_*`, `account_stats.memory/storage`, `peer_info.lag` |
| (implicit, field omitted entirely) | **No** | `account_stats.reserved_memory/reserved_storage` — server sends them (embedded `JetStreamTier`), schema doesn't list them |

The trap: **`minimum: 0` is not a reliable signal.** Signed counts use it too —
`num_ack_pending`, `num_waiting`, `streams`, `consumers`, `num_subjects`, `consumer_count` are all
`golang_int` *with* `minimum: 0`. So:

- Keying a generator off the `golang_uint64` **$ref** → **under-matches** (misses `api_stats`,
  `tier`, `peer_info.lag` — all `uint64` in Go, all inlined in schema).
- Keying off **`minimum: 0`** → **over-matches** (sweeps in signed counts that just happen to be ≥0).

That is the concrete reason the schema is "poor at reporting this," and why the server is the only
authority. (Minor aside: the schema file also has invalid JSON — trailing commas at the
`max_expires`/`inactive_threshold` defaults around lines 848–859 and after `stream_configuration`
at line 1310.)

---

## Part B — Server `uint64` inventory (the truth)

Every `uint64`-typed, JSON-serialized field across the four server files, with the struct it lives
on and the client object it maps to:

| Server struct (file) | JSON field | Java object |
|---|---|---|
| `StreamState` (store.go) | `messages`, `bytes`, `first_seq`, `last_seq` | `StreamState` |
| `StreamState` (store.go) | `subjects` (map values), `deleted` (array items) | `StreamState` |
| `LostStreamData` (store.go) | `bytes` (+ `msgs[]` items) | `LostStreamData` |
| `ConsumerInfo` (consumer.go) | `num_pending` | `ConsumerInfo` |
| `ConsumerConfig` (consumer.go) | `opt_start_seq`, `rate_limit_bps` | `ConsumerConfiguration` |
| `SequenceInfo` (consumer.go) | `consumer_seq`, `stream_seq` | `SequenceInfo` |
| `StreamConfig` (stream.go) | `first_seq` | `StreamConfiguration` |
| `PeerInfo` (stream.go) | `lag` | `Replica`/`PeerInfo` |
| `StreamSourceInfo` (stream.go) | `lag` | `StreamSourceInfo` (Mirror/SourceInfo) |
| `StreamSource` (stream.go) | `opt_start_seq` | `StreamSource` (Mirror/Source) |
| `PubAck` (stream.go) | `seq`, `count` | `PublishAck` |
| `JetStreamAPIStats` (jetstream.go) | `total`, `errors`, `inflight` | `ApiStats` |
| `JetStreamTier` (jetstream.go) | `memory`, `storage`, `reserved_memory`, `reserved_storage` | `AccountTier`, `AccountStatistics` |
| `JetStreamStats` (jetstream.go) | `memory`, `storage`, `reserved_memory`, `reserved_storage` | *(server-level; no client object)* |

**Explicitly NOT uint64** (signed `int`/`int64`/`int32` on the server — `-1` defaults are *correct*
because `-1` means unlimited/unset):
- `JetStreamAccountLimits`: `max_memory`, `max_storage` (int64), `max_streams`, `max_consumers`,
  `max_ack_pending` (int), `memory_max_stream_bytes`, `storage_max_stream_bytes` (int64).
- `StreamConfig`: `max_consumers` (int), `max_msgs`, `max_bytes`, `max_msgs_per_subject` (int64),
  `max_msg_size` (int32), `num_replicas` (int).
- `ConsumerConfig`: `max_deliver`, `max_ack_pending`, `max_waiting`, `max_batch`, `max_bytes`,
  `num_replicas` (all int).
- `ConsumerInfo`: `num_ack_pending`, `num_redelivered`, `num_waiting` (int).
- `StreamState`: `num_subjects`, `num_deleted`, `consumer_count` (int).
- `JetStreamTier`: `streams`, `consumers` (int).

---

## Part C — Java API-object audit (uint64 fields only)

How each `uint64` field is currently read, and the verdict. "Default" = value returned when the key is absent. The **schema** column shows whether the schema independently confirms the field is unsigned: `uint64 ✅` = the schema `$ref`s `golang_uint64`; `int ≥0 ⚠️` = the schema inlines `type:integer, minimum:0`, which does **not** distinguish it from a signed count (server is the only authority); `— (n/a)` = the field has no schema definition at all.

| Java object | getter | reads with | schema | default now | should be | verdict |
|---|---|---|---|---|---|---|
| `StreamState` | `getMessageCount` | `readLongOrMinusOne(MESSAGES)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `StreamState` | `getByteCount` | `readLongOrMinusOne(BYTES)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `StreamState` | `getFirstSequence` | `readLongOrMinusOne(FIRST_SEQ)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `StreamState` | `getLastSequence` | `readLongOrMinusOne(LAST_SEQ)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `ConsumerInfo` | `getNumPending` | `readLongOrMinusOne(NUM_PENDING)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `SequenceInfo` | `getConsumerSequence` | `readLongOrMinusOne(CONSUMER_SEQ)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `SequenceInfo` | `getStreamSequence` | `readLongOrMinusOne(STREAM_SEQ)` | uint64 ✅ | **-1** | 0 | ❌ **fix** |
| `StreamSourceInfo` | `getLag` | `readLongOrMinusOne(LAG)` | uint64 ✅ | **-1** | 0 | ❌ **fix** (inconsistent w/ `PeerInfo.lag`) |
| `ApiStats` | `getErrors` | `readLongOrMinusOne(ERRORS)` | **int ≥0 ⚠️** | **-1** | 0 | ❌ **fix** — *schema-unconfirmed* (inconsistent w/ `total`/`inflight`) |
| `AccountTier` | `getMemoryBytes` | `readLongOrMinusOne(MEMORY)` | **int ≥0 ⚠️** | **-1** | 0 | ❌ **fix** — *schema-unconfirmed* (inconsistent w/ `reserved_*`) |
| `AccountTier` | `getStorageBytes` | `readLongOrMinusOne(STORAGE)` | **int ≥0 ⚠️** | **-1** | 0 | ❌ **fix** — *schema-unconfirmed* (inconsistent w/ `reserved_*`) |
| `ApiStats` | `getTotal` | `readLongOrZero(TOTAL)` | int ≥0 ⚠️ | 0 | 0 | ✅ correct (schema-unconfirmed but already 0) |
| `ApiStats` | `getInFlight` | `readLong(INFLIGHT, 0)` | int ≥0 ⚠️ | 0 | 0 | ✅ correct (schema-unconfirmed but already 0) |
| `AccountTier` | `getReservedMemoryBytes` | `readLong(RESERVED_MEMORY, 0)` | int ≥0 ⚠️ | 0 | 0 | ✅ correct (schema-unconfirmed but already 0) |
| `AccountTier` | `getReservedStorageBytes` | `readLong(RESERVED_STORAGE, 0)` | int ≥0 ⚠️ | 0 | 0 | ✅ correct (schema-unconfirmed but already 0) |
| `PeerInfo`/`Replica` | `getLag` | `readLong(LAG, 0)` | int ≥0 ⚠️ | 0 | 0 | ✅ correct (schema-unconfirmed but already 0) |
| `StreamSource` | `getStartSequence` | `readLong(OPT_START_SEQ, 0)` | uint64 ✅ | 0 | 0 | ✅ correct |
| `ConsumerConfiguration` | `getStartSequence` | `readLong(OPT_START_SEQ, ULONG_UNSET)` | uint64 ✅ | 0 | 0 | ✅ correct |
| `ConsumerConfiguration` | `getRateLimit` | `readLong(RATE_LIMIT_BPS, ULONG_UNSET)` | uint64 ✅ | 0 | 0 | ✅ correct |

`AccountStatistics.getMemory()/getStorage()/...` delegate to a rollup `AccountTier`, so the two `AccountTier` fixes above also correct `AccountStatistics`.

### Schema vs. server — decision points

There is **no case where the schema *contradicts* the server** on signedness (it never declares a uint64 field as signed/`-1`-allowed, nor the reverse). The only divergence is **under-specification**: for the `int ≥0 ⚠️` rows the schema inlines a plain `integer, minimum:0` and so does *not* confirm the field is unsigned. For those, the decision to default to `0` rests on the server alone (plus sibling-consistency), not on the schema.

Of the 11-field fix list, **8 are schema-confirmed uint64** (rows 1–8 — unambiguous, both sources agree → flip to `0`). The remaining **3 are schema-unconfirmed** and are the only ones requiring a judgment call:

| Field | Server | Schema | Sibling fields in same object | Recommendation |
|---|---|---|---|---|
| `ApiStats.getErrors` | `uint64` | `int ≥0` (not tagged) | `total`, `inflight` already default `0` | `0` |
| `AccountTier.getMemoryBytes` | `uint64` | `int ≥0` (not tagged) | `reserved_memory`, `reserved_storage` already default `0` | `0` |
| `AccountTier.getStorageBytes` | `uint64` | `int ≥0` (not tagged) | `reserved_memory`, `reserved_storage` already default `0` | `0` |

In all three the server type is `uint64` and the *neighbouring* fields in the very same object already default to `0`, so `0` is the consistent and recommended choice — but since the schema doesn't independently confirm it, these are the rows to sign off on. (The under-specification is itself a schema defect; see `SCHEMA_VS_SERVER_AUDIT.md` finding **B**.)

### The fix list (11 fields)

Swap `readLongOrMinusOne(...)` → `readLongOrZero(...)` (the helper already exists in `ApiUtils`):

1. `StreamState.getMessageCount` — `MESSAGES`
2. `StreamState.getByteCount` — `BYTES`
3. `StreamState.getFirstSequence` — `FIRST_SEQ`
4. `StreamState.getLastSequence` — `LAST_SEQ`
5. `ConsumerInfo.getNumPending` — `NUM_PENDING`
6. `SequenceInfo.getConsumerSequence` — `CONSUMER_SEQ`
7. `SequenceInfo.getStreamSequence` — `STREAM_SEQ`
8. `StreamSourceInfo.getLag` — `LAG`
9. `ApiStats.getErrors` — `ERRORS`
10. `AccountTier.getMemoryBytes` — `MEMORY`
11. `AccountTier.getStorageBytes` — `STORAGE`

The most telling evidence these are bugs (not deliberate): in three objects the *sibling* uint64
fields already use `0` while one stray field uses `-1` — `ApiStats` (`total`/`inflight`=0 but
`errors`=-1), `AccountTier` (`reserved_*`=0 but `memory`/`storage`=-1), and `lag` (0 in `PeerInfo`,
-1 in `StreamSourceInfo`).

---

## Part D — Caution: two callers read the sentinel

Before flipping `SequenceInfo`/`ConsumerInfo` defaults, check these:

- **`SequenceInfo.EMPTY`** and **`ConsumerInfo.getCalculatedPending()`** — calculated pending is
  `num_pending + delivered.consumer_seq`. Today an error/empty consumer yields `-1 + -1 = -2`; after
  the fix it yields `0`. `0` is the more defensible answer, but confirm nothing downstream switches
  on the negative result. (`SequenceInfo.EMPTY` is also asserted in `ApiFieldsTest.testConsumerInfo`.)

These are correctness-improving changes, but they shift observable values, so they belong with a
test update rather than a silent swap.

---

## Part E — uint64 fields where `-1` is intentional (leave alone)

| Java object | field | schema | why `-1`/other is deliberate |
|---|---|---|---|
| `PublishAck` | `seq` | — (n/a) | Required field. `-1` is a *validation* sentinel — a missing/negative `seq` throws `IOException`. Not a value default. `PubAck` has no schema definition (it's a publish response, not in `definitions.json`), so the server is the only source. |
| `PublishAck` | `count` (`batchSize`) | — (n/a) | `omitempty` → absent when not a batch. `-1` distinguishes "not batched" from "batch of 0". Semantic, keep. |
| `StreamConfiguration` | `first_seq` | uint64 ✅ (weakened) | `uint64`, but default is **1** (NATS streams start at seq 1), not 0 or -1. Intentional. Schema `$ref`s `golang_uint64` but a redundant sibling `type:integer` effectively overrides it (see `SCHEMA_VS_SERVER_AUDIT.md` finding **E**). |
| `LostStreamData` | `bytes` | uint64 ✅ | Returned as a nullable `Long` (null when absent), not a numeric sentinel. Acceptable. |

---

## Part F — Secondary finding (NOT a uint64 issue, but adjacent)

`AccountLimits` is the place where `-1` is *correct* (signed int64, `-1` = unlimited) — its
`max_memory/max_storage/max_streams/max_consumers` read `readLongOrMinusOne` and that's right.

But two of its fields read with the **wrong** default in the other direction:
`memory_max_stream_bytes` and `storage_max_stream_bytes` (and `max_ack_pending`) are read with
default `0`, while the **schema** declares `"minimum": -1, "default": -1`. These are signed
"unlimited = -1" fields, so absent should arguably be `-1`, not `0`. This is *out of scope for the
ulong audit* (they are not unsigned), but flagging it since it's the mirror-image inconsistency and
sits in the same files.

---

# Implementation plan

> **STATUS: IMPLEMENTED** (jnats-json `3.0.10-SNAPSHOT` published with the unsigned helpers).
> All getters in the inventory now route through `readUnsignedLongOrZero`, each value field has an
> `getXxxAsBigInteger()` companion, and javadoc states the unsigned contract. The breaking
> `ApiFieldsTest` assertion (`empty.getMessageCount()` `-1`→`0`) was updated and a top-half
> round-trip test added.
>
> **Scope notes from implementation:**
> 1. **`MessageInfo` (impl/) — now handled** (was a gap in the Part B inventory). Its `seq`,
>    `lastSeq`, and `numPending` are uint64, so the JSON parse was changed `readLong`→`readUnsignedLong`
>    (the header path's `safeParseLong` already falls back to `Long.parseUnsignedLong`, so it was
>    already full-range), and `getSequenceAsBigInteger()` / `getLastSequenceAsBigInteger()` /
>    `getNumPendingAsBigInteger()` were added. **The `-1` "not known" sentinel was kept** (unlike the
>    `0`-default API objects) because a direct-get absent field is a genuine "not known" state — the
>    `BigInteger` getters preserve `-1` for that case (`-1` only; all other values read full-range
>    unsigned).
> 2. **`PublishAck.getBatchSize()` (`count`) — now unsigned `long`.** The server type is `uint64`
>    (`stream.go:267` `BatchSize uint64 json:"count"`), but the client read it into a Java `int`. Fixed:
>    the field is now `long`, read via `readUnsignedLong(COUNT, -1)` (keeping `-1` = "not a batch
>    publish"), with a `getBatchSizeAsBigInteger()` companion. `getBatchSize()`'s return type changed
>    `int`→`long` (source-compatible for callers; binary-incompatible signature). `PublishAck.seq` is
>    routed through `readUnsignedLong` (keeping its `-1` sentinel) and also got a
>    `getSequenceNumberAsBigInteger()` companion — so PublishAck is now fully unsigned like the rest.

> Supersedes the original 11-field "swap `readLongOrMinusOne` → `readLongOrZero`" fix list above
> (Part C). That swap fixes only the **default** (`0` not `-1`); it does **not** fix *reading* a
> top-half value (≥ 2⁶³). The plan below does both, and adds a `BigInteger` escape hatch.

## Why the simple swap is not enough — the parse gap

`readLongOrZero` resolves through `LazyJsonValue::getLong`, and `getLong()` returns **`null`** for a
`BIG_INTEGER`. The lazy parser (`AbstractIndexedJsonValue.ensureIntegerOnly`) accumulates into a
signed `long` and, on overflow at 2⁶³, **falls back to `BigInteger`**. So any uint64 in the top half
of the range (`2⁶³ … 2⁶⁴−1`) currently reads back as the *default*, silently — the actual value is
discarded. For real NATS data this never fires (sequences/counts/bytes stay far below 2⁶³), but it
is a genuine correctness gap and it is the whole reason we read via `getNumber().longValue()` instead
of `getLong()`:

- `Integer`/`Long` → `longValue()` is the value, trivially.
- `BigInteger` (parser top-half) → `longValue()` returns the **low 64 bits** = the correct
  two's-complement bit pattern for that uint64 (reads as a *negative* `long`; see Part C-adjacent
  analysis: uint64 max `2⁶⁴−1` ⟹ `-1`).

This means the `long` getters cover the full 0…2⁶⁴−1 range bit-for-bit; only the *interpretation* of
the top half is the caller's responsibility (hence the `BigInteger` parallel and the javadoc, below).

## Dependency — library helpers already exist; blocked only on publish

The `long` and `BigInteger` reads both come from helpers in jnats-json —
`LazyJsonValueUtils.readUnsignedLong(...)` and `readUnsignedBigInteger(...)`. **These are now
implemented** (jnats-json commit `5f83371` "Unsigned Number Helpers", 2026-06-10) as part of version
**`3.0.10`**, with the exact signatures this plan assumes:

```java
// io.nats.json.LazyJsonValueUtils — all four overloads exist
public static Long       readUnsignedLong(LazyJsonValue jv, String key);
public static long       readUnsignedLong(LazyJsonValue jv, String key, long dflt);
public static BigInteger readUnsignedBigInteger(LazyJsonValue jv, String key);
public static BigInteger readUnsignedBigInteger(LazyJsonValue jv, String key, BigInteger dflt);

// io.nats.json.LazyJsonValue (via AbstractIndexedJsonValue) — backing accessors
public Long       getUnsignedLong();        // null if absent/non-integral; low 64 bits for BIG_INTEGER
public BigInteger getUnsignedBigInteger();  // non-negative 0..2^64-1; null if absent/non-integral
```

`getUnsignedLong()` returns `null` for an absent/non-integral value and the default-bearing
`readUnsignedLong(jv, key, dflt)` substitutes `dflt` — so the client wrappers below are a direct fit.

**Status: blocked on publish, not on code.** `core/build.gradle:17` already declares
`io.nats:jnats-json-jdk21:3.0.10-SNAPSHOT`; once that snapshot (with `5f83371`) is published to the
repo, this client plan compiles against it unchanged. Do **not** start the client edits until the
snapshot is published and resolvable — per the working agreement, all changes here must compile on
first build. (The earlier "inline `getNumber().longValue()` as a fallback" option is moot now that the
real helpers exist — drop it.)

## Item 1 — route every uint64 getter through the unsigned read

Add to client `ApiUtils` (thin wrappers over the new library helpers, default `0` = `ULONG_UNSET`):

```java
public static long readUnsignedLongOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
    return io.nats.json.LazyJsonValueUtils.readUnsignedLong(ljv, key, 0L);
}
public static BigInteger readUnsignedBigIntegerOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
    return io.nats.json.LazyJsonValueUtils.readUnsignedBigInteger(ljv, key, BigInteger.ZERO);
}
```

Then convert each uint64 getter. The complete inventory (every JSON-serialized uint64 from Part B,
with the change for each):

| Java object | `long` getter | key | read change | default change |
|---|---|---|---|---|
| `StreamState` | `getMessageCount` | `MESSAGES` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `StreamState` | `getByteCount` | `BYTES` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `StreamState` | `getFirstSequence` | `FIRST_SEQ` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `StreamState` | `getLastSequence` | `LAST_SEQ` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `ConsumerInfo` | `getNumPending` | `NUM_PENDING` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `SequenceInfo` | `getConsumerSequence` | `CONSUMER_SEQ` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `SequenceInfo` | `getStreamSequence` | `STREAM_SEQ` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `StreamSourceInfo` | `getLag` | `LAG` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `ApiStats` | `getErrors` | `ERRORS` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `ApiStats` | `getTotal` | `TOTAL` | `readLongOrZero` → `readUnsignedLongOrZero` | 0 (same) |
| `ApiStats` | `getInFlight` | `INFLIGHT` | `readLong(...,0)` → `readUnsignedLongOrZero` | 0 (same) |
| `AccountTier` | `getMemoryBytes` | `MEMORY` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `AccountTier` | `getStorageBytes` | `STORAGE` | `readLongOrMinusOne` → `readUnsignedLongOrZero` | -1 → **0** |
| `AccountTier` | `getReservedMemoryBytes` | `RESERVED_MEMORY` | `readLong(...,0)` → `readUnsignedLongOrZero` | 0 (same) |
| `AccountTier` | `getReservedStorageBytes` | `RESERVED_STORAGE` | `readLong(...,0)` → `readUnsignedLongOrZero` | 0 (same) |
| `PeerInfo`/`Replica` | `getLag` | `LAG` | `readLong(...,0)` → `readUnsignedLongOrZero` | 0 (same) |
| `StreamSource` | `getStartSequence` | `OPT_START_SEQ` | `readLong(...,0)` → `readUnsignedLongOrZero` | 0 (same) |
| `ConsumerConfiguration` | `getStartSequence` | `OPT_START_SEQ` | `readLong(..., ULONG_UNSET)` → `readUnsignedLongOrZero` | 0 (same) |
| `ConsumerConfiguration` | `getRateLimit` | `RATE_LIMIT_BPS` | `readLong(..., ULONG_UNSET)` → `readUnsignedLongOrZero` | 0 (same) |

The 11 `-1 → 0` rows are the behavior change; the rest already default `0` but still need the
**read** change so a top-half value isn't silently dropped.

### Special-handling uint64 fields — apply the unsigned *read*, keep the special default/semantics

| Java object | getter | key | what to do |
|---|---|---|---|
| `StreamConfiguration` | `getFirstSequence` | `FIRST_SEQ` | uint64, but absent-default is **1** (streams start at seq 1), not 0. Switch the *read* to unsigned (`readUnsignedLong(ljv, FIRST_SEQ, 1L)`) but keep default `1`. |
| `LostStreamData` | `getBytes` | `BYTES` | Returns nullable `Long` (null when absent), not a sentinel. Switch to nullable `readUnsignedLong(ljv, BYTES)` (no default). |
| `PublishAck` | `getSequenceNumber` | `SEQ` | uint64, but `-1` is a *validation* sentinel (missing/negative `seq` throws). Read via unsigned so a top-half seq isn't misread as null→`-1`→spurious throw; keep `-1`-means-absent validation. |
| `PublishAck` | `getBatchSize` (`count`) | (count) | `omitempty`; `-1` = "not batched" (semantic, keep). Read via unsigned, keep `-1` default. |

## Item 2 — parallel `BigInteger` getter on each uint64 field

For every getter in the Item-1 inventory, add a sibling that returns the **true non-negative
unsigned value** as `BigInteger`. Naming convention (decided): **`getXxxAsBigInteger()`**.

```java
// in StreamState, alongside getMessageCount()
public BigInteger getMessageCountAsBigInteger() {
    return readUnsignedBigIntegerOrZero(ljv, MESSAGES);
}
```

`readUnsignedBigInteger` returns a value in `0 … 2⁶⁴−1` with no sign ambiguity, so this is the getter
a caller reaches for when a field *could* be in the top half and they don't want to deal with
`Long.*Unsigned*`. Defaults mirror the `long` getter (`BigInteger.ZERO`; for `StreamConfiguration`
`first_seq` use `BigInteger.ONE`; for `LostStreamData.bytes` return nullable `BigInteger`).

Scope note: add the `BigInteger` parallel to the **value fields** (`StreamState`, `ConsumerInfo`,
`SequenceInfo`, `StreamSourceInfo`, `ApiStats`, `AccountTier`, `PeerInfo`, `StreamSource`,
`ConsumerConfiguration`, `StreamConfiguration.getFirstSequence`, `LostStreamData`). *(Superseded by
the implementation summary: `PublishAck.seq` and `count` also got companions — the companion returns
the `-1` sentinel for the absent case and the true unsigned value otherwise, so it is **not**
meaningless. "Unsigned everywhere" — every server uint64 has a `long` getter + `BigInteger` companion.)*

## Item 3 — javadoc: make the unsigned contract explicit

Every uint64 `long` getter gets a javadoc line stating it is unsigned and pointing at both the
`Long.*Unsigned*` helpers and the `BigInteger` sibling. Template:

```java
/**
 * The number of messages in the stream.
 * <p>This is an <b>unsigned 64-bit</b> value held in a {@code long}. For NATS this is always far
 * below {@code Long.MAX_VALUE}, but for the theoretical top of the range ({@code > 2^63}) the
 * returned {@code long} reads as <i>negative</i>; interpret it with {@link Long#toUnsignedString(long)}
 * / {@link Long#compareUnsigned(long, long)}, or use {@link #getMessageCountAsBigInteger()} to get the
 * value as a non-negative {@link java.math.BigInteger}.
 * @return the message count (unsigned)
 */
```

The `BigInteger` getter's javadoc states it returns the non-negative unsigned value and is the
allocation-heavier alternative to the `long` getter.

## Test impact (carry over from Part D)

- `SequenceInfo.EMPTY` and `ConsumerInfo.getCalculatedPending()` shift from `-1`/`-2` to `0` — update
  `ApiFieldsTest.testConsumerInfo` and any assertion on the empty/error consumer.
- Add a top-half round-trip test per object: parse a JSON value of `18446744073709551615`
  (`2⁶⁴−1`) and assert the `long` getter returns `-1` **and** the `BigInteger` getter returns
  `2⁶⁴−1`. This is the test that would have caught the silent-drop gap.

---

# Implementation summary (done — come back to the open items below)

**Core change** — every uint64 getter in the inventory now reads the **full `0…2⁶⁴−1` range** (via
the published jnats-json `readUnsignedLong`/`readUnsignedBigInteger`) instead of `getLong()`, which
silently dropped any value ≥ 2⁶³ to the default. Defaults that were `-1` are now `0` (`1` for
`StreamConfiguration.first_seq`).

**25 new `getXxxAsBigInteger()` companions** across 12 classes, each returning the non-negative
unsigned value:

| Class | uint64 getters converted (+ BigInteger companion) |
|---|---|
| `StreamState` | messages, bytes, first_seq, last_seq |
| `ConsumerInfo` | num_pending |
| `SequenceInfo` | consumer_seq, stream_seq |
| `StreamSourceInfo` | lag |
| `ApiStats` | total, errors, inflight |
| `AccountTier` | memory, storage, reserved_memory, reserved_storage |
| `AccountStatistics` | memory, storage, reserved_memory, reserved_storage (delegating) |
| `PeerInfo` | lag |
| `StreamSource` | opt_start_seq |
| `ConsumerConfiguration` | opt_start_seq, rate_limit_bps |
| `StreamConfiguration` | first_seq (default kept at 1) |
| `LostStreamData` | bytes (nullable) |
| `PublishAck` | seq, count/batchSize (both unsigned `long`, kept `-1` sentinel, both with companions; `getBatchSize()` `int`→`long`) |

**Javadoc** — every long getter now states it's unsigned and points at
`Long.toUnsignedString`/`compareUnsigned` and its `BigInteger` companion. **`ApiUtils`** got
`readUnsignedLongOrZero` / `readUnsignedBigIntegerOrZero` wrappers.

**Tests** — fixed the one breaking assertion (`ApiFieldsTest`: `empty.getMessageCount()` `-1`→`0`)
and added **top-half round-trip tests**:
- `ApiFieldsTest` — `StreamState messages=2⁶⁴−1` returns `-1` as a long but the true value via
  `getMessageCountAsBigInteger()`.
- `PublishAckTests.testUnsignedFields` — `count=2⁶³` round-trips (`Long.MIN_VALUE` long /
  `2⁶³` BigInteger); absent `count` → `-1`; the `count=2⁶⁴−1`↔`-1` sentinel collision is asserted as
  a documented edge; a top-half `seq` is asserted to throw (the `seq<0` validation).
- `MessageInfoTest.testUnsignedSequences` — top-half `seq` via the JSON path (`readUnsignedLong`) and
  `seq`/`lastSeq` via the direct-header path (`safeParseLong`→`parseUnsignedLong`), plus the `-1`
  "not known" sentinel preserved by the `BigInteger` companions.

(SequenceInfo/ApiStats/Replica empty assertions already expected `0` in the working tree.)

## Open items to revisit

1. **`MessageInfo` (impl/) — RESOLVED.** `seq` / `lastSeq` / `numPending` now parse as full-range
   uint64 (`readLong`→`readUnsignedLong`; `safeParseLong` already handled it via its
   `parseUnsignedLong` fallback) and have `getXxxAsBigInteger()` companions. The `-1` "not known"
   sentinel was deliberately **kept** (a direct-get absent field is a real "not known" state, not a
   `0`); the `BigInteger` getters return `-1` only for that sentinel and the true unsigned value
   otherwise.

2. **`PublishAck.getBatchSize()` (`count`) — RESOLVED.** Server type is `uint64`; the client was
   reading it into a Java `int`. Now `long` via `readUnsignedLong(COUNT, -1)` (keeping `-1` = "not a
   batch publish") with a `getBatchSizeAsBigInteger()` companion. `getBatchSize()` return type
   changed `int`→`long` (binary-incompatible signature; source-compatible for callers). `seq` also
   got a `getSequenceNumberAsBigInteger()` companion — PublishAck is now fully unsigned.

3. **Build not run** (no gradle per working agreement) — imports/symbols verified statically only.
   Run `gradle compileJava test` against the published `3.0.10-SNAPSHOT` to confirm.
