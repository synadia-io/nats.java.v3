```json
{
  "name": "sname",
  "subjects": [
    "sname"
  ],
  "retention": "limits",
  "storage": "file",
  "num_replicas": 1,
  "discard": "old",
  "duplicate_window": 120000000000,
  "consumer_limits": {},
  "metadata": {
    "_nats.ver": "2.14.0-dev",
    "_nats.level": "4",
    "_nats.req.level": "0"
  }
}
```

# JetStream API v1 Definitions

Source: `jsm.go/schema_source/jetstream/api/v1/definitions.json`

## Primitive Types

| Definition | Go Type | JSON Type | Notes |
|---|---|---|---|
| golang_duration_nanos | int64 | integer | nanoseconds, min 0 |
| golang_int | int64 | integer | platform-dependent, up to 64bit |
| golang_uint64 | uint64 | integer | unsigned, 0..2^64-1 |
| golang_int32 | int32 | integer | signed 32-bit |
| golang_int64 | int64 | integer | signed 64-bit |
| golang_time | time.Time | string | RFC3339 date-time |
| basic_name | string | string | pattern-constrained |

## Object Definitions

### account_limits
| Field | Type | Required | Description |
|---|---|---|---|
| max_memory | integer | yes | Max Memory storage Stream Messages may consume |
| max_storage | integer | yes | Max File storage Stream Messages may consume |
| max_streams | integer | yes | Max number of Streams |
| max_consumers | integer | yes | Max number of Consumers |
| max_bytes_required | boolean | no | Streams require max_bytes property set |
| max_ack_pending | integer | no | Max outstanding ACKs any consumer may configure |
| memory_max_stream_bytes | integer | no | Max size any single memory stream may be |
| storage_max_stream_bytes | integer | no | Max size any single storage-based stream may be |

### account_stats
| Field | Type | Required | Description |
|---|---|---|---|
| memory | integer | yes | Memory storage used |
| storage | integer | yes | File storage used |
| streams | integer | yes | Number of active Streams |
| consumers | integer | yes | Number of active Consumers |
| domain | string | no | JetStream domain |
| limits | account_limits | yes | Account limits |
| tiers | object (map of tier) | no | Account tiers |
| api | api_stats | yes | API statistics |

### api_error
| Field | Type | Required | Description |
|---|---|---|---|
| code | integer | yes | HTTP-like error code (300-500) |
| description | string | no | Human-friendly description |
| err_code | integer | no | NATS error code |

### api_stats
| Field | Type | Required | Description |
|---|---|---|---|
| level | integer | no | JetStream API Level |
| total | integer | yes | Total API requests |
| errors | integer | yes | API requests resulting in error |
| inflight | integer | no | Inflight API requests |

### cluster_info
| Field | Type | Required | Description |
|---|---|---|---|
| name | string | no | Cluster name |
| leader | string | no | Server name of RAFT leader |
| replicas | array\<peer_info\> | no | Members of the RAFT cluster |
| leader_since | golang_time | no | Time elected as leader |
| raft_group | string | no | Raft group name |
| system_account | boolean | no | Traffic uses system account |
| traffic_account | string | no | Account for replication traffic |

### consumer_configuration
| Field | Type | Required | Description |
|---|---|---|---|
| durable_name | basic_name | no | Unique name for durable consumer |
| name | basic_name | no | Unique name for consumer |
| description | string | no | Short description |
| deliver_policy | string | no | Point in stream to receive from |
| deliver_subject | string | no | Subject for push delivery |
| deliver_group | string | no | Queue group name |
| ack_policy | string | no | Client acknowledgment requirement |
| ack_wait | golang_duration_nanos | no | Time before redelivery attempt |
| max_deliver | golang_int | no | Max redelivery attempts |
| filter_subject | string | no | Single subject filter |
| filter_subjects | array\<string\> | no | Multiple subject filters |
| replay_policy | string | no | Rate of message push |
| sample_freq | string | no | Acknowledgment sampling % |
| rate_limit_bps | golang_uint64 | no | Delivery rate in bps |
| max_ack_pending | golang_int | no | Max unacked messages |
| idle_heartbeat | golang_duration_nanos | no | Idle heartbeat interval |
| flow_control | boolean | no | Enable flow control |
| max_waiting | golang_int | no | Max outstanding pulls |
| direct | boolean | no | Special non-Raft consumer (internal) |
| headers_only | boolean | no | Deliver only headers |
| max_batch | integer | no | Max batch size for pull |
| max_expires | golang_duration_nanos | no | Max expires for pull |
| max_bytes | golang_int | no | Max bytes for pull |
| inactive_threshold | golang_duration_nanos | no | Cleanup threshold for ephemeral |
| backoff | array\<golang_duration_nanos\> | no | Retry time scale for NaK'd messages |
| num_replicas | integer | no | Replica count override |
| mem_storage | boolean | no | Force in-memory state |
| metadata | object | no | Additional metadata |
| pause_until | golang_time | no | Pause deadline |
| priority_groups | array\<string\> | no | Priority groups supported |
| priority_policy | priority_policy | no | Priority policy |
| priority_timeout | golang_duration_nanos | no | Pinned client timeout |
| opt_start_seq | golang_uint64 | no | Start sequence for DeliverByStartSequence |
| opt_start_time | golang_time | no | Start time for DeliverByStartTime |

### consumer_info
| Field | Type | Required | Description |
|---|---|---|---|
| stream_name | string | yes | Stream the consumer belongs to |
| name | string | yes | Unique consumer name |
| ts | golang_time | no | Server time info was created |
| config | consumer_configuration | yes | Active configuration |
| created | golang_time | yes | Time consumer was created |
| delivered | sequence_info | yes | Last message delivered |
| ack_floor | sequence_info | yes | Highest contiguous ack |
| num_ack_pending | golang_int | yes | Messages pending ack |
| num_redelivered | golang_int | yes | Redelivery count |
| num_waiting | golang_int | yes | Pull consumers waiting |
| num_pending | golang_uint64 | yes | Messages unconsumed |
| cluster | cluster_info | no | Cluster information |
| push_bound | boolean | no | Client connected to push consumer |
| paused | boolean | no | Consumer is paused |
| pause_remaining | golang_duration_nanos | no | Time remaining until unpause |
| priority_groups | array\<priority_group_state\> | no | Priority group states |

### external_stream_source
| Field | Type | Required | Description |
|---|---|---|---|
| api | string | yes | Subject prefix for imports |
| deliver | string | no | Delivery subject for push consumer |

### lost_stream_data
| Field | Type | Required | Description |
|---|---|---|---|
| msgs | array\<integer\> or null | no | Lost message sequences |
| bytes | golang_uint64 | no | Lost bytes |

### peer_info
| Field | Type | Required | Description |
|---|---|---|---|
| name | string | yes | Server name |
| current | boolean | yes | Up to date and synchronized |
| observer | boolean | no | Running as observer |
| active | number | yes | Nanoseconds since last seen |
| offline | boolean | no | Considered offline |
| lag | integer | no | Uncommitted operations behind leader |

### placement
| Field | Type | Required | Description |
|---|---|---|---|
| cluster | string | no | Desired cluster name |
| tags | array\<string\> | no | Required server tags |
| preferred | string | no | Preferred server for leader |

### priority_group_state
| Field | Type | Required | Description |
|---|---|---|---|
| group | string | yes | Group name |
| pinned_client_id | string | no | Generated pinned client ID |
| pinned_ts | golang_time | no | Timestamp when client was pinned |

### republish
| Field | Type | Required | Description |
|---|---|---|---|
| src | string | yes | Source subject |
| dest | string | yes | Destination subject |
| headers_only | boolean | no | Only send headers |

### sequence_info
| Field | Type | Required | Description |
|---|---|---|---|
| consumer_seq | golang_uint64 | yes | Consumer sequence number |
| stream_seq | golang_uint64 | yes | Stream sequence number |
| last_active | golang_time | no | Last delivery or ack time |

### sequence_pair
| Field | Type | Required | Description |
|---|---|---|---|
| consumer_seq | golang_uint64 | yes | Consumer sequence number |
| stream_seq | golang_uint64 | yes | Stream sequence number |

### stored_message
| Field | Type | Required | Description |
|---|---|---|---|
| subject | string | yes | Original subject |
| seq | golang_uint64 | yes | Sequence number in Stream |
| data | string | no | Base64 encoded payload |
| time | string | yes | Time received |
| hdrs | string | no | Base64 encoded headers |

### stream_alternate
| Field | Type | Required | Description |
|---|---|---|---|
| name | string | yes | Mirror stream name |
| cluster | string | yes | Cluster holding the stream |
| domain | string | no | Domain holding the stream |

### stream_configuration
| Field | Type | Required | Description |
|---|---|---|---|
| name | string | no | Unique stream name |
| description | string | no | Short description |
| subjects | array\<string\> | no | Subjects to consume |
| subject_transform | subject_transform | no | Subject transform for matching messages |
| retention | string | yes | Message retention policy |
| max_consumers | golang_int | yes | Max consumers (-1 unlimited) |
| max_msgs | golang_int64 | yes | Max messages (-1 unlimited) |
| max_msgs_per_subject | golang_int64 | no | Per-subject message limit |
| max_bytes | golang_int64 | yes | Max stream size (-1 unlimited) |
| max_age | golang_duration_nanos | yes | Max message age (0 unlimited) |
| max_msg_size | golang_int32 | no | Max message size (-1 unlimited) |
| storage | string | yes | Storage backend |
| compression | string | no | Compression algorithm |
| first_seq | integer | no | Custom first sequence number |
| num_replicas | golang_int | yes | Replica count |
| no_ack | boolean | no | Disable message acknowledgments |
| template_owner | string | no | Stream Template identifier |
| discard | string | no | Discard policy at limits |
| duplicate_window | golang_duration_nanos | no | Duplicate tracking window |
| placement | placement | no | Placement directives |
| mirror | stream_source | no | 1:1 mirror configuration |
| sources | array\<stream_source\> | no | Source streams to replicate |
| sealed | boolean | no | Seal stream (irreversible) |
| deny_delete | boolean | no | Restrict message deletion |
| deny_purge | boolean | no | Restrict message purging |
| allow_rollup_hdrs | boolean | no | Allow Nats-Rollup header |
| allow_direct | boolean | no | Allow direct get access |
| allow_atomic | boolean | no | Allow atomic batched publishes |
| allow_msg_counter | boolean | no | Configure as counter stream |
| allow_msg_schedules | boolean | no | Allow message scheduling |
| mirror_direct | boolean | no | Allow direct access for mirrors |
| republish | republish | no | Republish configuration |
| discard_new_per_subject | boolean | no | Apply discard new per-subject |
| metadata | object | no | Additional metadata |
| consumer_limits | stream_consumer_limits | no | Consumer limit defaults |
| allow_msg_ttl | boolean | no | Enable per-message TTL |
| subject_delete_marker_ttl | golang_duration_nanos | no | Server delete marker duration |
| persist_mode | string | no | Persistence mode |

### stream_consumer_limits
| Field | Type | Required | Description |
|---|---|---|---|
| inactive_threshold | golang_duration_nanos | no | Max inactive_threshold for consumers |
| max_ack_pending | golang_int | no | Max max_ack_pending for consumers |

### stream_info
| Field | Type | Required | Description |
|---|---|---|---|
| config | object | yes | Active stream configuration |
| state | object | yes | Current stream state |
| created | golang_time | yes | Stream creation timestamp |
| ts | golang_time | no | Server time info was created |
| cluster | cluster_info | no | Cluster information |
| mirror | stream_source_info | no | Mirror information |
| sources | array\<stream_source_info\> | no | Source stream information |
| alternates | array\<stream_alternate\> | no | Mirrors sorted by priority |

### stream_source
| Field | Type | Required | Description |
|---|---|---|---|
| name | basic_name | yes | Stream name |
| opt_start_seq | golang_uint64 | no | Start replication sequence |
| opt_start_time | golang_time | no | Start replication time |
| filter_subject | string | no | Subject filter for replication |
| subject_transforms | array or null | no | Subject filtering and transforms |
| external | external_stream_source | no | External stream source |

### stream_source_info
| Field | Type | Required | Description |
|---|---|---|---|
| name | string | yes | Stream being replicated |
| filter_subject | string | no | Subject filter applied |
| subject_transforms | array or null | no | Subject filtering transforms |
| lag | golang_uint64 | yes | Messages behind mirror |
| active | golang_duration_nanos | yes | Last activity time (-1 if never) |
| external | external_stream_source | no | External source |
| error | api_error | no | Error information |

### stream_state
| Field | Type | Required | Description |
|---|---|---|---|
| messages | golang_uint64 | yes | Number of messages |
| bytes | golang_uint64 | yes | Combined message size |
| first_seq | golang_uint64 | yes | First message sequence |
| first_ts | string | no | First message timestamp |
| last_seq | golang_uint64 | yes | Last message sequence |
| last_ts | string | no | Last message timestamp |
| deleted | array\<golang_uint64\> | no | Deleted message IDs |
| subjects | object | no | Subject message counts |
| num_subjects | golang_int | no | Unique subject count |
| num_deleted | golang_int | no | Deleted message count |
| lost | lost_stream_data | no | Lost data information |
| consumer_count | golang_int | yes | Consumer count |

### subject_transform
| Field | Type | Required | Description |
|---|---|---|---|
| src | string | yes | Transform source |
| dest | string | yes | Transform destination |

### tier
| Field | Type | Required | Description |
|---|---|---|---|
| memory | integer | yes | Memory storage used |
| storage | integer | yes | File storage used |
| reserved_memory | integer | no | Reserved memory bytes |
| reserved_storage | integer | no | Reserved disk bytes |
| streams | integer | yes | Active stream count |
| consumers | integer | yes | Active consumer count |
| limits | account_limits | yes | Account limits |

## Enum Definitions

### priority_policy
Values: `none`, `overflow`, `pinned_client`, `prioritized`

## Request/Response Definitions (Internal)

These are protocol-level definitions not typically represented as standalone Java objects:

- **error_response**: wraps an api_error
- **iterable_request**: offset field for pagination
- **iterable_response**: total/offset/limit for pagination
- **deliver_policy variants**: all_deliver_policy, last_deliver_policy, last_per_subject_deliver_policy, new_deliver_policy, start_sequence_deliver_policy, start_time_deliver_policy
