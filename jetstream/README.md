# nats.java.v3 — JetStream

> **Work in progress.**

JetStream client APIs: publish, push/pull subscriptions, consumer management, stream management.

## Entry points

- `io.synadia.client.impl.JetStream` — publish + subscribe
- `io.synadia.client.impl.JetStreamManagement` — stream and consumer management

Both are constructed directly from a `NatsConnection`:

```java
NatsConnection nc = Nats.connect();
JetStream js = new JetStream(nc);
JetStreamManagement jsm = new JetStreamManagement(nc);
```

Each constructor has an overload that takes `JetStreamOptions`.

## Migrating from v2

See the [top-level migration guide](../MIGRATION_GUIDE.md#jetstream).
