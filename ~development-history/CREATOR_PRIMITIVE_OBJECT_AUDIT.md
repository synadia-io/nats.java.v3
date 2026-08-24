# Creator Classes — Primitive vs Object Parameter Audit

Public setter-style (chainable) methods in `*Creator` classes that accept `int`, `long`, or `boolean` parameters (primitive or boxed). Constructors, getters, `equals`, `hashCode`, and methods with non-numeric/boolean params are excluded.

Three buckets:
- **Primitive only** — only a primitive overload exists for that field name.
- **Object only** — only a boxed (`Integer` / `Long` / `Boolean`, typically `@Nullable`) overload exists.
- **Paired (primitive + boxed)** — both overloads exist for the same field name.

A separate **Duration + millis** section is given for methods that pair a `Duration` overload with a `long` millis overload (not numeric-paired in the bucket sense, but called out for completeness).

---

## StreamCreator

### Primitive only
- `maxConsumers(long)`
- `maxMessages(long)`
- `maxMessagesPerSubject(long)`
- `maxBytes(long)`
- `maxMessageSize(int)`
- `replicas(int)`
- `firstSequence(long)`
- `noAck(boolean)`
- `sealed(boolean)`
- `allowRollup(boolean)`
- `allowDirect(boolean)`
- `mirrorDirect(boolean)`
- `denyDelete(boolean)`
- `denyPurge(boolean)`
- `discardNewPerSubject(boolean)`
- `allowMessageTtl(boolean)`
- `allowMessageSchedules(boolean)`
- `allowMessageCounter(boolean)`
- `allowAtomicPublish(boolean)`
- `allowBatched(boolean)`

### Object only
None.

### Paired (primitive + boxed)
None.

### Duration + millis
- `maxAge(Duration)` / `maxAge(long)`
- `duplicateWindow(Duration)` / `duplicateWindow(long)`
- `subjectDeleteMarkerTtl(Duration)` / `subjectDeleteMarkerTtl(long)`

---

## StreamSourceCreator (base of SourceCreator, MirrorCreator)

### Primitive only
- `startSequence(long)`

### Object only / Paired / Duration+millis
None.

---

## SourceCreator
No additional numeric/boolean setters beyond inherited `startSequence(long)`.

## MirrorCreator
No additional numeric/boolean setters beyond inherited `startSequence(long)`.

---

## ConsumerCreator (base of all consumer creators)

### Primitive only
- `startSequence(long)`
- `headersOnly(boolean)`

### Object only
- `rateLimit(@Nullable Long)` — no primitive sibling.

### Paired (primitive + boxed)
None.

### Duration + millis
- `idleHeartbeat(Duration)` / `idleHeartbeat(long)`
- `inactiveThreshold(Duration)` / `inactiveThreshold(long)`

---

## AbstractEphemeralConsumerCreator (base of PullConsumerCreator, PushConsumerCreator)

### Primitive only
- `maxDeliver(long)`
- `maxAckPending(long)`
- `numReplicas(int)`
- `memStorage(boolean)`

### Object only
None.

### Paired (primitive + boxed)
None.

### Duration + millis
- `ackWait(Duration)` / `ackWait(long)`
- `flowControl(Duration)` / `flowControl(long)`
- `backoff(Duration...)` / `backoff(long...)` — varargs

---

## AbstractOrderedConsumerCreator (base of PullOrderedConsumerCreator, PushOrderedConsumerCreator)
No own numeric/boolean setters.

---

## PullConsumerCreator

### Primitive only
- `maxPullWaiting(long)`

### Object only
None.

### Paired (primitive + boxed)
- `maxBatch(long)` / `maxBatch(@Nullable Long)`
- `maxBytes(long)` / `maxBytes(@Nullable Long)`

### Duration + millis
- `maxExpires(Duration)` / `maxExpires(long)`
- `priorityTimeout(Duration)` / `priorityTimeout(long)`

---

## PullOrderedConsumerCreator

### Primitive only
None.

### Object only
None.

### Paired (primitive + boxed)
- `maxPullWaiting(long)` / `maxPullWaiting(@Nullable Long)`
- `maxBatch(long)` / `maxBatch(@Nullable Long)`
- `maxBytes(long)` / `maxBytes(@Nullable Long)`

### Duration + millis
- `maxExpires(Duration)` / `maxExpires(long)`
- `priorityTimeout(Duration)` / `priorityTimeout(long)`

---

## PushConsumerCreator
No own numeric/boolean setters.

## PushOrderedConsumerCreator
No own numeric/boolean setters.

---

## ConsumerLimitsCreator

### Primitive only
- `maxAckPending(long)`

### Object only / Paired
None.

### Duration + millis
- `inactiveThreshold(Duration)` / `inactiveThreshold(long)`

---

## ExternalCreator
No numeric/boolean setters.

## PlacementCreator
No numeric/boolean setters.

## RepublishCreator
No numeric/boolean setters (`headersOnly` is a constructor arg only).

## SubjectTransformCreator
No numeric/boolean setters.

---

## KeyValueConfigurationCreator

### Primitive only
- `maxBucketSize(long)`
- `replicas(int)`
- `compression(boolean)`
- `maxHistoryPerKey(int)`
- `maximumValueSize(int)`

### Object only / Paired
None.

### Duration + millis
- `limitMarker(Duration)` / `limitMarker(long)`

---

## ObjectStoreConfigurationCreator

### Primitive only
- `maxBucketSize(long)`
- `replicas(int)`
- `compression(boolean)`

### Object only / Paired / Duration+millis
None.

---

## Cross-Class Summary

### Methods that are Paired (primitive + boxed object)
The **only** field names with both primitive and boxed overloads:

| Class | Field |
| --- | --- |
| PullConsumerCreator | `maxBatch` |
| PullConsumerCreator | `maxBytes` |
| PullOrderedConsumerCreator | `maxPullWaiting` |
| PullOrderedConsumerCreator | `maxBatch` |
| PullOrderedConsumerCreator | `maxBytes` |

Note the asymmetry: `maxPullWaiting` is paired on `PullOrderedConsumerCreator` but **primitive-only** on `PullConsumerCreator`.

### Methods that are Object-only (boxed without primitive sibling)
- `ConsumerCreator.rateLimit(@Nullable Long)`

### Boolean handling
Every boolean setter across every Creator is `boolean` primitive. There are no `Boolean` boxed overloads anywhere.

### Integer handling
Every `int` setter is primitive. There are no `Integer` boxed overloads anywhere.
