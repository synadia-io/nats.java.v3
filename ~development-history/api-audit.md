# API Audit Report

## Status Summary

| Category | Count | % |
|----------|-------|---|
| Lazy (LazyJsonValue) | 25 | 33% |
| Eager (needs conversion) | 11 | 14% |
| Creator (send to server) | 22 | 29% |
| Other (enums, interfaces) | 18 | 24% |

## Already Lazy (done)

| File | Notes |
|------|-------|
| ClusterInfo | ✓ |
| ConsumerInfo | ✓ extends ApiResponse |
| ConsumerLimits | ✓ |
| External | ✓ |
| LostStreamData | ✓ |
| Mirror | ✓ extends StreamSource |
| MirrorInfo | ✓ extends StreamSourceInfo |
| PeerInfo | ✓ abstract |
| Placement | ✓ |
| PriorityGroupState | ✓ |
| Replica | ✓ extends PeerInfo |
| Republish | ✓ |
| SequenceInfo | ✓ |
| Source | ✓ extends StreamSource |
| SourceInfo | ✓ extends StreamSourceInfo |
| StreamAlternate | ✓ |
| StreamConfiguration | ✓ |
| StreamInfo | ✓ extends ApiResponse |
| StreamSource | ✓ abstract |
| StreamSourceInfo | ✓ abstract |
| StreamState | ✓ |
| SubjectTransform | ✓ |
| AccountLimits | ✓ |
| AccountStatistics | ✓ extends ApiResponse |
| AccountTier | ✓ |
| ApiStats | ✓ |
| ConsumerConfiguration (js/consumer) | ✓ |

## Eager — Should Convert to Lazy

| File | Fields | Priority |
|------|--------|----------|
| ConsumerPauseResponse | extends ApiResponse, paused, pauseUntil, pauseRemaining | medium |
| Error | code, apiErrorCode, description | low (also built locally) |
| MessageInfo | extends ApiResponse, subject, seq, data, time, headers, stream, lastSeq, numPending | medium |
| ObjectInfo | bucket, nuid, size, chunks, digest, deleted, objectMeta, modified | low |
| ObjectMeta | objectName, description, headers, metadata, objectMetaOptions | low |
| ObjectMetaOptions | link, chunkSize | low |
| PublishAck | extends ApiResponse, stream, seq, domain, duplicate | high |
| PurgeResponse | extends ApiResponse, success, purged | medium |
| ServerInfo | 17+ fields | low |
| Subject | name, count (POJO from map entries, keep eager) | skip |
| KeyValueEntry | from MessageInfo/Message | low |

## Creators (done, no conversion needed)

| File | Base |
|------|------|
| StreamCreator | standalone |
| StreamSourceCreator | abstract |
| MirrorCreator | extends StreamSourceCreator |
| SourceCreator | extends StreamSourceCreator |
| SubjectTransformCreator | standalone |
| ExternalCreator | standalone |
| PlacementCreator | standalone |
| RepublishCreator | standalone |
| ConsumerLimitsCreator | standalone |
| ConsumerCreator (js/consumer) | abstract |
| AbstractEphemeralConsumerCreator | extends ConsumerCreator |
| AbstractOrderedConsumerCreator | extends ConsumerCreator |
| PullConsumerCreator | extends AbstractEphemeral |
| PushConsumerCreator | extends AbstractEphemeral |
| PullEphemeralConsumerCreator | extends AbstractEphemeral |
| PushEphemeralConsumerCreator | extends AbstractEphemeral |
| PullOrderedConsumerCreator | extends AbstractOrdered |
| PushOrderedConsumerCreator | extends AbstractOrdered |
| ConsumerCreateRequest | standalone |
| ConsumerPauseRequest | standalone |
| MessageDeleteRequest | standalone |
| MessageGetRequest | standalone |

## Other (no conversion needed)

Enums: AckPolicy, CompressionOption, DeliverPolicy, DiscardPolicy, KeyValueOperation, KeyValueWatchOption, ObjectStoreWatchOption, PersistMode, PriorityPolicy, ReplayPolicy, RetentionPolicy, StorageType

Interfaces: KeyValueWatcher, ObjectStoreWatcher, Watcher

Base classes: ApiResponse, FeatureConfiguration

Wrappers: KeyValueConfiguration, KeyValueStatus, ObjectStoreConfiguration, ObjectStoreStatus, SuccessApiResponse, KeyResult, SubscribeBehavior
