# kv / os / service: what they use from jetstream and core

Written 2026-10-07. Read-only. Measured from the compiled classes of `kv`, `os`, `service` (`javap -c` member references, supertypes) plus a source pass for inherited members and inlined constants, which bytecode does not show under the declaring class.

kv (`io.synadia.client.kv`), os (`io.synadia.client.os`) and service (`io.synadia.service`) each own their package; none shares a package with jetstream or core. So the compiler already limits them to `public` members, plus `protected` members reached by subclassing. The question this audit answers is which of those members are user API and which are public only so these modules can reach them.

## 1. kv / os → jetstream, user API (no issue)

- `JetStream`: `publish`, `pushSubscribe` (os).
- `JetStreamManagement`: `addStream`, `updateStream`, `deleteStream`, `purgeStream`, `getStreamInfo`, `getStreamNames`, `getLastMessage` (os), `getTimeout` (os).
- `StreamCreator` builder and getters, `StreamConfiguration` / `StreamInfo` / `StreamState` getters, `Mirror`, `MirrorCreator`, `External`, `SourceCreator` getters (kv), `PushOrderedConsumerCreator.filterSubject` (os).
- `DeliverPolicy`, `DiscardPolicy`, `CompressionOption`, `PublishAck.getSequenceNumber`, `MessageInfo` getters, `PurgeOptions` and `PublishOptions` builders, `JetStreamPushSubscription.nextMessage` / `unsubscribe` (os), `JetStreamApiException.getApiErrorCode` (kv), `JetStreamOptions.getPrefix` / `isDefaultPrefix` (kv).
- `FeatureOptions` and `FeatureOptions.Builder` (`jetStreamOptions`, `jsDomain`, `jsPrefix`, `jsRequestTimeout`): the base of `KeyValueOptions` / `ObjectStoreOptions`, so user-facing.

## 2. kv / os → jetstream, NOT user API (public or protected only for kv / os)

| Type (jetstream) | What kv / os use | Other users in jetstream main |
|---|---|---|
| `impl.AbstractBucketFeature` (base of `KeyValue`, `ObjectStore`) | 6 **public final fields**: `nc`, `fo`, `js`, `jsm`, `bucketName`, `streamName` (kv 11/0/2/14/53/7 refs, os 5/0/4/18/53/11); protected `_getLast` (kv, os), `_getBySeq` (kv), `visitSubject` (kv 4, os 1) | `NatsWatchSubscription` only |
| `impl.NatsWatchSubscription` (base of `KeyValueWatchSubscription`, `ObjectStoreWatchSubscription`) | public constructor, protected `finishInit`, protected nested `WatchMessageHandler` | none |
| `utils.JsValidator` | kv: `validateBucketName`, `validateKvKeyWildcardAllowedRequired`, `validateKvKeysWildcardAllowedRequired`, `validateNonWildcardKvKeyRequired`, `validateMaxBucketBytes`, `validateMaxHistory`, `validateMaxValueSize`, `validateMillisGtOrEqSeconds`, `validateNotNull`; os: `validateBucketName`, `validateMaxBucketBytes` | 9 jetstream classes use other `JsValidator` methods |
| `impl.JetStreamConstants` | kv: `JS_SEQUENCE_TEMPORARILY_UNKNOWN`, `JS_WRONG_LAST_SEQUENCE`, `ROLLUP_HDR`, `ROLLUP_HDR_SUBJECT`, `SERVER_DEFAULT_DUPLICATE_WINDOW_MS`; os: `ROLLUP_HDR`, `ROLLUP_HDR_SUBJECT` | many |
| `utils.ApiConstants` (JSON field names) | kv: `API`; os: `API`, `BUCKET`, `CHUNKS`, `DELETED`, `DESCRIPTION`, `DIGEST`, `HEADERS`, `LINK`, `MAX_CHUNK_SIZE`, `METADATA`, `NAME`, `NUID`, `OPTIONS`, `SIZE` | many |
| `utils.JetStreamApiUtils` | os: `ULONG_UNSET` | many |

The sharpest item: the six `AbstractBucketFeature` fields are public, so every user holding a `KeyValue` or `ObjectStore` sees `kv.nc`, `kv.js`, `kv.jsm`, `kv.fo`, `kv.bucketName`, `kv.streamName` as API. `KeyValueWatchSubscription` reads `kv.js` from outside the subclass relationship, which is the one use that `protected` would not cover.

## 3. kv / os / service → core, not user API

- `utils.Validator`: service `emptyAsNull`, `nullOrEmpty`, `required`, `validateIsRestrictedTerm`, `validateSemVer`, `validateSubjectTermStrict`; os `validateNotNull`.
- `utils.ClientError.instance` (os), `utils.Digester` (os), `utils.ApiUtils.loadVersion` (all three; added 2026-10-07 for `LIBRARY_VERSION`).
- `impl.JetStreamMetaData` (kv, os): a JetStream concept that lives in core.
- `utils.NatsConstants` constants (`DOT`, `GREATER_THAN`, `NANOS_PER_MILLI`).
- User types that live in `.impl`: `NatsConnection`, `Headers`, `NatsMessage` / builder, `NatsSubscription` (service). These are user API in the wrong-looking package, not a leak.

service uses no jetstream at all. Its connection use is all user API: `createDispatcher`, `closeDispatcher`, `publish`, `request`, `subscribe`, `RTT`, `Dispatcher.subscribe` / `drain`, `Message` getters.

## 4. What "more stringent" can mean here

Java cannot export a member to kv and os only. A member kv or os uses is either `public` (everyone sees it) or `protected` (every subclass sees it). So the achievable goal is: kv / os use user API, plus a minimal, deliberate `protected` contract on the two base classes; nothing public exists only for them.

**Decision 2026-10-07 (Scott):** the same rule as jetstream → core. Package access is the boundary, and a user can do the same, so A1, A2, A4, A5, A6 are not done. A3 is applied.

Candidate changes:
- **A1.** `AbstractBucketFeature` fields `public` → `protected` (or private with protected getters). `KeyValueWatchSubscription` then gets `js` another way (passed in, or a package-private accessor in `KeyValue`).
- **A2.** `NatsWatchSubscription` constructor `public` → `protected` (only subclasses construct it).
- **A3. Applied 2026-10-07.** `validateKvKeysWildcardAllowedRequired`, `validateKvKeyWildcardAllowedRequired`, `validateNonWildcardKvKeyRequired`, `validateWildcardKvKey`, `validateNonWildcardKvKey`, `validateMaxHistory`, `validateMaxValueSize`, `notNonWildcardKvKey`, `notWildcardKvKey` moved from `JsValidator` to the new package-private `io.synadia.client.kv.KvValidator`; their 9 tests moved from `JsValidatorTests` to `KvValidatorTests` (`testValidateMaxMessagesPerSubject`, which tested max history, renamed `testValidateMaxHistory`); `MAX_HISTORY_PER_KEY` moved from `JetStreamConstants` to `KeyValueUtils`. Original proposal: kv-only validators (`validateKvKey*`, `validateNonWildcardKvKeyRequired`, `validateMaxHistory`, `validateMaxValueSize`) move from `JsValidator` into kv (`KeyValueUtils`). Shared ones (`validateBucketName`, `validateMaxBucketBytes`, `validateMillisGtOrEqSeconds`) stay or move to `AbstractBucketFeature` as protected statics.
- **A4.** os's `ApiConstants` JSON field names: os defines its own (they are its JSON format), or `ApiConstants` is accepted as shared public constants.
- **A5.** `JetStreamConstants` header and error-code constants: accept as public protocol constants, or kv / os define their own.
- **A6.** Leave as is: the core items in §3, which are the same core-sharing question the JPMS decision already settled.
