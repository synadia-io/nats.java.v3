# ConsumerFields Hierarchy

```
ConsumerCreator<T> (all fields, getters, 18 common setters, protected _delegates)
ConsumerConfiguration (lazy, read-only from server JSON, same getters as ConsumerCreator)

ConsumerCreator<T>
        ├── AbstractEphemeralConsumerCreator<T> (+ stream)
        │     ├── PullEphemeralConsumerCreator
        │     ├── PushEphemeralConsumerCreator
        │     ├── PullConsumerCreator
        │     └── PushConsumerCreator
        └── AbstractOrderedConsumerCreator<T> (+ stream, namePrefix)
              ├── PullOrderedConsumerCreator
              └── PushOrderedConsumerCreator
```

### Creator Field Distribution
| Field             | Abstract Consumer Creator | Abstract Ephemeral | Push | Pull | Abstract Ordered | Push | Pull |
|:------------------|:-------------------------:|:------------------:|:----:|:----:|:----------------:|:----:|:----:|
| description       |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| deliverPolicy     |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| startSequence     |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| startTime         |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| filterSubject     |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| filterSubjects    |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| replayPolicy      |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| sampleFrequency   |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| rateLimit         |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| idleHeartbeat     |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| inactiveThreshold |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| headersOnly       |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| metadata          |             ✓             |         ✓          |  ✓   |  ✓   |        ✓         |  ✓   |  ✓   |
| name              |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| ackPolicy         |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| ackWait           |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| maxDeliver        |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| maxAckPending     |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| flowControl       |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| numReplicas       |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| pauseUntil        |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| memStorage        |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| backoff           |                           |         ✓          |  ✓   |  ✓   |                  |      |      |
| namePrefix        |                           |                    |      |      |        ✓         |  ✓   |  ✓   |
| durable           |                           |                    |  ✓   |  ✓   |                  |      |      |
| deliverSubject    |                           |                    | ctor |      |                  | ctor |      |
| deliverGroup      |                           |                    |  ✓   |      |                  |  ✓   |      |
| maxExpires        |                           |                    |      |  ✓   |                  |      |  ✓   |
| maxPullWaiting    |                           |                    |      |  ✓   |                  |      |  ✓   |
| maxBatch          |                           |                    |      |  ✓   |                  |      |  ✓   |
| maxBytes          |                           |                    |      |  ✓   |                  |      |  ✓   |
| priorityGroups    |                           |                    |      |  ✓   |                  |      |  ✓   |
| priorityPolicy    |                           |                    |      |  ✓   |                  |      |  ✓   |
| priorityTimeout   |                           |                    |      |  ✓   |                  |      |  ✓   |

## Setter placement rationale

- **Pull-specific** 
  - PullConsumerCreator
  - PullEphemeralConsumerCreator
  - PullOrderedConsumerCreator
  - _Fields_ 
    - maxExpires
    - maxPullWaiting
    - maxBatch
    - maxBytes
    - priorityGroups
    - priorityPolicy
    - priorityTimeout

- **Push-specific** 
  - PushConsumerCreator
  - PushEphemeralConsumerCreator
  - PushOrderedConsumerCreator
  - _Fields_
    - deliverSubject (via constructor)
    - deliverGroup (via setter)

- **Durable**:
  - PullConsumerCreator
  - PushConsumerCreator
