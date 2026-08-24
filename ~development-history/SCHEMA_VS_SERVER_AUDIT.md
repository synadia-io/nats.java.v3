# Schema vs. Server audit

A direct comparison of the JSON schema against the server Go structs. The question: where does `definitions.json` disagree with, or fail to faithfully describe, what the server actually serializes?

## Sources

- **Server (truth):** `stream.go`, `consumer.go`, `jetstream.go`, `store.go`, `jetstream_cluster.go` (`Placement`).
- **Schema:** `schema_source/jetstream/api/v1/definitions.json`.

## Bottom line

The schema is **structurally accurate for almost every field** — names, nesting, and signed-int limits all line up. Five classes of problem remain:

| | Finding | Severity |
|---|---|---|
| **A** | Two genuinely missing fields (server emits, schema omits) | Material |
| **B** | Systemic under-tagging of `uint64` | Systemic |
| **C** | A few over-strict `required` lists | Minor |
| **D** | Timestamp / duration representation drift | Cosmetic |
| **E** | Invalid-JSON / schema smells | Mixed |

In every discrepancy below, **the server is correct** and the schema is what needs fixing.

---

## A. Missing fields — schema does not describe what the server emits *(material)*

| Definition | Field server emits | Server origin | Notes |
|---|---|---|---|
| `account_stats` | **`reserved_memory`** | `JetStreamAccountStats` embeds `JetStreamTier.ReservedMemory uint64` | Not defined in schema. |
| `account_stats` | **`reserved_storage`** | embeds `JetStreamTier.ReservedStore uint64` | Not defined in schema. |
| `consumer_configuration` | **`sourcing`** | `ConsumerConfig.Sourcing bool \`json:"sourcing,omitempty"\`` | Not defined in schema. |

`account_stats` is `JetStreamAccountStats`, which **embeds `JetStreamTier` inline** (no json tag, so the fields are promoted). The wire object therefore carries all six tier fields (`memory`, `storage`, `reserved_memory`, `reserved_storage`, `streams`, `consumers`) plus `limits`, but the schema's `account_stats` only lists `memory`, `storage`, `streams`, `consumers`, `limits` (plus `domain`, `api`, `tiers`) — the two `reserved_*` are dropped. Because `account_stats` does **not** set `additionalProperties: false`, these are silently *allowed*, just undocumented. The same is true for `sourcing` on `consumer_configuration`.

> **Root cause:** `account_stats` hand-copies the tier fields instead of `$ref`-ing the `tier` definition. That manual duplication is exactly how the two `reserved_*` got dropped — a `$ref` to `tier` would have stayed in sync.

---

## B. `uint64` under-tagging — schema can't be machine-read for unsigned-ness *(systemic)*

The schema has a precise marker, `golang_uint64` (`type:integer, minimum:0, maximum:1.8e19`), but uses it **inconsistently**. For some `uint64` server fields it `$ref`s the marker; for others it inlines a bare `type:integer, minimum:0`, which is indistinguishable from a signed count that happens to be ≥0 (e.g. `num_subjects`, `streams`, `consumers` are all `golang_int` with `minimum:0`).

| Server `uint64` field | Schema definition | Tagged `golang_uint64`? |
|---|---|---|
| `stream_state.messages/bytes/first_seq/last_seq` | `stream_state` | ✅ ref |
| `stream_state.subjects` (values), `deleted` (items) | `stream_state` | ✅ ref |
| `sequence_info.consumer_seq/stream_seq` | `sequence_info` | ✅ ref |
| `consumer_info.num_pending` | `consumer_info` | ✅ ref |
| `consumer_configuration.opt_start_seq/rate_limit_bps` | `consumer_configuration` | ✅ ref |
| `stream_source.opt_start_seq` | `stream_source` | ✅ ref |
| `stream_source_info.lag` | `stream_source_info` | ✅ ref |
| `stream_configuration.first_seq` | `stream_configuration` | ✅ ref |
| `lost_stream_data.bytes` (+ `msgs[]`) | `lost_stream_data` | ✅ ref |
| `stored_message.seq` | `stored_message` | ✅ ref |
| **`api_stats.total/errors/inflight`** | `api_stats` | ❌ inline `integer, minimum:0` |
| **`tier.memory/storage/reserved_memory/reserved_storage`** | `tier` | ❌ inline `integer, minimum:0` |
| **`account_stats.memory/storage`** | `account_stats` | ❌ inline `integer, minimum:0` |
| **`peer_info.lag`** | `peer_info` | ❌ inline `integer, minimum:0` |

A generator that keys off the `golang_uint64` `$ref` silently misses `api_stats`, `tier`, `account_stats`, and `peer_info.lag`. A generator that keys off `minimum:0` over-matches the signed counts. Neither heuristic is correct against this schema — only the server is.

---

## C. Over-strict `required` — schema requires a field the server marks `omitempty`

| Definition | Schema requires | Server tag | Consequence |
|---|---|---|---|
| `consumer_info` | `config` | `Config *ConsumerConfig \`json:"config,omitempty"\`` | A consumer-info with no config (error responses) is legal on the wire but fails schema validation. |
| `republish` | `src` | `Source string \`json:"src,omitempty"\`` | A republish with an empty source omits `src`; schema would reject it. |

Conversely, several fields the server *always* emits — `consumer_info.ts`, `api_stats.level`, `tier.reserved_*` — are **not** in `required`. Harmless, but it confirms the `required` lists are not derived from the `omitempty` tags in either direction.

---

## D. Timestamp / duration representation drift *(cosmetic)*

Most timestamps use `golang_time` (`type:string, format:date-time`) and most nanosecond durations use `golang_duration_nanos`. A few don't:

| Field | Server type | Schema says | Should be |
|---|---|---|---|
| `stream_state.first_ts` / `last_ts` | `time.Time` | `type:string` (no `format`) | `golang_time` |
| `stored_message.time` | (string time) | `type:string` (no `format`) | `golang_time` |
| `peer_info.active` | `time.Duration` (nanos) | `type:number` | `golang_duration_nanos` |

Purely cosmetic for parsing, but it means a tool can't uniformly find "timestamp" or "duration" fields by `$ref`.

---

## E. Invalid-JSON and schema smells

- **Trailing commas (invalid under draft-07).** In `consumer_configuration`, the `max_expires` and `inactive_threshold` property objects end with `"default": 0,` before `}` (~lines 848–849, 859–860); and the `stream_configuration` definition object has a trailing comma before its closing brace (~line 1310). A strict JSON parser rejects the file as-is.
- **`minimum` on a string.** `peer_info.name` declares `"minimum": 1` (line ~212); `minimum` is a numeric keyword, so this should be `minLength`. It's a no-op as written.
- **Redundant `type` + `$ref` on one property.** `stream_configuration.first_seq` has both `"type":"integer"` and `"$ref":golang_uint64` (lines ~1184–1185); `consumer_configuration.num_replicas` has both `"type":"integer"` and `"$ref":golang_int` (lines ~868–871). Under draft-07 a sibling `$ref` is typically ignored, so the inline `type` silently wins — which for `first_seq` drops the `minimum:0/maximum` of the uint64 ref.
- **Inconsistent `$ref` form.** `consumer_configuration.max_bytes` refs `"definitions.json#/definitions/golang_int"` (self-qualified URL) while every other ref in the file uses the local `"#/definitions/..."` form (line ~852).

---

## Appendix — per-definition reconciliation

✅ = schema matches server faithfully.   ⚠️ = discrepancy (see findings above).

| Schema definition | Server struct (file) | Status |
|---|---|---|
| `stream_configuration` | `StreamConfig` (stream.go) | ✅ all ~38 fields present; ⚠️ E (first_seq redundant type) |
| `consumer_configuration` | `ConsumerConfig` (consumer.go) | ⚠️ A (`sourcing` missing); ⚠️ E (trailing commas, max_bytes ref, num_replicas redundant type) |
| `stream_state` | `StreamState` (store.go) | ✅ ⚠️ D (first_ts/last_ts) |
| `consumer_info` | `ConsumerInfo` (consumer.go) | ⚠️ C (`config` over-required) |
| `stream_info` | `StreamInfo` (stream.go) | ✅ |
| `sequence_info` / `sequence_pair` | `SequenceInfo` (consumer.go) | ✅ |
| `account_stats` | `JetStreamAccountStats` (jetstream.go) | ⚠️ A (`reserved_memory`/`reserved_storage` missing); ⚠️ B |
| `tier` | `JetStreamTier` (jetstream.go) | ⚠️ B (uint64 under-tagged) |
| `account_limits` | `JetStreamAccountLimits` (jetstream.go) | ✅ all 8 fields, signed `min -1` correct, `additionalProperties:false` matches |
| `api_stats` | `JetStreamAPIStats` (jetstream.go) | ⚠️ B (total/errors/inflight under-tagged) |
| `peer_info` | `PeerInfo` (stream.go) | ⚠️ B (lag), ⚠️ D (active), ⚠️ E (name `minimum`) |
| `cluster_info` | `ClusterInfo` (stream.go) | ✅ |
| `stream_source` | `StreamSource` (stream.go) | ✅ (server-only `iname` is unexported, correctly absent) |
| `stream_source_info` | `StreamSourceInfo` (stream.go) | ✅ (lag correctly ref'd; active=-1 documented) |
| `external_stream_source` | `ExternalStream` (stream.go) | ✅ |
| `stream_consumer_limits` | `StreamConsumerLimits` (stream.go) | ✅ |
| `subject_transform` | `SubjectTransformConfig` (stream.go) | ✅ |
| `republish` | `RePublish` (stream.go) | ⚠️ C (`src` over-required) |
| `placement` | `Placement` (jetstream_cluster.go) | ✅ |
| `lost_stream_data` | `LostStreamData` (store.go) | ✅ |
| `stream_alternate` | `StreamAlternate` (stream.go) | ✅ |
| `priority_group_state` | `PriorityGroupState` (consumer.go) | ✅ |
| `stored_message` | `StoredMsg` (server, direct-get) | ✅ ⚠️ D (`time`) |
| `api_error` | `ApiError` (server) | ✅ |
| `stream_consumer_source` | `StreamConsumerSource` (stream.go) | ✅ |

Definitions with no server struct (request/response envelopes and policy enums) are out of scope here: `iterable_request`, `iterable_response`, `error_response`, `deliver_policy` (plus the six `*_deliver_policy` variants), `priority_policy`, `basic_name`, and the `golang_*` primitives.

---

## Suggested schema corrections

The server is correct in every case; these changes bring the schema into line.

1. **[A]** Add `reserved_memory` and `reserved_storage` to `account_stats` (or make it `allOf` the `tier` definition plus `domain`/`api`/`tiers`).
2. **[A]** Add `sourcing` (boolean) to `consumer_configuration`.
3. **[B]** Replace the inline `type:integer, minimum:0` with `$ref: golang_uint64` for `api_stats.total/errors/inflight`, `tier.*` / `account_stats.memory/storage`, and `peer_info.lag`.
4. **[C]** Drop `config` from `consumer_info.required` and `src` from `republish.required`.
5. **[D]** Point `stream_state.first_ts/last_ts` and `stored_message.time` at `golang_time`, and `peer_info.active` at `golang_duration_nanos`.
6. **[E]** Remove the trailing commas; change `peer_info.name`'s `minimum` to `minLength`; drop the redundant `type` siblings on `first_seq` / `num_replicas`; normalize the `max_bytes` `$ref`.
