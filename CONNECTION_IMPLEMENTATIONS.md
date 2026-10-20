# Connection Implementations: V3 and Classic

The client ships two connection implementations. **V3** (`NatsConnectionV3`) is the default. **Classic** (`NatsConnection`) is the implementation carried over from the v2 client.

`NatsConnectionV3` is a subclass of `NatsConnection`, so every API that takes a connection accepts either one, and your application code does not change when you switch. Subscriptions, dispatchers, request/reply, drain, JetStream, KV, Object Store and Service work the same on both.

## Choosing one

```java
Options options = Options.builder()
    .server("nats://localhost:4222")
    .connectionImplementation(ConnectionImplementation.Classic) // or V3, the default
    .build();
```

In a properties file, use `connectionImplementation=Classic` (the `io.nats.client.` prefix is optional). A JVM system property `io.nats.client.connectionImplementation` replaces the default for every `Options` built in that JVM. An absent or unrecognized value means V3.

Use V3 unless you depend on one of the behavior differences listed below.

## What is different

V3 replaces two parts of the connection: the outgoing path (how publishes and protocol get to the socket), and the connect / reconnect / close lifecycle. The inbound path (the reader, message delivery, dispatchers) is the same code in both.

### Publishing

| | Classic | V3 |
|---|---|---|
| When a publish is serialized | When the writer thread gets to it | When `publish` is called |
| Changing headers or data after `publish` returns | Can change what is sent, while the message is still queued | Has no effect; what was published is what is sent |
| Per-publish work on your thread | Builds a message object and a protocol array, takes two locks | Writes the bytes into the outgoing buffer under one short lock; nothing is allocated in steady state |
| Memory held by a backlog | References to your arrays | Copies of your data |

The memory row matters only if you publish large messages faster than the socket drains them. The bound is the same in both (`maxMessagesInOutgoingQueue` messages), but V3 holds a copy of each queued message, where classic holds a reference to your array. If you reuse one large array for every publish, classic's backlog costs almost nothing and V3's costs the full size of each message.

The outgoing settings mean the same thing in both: `bufferSize` is the most bytes written to the socket at once (a larger message is written alone), `maxMessagesInOutgoingQueue`, `discardMessagesWhenOutgoingQueueFull` and `writeQueuePushTimeout` behave as before with the same exception messages, and `reconnectBufferSize` bounds what is held while disconnected.

### Measured performance

Measured on one development machine (WSL2, 16 cores, local nats-server 2.16.0-dev, default options, median of 3 runs, 2026-10-06). Your numbers will differ; the ratios are the useful part.

| Publish, no subscriber | Classic msgs/s | V3 msgs/s | V3 / Classic |
|---|---|---|---|
| 16 B, 1 thread | 4,644,135 | 10,310,763 | 2.2x |
| 16 B, 4 threads | 1,645,635 | 6,895,458 | 4.2x |
| 128 B, 1 thread | 4,756,071 | 5,780,143 | 1.22x |
| 1 KB, 1 thread | 792,567 | 823,787 | 1.04x |
| 16 KB, 1 thread | 52,214 | 52,468 | 1.00x |

Small messages gain the most, and V3 holds up far better when several threads publish on one connection. Messages of 1 KB and up are limited by the socket for both. Request/reply latency and subscriber throughput are the same in both, because the inbound path is the same code.

Bytes allocated on the publishing thread per publish: classic 336 B, V3 about 1 B (16 B payload) and about 5 B (1 KB payload).

### Connecting with bad credentials

During the connect handshake V3 reads the server's reply on the connecting thread, so a server `-ERR` fails the attempt as soon as it arrives. Classic waits for the connection timeout.

| `Nats.connect` with a wrong token, `maxReconnects(0)` | Median time to fail |
|---|---|
| Classic | 2,001 ms |
| V3 | 3 ms |

Both throw `AuthenticationException`. One exception keeps classic timing on purpose: when an authentication error counts toward the rule that two consecutive identical authentication errors from a server stop the reconnect (a reconnect attempt, or an initial attempt made with `Nats.connectReconnectOnFailure` or `Nats.connectAsynchronously(options, true)`), V3 also waits out the connection timeout, so a server gets the same time to start accepting again as it does with classic.

### Reconnect and close

| | Classic | V3 |
|---|---|---|
| Reader and writer threads | One reader and one writer, reused for every socket | A new reader and writer for every socket |
| A reader or writer of an old socket that reports an error late | `forceReconnect` waits for the old threads to exit, up to the connection timeout, so that they cannot disconnect the new socket | The report is ignored, because it does not come from the current socket; nothing has to wait |
| `close()` while a reconnect is running | Close and the reconnect run at the same time against the same state | Close stops the reconnect: it cancels the reconnect wait, ends a connect attempt in progress, and returns once the connection is closed or the connection timeout passes |
| A reconnect requested while another transition is running | Dropped | Runs when the current one finishes |
| `outgoingPendingMessageCount()`, `outgoingPendingBytes()` | Can block for as long as a reconnect takes | Never block |

Messages you publish are not copied between sockets in either implementation. In V3 the outgoing buffer belongs to the connection, not to the writer, and each new writer drains the same buffer. On a disconnect, SUB, UNSUB and PING are removed from it and your publishes are kept for the next connection, as classic does.

### Smaller differences

- V3 writes a PONG ahead of queued outgoing data instead of behind it, so a server PING is answered promptly even with a large backlog. PING, which `flush` uses, stays in order with your publishes.
- `outgoingPendingMessageCount()` and `outgoingPendingBytes()` do not count a PONG waiting to be written.
- The test hooks `getReader()` and `getWriter()` return objects V3 does not use.

## What is the same

- The public API and every listener: connection events, error listener callbacks, statistics.
- Server pool behavior: server selection, randomization, discovered servers, `maxReconnects`, the reconnect delay handler and `reconnectDelayBehavior`.
- TLS, websocket, authentication and every option not listed above.
- Each failed connect or reconnect attempt raises a `DISCONNECTED` event, in both.
