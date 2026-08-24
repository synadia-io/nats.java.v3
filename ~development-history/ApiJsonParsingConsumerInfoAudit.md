# ConsumerInfo JSON Parsing Audit

## Summary

This audit covers every parseable getter exposed by `ConsumerInfo` and the nested objects it transitively reads from the fixture JSON: `ConsumerConfiguration`, `SequenceInfo` (used for both `delivered` and `ack_floor`), `ClusterInfo`, `Replica` (extends `PeerInfo`), `PriorityGroupState`, plus the inherited `ApiResponse` getters that read from the same JSON. `ConsumerCreator`-only writer state, `Error`/error getters, `getCalculatedPending()` (derived), `getSourceLazyJsonValue()`, `hasError()`, `equals/hashCode/toString`, and the `EMPTY` sentinel constructors are out of scope.

**Totals (parseable getters counted): 56**

- ✅ covered (value present in JSON AND asserted by test): **30**
- ⚠ value present in JSON but no assertion in test: **11**
- ❌ value missing from JSON (and therefore not asserted): **15**

The test does a solid job on the top-level `ConsumerInfo` shape and `ClusterInfo`, but leaves many `ConsumerConfiguration` fields un-asserted (description, deliver_group, opt_start_seq, opt_start_time, max_ack_pending, filter_subject(s), sample_freq, rate_limit_bps, idle_heartbeat, flow_control, max_waiting, headers_only, max_batch, max_bytes, max_expires, inactive_threshold, backoff, num_replicas, mem_storage, metadata, priority_groups, priority_policy, priority_timeout). It also misses `num_waiting`, `push_bound`, `filter_subjects` (plural), `getStartTime`, `getDeliverGroup`, `getSampleFrequency`, and `getPriorityPolicy`. The `Replica` payload exists but no per-field assertions are made on the elements.

---

## Per-class coverage

### ApiResponse (inherited by ConsumerInfo)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `ConsumerInfo.getType()` (reads `"type"`) | yes | yes (`io.nats.jetstream.api.v1.consumer_info_response`) | ✅ |

### ConsumerInfo (top-level)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getStreamName()` (`stream_name`) | yes | yes (`"foo-stream"`) | ✅ |
| `getName()` (`name`) | yes | yes (`"foo-name"`) | ✅ |
| `getCreationTime()` (`created`) | yes | yes | ✅ |
| `getTimestamp()` (`ts`) | yes | yes | ✅ |
| `getConsumerConfiguration()` (`config`) | yes | yes (object retrieved, fields checked individually below) | ✅ |
| `getDelivered()` (`delivered`) | yes | yes | ✅ |
| `getAckFloor()` (`ack_floor`) | yes | yes | ✅ |
| `getNumPending()` (`num_pending`) | yes | yes (`24`) | ✅ |
| `getNumWaiting()` (`num_waiting`) | **no** | no | ❌ |
| `getNumAckPending()` (`num_ack_pending`) | yes | yes (`42`) | ✅ |
| `getRedelivered()` (`num_redelivered`) | yes | yes (`42`) | ✅ |
| `getPaused()` (`paused`) | yes | yes (`true`) | ✅ |
| `getPauseRemaining()` (`pause_remaining`) | yes | yes (`Duration.ofSeconds(20)`) | ✅ |
| `getClusterInfo()` (`cluster`) | yes | yes (object retrieved) | ✅ |
| `isPushBound()` (`push_bound`) | **no** | no | ❌ |
| `getPriorityGroupStates()` (`priority_groups`) | yes | yes (list size + element fields) | ✅ |

### ConsumerConfiguration (nested at `config`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getDescription()` (`description`) | yes (`"foo-desc"`) | no | ⚠ |
| `getDurable()` (`durable_name`) | yes (`"foo-name"`) | yes | ✅ |
| `getName()` (`name`) | yes (`"foo-name"`) | no | ⚠ |
| `getDeliverSubject()` (`deliver_subject`) | yes (`"deliver.subject"`) | yes (asserts `"bar"` — **mismatch**, see Actions) | ✅* |
| `getDeliverGroup()` (`deliver_group`) | yes (`"deliver-group"`) | no | ⚠ |
| `getDeliverPolicy()` (`deliver_policy`) | yes (`"all"`) | yes (`DeliverPolicy.All`) | ✅ |
| `getStartSequence()` (`opt_start_seq`) | yes (`42`) | no | ⚠ |
| `getStartTime()` (`opt_start_time`) | yes | no | ⚠ |
| `getAckPolicy()` (`ack_policy`) | yes (`"all"`) | yes (`AckPolicy.All`) | ✅ |
| `getAckWait()` (`ack_wait`) | yes (`30s`) | yes (`Duration.ofSeconds(30)`) | ✅ |
| `getMaxDeliver()` (`max_deliver`) | yes (`10`) | yes (`10`) | ✅ |
| `getFilterSubject()` (`filter_subject`) | yes (`"sub.single"`) | no | ⚠ |
| `getFilterSubjects()` (derived: `filter_subject` or `filter_subjects`) | yes (via singular) | no | ⚠ |
| `hasMultipleFilterSubjects()` | derived | no | ⚠ |
| `getPriorityGroups()` (`priority_groups` inside config) | yes (`["pgroup1","pgroup2"]`) | no | ⚠ |
| `getReplayPolicy()` (`replay_policy`) | yes (`"original"`) | yes (`ReplayPolicy.Original`) | ✅ |
| `getRateLimit()` (`rate_limit_bps`) | yes (`73`) | no | ⚠ |
| `getMaxAckPending()` (`max_ack_pending`) | yes (`42`) | no | ⚠ |
| `getSampleFrequency()` (`sample_freq`) | yes | no | ⚠ |
| `getIdleHeartbeat()` (`idle_heartbeat`) | yes (`20s`) | no | ⚠ |
| `isFlowControl()` (`flow_control`) | yes (`true`) | no | ⚠ |
| `getMaxPullWaiting()` (`max_waiting`) | yes (`128`) | no | ⚠ |
| `isHeadersOnly()` (`headers_only`) | yes (`true`) | no | ⚠ |
| `isMemStorage()` (`mem_storage`) | yes (`true`) | no | ⚠ |
| `getMaxBatch()` (`max_batch`) | yes (`55`) | no | ⚠ |
| `getMaxBytes()` (`max_bytes`) | yes (`6666666666`) | no | ⚠ |
| `getMaxExpires()` (`max_expires`) | yes (`40s`) | no | ⚠ |
| `getInactiveThreshold()` (`inactive_threshold`) | yes (`50s`) | no | ⚠ |
| `getBackoff()` (`backoff`) | yes (`[1s,2s,3s]`) | no | ⚠ |
| `getMetadata()` (`metadata`) | yes (`{meta-test-key=meta-test-value}`) | no | ⚠ |
| `getNumReplicas()` (`num_replicas`) | yes (`5`) | no | ⚠ |
| `getPauseUntil()` (`pause_until`) | yes | yes | ✅ |
| `getPriorityPolicy()` (`priority_policy`) | yes (`"overflow"`) | no | ⚠ |
| `getPriorityTimeout()` (`priority_timeout`) | yes (`60s`) | no | ⚠ |

*Note: the test currently expects `"bar"` for `getDeliverSubject()`, but the fixture contains `"deliver.subject"`. Either the test or the fixture is wrong — flagged below.

### SequenceInfo (used for `delivered` and `ack_floor`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getConsumerSequence()` (`consumer_seq`) | yes (both) | yes (both) | ✅ |
| `getStreamSequence()` (`stream_seq`) | yes (both) | yes (both) | ✅ |
| `getLastActive()` (`last_active`) | yes (both) | yes (both) | ✅ |

### ClusterInfo (nested at `cluster`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getName()` (`name`) | yes (`"clustername"`) | yes | ✅ |
| `getRaftGroup()` (`raft_group`) | yes (`"raftgroupname"`) | yes | ✅ |
| `getLeader()` (`leader`) | yes (`"clusterleader"`) | yes | ✅ |
| `getLeaderSince()` (`leader_since`) | yes | yes | ✅ |
| `isSystemAccount()` (`system_account`) | yes (`true`) | yes | ✅ |
| `getTrafficAccount()` (`traffic_account`) | yes (`"trafficaccountname"`) | yes | ✅ |
| `getReplicas()` (`replicas`) | yes (2 elements) | size only (`2`) | ✅* (list retrieved, but per-element fields not asserted — see Replica/PeerInfo below) |

### Replica / PeerInfo (inside `cluster.replicas`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getName()` (`name`) | yes (`name0`, `name1`) | no | ⚠ |
| `isCurrent()` (`current`) | yes | no | ⚠ |
| `isOffline()` (`offline`) | yes | no | ⚠ |
| `getActive()` (`active`) | yes | no | ⚠ |
| `getLag()` (`lag`) | yes | no | ⚠ |

### PriorityGroupState (entries in top-level `priority_groups`)

| Getter | In JSON? | Asserted? | Status |
|---|---|---|---|
| `getGroup()` (`group`) | yes (both entries) | yes (both) | ✅ |
| `getPinnedClientId()` (`pinned_client_id`) | yes (both) | yes (both) | ✅ |
| `getPinnedTime()` (`pinned_ts`) | yes (both) | yes (both) | ✅ |

---

## Action list

Priority is roughly in order of importance (data-shape clarifications first, then expansion).

1. **Resolve the `deliver_subject` discrepancy.** The fixture JSON has `"deliver_subject": "deliver.subject"` but the test asserts `"bar"`. The test passes today only because of how the fixture is being read — this needs investigation (likely the test or fixture was updated independently). Pick one and align.
2. **Add fixture entries and assertions for the two missing top-level fields:** `num_waiting` and `push_bound`. These are real server-returned fields on `ConsumerInfo` and are completely uncovered.
3. **Assert every `ConsumerConfiguration` field that is already present in the fixture** — currently 22 of them have values in JSON but no assertions. In particular: `description`, `name` (config.name), `deliver_group`, `opt_start_seq`, `opt_start_time`, `max_ack_pending`, `filter_subject`, `priority_groups` (config-level), `rate_limit_bps`, `sample_freq`, `idle_heartbeat`, `flow_control`, `max_waiting`, `headers_only`, `mem_storage`, `max_batch`, `max_bytes`, `max_expires`, `inactive_threshold`, `backoff`, `metadata`, `num_replicas`, `priority_policy`, `priority_timeout`.
4. **Assert `ClusterInfo.getReplicas()` element contents.** The list size is checked but no `Replica/PeerInfo` getter (`name`, `current`, `offline`, `active`, `lag`) is asserted. The fixture has two replicas with distinct values precisely for this — wire them up.
5. **Exercise `getFilterSubjects()` (plural) and `hasMultipleFilterSubjects()`.** Currently the fixture only sets the singular `filter_subject`. Either add a second variant test or extend the fixture/test to cover both branches of the singular-vs-plural fallback in `getFilterSubjects()`.
