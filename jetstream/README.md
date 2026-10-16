# nats.java.v3 — JetStream

> **Work in progress.**

JetStream client APIs: publish, push/pull subscriptions, consumer management, stream management.

## Entry points

- `io.synadia.client.impl.JetStream` — publish, and the simplified consumers (`getStreamContext`, `getConsumerContext`)
- `io.synadia.client.impl.JsPushSubscribe` — legacy push subscriptions
- `io.synadia.client.impl.JsPullSubscribe` — legacy pull subscriptions
- `io.synadia.client.impl.JetStreamManagement` — stream and consumer management

Both are constructed directly from a `NatsConnection`:

```java
NatsConnection nc = Nats.connect();
JetStream js = new JetStream(nc);
JetStreamManagement jsm = new JetStreamManagement(nc);
```

Each constructor has an overload that takes `JetStreamOptions`.

The preferred way to consume is the simplified API (`ConsumerContext`); see [Consuming from JetStream](../README.md#consuming-from-jetstream). `JsPushSubscribe` and `JsPullSubscribe` exist only to support legacy behavior. Their methods take the `JetStream` context. Use them as static methods with the context as the first parameter, or through an instance made from the context:

```java
JetStreamPushSubscription push = JsPushSubscribe.pushSubscribe(js, stream, consumerName);
JetStreamPullSubscription pull = new JsPullSubscribe(js).pullSubscribe(stream, consumerName);
```

## Migrating from v2

See the [top-level migration guide](../MIGRATION_GUIDE.md#jetstream).

## Client Error Messages

Grouped and numbered client errors, each a stable id the message begins with. See the [core README](../README.md#client-error-messages) for how they work. Groups: `SUB` subscription building and creation, `CON` consumer creation and use. A `%s` in a description is a label the thrower fills in.

`JetStreamClientError`

| Name | Group-Code | Description |
|---|---|---|
| `JsSubNoMatchingStreamForSubject` | SUB-90007 | No matching streams for subject. |
| `JsSubDispatcherNoHandlerCantReceiveMessages` | SUB-90023 | Dispatcher without a handler cannot receive messages. |
| `JsConsumerOrderedAlreadyReceiving` | CON-90304 | The ordered consumer is already receiving messages. Ordered Consumer does not allow multiple instances at time. |
| `JsConsumerPinnedNotAllowed` | CON-90305 | Pinned not allowed with %s. |
