# nats.java.v3 — Legacy JetStream

Legacy JetStream behaviors carried over from v2 that are not in the main JetStream library. They exist only to support legacy behavior; the preferred way to consume is the simplified API (`ConsumerContext`), see [Consuming from JetStream](../README.md#consuming-from-jetstream).

| Group      | Artifact   |
|------------|------------|
| io.synadia | jnats3-ljs |

## Pull subscription fetch, iterate and reader

`io.synadia.client.impl.JetStreamLegacyPull` holds `fetch`, `iterate` and `reader` for a `JetStreamPullSubscription`, and `reader` returns a `JetStreamReader`. Call them as static methods with the subscription as the first parameter, or through an instance made from the subscription:

```java
JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(js, stream, consumerName);

List<Message> messages = JetStreamLegacyPull.fetch(sub, 10, 1000);
Iterator<Message> iterator = new JetStreamLegacyPull(sub).iterate(10, 1000);
JetStreamReader reader = JetStreamLegacyPull.reader(sub, 500, 125);
```

- `fetch(batchSize, maxWaitMillis)` returns up to `batchSize` messages, waiting no longer than `maxWaitMillis`.
- `iterate(batchSize, maxWaitMillis)` returns an iterator over up to `batchSize` messages; it has no messages if the first does not arrive within `maxWaitMillis`.
- `reader(batchSize, repullAt)` returns an endless reader that pulls `batchSize` messages again once `repullAt` messages of the current batch were read.

## Migrating from v2

See the [top-level migration guide](../MIGRATION_GUIDE.md#jetstream).
