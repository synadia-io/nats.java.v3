# Audit: core classes used only by the jetstream module

**Question:** which types defined in `core/src/main` are referenced by `jetstream/src/main` but by **nothing** in `core/src/main`? Those are candidates to move into the jetstream module (they don't belong to core's own surface).

**Method:** for every `*.java` in `core/src/main`, whole-word grep its type name across `core/src/main` (excluding its own file) and across `jetstream/src/main`. Flag it when core-main usages == 0 and jetstream-main usages > 0. Core *test* usage is reported separately (a mover's tests move with it).

*Caveats:* (1) it's a token match, so a mention of the name in a core string/comment counts as "used in core" — that's **conservative** (it can hide a candidate, never invent one). (2) It's **direct** usage only — it does not follow transitive chains (a core class used only by another core class that is itself jetstream-only). See the note at the end.

## Candidates — core types with zero core-main usage, used only by jetstream

| Type | Defined in | jetstream-main consumers | core test | Recommendation |
|---|---|---|---|---|
| `MessageTtl` | `client/MessageTtl.java` | `PublishOptions`, `KeyValue`, `KeyValueUtils` | none | Move to jetstream. It's a JetStream/KV publish-TTL value with no core footprint. |
| `Digester` | `client/utils/Digester.java` | `os/ObjectStore` | `utils/DigesterTests` | Move to jetstream (Object Store digest helper). Move `DigesterTests` with it. |
| `JetStreamClientError` | `client/utils/JetStreamClientError.java` | `api/ConsumerCreator`, `impl/JetStream`, `impl/JetStreamImpl`, `os/ObjectStore`, `utils/JsValidator` (5) | `utils/ValidatorTests` | Move to jetstream. It's the JetStream error catalog; `JsValidator` (already in jetstream) is a primary consumer. The `JetStreamClientError` cases in the core `ValidatorTests` need to move to a jetstream test. |

All three have **zero** core references — not even in strings/comments — so they're clean movers.

## Why the other JetStream-flavored core classes did NOT qualify

Several types *sound* like they belong to jetstream but are genuinely used by core, so they correctly stay:

| Type | core-main uses | jetstream uses | why it stays in core |
|---|---|---|---|
| `ApiConstants` | 2 (`api/ServerInfo`, `utils/ApiUtils`) | 58 | `ServerInfo` parses the server `INFO` handshake (core connection infra) using these JSON field names, so core depends on it transitively. |
| `ApiUtils` | 1 | 19 | same cluster — pulled in by `ServerInfo`. |
| `ServerInfo` | 2 | 2 | used by the core connection handshake; it's core. |
| `Status` | 11 | 8 | core protocol status; used all over core. |
| `Consumer` | 15 | 16 | the base consumer interface — heavily used by both. |
| `AckType`, `ConsumerMessageQueue`, `InternalPublishableMessage`, `JetStreamMessage`, `JetStreamMetaData`, `NatsConsumer`, `WebsocketInputStream/OutputStream` | ≥2 each | 0–1 | used within core; not jetstream-only. |

So the `Api*`/`ServerInfo` cluster is a shared dependency (core needs it for the INFO handshake, jetstream needs it for API calls) — not a candidate. That's the correct outcome for a token that "looks jetstream."

## Recommendation & follow-ups

- **Move the three:** `MessageTtl`, `Digester`, `JetStreamClientError` (plus `DigesterTests` and the `JetStreamClientError` portion of `ValidatorTests`) into the jetstream module. This aligns with the intended one-way dependency (jetstream → core, never the reverse).
- **Transitive pass (optional, deeper):** this audit is direct-usage only. A fuller pass would iterate — after removing/moving classes, re-run to catch core types that become jetstream-only once their only core consumer moves. With only three movers (and none consumed by each other in core), a second iteration is unlikely to surface much, but it's the rigorous way to finish.
- **Verify before moving:** confirm none of the three are referenced by class-name **string** anywhere (reflection/properties) — the grep would have shown a core mention, but jetstream/tests should be re-checked for string references if you relocate packages.

---

## Member-level inventory: `ApiUtils` (candidate for splitting into `JetStreamApiUtils`)

`ApiUtils` (`core/.../utils/ApiUtils.java`) splits by parameter type: **every method that takes a `LazyJsonValue`** — core's JSON-parse type — **stays in core**. `ServerInfo` (the INFO-handshake parser) already uses `readString(LazyJsonValue,…)`, and the rest of the lazy readers are the same core JSON infrastructure. `randomString()` also stays. Only the non-`LazyJsonValue` helpers — the three `UNSET` constants and the `normalize*` family — move to `JetStreamApiUtils`.

Usage columns: **core-main** (excluding `ApiUtils.java` itself) / **core-test** = `ApiUtilsTests` / **js-main** = jetstream/src/main files referencing it.

| Member | core-main | core-test | js-main | Verdict |
|---|---|---|---|---|
| ~~`DURATION_UNSET` (const)~~ | — | ✓ | 1 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`UNSET` (const)~~ | — | ✓ | 6 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`ULONG_UNSET` (const)~~ | — | ✓ | 3 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`JV_NAME_UNDEFINED` (private field)~~ | — | — | — | ✅ **deleted** |
| `randomString()` | — | ✓ | 1 | **KEEP in core** (leave as is) |
| ~~`orEmpty(List)`~~ | — | ✓(test only) | 0 | ✅ **deleted** |
| `mapToList(LazyJsonValue, Function)` | — | ✓ | 6 | **KEEP in core** (LazyJsonValue) |
| ~~`copyOrNull(List)` · `copyOrNull(Map)`~~ | — | ✓(test only) | 0 | ✅ **deleted** |
| ~~`copyOrEmpty(List)` · `copyOrEmpty(Map)`~~ | — | ✓(test only) | 0 | ✅ **deleted** |
| ~~`normalizeLong(Long,long)`~~ | — | ✓ | 3 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`normalizeInt(Integer,int)`~~ | — | ✓ | 1 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`normalizeULong(Long)`~~ | — | ✓ | 1 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`normalizeDuration(Duration,Duration)` · `normalizeDuration(Long,Duration)`~~ | — | ✓ | 2 | ✅ **moved** to `JetStreamApiUtils` |
| ~~`jvNameUndefined()`~~ | — | ✓(test only) | 0 | ✅ **deleted** |
| `readString(LazyJsonValue,String,String)` | **1 (`ServerInfo`)** | ✓ | many | **KEEP in core** (LazyJsonValue; also `ServerInfo`) |
| ~~`readString(JsonValue,String,String)`~~ | — | ✓ | 0 | ✅ **deleted** (dead — no eager-`JsonValue` caller) |
| ~~`readBoolean(JsonValue,String,boolean)`~~ | — | ✓ | 0 | ✅ **deleted** (dead) |
| ~~`readInteger(JsonValue,String,int)`~~ | — | ✓ | 0 | ✅ **deleted** (dead) |
| ~~`readLong(JsonValue,String,long)`~~ | — | ✓ | 0 | ✅ **deleted** (dead) |
| `readStringOrEmpty(LazyJsonValue,String)` | — | ✓ | 10 | **KEEP in core** (LazyJsonValue) |
| `readLongOrMinusOne(LazyJsonValue,String)` | — | ✓ | 5 | **KEEP in core** (LazyJsonValue) |
| ~~`readLongOrZero(LazyJsonValue,String)`~~ | — | — | 0 | ✅ **deleted** |
| `readUnsignedLongOrZero(LazyJsonValue,String)` | — | — | 9 | **KEEP in core** (LazyJsonValue) |
| `readUnsignedBigIntegerOrZero(LazyJsonValue,String)` | — | — | 9 | **KEEP in core** (LazyJsonValue) |
| `readIntegerOrMinusOne(LazyJsonValue,String)` | — | ✓ | 1 | **KEEP in core** (LazyJsonValue) |
| `readDurationOrZero(LazyJsonValue,String)` | — | ✓ | 3 | **KEEP in core** (LazyJsonValue) |
| `readDateOrDefault(LazyJsonValue,String)` | — | ✓ | 1 | **KEEP in core** (LazyJsonValue) |

### What this means for the split

- **Keep in core:** every `LazyJsonValue`-based reader — `readString(Lazy)`, `readStringOrEmpty`, `readLongOrMinusOne`, `readUnsignedLongOrZero`, `readUnsignedBigIntegerOrZero`, `readIntegerOrMinusOne`, `readDurationOrZero`, `readDateOrDefault`, `mapToList` — since they operate on `LazyJsonValue` (core's JSON-parse type), plus `randomString()`.
- **Moved ✅ (done):** the non-`LazyJsonValue` helpers — the `DURATION_UNSET` / `UNSET` / `ULONG_UNSET` constants and the `normalize*` family (`normalizeLong`, `normalizeInt`, `normalizeULong`, `normalizeDuration` ×2) — relocated to `JetStreamApiUtils` (which `extends ApiUtils`). `StreamCreator` and `SimplificationTests` repointed from `ApiUtils.*` to `JetStreamApiUtils.*` (all other call sites already imported via `JetStreamApiUtils`, so they were unaffected). The `@Nullable`/`@NullMarked` annotations were added since `JetStreamApiUtils` is `@NullMarked`. The `normalize*`/`constants` test cases moved from `ApiUtilsTests` to `JetStreamApiUtilsTests`; full build + both test classes green.
- **Deleted ✅ (done):** `orEmpty`, `copyOrNull`(×2), `copyOrEmpty`(×2), `jvNameUndefined` (+ `JV_NAME_UNDEFINED`), `readLongOrZero`, and the four `JsonValue`-param readers `readString(JsonValue)` / `readBoolean` / `readInteger` / `readLong`. All had **no main caller anywhere** — every reader call in main resolves to `JsonValueUtils`/`LazyJsonValueUtils`, so the `ApiUtils` `JsonValue` overloads were dead too. Removed from `ApiUtils` and their cases removed from `ApiUtilsTests`; full build + `ApiUtilsTests` green.
- **`ApiUtilsTests` (core test) splits two ways now:** cases for the *move* members go to a jetstream test alongside them; the kept `readString(Lazy)` case stays in core. (Dead-member cases already removed.)

Net (done): the `LazyJsonValue` readers and `randomString()` stay in core; the 8 non-Lazy members (the `UNSET` constants + five `normalize*` methods) were moved to `JetStreamApiUtils`; and the dead members were deleted. `ApiUtils` now carries only its core JSON-parse surface.

---

## Member-level inventory: `ApiConstants` (the JSON-field-name constants)

`ApiConstants` (`core/.../utils/ApiConstants.java`) is an interface of **220** JSON schema-field-name string constants. Its **only** consumer in `core/src/main` is **`ServerInfo`** (the server `INFO`-handshake parser) — that is the entire reason `ApiConstants` "did not qualify" as jetstream-only in the class-level table above. So the constants split cleanly into three buckets.

*Method:* the 220 constant names were classified by whether they appear in the files that actually consume `ApiConstants` — core side = `ServerInfo` only; jetstream side = every `jetstream/src/main` file that references `ApiConstants`. *Caveats:* token match (short names like `GO`/`HOST`/`PORT` could in principle collide, but the match is restricted to `ApiConstants`-consumer files, so a bare match there is a genuine reference); "unused" means no *constant* reference in main source — a field could still be handled via its raw string literal. The 19 "unused" were also spot-checked for zero test usage.

### Core-needed — **19** constants, all consumed by `ServerInfo`; must stay in core

- **Both core & jetstream (2):** `CLUSTER`, `HEADERS` — `ServerInfo` reads them from the server INFO, and jetstream also uses them (stream cluster info / message-header support). On a split these stay in core and jetstream references core's copies.
- **Core-only (17)** — pure server-INFO handshake fields: `AUTH_REQUIRED`, `CLIENT_ID`, `CLIENT_IP`, `CONNECT_URLS`, `GO`, `HOST`, `JETSTREAM`, `LAME_DUCK_MODE`, `MAX_PAYLOAD`, `NONCE`, `PORT`, `PROTO`, `SERVER_ID`, `SERVER_NAME`, `TLS_AVAILABLE`, `TLS_REQUIRED`, `VERSION`.

### JetStream-only — **182** constants; movable

Everything **not** in the two lists above and **not** in the unused list below — i.e. all the stream/consumer/account/API schema fields (`ACK_WAIT`, `BATCH`, `EXPIRES`, `IDLE_HEARTBEAT`, `MAX_AGE`, `NUM_PENDING`, `DELIVER_SUBJECT`, …). These are candidates to move to a `JetStreamApiConstants` (mirroring the `JetStreamApiUtils` split), leaving the 19 core-needed here. (Full 182-name list omitted for length — it's the exact complement of the 19 core-needed + 19 unused; say the word and I'll dump it.)

### Unused in main — **19** constants; delete candidates (no main *or* test reference)

`API_URL`, `AVERAGE_PROCESSING_TIME`, `ENDPOINTS`, `INTERNAL`, `LAST_ERROR`, `MULTI_LAST`, `NUM_ERRORS`, `NUM_REQUESTS`, `PROCESSING_TIME`, `QUEUE_GROUP`, `REPLICA`, `REQUEST`, `RESPONSE`, `SCHEMA`, `SOURCE`, `STARTED`, `STATS`, `UP_TO_SEQ`, `UP_TO_TIME`. None is referenced by the constant name in main or test source. Verify no code reads these fields via a raw string literal before deleting (several — `NUM_REQUESTS`, `NUM_ERRORS`, `PROCESSING_TIME`, `ENDPOINTS`, `QUEUE_GROUP` — are service/monitoring fields, so confirm the service package isn't reading them by literal).

### What this means for the split

- **Keep in core (19):** the `ServerInfo` INFO-handshake fields, including the two shared `CLUSTER`/`HEADERS`.
- **Move (182):** the JetStream schema fields → `JetStreamApiConstants`.
- **Delete (19):** the unused constants, pending the string-literal check above.

So of 220 constants, only **19 anchor `ApiConstants` to core** (via `ServerInfo`); the other 201 are jetstream-only (182) or dead (19).

---

## Why the IDE-flagged "no uses" `ApiConstants` fields exist — V2 / Orbit / server-schema cross-reference

The IDE flags 12 `ApiConstants` fields as having no uses in v3. `ApiConstants` was ported wholesale from v2's `io.nats.client.support.ApiConstants` (the full server-schema catalog), so for each one the question is whether v2, **Orbit** (the jnats extension libs at `C:\nats\orbit.java`), or the **server API schema** (`C:\nats\jsm.go\schema_source`) actually backs the constant.

**3 are Orbit-only → moved to [`ORBIT_CONSTANTS.md`](ORBIT_CONSTANTS.md):** `MULTI_LAST`, `UP_TO_SEQ`, `UP_TO_TIME` — the JetStream direct batch-get fields used by Orbit's `direct-batch/MessageBatchGetRequest`, unused in v3/v2 core. Kept (not deleted) for when Orbit is brought in as a subproject.

**The other 9** are unused in v2 core *and* Orbit (verified line-by-line — the apparent v2 "uses" for `INTERNAL`/`MTIME`/`REQUEST`/`SOURCE`/`STATS` are comments, string literals, or the differently-named `SRV_STATS` constant). To decide keep-vs-delete, checked whether each wire value exists in the **server API schema** (`jsm.go/schema_source`, 88 json files):

| Constant | wire value | in server schema? | where / verdict |
|---|---|---|---|
| `REQUEST` | `request` | ✅ | JetStream **API-audit advisory** (`jetstream/advisory/v1/api_audit.json`) — "full unparsed request body". Real field; not wired in v3 (no advisory parsing). |
| `RESPONSE` | `response` | ✅ | same advisory — "full unparsed response body". Real field; not wired in v3. |
| `STATS` | `stats` | ✅ | **server monitor** (`server/monitor/v1/definitions.json`, `$ref jetstream_stats_v1`) — varz/monitoring, not the JetStream API. Real field; not wired in v3. |
| `MTIME` | `mtime` | ✗ (server schema) | Absent from the jsm.go server schema, **but a real Object Store client field** (`ObjectInfo` "mtime"); OS metadata isn't part of the server API schema. Keep with the OS code. |
| `API_URL` | `api_url` | ✗ | 0 quoted occurrences anywhere in `schema_source` — obsolete. |
| `INTERNAL` | `internal` | ✗ | 0 occurrences — obsolete. |
| `REPLICA` | `replica` | ✗ | 0 occurrences (schema uses `num_replicas`/`replicas`, not singular `replica`) — obsolete. |
| `SCHEMA` | `schema` | ✗ | 0 occurrences as a field (`$schema` is the JSON-schema meta-keyword, unrelated) — obsolete. |
| `SOURCE` | `source` | ✗ | 0 occurrences (schema uses `sources`/`mirror`, not singular `source`) — obsolete. |

**Net for the 9:**
- **Real server fields, just not wired up in v3** — `REQUEST`, `RESPONSE` (API-audit advisory) and `STATS` (server monitor): keep-vs-delete is a product call (do you intend to parse advisories / monitoring? if not they're deletable, but they're not *wrong*).
- **`MTIME`** — real Object Store field, absent from the *server* schema only; keep it with the object-store code.
- **Truly obsolete (0 schema occurrences), safe to delete:** `API_URL`, `INTERNAL`, `REPLICA`, `SCHEMA`, `SOURCE`.

*Method:* constant-name usage in v2 (`/mnt/c/nats/nats.java`) and Orbit (`/mnt/c/nats/orbit.java`) filtered for genuine `ApiConstants` references (not comments/literals/other constants); then each wire value grepped across the **full** `jsm.go/schema_source` tree (not just `jetstream/api/v1/definitions.json`, since the advisory/monitor fields live in sibling schemas).
