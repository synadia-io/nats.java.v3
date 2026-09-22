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

## Client Error Messages

Grouped and numbered client errors, each a stable id the message begins with. See the [core README](../README.md#client-error-messages) for how they work. Groups: `SUB` subscription building and creation, `CON` consumer creation and use, `OS` ObjectStore operations. A `%s` in a description is a label the thrower fills in.

`JetStreamClientError`

| Name | Group-Code | Description |
|---|---|---|
| `JsSubNoMatchingStreamForSubject` | SUB-90007 | No matching streams for subject. |
| `JsSubDispatcherNoHandlerCantReceiveMessages` | SUB-90023 | Dispatcher without a handler cannot receive messages. |
| `JsConsumerOrderedAlreadyReceiving` | CON-90304 | The ordered consumer is already receiving messages. Ordered Consumer does not allow multiple instances at time. |
| `JsConsumerPinnedNotAllowed` | CON-90305 | Pinned not allowed with %s. |

`ObjectStoreClientError` — moves to [os/README.md](../os/README.md) when ObjectStore becomes its own module.

| Name | Group-Code | Description |
|---|---|---|
| `OsObjectNotFound` | OS-90201 | The object was not found. |
| `OsObjectIsDeleted` | OS-90202 | The object is deleted. |
| `OsObjectAlreadyExists` | OS-90203 | An object with that name already exists. |
| `OsCantLinkToLink` | OS-90204 | A link cannot link to another link. |
| `OsGetDigestMismatch` | OS-90205 | Digest does not match metadata. |
| `OsGetChunksMismatch` | OS-90206 | Number of chunks does not match metadata. |
| `OsGetSizeMismatch` | OS-90207 | Total size does not match metadata. |
| `OsGetLinkToBucket` | OS-90208 | Cannot get object, it is a link to a bucket. |
| `OsLinkNotAllowOnPut` | OS-90209 | Link not allowed in metadata when putting an object. |
| `OsCantLinkToDeletedObject` | OS-90210 | Cannot link to a deleted object. |
