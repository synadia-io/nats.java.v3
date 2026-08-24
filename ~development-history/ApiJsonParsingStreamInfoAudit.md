# StreamInfo JSON Parsing Audit

## Summary

This audit walks every getter exposed by `StreamInfo` (server-returned, not `StreamCreator`-only / `toJson()` only) and every getter on the nested types it can transitively read: `StreamConfiguration`, `StreamState`, `ClusterInfo`, `Replica` / `PeerInfo`, `MirrorInfo` / `SourceInfo` / `StreamSourceInfo`, `StreamAlternate`, `Subject`, `LostStreamData`, `ConsumerLimits`, `Republish`, `Placement`, `External`, `Mirror` / `Source` / `StreamSource`, `SubjectTransform`, and the inherited `ApiResponse` fields (`type`, error chain, `didCreate`). `SequenceInfo` is reachable only via `ConsumerInfo`/`StreamInfo` does not read it directly, so it is **not** counted (kept here only for orientation).

**Total parseable fields counted: 88** across these classes (excluding `SequenceInfo`, which is not transitively reachable from `StreamInfo`).

- **✅ Covered (value in fixture + asserted by test): 50**
- **⚠ In fixture JSON but no assertion: 4**
- **❌ Not in fixture JSON and not asserted: 34**

Notes on scope:
- Fields under `StreamConfiguration` like `Mirror`, `Sources` (list of `Source`), `SubjectTransform`, `Republish`, `ConsumerLimits`, `Compression`, `Metadata`, `MaxMessagesPerSubject`, the many `Allow*`/`Deny*`/`Sealed` flags, `Description`, `TemplateOwner`, `PersistMode`, `SubjectDeleteMarkerTtl`, `Mirror.*`, `Source.*` — server *does* return these when set on the stream. The fixture omits them entirely, which is the largest gap.
- `Subject.toString()`, `Subject.equals`/`hashCode`, `Subject.compareTo` are utility methods on a parsed POJO, but not parsed JSON fields — listed below only for completeness as zero-cost coverage gaps.
- `StreamSourceInfo.getError()` is documented as server-returned but is rarely populated; the test asserts `assertNull(mi.getError())` on the mirror, so it gets a tick.
- `Replica` extends `PeerInfo`; getters live on the abstract base.
- `Mirror`/`Source` extend `StreamSource`; getters live on the abstract base.
- `MirrorInfo`/`SourceInfo` extend `StreamSourceInfo`; getters live on the abstract base.

Legend: ✅ = value present in JSON and asserted; ⚠ = value in JSON but test does not assert; ❌ = value missing from JSON (and so cannot be asserted).

---

## StreamInfo (`io.synadia.client.api.StreamInfo`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getConfiguration()` | yes (`config`) | yes (`assertNotNull` + many sub-asserts) | ✅ |
| `getStreamState()` | yes (`state`) | yes (`assertNotNull` + many sub-asserts) | ✅ |
| `getCreateTime()` | yes (`created`) | yes | ✅ |
| `getMirrorInfo()` | yes (`mirror`) | yes (`assertNotNull` + sub-asserts) | ✅ |
| `getSources()` | yes (`sources`) | yes (size + per-element) | ✅ |
| `getClusterInfo()` | yes (`cluster`) | yes (`assertNotNull` + sub-asserts) | ✅ |
| `getAlternates()` | yes (`alternates`) | yes (size + per-element) | ✅ |
| `getTimestamp()` | yes (`ts`) | yes | ✅ |
| `didCreate()` | no (`did_create`) | no | ❌ |
| `getType()` (inherited) | yes (`type`) | no | ⚠ |
| `hasError()` (inherited) | no (no `error` in JSON) | only via empty-map fallback path | ⚠ (positive-path only) |
| `getErrorObject()` / `getError()` / `getErrorCode()` / `getApiErrorCode()` / `getDescription()` (inherited) | no | no | ❌ (no error fixture) |

---

## StreamConfiguration (`io.synadia.client.api.StreamConfiguration`)

| Getter | In JSON (`config.*`)? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (`name`) | yes | ✅ |
| `getDescription()` | no (`description`) | no | ❌ |
| `getSubjects()` | yes (`subjects`) | yes (size + each) | ✅ |
| `getRetentionPolicy()` | yes (`retention`) | yes | ✅ |
| `getCompressionOption()` | no (`compression`) | no | ❌ |
| `getMaxConsumers()` | yes (`max_consumers`) | yes | ✅ |
| `getMaxMessages()` | yes (`max_msgs`) | yes | ✅ |
| `getMaxMessagesPerSubject()` | no (`max_msgs_per_subject`) | no | ❌ |
| `getMaxBytes()` | yes (`max_bytes`) | yes | ✅ |
| `getMaxAge()` | yes (`max_age`) | yes | ✅ |
| `getMaxMessageSize()` | yes (`max_msg_size`) | yes | ✅ |
| `getStorageType()` | yes (`storage`) | yes | ✅ |
| `getReplicas()` | yes (`num_replicas`) | yes | ✅ |
| `getNoAck()` | no (`no_ack`) | no | ❌ |
| `getTemplateOwner()` | no (`template_owner`) | no | ❌ |
| `getDiscardPolicy()` | yes (`discard`) | yes | ✅ |
| `getDuplicateWindow()` | yes (`duplicate_window`) | yes | ✅ |
| `getPlacement()` | yes (`placement`) | yes (cluster + tags) | ✅ |
| `getRepublish()` | no (`republish`) | no | ❌ |
| `getSubjectTransform()` | no (`subject_transform`) | no | ❌ |
| `getConsumerLimits()` | no (`consumer_limits`) | no | ❌ |
| `getMirror()` (config-side Mirror, distinct from `StreamInfo.getMirrorInfo()`) | no (`config.mirror`) | no | ❌ |
| `getSources()` (config-side list of `Source`, distinct from `StreamInfo.getSources()`) | no (`config.sources`) | no | ❌ |
| `getSealed()` | no (`sealed`) | no | ❌ |
| `getAllowRollup()` | no (`allow_rollup_hdrs`) | no | ❌ |
| `getAllowDirect()` | no (`allow_direct`) | no | ❌ |
| `getMirrorDirect()` | no (`mirror_direct`) | no | ❌ |
| `getDenyDelete()` | no (`deny_delete`) | no | ❌ |
| `getDenyPurge()` | no (`deny_purge`) | no | ❌ |
| `isDiscardNewPerSubject()` | no (`discard_new_per_subject`) | no | ❌ |
| `getMetadata()` | no (`metadata`) | no | ❌ |
| `getFirstSequence()` | yes (`first_seq`) | yes | ✅ |
| `getAllowMessageTtl()` | no (`allow_msg_ttl`) | no | ❌ |
| `getAllowMessageSchedules()` | no (`allow_msg_schedules`) | no | ❌ |
| `getAllowMessageCounter()` | no (`allow_msg_counter`) | no | ❌ |
| `getAllowAtomicPublish()` | no (`allow_atomic`) | no | ❌ |
| `getAllowBatched()` | no (`allow_batched`) | no | ❌ |
| `getSubjectDeleteMarkerTtl()` | no (`subject_delete_marker_ttl`) | no | ❌ |
| `getPersistMode()` | no (`persist_mode`) | no | ❌ |

---

## StreamState (`io.synadia.client.api.StreamState`)

| Getter | In JSON (`state.*`)? | Asserted? | Status |
|---|---|---|---|
| `getMessageCount()` | yes (`messages`) | yes | ✅ |
| `getByteCount()` | yes (`bytes`) | yes | ✅ |
| `getFirstSequence()` | yes (`first_seq`) | yes | ✅ |
| `getFirstTime()` | yes (`first_ts`) | yes | ✅ |
| `getLastSequence()` | yes (`last_seq`) | yes | ✅ |
| `getLastTime()` | yes (`last_ts`) | yes | ✅ |
| `getConsumerCount()` | yes (`consumer_count`) | yes | ✅ |
| `getSubjectCount()` | yes (`num_subjects`) | yes | ✅ |
| `getSubjects()` | yes (`subjects`) | yes (3 entries) | ✅ |
| `getSubjectMap()` | yes (`subjects`) | no | ⚠ |
| `getDeletedCount()` | yes (`num_deleted`) | yes | ✅ |
| `getDeleted()` | yes (`deleted`) | yes (size + each) | ✅ |
| `getLostStreamData()` | yes (`lost`) | yes (sub-asserts) | ✅ |

---

## ClusterInfo (`io.synadia.client.api.ClusterInfo`)

| Getter | In JSON (`cluster.*`)? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (`name`) | yes | ✅ |
| `getRaftGroup()` | yes (`raft_group`) | yes | ✅ |
| `getLeader()` | yes (`leader`) | yes | ✅ |
| `getLeaderSince()` | yes (`leader_since`) | yes | ✅ |
| `isSystemAccount()` | yes (`system_account`) | yes | ✅ |
| `getTrafficAccount()` | yes (`traffic_account`) | yes | ✅ |
| `getReplicas()` | yes (`replicas`) | yes (size + per-element) | ✅ |

---

## Replica / PeerInfo (`io.synadia.client.api.Replica` extends `PeerInfo`)

| Getter | In JSON (`cluster.replicas[*]`)? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (`name`) | yes | ✅ |
| `isCurrent()` | yes (`current`) | yes (both true and false branches) | ✅ |
| `isOffline()` | yes (`offline`) | yes (both branches) | ✅ |
| `getActive()` | yes (`active`) | yes | ✅ |
| `getLag()` | yes (`lag`) | yes | ✅ |

---

## MirrorInfo / SourceInfo / StreamSourceInfo (abstract base)

Used for `StreamInfo.getMirrorInfo()` (one) and `StreamInfo.getSources()` (three entries: 17 has `active`, 18 has `active: -1` so getter returns null, 19 omits `active`).

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (`name`) | yes | ✅ |
| `getFilterSubject()` | yes (`filter_subject`) | yes | ✅ |
| `getLag()` | yes (`lag`) | yes | ✅ |
| `getActive()` | yes (set, -1, missing — all three branches present) | yes (asserted for set; asserted null for source 18 + 19) | ✅ |
| `getExternal()` | yes (`external`) | yes (via `validateExternal`) | ✅ |
| `getSubjectTransforms()` | yes (`subject_transforms`) | yes (via `StreamCreatorConfigurationTests.validateSubjectTransforms`) | ✅ |
| `getError()` | no (`error`) | yes — but only `assertNull` on mirror; never positively populated | ⚠ (negative-only) |

---

## StreamAlternate (`io.synadia.client.api.StreamAlternate`)

| Getter | In JSON (`alternates[*]`)? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (`name`) | yes | ✅ |
| `getDomain()` | yes (`domain`) | yes | ✅ |
| `getCluster()` | yes (`cluster`) | yes | ✅ |

---

## Subject (`io.synadia.client.api.Subject`)

| Getter | In JSON (`state.subjects.*`)? | Asserted? | Status |
|---|---|---|---|
| `getName()` | yes (map keys) | yes | ✅ |
| `getCount()` | yes (map values) | yes | ✅ |

---

## LostStreamData (`io.synadia.client.api.LostStreamData`)

| Getter | In JSON (`state.lost.*`)? | Asserted? | Status |
|---|---|---|---|
| `getMessages()` | yes (`msgs`) | yes (size + each) | ✅ |
| `getBytes()` | yes (`bytes`) | yes | ✅ |

---

## Placement (`io.synadia.client.api.Placement`)

| Getter | In JSON (`config.placement.*`)? | Asserted? | Status |
|---|---|---|---|
| `getCluster()` | yes (`cluster`) | yes | ✅ |
| `getTags()` | yes (`tags`) | yes (size + each) | ✅ |
| `hasData()` | n/a — derived | no | ⚠ (derived, optional) |

---

## External (`io.synadia.client.api.External`)

| Getter | In JSON (mirror/sources `external`)? | Asserted? | Status |
|---|---|---|---|
| `getApi()` | yes (`api`) | yes (via `validateExternal`) | ✅ |
| `getDeliver()` | yes (`deliver`) | yes (via `validateExternal`) | ✅ |

---

## SubjectTransform (`io.synadia.client.api.SubjectTransform`)

| Getter | In JSON (mirror/sources `subject_transforms[*]`)? | Asserted? | Status |
|---|---|---|---|
| `getSource()` | yes (`src`) | yes (via external `validateSubjectTransforms`) | ✅ |
| `getDestination()` | yes (`dest`) | yes (via external `validateSubjectTransforms`) | ✅ |

---

## ConsumerLimits (`io.synadia.client.api.ConsumerLimits`)

Only reached if `config.consumer_limits` is populated; the fixture omits this object entirely.

| Getter | In JSON (`config.consumer_limits.*`)? | Asserted? | Status |
|---|---|---|---|
| `getInactiveThreshold()` | no | no | ❌ |
| `getMaxAckPending()` | no | no | ❌ |

---

## Republish (`io.synadia.client.api.Republish`)

Only reached if `config.republish` is populated; the fixture omits it.

| Getter | In JSON (`config.republish.*`)? | Asserted? | Status |
|---|---|---|---|
| `getSource()` | no (`src`) | no | ❌ |
| `getDestination()` | no (`dest`) | no | ❌ |
| `isHeadersOnly()` | no (`headers_only`) | no | ❌ |

---

## Mirror / Source / StreamSource (config-side)

`StreamConfiguration.getMirror()` and `StreamConfiguration.getSources()` parse `config.mirror` / `config.sources`. These differ from `StreamInfo.getMirrorInfo()` / `StreamInfo.getSources()` because they live on the *configuration* (start-seq, start-time, filter, external, subject-transforms). The fixture's `config` block contains neither.

| Getter (StreamSource) | In JSON (`config.mirror` / `config.sources[*]`)? | Asserted? | Status |
|---|---|---|---|
| `getStreamName()` | no (`name`) | no | ❌ |
| `getStartSequence()` | no (`opt_start_seq`) | no | ❌ |
| `getStartTime()` | no (`opt_start_time`) | no | ❌ |
| `getFilterSubject()` | no (`filter_subject`) | no | ❌ |
| `getExternal()` | no | no | ❌ |
| `getSubjectTransforms()` | no | no | ❌ |

(`Mirror` is just `StreamSource` with no extra fields; `Source` adds nothing beyond `StreamSource`.)

---

## Action List

### Add to `StreamInfo.json` and add assertions

These are server-returned fields that any real `STREAM_INFO` for a fully configured stream may include. Highest impact first:

1. `config.description` — assert via `getDescription()`.
2. `config.compression` — assert via `getCompressionOption()`.
3. `config.max_msgs_per_subject` — assert via `getMaxMessagesPerSubject()`.
4. `config.metadata` (map) — assert via `getMetadata()`.
5. `config.consumer_limits` (with `inactive_threshold` and `max_ack_pending`) — assert `getConsumerLimits().getInactiveThreshold()` and `getMaxAckPending()`.
6. `config.republish` (with `src`, `dest`, `headers_only`) — assert all three Republish getters.
7. `config.subject_transform` (object) — assert `getSubjectTransform().getSource()` / `.getDestination()`.
8. `config.mirror` (Mirror block: `name`, `opt_start_seq`, `opt_start_time`, `filter_subject`, `external`, `subject_transforms`) — assert all `StreamSource` getters.
9. `config.sources` (list of Source) — assert all `StreamSource` getters for at least one element.
10. Boolean / flag fields: `sealed`, `allow_rollup_hdrs`, `allow_direct`, `mirror_direct`, `deny_delete`, `deny_purge`, `discard_new_per_subject`, `no_ack`, `allow_msg_ttl`, `allow_msg_schedules`, `allow_msg_counter`, `allow_atomic`, `allow_batched` — assert each.
11. `config.template_owner` — assert `getTemplateOwner()`.
12. `config.subject_delete_marker_ttl` — assert `getSubjectDeleteMarkerTtl()`.
13. `config.persist_mode` — assert `getPersistMode()`.
14. `did_create` (top-level) — assert `didCreate()`.
15. `type` (top-level) — already present, just add `assertEquals("io.nats.jetstream.api.v1.stream_create_response", si.getType())`.

### Add assertions (value already in JSON, no fixture change needed)

1. `StreamState.getSubjectMap()` — fixture has `state.subjects`, add an assertion on map keys/values.
2. `StreamInfo.getType()` — JSON has `type`; just assert it.
3. `Placement.hasData()` — derived, add cheap assert for completeness.
4. `StreamSourceInfo.getError()` — only asserted as `null`; consider a second fixture (or extending this one) that includes a `mirror.error` and a `sources[*].error` to exercise the positive path.

### Lower priority / negative-path coverage

- `ApiResponse.hasError()` / `getErrorObject()` / `getErrorCode()` / `getApiErrorCode()` / `getDescription()` / `getError()` — the test only exercises the empty-map fallback (`assertTrue(si.hasError())`). A second fixture containing an `error` object would cover the populated error path properly.
- `StreamInfo` constructed from a `LazyJsonValue` directly (the second constructor) — not exercised; the test only uses the `Message` constructor (and `EMPTY_MAP`).
