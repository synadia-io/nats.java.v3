# Plan: NatsConnection — a rewrite (NatsConnectionV3) and surgical changes

Written 2026-10-06. Two proposals in one document, as asked, plus a review of the other open docs that touch the connection:

- **Part A — a rewrite, `NatsConnectionV3`.** Written, compiling and tested in the working tree, not committed. Selectable per connection; the classic `NatsConnection` is unchanged and stays the default.
- **Part B — surgical changes to the classic `NatsConnection`.** Proposed only. Nothing in Part B is applied to the classic code.
- **Part C — the open docs that touch the connection**, and what this plan means for each, `PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md` in particular.

Where there is more than one way to do something, the options are listed in my order of preference, first is preferred. Measured facts and inferences are labeled as such.

## Contents

1. Short version
2. Measured results
3. Part A — the rewrite
4. Part B — surgical changes to the classic implementation
5. Part C — review of the open docs that touch the connection
6. Open decisions
7. Files in the working tree
8. How the measurements were taken

---

## 1. Short version

- V3 replaces the outgoing path (publishes are serialized straight into fixed-size byte blocks at publish time; the writer writes one block per pass, as it is) and the connect / reconnect / close lifecycle (one compare-and-set guard, a new reader and writer per socket, the connect handshake read on the connecting thread).
- Measured on this machine: small-message publish throughput is **2.2x** classic on one thread and **4.2x** on four threads; publisher allocation per publish drops to about 1 byte (16 B payload) and 5 bytes (1 KB payload), against 336 bytes for classic with B-S1; a connect with bad credentials fails in **3 ms instead of 2,001 ms**. Messages of 1 KB and up are bound near 750-820 MB/s for both, and the inbound path and request latency are unchanged, because V3 does not change them (yet).
- V3 is the default since 2026-10-06 (D2). Every test in the re-run classes passes on it (A4).
- Part B: B-S1, B-S2, B-S3 and B-S6 are applied to classic.

---

## 2. Measured results

Median of 3 runs, each run a fresh JVM, classic and V3 alternated. Local `nats-server` v2.16.0-dev, WSL2 on this machine, 16 cores. Default options except where stated. Method in section 8. Classic here includes the Part B fixes (B-S1 changes its allocation; the others do not touch the publish path).

### Publish (no subscriber)

| Payload | Threads | Classic msgs/s | V3 msgs/s | V3 / Classic |
|---|---|---|---|---|
| 16 B | 1 | 4,644,135 | 10,310,763 | 2.2x |
| 16 B | 4 | 1,645,635 | 6,895,458 | 4.2x |
| 128 B | 1 | 4,756,071 | 5,780,143 | 1.22x |
| 128 B with 2 headers | 1 | 3,509,753 | 4,250,768 | 1.21x |
| 1 KB | 1 | 792,567 (774 MB/s) | 823,787 (805 MB/s) | 1.04x |
| 1 KB | 4 | 797,138 | 803,013 | 1.01x |
| 16 KB | 1 | 52,214 (816 MB/s) | 52,468 (820 MB/s) | 1.00x |
| 64 KB | 1 | 12,518 (782 MB/s) | 12,055 (754 MB/s) | 0.96x, within the spread (classic 716-801, V3 749-764 MB/s) |

Notes, each measured:

- Classic **loses 65% of its single-thread rate when four threads publish** (4.64M → 1.65M). V3 loses 33% (10.3M → 6.9M). Classic's publish takes two locks (`editLock`, then the `LinkedBlockingQueue` put lock) and allocates; V3 takes one short lock.
- For reference, the Go CLI (`nats bench pub`, same server) does 7.8M msgs/s at 16 B, 6.2M at 128 B and 1.4 GiB/s at 1 KB and 16 KB. So V3 is above Go at 16 B and close at 128 B, and **both Java implementations stop near 750-820 MB/s for large messages, about 55-60% of Go.**
- I tested the obvious explanation for that ceiling, the JDK copying a heap array into a direct buffer before the socket write: a raw Java loopback write measured 0.95-1.05 GiB/s for both a `Socket` stream with a heap array and a `SocketChannel` with a direct buffer. So the hypothesis is rejected, and the client is at roughly 80% of raw Java socket speed on this machine. The rest of the gap to Go is not explained.

### Publish to a subscriber on a second connection (inbound path included)

| Payload | Classic msgs/s | V3 msgs/s |
|---|---|---|
| 1 KB | 511,983 | 528,547 |

No difference beyond run-to-run spread; earlier runs at 16 B and 16 KB showed none either. Expected: the subscriber side is the classic reader and dispatcher in both. This is where the next gain is (A5).

### Request / reply, 128 B, sequential

| | req/s | p50 | p99 |
|---|---|---|---|
| Classic | 1,264 | 771 µs | 1,131 µs |
| V3 | 1,259 | 774 µs | 1,153 µs |
| Go (`nats bench service`) | 1,284 | 752 µs | 1,139 µs |

All three are the same, so the ~770 µs round trip is this machine (WSL2 loopback and thread wake-up), not the client.

### Publisher-thread allocation per publish (`ThreadMXBean.getThreadAllocatedBytes`)

| Payload | Classic before B-S1 | Classic | V3 |
|---|---|---|---|
| 16 B | 400 B | 336 B | 0.7 B |
| 1 KB | 1,360 B | 336 B | 4.7 B |

At 64 KB, V3 measured 16 KB per publish against classic's 353 B. That is not per-publish overhead: the test publishes faster than the socket drains, so a backlog of up to `maxMessagesInOutgoingQueue` (5,000) messages builds, and V3 holds it as copies (about 330 MB at 64 KB), where classic holds references to the caller's array (this test reuses one array). Same bound in both (`maxMessagesInOutgoingQueue × message size`); V3 releases the blocks when the backlog drains, except the ones it keeps for reuse (A2.1).

### Connect with a wrong token (server requires a token), `maxReconnects(0)`, 10 tries

| | AuthenticationException | median time to fail | max |
|---|---|---|---|
| Classic | 10 / 10 | 2,001 ms | 2,064 ms |
| V3 | 10 / 10 | 3 ms | 92 ms |

Classic waits for the PONG future until the 2,000 ms connection timeout even though the server already said `-ERR 'Authorization Violation'` and closed (A2.2).

---

## 3. Part A — the rewrite

### A1. How the old and the new implementation are offered side by side

The requirement: a user must be able to choose the proven implementation or the new one. Three ways, in order of preference.

1. **`NatsConnectionV3 extends NatsConnection`, chosen by an Options setting (implemented).** `OptionsBuilder.connectionImplementation(...)` or the properties key `io.nats.client.connectionImplementation`. The default is V3 since 2026-10-06 (D2). For testing, the JVM system property of the same name replaces the builder's default, and `-PconnectionImplementation=<name>` on a Gradle test run sets it. `NatsImpl.createConnection` builds one or the other. Because V3 *is a* `NatsConnection`, JetStream, KV, OS, Service, `DataPort`, the listeners and every test helper accept it with no change. Outside the new files, the only edits are the Options plumbing (section 7) and the Part B fixes to classic.
   - Cost: V3 inherits fields it does not use. The inherited constructor still builds a classic reader and writer (about 130 KB of buffers per connection, never started). V3 overrides every method of the classic lifecycle and outgoing path so none of the inherited ones can run against the classic reader/writer (list in A2.5).
   - Cost: the classic class's protected surface becomes V3's contract. A later change to a protected method in `NatsConnection` has to be checked against V3.
2. **Resurrect a `Connection` interface, two independent implementations.** `PLAN_CORE_JETSTREAM_BOUNDARY.md` §8 (:245-255) names "more than one connection implementation" as the first valid reason to reintroduce the interface, and says extracting it later is mechanical; this would be that case. What it costs, measured from the code:
   - Every public signature in core, JetStream, KV, OS and Service that takes or returns `NatsConnection` changes to `Connection`.
   - The internals are typed on the concrete class and reach package-private members: `NatsSubscription` and `NatsDispatcher` call `connection.invalidate`, `unsubscribe`, `sendUnsub`, `sendSubscriptionMessage`, `subjectValidate`, `cleanupDispatcher`, `reSubscribe`, `getNextSid`; JetStream calls `_createSubscriptionByFactory`, `getScheduledExecutor`, `isClosing` (boundary plan §3). `DataPort.connect(NatsConnection, ...)` (`DataPort.java:22`) is typed on the concrete class too. So the interface needs a second, internal SPI interface for those, or both implementations share the subscription/dispatcher classes through it.
   - This is the right end state if V3 replaces classic eventually, and V3 moves to it without redesign: its new classes (`OutboundBuffer`, `OutboundWriter`, `PrefixedDataPort`) do not depend on being a subclass.
3. **One `NatsConnection`, two internal engines.** Move the outgoing path and lifecycle behind an internal `ConnectionEngine` and keep `NatsConnection` as the facade. Lowest public impact, but it means restructuring the proven class to extract the classic engine, which defeats the purpose of keeping the proven implementation untouched. Not recommended.

Recommendation: keep 1 now; decide 2 when (and if) V3 becomes the default.

### A2. What V3 changes, and why

Kept from classic, unchanged: the public API and its validation, subscriptions, dispatchers, `deliverMessage`, request/reply bookkeeping (`responsesAwaiting`, `deliverReply`, `cleanResponses`), drain, flush, RTT, the reader's parser (`NatsConnectionReader`, one new instance per socket), `ServerPool`, `DataPort`, the reconnect delay handler, the listeners and the statistics.

Replaced: the outgoing path and the connection lifecycle.

#### A2.1 Outgoing: serialize at publish into byte blocks (`OutboundBuffer`, `OutboundWriter`)

How classic does it, from the code:

- `publish` builds an `InternalPublishableMessage`. Its `calculate()` allocates a `ByteArrayBuilder` sized `32 + subject*2 + replyTo + headerAndDataLen` (`NatsMessage.java:122`) to hold a control line of a few dozen bytes. **So every publish allocates a throwaway array as large as its own payload** (measured: 1,360 bytes per 1 KB publish). It also allocates `subject.getBytes`, `replyTo.getBytes` and two `Integer.toString(...).getBytes` (`:133-146`), and a `Headers` copy when the headers are not read-only (`InternalPublishableMessage.java:33`).
- The message goes onto a `LinkedBlockingQueue` (a node per message) after `editLock.tryLock` (`WriterMessageQueue.java:42`), so every publish takes two locks.
- The writer thread polls messages one at a time, chains them through `NatsMessage.next`, and copies each into a 64 KB send buffer before the socket write. A message bigger than the buffer permanently grows the buffer (`NatsConnectionWriter.java:153`).

How V3 does it:

- The publishing thread takes one short lock and writes the control line, headers and payload straight into the data lane. ASCII subjects are written char by char, numbers as digits; nothing is allocated per publish in steady state. This copy of the payload is the only one before the socket write, as classic's copy into its send buffer is.
- The data lane is a queue of blocks of `bufferSize` bytes (64 KB by default). A publish goes into the last block, or starts a new one when it does not fit; an entry bigger than `bufferSize` gets a block of its own size. An entry published with `flushImmediatelyAfterPublish` ends its block, and the writer flushes after writing it, as classic ends its batch at such a message.
- The writer takes one block per pass under the lock and writes it as it is, outside the lock. So a socket write is at most `bufferSize` bytes (or one larger message), and **a failed write loses at most that one block**, the same bound as classic's batch; everything still queued survives the disconnect. The writer is signaled only if it is waiting, so a steady stream of publishes does not signal per message.
- Written blocks are kept for reuse: `bufferSize` blocks up to the larger of 1 MB or 4 blocks (16 at the 64 KB default), and larger blocks up to the first server's max payload + max control line + 2 bytes in total, the largest entry client side limit checks allow (nothing is kept before the first INFO). A larger block is never dropped to keep a smaller one. Under steady load the blocks circulate without allocation; when a backlog builds, it is held in new blocks, released once written.
- Each entry records its size and whether it is filter-on-stop (SUB, UNSUB, PING, PONG), packed into one `int`. That keeps three classic behaviors without message objects: `statistics.incrementOut` per message at write time, the outgoing-queue message limit (`maxMessagesInOutgoingQueue`, discard or wait up to `writeQueuePushTimeout`, the same exception messages), and filtering SUB/UNSUB/PING out on disconnect (done by compacting each block).
- Two lanes. The **data lane** carries publishes and all other protocol in call order, exactly classic's normal queue. The **control lane** is small, is always written first, and carries what must precede the queued data: resubscribes during a reconnect (classic's reconnect queue), and PONG (a deliberate change, B2).
- The data lane is held back from the moment a socket is lost until the new socket is connected and the resubscribes are queued. That is classic's `END_RECONNECT` marker, as a boolean. Publishes made while disconnected accumulate in it, bounded by `reconnectBufferSize`, checked under the same lock as the append (classic checks before taking the lock).

What this removes from the classic design: `InternalPublishableMessage` per publish, `NatsMessage.next`, `MarkerMessage`, the proposed `MessageBatch`, `WriterMessageQueue`, the reconnect queue, and the writer's 1 ms poll loop in reconnect mode (`NatsConnectionWriter.java:204`).

#### A2.2 Connect handshake read on the connecting thread

Classic starts the reader, queues CONNECT and PING, then waits for the PONG future (`NatsConnection.java:602-614`). When the server rejects the login it sends `-ERR` and closes; the PONG never comes, so the attempt waits for the full connection timeout (measured 2,001 ms of a 2,000 ms timeout).

V3 writes CONNECT and PING itself and reads the reply lines on the connecting thread until PONG: `INFO` goes to `handleInfo`, `+OK` to `processOK`, `PING` is answered, `-ERR` goes to `processError` and fails the attempt at once (measured 3 ms). Bytes read past the PONG are handed to the new reader through `PrefixedDataPort`. Statistics (ping count, bytes in and out) and the read listener see the handshake as they did in classic.

What this does **not** fix, corrected from my first draft: the `TEST_TRACKING.md` race where `AuthTests.testToken` / `testEncodedPassword` sometimes get `IOException` instead of `AuthenticationException` on Windows. Classic's reader parses the `-ERR` line before it can see the close, because both arrive in order on one stream, so if the `-ERR` bytes arrive, classic also reports `AuthenticationException` (10 of 10 here). The flake therefore means the `-ERR` bytes never reached the client. A plausible mechanism is a TCP reset discarding unread data on Windows, because the server closes with the client's PING still unread. That is not verified. V3 writes CONNECT and PING together exactly like classic, so it would be exposed the same way.

#### A2.3 Lifecycle: one guard, one owner per transition

Classic coordinates connect, reconnect, force reconnect and close with `connecting`, `disconnecting`, `closing`, `exceptionDuringConnectChange`, `statusLock`, `closeSocketLock` and `tryingToConnect`, and checks `tryingToConnect` with get-then-set rather than compare-and-set (`NatsConnection.java:220, 322, 408, 747`), so two threads can both enter.

V3 uses `tryingToConnect` with `compareAndSet` as the single guard. Whoever wins it runs the whole transition: initial connect, reconnect after an I/O error, force reconnect, close. Requests that arrive while a transition runs are not dropped:

- An I/O error from the live socket marks it as needing a reconnect and submits a task; if the guard is busy, the holder runs the reconnect when it releases the guard (`endTransition`).
- `close()` marks the connection closing, cancels the reconnect wait, force-closes an in-progress connect attempt so it fails at once, then either takes the guard and closes, or waits (up to the connection timeout) for the holder to see the flag and close.
- Errors reported during a connect attempt (a TLS handshake failure reported through `handleCommunicationIssue`, a reader error) fail that attempt, as classic's `exceptionDuringConnectChange` does. An error that lands after the attempt succeeded becomes a pending reconnect instead of being lost.
- `closing` and `reconnectWaiter` are non-volatile in classic and are read across threads; V3 keeps volatile copies.

#### A2.4 One reader and writer per socket (`Generation`)

Every connect attempt makes a new socket, a new `NatsConnectionReader` and a new `OutboundWriter`, bundled as a `Generation`. Each one's own `running` flag keeps a stopped reader or writer quiet in the normal case. For the remaining race (a thread that checked its flag just before the stop and reports after it), the reader reports through `handleCommunicationIssue(NatsConnectionReader source, Exception)` and the writer through `handleWriterIssue(Generation, Exception)`, and V3 ignores a report whose source is not the current generation's. Without that, a late report during teardown would be counted as an exception and a server failure, and a report that arrives after a new socket is up would reconnect a healthy connection. Classic reuses one reader and one writer across sockets and relies on joins to stop an old thread from stopping the new connection (comment at `NatsConnection.java:382-385`).

#### A2.5 Overridden methods

`connect`, `connectImpl`, `forceReconnect`, `forceReconnectImpl`, `reconnect`, `reconnectImpl`, `tryToConnect`, `closeSocket`, `closeSocketImpl`, `close(boolean, boolean)`, both `handleCommunicationIssue` overloads, `handleInfo` (to track lame duck; the classic field is private), the seven `publish` overloads, the core six-argument `requestAsync`, `sendSubscriptionMessage`, `sendUnsub`, `sendPing`, `sendPong`, `queueOutgoing`, `queueInternalOutgoing`, `flushBuffer`, `outgoingPendingMessageCount`, `outgoingPendingBytes`, `setReadListener`. Inherited and used as is: everything else, including `flush`, `RTT`, `drain`, `deliverMessage`, `handlePong`, `processError`, `readInitialInfo`, `upgradeToSecureIfNeeded`, `checkVersionRequirements`, `updateStatus`, `waitWhile`.

#### A2.6 The outgoing-buffer settings in V3

The settings mean what they mean in classic. `bufferSize` bounds each socket write and therefore what a failed write can lose (Scott's point, 2026-10-06: that is why the setting exists).

| Setting (default) | Classic | V3 |
|---|---|---|
| `bufferSize` (64 KB) | Send buffer size: the most bytes per write batch; a larger message is written alone | Block size: the most bytes per write; a larger message gets a block of its own. A failed write loses at most one block |
| `maxMessagesInOutgoingQueue` (5,000) | Outgoing queue capacity, in messages | Most entries waiting in the data lane, in messages and protocol lines |
| `discardMessagesWhenOutgoingQueueFull` (false) | Discard and call `messageDiscarded` when full | Same |
| `writeQueuePushTimeout` (2 s) | Wait this long when full, then "Output queue is full" | Same, same messages |
| `reconnectBufferSize` (8 MB) | While disconnected, queued bytes plus the new message must stay below it | Same rule on the held-back data lane, checked under the append lock |
| `MAX_MESSAGES_IN_NETWORK_BUFFER` (1,000, constant) | Most messages per write batch | Not used; the block size bounds the batch |

In flight is the same as classic: up to `maxMessagesInOutgoingQueue` waiting plus one write of at most `bufferSize`. What differs is memory: classic's queue holds references to the caller's arrays, V3 holds copies (section 2, allocation at 64 KB); the bound is the same.

### A3. Behavior changes, explicitly

Each of these is in the `NatsConnectionV3` class javadoc too (B3a and B7 excepted, which are consequences rather than API).

| # | Change | Why | Who could notice |
|---|---|---|---|
| B1 | A publish is serialized when it is called. Changing the message's headers or data after `publish` returns no longer changes what is sent. | Throughput. It also removes the documented classic limitation that headers must not be changed after publish while the message is queued. `OutboundBufferTests.testHeadersAndDataAreCopiedAtPublish` covers V3. | Only code that relies on mutating after publish. |
| B2 | PONG is written ahead of queued outgoing data instead of behind it. | A server PING answered behind megabytes of backlog can trip the server's stale-connection check. PONG has no ordering meaning in the protocol. PING (used by flush) stays in order. | Nobody, except a test that inspects wire order. |
| B3 | A `-ERR` during the connect handshake fails the attempt when it is read, not when the connection timeout runs out — except an authentication error that counts toward the double auth error rule (a reconnect attempt, or an initial attempt with `reconnectOnConnect`): that attempt is held to its connection timeout, as in classic (D3). | 3 ms instead of 2,001 ms for `Nats.connect` with bad credentials. The hold keeps the time between the two counted rejections the same as classic, which is the time a server has to start accepting again. | Only the fast case: `Nats.connect` without `reconnectOnConnect`. |
| B3a | Without the hold, the double auth error rule fired one connection timeout sooner than in classic. | — | `AuthTests.testThatAuthErrorIsCleared` failed on V3 before the hold; it passes with it (D3). |
| B4 | `outgoingPendingMessageCount()` / `outgoingPendingBytes()` never block. They do not count a PONG waiting to be written. | Classic takes `closeSocketLock` (`NatsConnection.java:2851, 2868`), which `closeSocket` holds across the entire reconnect campaign, so the getters can block for as long as reconnecting takes. `TODO.md` TBD #4 (V2 PR #1632) asks for the same fix. | Nobody. |
| B5 | A reader or writer of a replaced socket cannot trigger a reconnect. | Correctness (A2.4). | Nobody. |
| B6 | `close()` during a reconnect stops the reconnect: it force-closes the attempt in progress and returns once the connection is closed or the connection timeout passes. | Classic's close and the reconnect loop run concurrently against the same fields. | Close during reconnect returns sooner. |
| B7 | `sendPing` removes its pong future if queueing the PING throws. | A leaked future takes the next PONG owed to a live waiter — the same bug already fixed for `RTT`. | Nobody. |
| B8 | The test hooks `getReader()` / `getWriter()` / `getDataPortFuture()` return the inherited objects V3 does not use. | They are classic internals. | Tests that pause the writer now go through `WriterTestControl`, which uses `getWriter()` on classic and `NatsConnectionV3.pauseWriterForTest()` on V3 (D9). `ReconnectTests.testWriterFilterTiming` (a classic writer race, #203) is skipped on V3. `ParseTests` uses `getReader()` and is fine: V3 uses the same reader class. |

Kept on purpose, although questionable, so the behavior matches classic: each failed connect or reconnect attempt raises a `DISCONNECTED` event (classic does it through `closeSocket(false, true)` in the attempt's catch); that event's URL detail is the last connected server, not the server of the attempt; an interrupt during `Nats.connect` is swallowed.

I tried one more change and reverted it: skipping a server that rejected the credentials twice instead of ending the campaign. It made `testThatAuthErrorIsCleared` pass but made `testCloseOnReconnectWithSameError` fail (5 of 5), and the rule itself is a client parity specification (D3), so it stays as is.

### A4. Test status on V3

Run in an isolated copy of the working tree on the Linux filesystem (no shared `.gradle` or build directory with your Windows runs). V3 is the default; `-PconnectionImplementation=Classic` runs a class on classic. A test class printed the built connection's class to confirm the switch takes effect.

- **Latest, V3 default, block writer (2026-10-06):** core, 32 classes (Auth, Connect, Echo, Publish, Subscribe, ListenerId, Options, AuthAndConnect, AuthViolationDuringReconnectOnFlushTimeout, ConnectionListenerStaleState, ConnectionListener, ConnectionStateConsistency, Dispatcher, Drain, ErrorListener, InfoHandler, MaxReconnectResolvedIps, MessageContent, NatsConnectionImpl, NatsConnectionReaderRepoint, NatsStatistics, OutboundBuffer, Parse, Ping, Reconnect, Request, SlowConsumer, SocketDataPortProxyHostname, SubscribeRegistrationOrder, TLSConnect, ValidateIssue1426, WebsocketConnect), and JetStream (JsPublish, JetStreamConsumer, JetStreamGeneral, JetStreamPull, JetStreamPub, JetStreamPushAsync, JetStreamPush, JetStreamPushQueue, JetStreamTimeout, JetStreamSubscribe, Simplification), KV (KeyValueTests), OS (ObjectStoreTests), Service (ServiceTests): **every test passes**; `ReconnectTests.testWriterFilterTiming` is skipped on V3 by design (D9).
- **After the large-block pool change:** `OutboundBufferTests`, `PublishTests`, `ReconnectTests`, `ErrorListenerTests`, `NatsConnectionImplTests`, `JetStreamPubTests`, `SimplificationTests` pass. `ReconnectTests.testForceReconnectQueueBehaviorCheck` failed once in that run in its cluster setup (`managedConnect` gave up after 10 tries, before the test body ran), passed on retry, and passed 3 of 3 run alone.
- **Classic:** the 30 connection-related core classes passed 322 of 322 executions before V3 became the default; since then `OptionsTests`, `PingTests` and `ReconnectTests` (including `testWriterFilterTiming`) pass with `-PconnectionImplementation=Classic`.
- **`OutboundBufferTests`, 15 tests, no server:** the bytes V3 writes for a publish are identical to the classic encoding (plain, reply, headers, headers plus reply, empty payload, non-ASCII subject and reply, a 123 KB payload); SUB/UNSUB/PING encoding; PONG and resubscribe routing; filter on disconnect; copy at publish; discard when full; full queue timeout; reconnect buffer; client-side limits; closed; blocks of `bufferSize` with an oversized entry on its own; a flush entry ending its block; the writer taking one block per pass and the rest surviving a disconnect.
- **Seen once, not reproduced:** `AuthTests.testReconnectAfterAccountAuthenticationExpired` failed once on V3 (the initial connect landed on server 2 instead of the mock server, before any reconnect); 40 of 40 runs of the four `testReconnectAfter*` tests passed on each implementation afterwards.
- **Full suites, 2026-10-06, V3 default:** Linux (ext4 copy) and Windows (Windows-side copy), all modules: core 610 (1 skipped, `testWriterFilterTiming`), JetStream 470, KV 35, OS 17, Service 16, 0 failures on both. The first Linux run found that V3 did not apply the control line limit to SUB (`NatsMessageTests.testBigProtocolLine`, 5 of 5); fixed in `OutboundBuffer.sub`, test `OutboundBufferTests.testSubControlLineLimit`. `ConnectTests.testAsyncConnectionWithReconnect` failed one attempt in that run, passed on retry and 5 of 5 alone; cause not found.

### A5. What V3 does not change yet — the inbound path

The reader, `deliverMessage`, the dispatcher and the subscription queues are classic, which is why the subscriber and request numbers are unchanged. From the code, per inbound message: a `String` sid (`NatsConnectionReader.java:590`), a `ConcurrentHashMap<String, ...>` lookup of that sid (`NatsConnection.java:2174`), an `IncomingMessageFactory` and a `NatsMessage`, a `LinkedBlockingQueue` node plus its lock and possibly a wake-up per message (`ConsumerMessageQueue.push`), then on the dispatcher thread a second `ConcurrentHashMap` lookup of the handler by sid (`NatsDispatcher.java:88`). Candidates for a phase 2, in order of expected value, none measured yet:

1. Dispatcher queue: replace the `LinkedBlockingQueue` with a queue built for one producer (the reader thread is the only producer for a connection), drained in batches by the consumer, with one wake-up per read buffer instead of per message.
2. Cache the handler on the subscription at subscribe time instead of looking it up per message.
3. Key subscriptions by a numeric sid parsed from the line; create the `String` sid lazily for `Message.getSID()`.

Also open: the large-message ceiling (section 2) — about 60% of Go, not explained by the socket write path.

---

## 4. Part B — surgical changes to the classic implementation

**Applied 2026-10-06 (working tree, not committed): B-S1, B-S2, B-S3, B-S6.** B-S4 dropped (D3); B-S5 and B-S7 stay as written. Ordered by value for the risk. Each says whether anything observable changes.

**B-S1. Size the protocol buffer to the control line.** `NatsMessage.java:122`: drop `+ headerAndDataLen` from the `ByteArrayBuilder` initial size. Measured in a scratch copy: publisher allocation per 1 KB publish 1,360 → 336 bytes, per 16 B publish 400 → 336 bytes; throughput unchanged within run-to-run spread (the large sizes are bound elsewhere). The win is GC pressure for applications with large payloads. Behavior change: none. One line. **Applied.**

**B-S2. `tryingToConnect.compareAndSet` instead of get-then-set** at `NatsConnection.java:220, 322, 408, 747`. Two threads (an I/O error task and a `forceReconnect`, or two I/O errors) can both pass the check today. Behavior change: the second caller returns instead of running a concurrent transition, which is what the code intends. Four lines. **Applied.** No test: it is a race between two threads entering, and a test would only prove the timing of one run.

**B-S3. Lock-free `outgoingPendingMessageCount()` / `outgoingPendingBytes()`.** Drop `closeSocketLock` (`NatsConnection.java:2851, 2868`) and `writerLock` (`NatsConnectionWriter.java:279-298`); the queue counters are already atomics. Today the getters can block for an entire reconnect campaign. This is `TODO.md` TBD #4 (V2 PR #1632). Behavior change: the getters stop blocking. **Applied**, with `NatsConnectionImplTests.testOutgoingPendingGettersDoNotWaitForCloseSocketLock` (fails 5 of 5 against the unchanged code, passes with the change). The comment in `testOutgoingPendingCountCoverage` that said the getters cannot be read during a reconnect was removed.

**B-S4. Dropped (D3).** Was: fail a classic connect attempt as soon as the handshake `-ERR` is processed. D3 decided that an auth error counted by the double rule keeps classic's timing, so classic needs no change here.

**B-S5. Shrink the writer's send buffer after an oversized message.** `NatsConnectionWriter.java:153` grows the 64 KB buffer to the largest message ever sent and keeps it. Behavior change: none; memory only.

**B-S6. `sendPing` must not leak its pong future when queueing throws.** `NatsConnection.java:2034-2042`: `pongQueue.add` happens before `queueOutgoing`, which can throw (queue full / busy / interrupted); the orphaned future then consumes the next PONG owed to a flush. Same bug class as the one fixed in `RTT`. Behavior change: none intended. **Applied**, with `NatsConnectionImplTests.testSendPingDoesNotLeavePongFutureWhenQueueingFails` (fails 5 of 5 against the unchanged code, passes with the change).

**B-S7. Not measured, listed for completeness.** The double lock per publish (`editLock` plus the `LinkedBlockingQueue` lock) is the likely cause of classic's four-thread collapse (5.16M → 1.47M msgs/s); removing `editLock` from the push path needs another way to keep `filter()` and `clear()` safe. Not a one-liner, and V3 is the cleaner answer to it.

Recommendation (applied): B-S2, B-S3 and B-S6 regardless of V3; B-S1 while classic is the default. B-S5 and B-S7 only if V3 is not pursued.

---

## 5. Part C — review of the open docs that touch the connection

All 19 docs in `~development-todo/` were checked (a read-only pass; line numbers below re-verified against the current code). Docs with nothing on the connection: `ACCOUNT_PUSH_PULL_EXPORTS.md`, `ORBIT_CONSTANTS.md`, `PLAN_FLUENT_SUBSCRIBE_BUILDER.md`, `PLAN_REMOVE_LEGACY_PULL_METHODS.md`, `PLAN_V2_FACADE.md`.

**`PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md`.** Removes `NatsMessage.next` with a reusable `MessageBatch`, and (2026-09-07) serializes a message at publish hand-off, with markers as byte patterns.
- If V3 is adopted, V3 does what this plan wants, by a different route: no `next`, no `MarkerMessage` (`END_RECONNECT` becomes the lane's held-back flag, so no marker byte pattern is needed), serialized at publish. Its four open questions are answered by V3: (1) serialization happens on the caller's thread inside `publish`, under the buffer lock; (2) marker patterns are not needed; (3) byte accounting is exact (real sizes per entry); (4) the fields read after enqueue — `isFilterOnStop` (read by `WriterMessageQueue.filter()` on stop), `flushImmediatelyAfterPublish`, and the size read by `canQueueDuringReconnect` — are carried as per-entry metadata and a lane flag.
- If classic stays the only implementation, the plan is still valid for classic. Its line numbers have drifted: `next` is now `NatsMessage.java:46` (doc :42), `toDetailString` `next=` `:390` (doc :365), `nextToString` `:419` (doc :394), `accumulate` `WriterMessageQueue.java:108` (doc :103), `sendMessageBatch` `NatsConnectionWriter.java:131` (doc :127), the `END_RECONNECT` check `:138` (doc :134), `msg = msg.next` `:187` (doc :183), the `run()` call sites `:213, :216` (doc :209, :212).
- One fact the plan does not have: the immutability gap it targets is real for headers in a specific way — the copy taken at publish is shallow per key (`Headers.java:76`), and the data array is not copied at all (`InternalPublishableMessage.java:21`).
- Recommendation: mark it superseded by this plan if V3 goes ahead (decision D7); do not implement `MessageBatch` in classic in the meantime.

**`REQUEST_BEHAVIOR_IMPROVEMENT.md`** (the Current Implementation item). V3 overrides the six-argument `requestAsync` (it has to: classic's calls the private `_publish`). So while both implementations exist, a change to the request path — the plan's Step 6a `requestFuture(...)` extraction in particular — must be made in both classes. Two ways, in order of preference: (1) make Step 6a's extracted method `protected` and take the publish as a parameter, so V3 calls it instead of copying the body; (2) land the request work first and rebase V3. Also, the plan's #1596 close window (subscriptions invalidated, then requests cancelled, then `CLOSED`, `NatsConnection.java:877, 891, 896`) exists in V3's `finalClose` in the same order on purpose; the fix should go into both. The plan's Step 6 code still uses `Duration` where the API is now `long timeoutMillis` (`:1497`); Step 8 was already refreshed.

**`CANCEL_ACTION_REVISIT.md`.** No interaction: `deliverReply` is inherited by V3. Its line citations have drifted (`deliverReply` is `NatsConnection.java:1677`), and it says there is no public `requestAsync(..., CancelAction)`; the six-argument one at `:1622` is public.

**`PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md`.** V3 copies the creation of the ping and cleanup tasks (classic does it inside `tryToConnect`, which V3 replaces). A change to the cleanup task must be made in both, until the creation is moved into a shared method. Its line citations have drifted (cleanup task `:632-635`, async default `:1655`, blocking default `:1503`).

**`PLAN_CORE_JETSTREAM_BOUNDARY.md`.** §8's interface-vs-concrete question is A1 option 2. V3 adds no new JetStream reach into core. Its M2 item (`isClosed`/`isClosing` → `getStatus()`) interacts with V3's volatile `closeRequested`: if M2 adds a closing status, V3 should set it.

**`INTERFACES_REPORT.md`.** The `Connection` row ("refactored to concrete `NatsConnection`") is the baseline for A1 option 2; `DataPort` typed on `NatsConnection` (`DataPort.java:22`) is one of the costs listed there.

**`EXCEPTIONS_AUDIT.md`.** A21's contracts hold in V3: a full, busy or interrupted enqueue throws `IllegalStateException` with the same `OUTPUT_QUEUE_*` messages, and `requestAsync` unregisters its future when the publish throws.

**`TEST_TRACKING.md`.** The `testToken` / `testEncodedPassword` entries: see the correction in A2.2 — the mechanism is more likely lost `-ERR` bytes than an in-client race, and V3 does not change that. `testThatAuthErrorIsCleared` is newly identified as timing-dependent on classic (B3a); worth an entry there once D3 is decided.

**`TODO.md`.** TBD #4 (lock-free pending getters) is B-S3, and V3 already does it (B4). The "NatsConnection.drain timeout <= 0" note is unaffected. "move `buildProtocolConnectOptionsString` out of Options" — V3 still calls it, unchanged. Two notes there are stale: "SocketDataPortWithWriteTimeout — combine with SocketDataPort" (no such class; `SocketDataPort` already has the write-timeout watch) and "Is Options the correct place for `buildDataPort()`" (it does not exist).

**`VIRTUAL_THREAD_DISPATCHER_EXAMPLE.md`.** Inbound dispatch, so phase 2 territory (A5). `NatsDispatcherWithExecutor` already exists for the "dispatch to an executor" option.

**`NEXTMESSAGE_STATUS_EXCEPTION_ANALYSIS.md`, `V2_V3_TEST_METHOD_AUDIT.md`, `INTERFACE_DEFAULT_METHODS_AUDIT.md`, `OSGi_JPMS_TODO.md`.** Nothing to change. A new connection class placed in a new or exported package would interact with OSGi/JPMS O3; V3 lives in `.impl` like the classic class.

---

## 6. Open decisions

Options in my order of preference.

### Open

- **D1. How V3 is offered.** (1) Subclass plus Options setting, as implemented; (2) resurrect `Connection` with two implementations, when V3 becomes the default; (3) internal engines. See A1.
- **D5. Phase 2, the inbound path (A5).** Whether to do it in V3 next. It is where the subscriber-side throughput is.
- **D7. `PLAN_MESSAGE_NEXT_LINKED_LIST_FIX.md`.** (1) Mark superseded by this plan if V3 goes ahead; (2) keep it for classic only if V3 does not.
- **D8. The request path shared with `REQUEST_BEHAVIOR_IMPROVEMENT.md`.** See Part C; (1) a protected `requestFuture(...)` taking the publish as a parameter; (2) land the request work first and rebase V3.
- **D13. A test for the stale-report race.** Needs a hook that stalls an old reader or writer between its flag check and its report; without it the V3 source check is untested.
- **D14. User-facing text for `connectionImplementation`.** README and migration guide entries, not written yet.

### Done

- **DONE — D2. The default for `connectionImplementation`.** Decided 2026-10-06: **V3 is the default**, so the regular test runs and main builds exercise it. `Classic` is chosen with `connectionImplementation(ConnectionImplementation.Classic)`, the properties key, or for a test run `-PconnectionImplementation=Classic`; `build-variants.yml` covers it with `OTHER_IMPLEMENTATION: Classic`.
- **DONE — D3. The double auth error rule.** Decided 2026-10-06: two consecutive identical auth errors from a server stop trying — a client parity specification — and the time between them matters, because a server can fail once and accept on a later try. So V3 holds an attempt rejected for authentication to its connection timeout when the rejection counts toward the rule (reconnect attempts, and initial attempts with `reconnectOnConnect`); every other failure stays fast. `testThatAuthErrorIsCleared` and `testCloseOnReconnectWithSameError` both pass on V3 unchanged.
- **DONE — D4. Names.** Decided 2026-10-06: kept as is — `ConnectionImplementation` with `Classic` / `V3`, `connectionImplementation(...)`, property `io.nats.client.connectionImplementation`, class `NatsConnectionV3`.
- **DONE — D6. Part B for classic.** Decided 2026-10-06: B-S1, B-S2, B-S3, B-S6 applied. B-S4 dropped (D3).
- **DONE — D9. Tests that use classic-only hooks.** Decided 2026-10-06: the tests tell the implementation apart with `instanceof NatsConnectionV3` or `options.connectionImplementation()`; no test hooks were added to the classic class. `WriterTestControl` pauses and resumes the writer for either implementation (used by `testDiscardedMessageFastProducer`, `testOutgoingPendingCountCoverage`, `testSendPingDoesNotLeavePongFutureWhenQueueingFails`); `NatsConnectionV3.pauseWriterForTest()` / `resumeWriterForTest()` stop and restart its writer's taking without changing routing; `testWriterFilterTiming` is skipped on V3.
- **DONE — D10. CI for the other implementation.** Decided 2026-10-06: `build-windows.yml` became `build-variants.yml`, a post-merge matrix of Windows on the default, Windows on the other implementation, and Linux on the other implementation (`OTHER_IMPLEMENTATION: Classic`, since V3 is the default). With D3 and D9 done, no test is expected to fail on the "other" jobs.
- **DONE — D11. V2.** Decided 2026-10-06: V2 gets bug fixes only, and those are in `V2_BUGS_FROM_CONNECTION_REVIEW.md`.
- **DONE — D12. B-S8 for classic: not applied.** Decided 2026-10-07. The per-run reader/writer flag was applied on 2026-10-06 and removed on 2026-10-07: the race needs a thread to outlive the `connectionTimeout` join in `closeSocketImpl` after its socket is closed, it was never reproduced, and the flag added complexity for that case only. Removed from V2 as well (entry 2 of `V2_BUGS_FROM_CONNECTION_REVIEW.md`).
- **DONE — D15. A byte limit on the outgoing buffer.** Decided 2026-10-06: no new setting. `bufferSize` bounds each write and the loss on a failed write, as in classic (A2.1, A2.6); what waits is bounded by `maxMessagesInOutgoingQueue`, as in classic.

Unexplained, not decisions: the large-message ceiling (section 2, about 60% of Go, the socket write path ruled out), and the mechanism of the `testToken` / `testEncodedPassword` flake (A2.2, unverified).

---

## 7. Files in the working tree

New:

- `core/src/main/java/io/synadia/client/ConnectionImplementation.java` — the enum.
- `core/src/main/java/io/synadia/client/impl/NatsConnectionV3.java` — the connection.
- `core/src/main/java/io/synadia/client/impl/OutboundBuffer.java` — lanes, encoding, backpressure.
- `core/src/main/java/io/synadia/client/impl/OutboundWriter.java` — the writer thread, one per socket.
- `core/src/main/java/io/synadia/client/impl/PrefixedDataPort.java` — serves bytes read past the handshake PONG to the reader.
- `core/src/test/java/io/synadia/client/impl/OutboundBufferTests.java` — 12 tests.

Changed:

- `Options.java`, `OptionsBuilder.java`, `OptionsProperties.java` — the `connectionImplementation` setting, its property, its copy in the builder's copy constructor, and the system-property default.
- `impl/NatsImpl.java` — builds `NatsConnectionV3` when the setting says so.
- `OptionsTests.testConnectionImplementation` — default, setter, null, copy, properties key, unrecognized value.
- `core/src/test/java/io/synadia/client/impl/WriterTestControl.java` (new) — pause and resume the writer on either implementation (D9). `ErrorListenerTests`, `NatsConnectionImplTests` and `ReconnectTests` updated to use it or to skip on V3.
- `build.gradle` — for testing, `-PconnectionImplementation=<name>` is passed to the test JVM as the system property. Without the flag nothing changes.
- `.github/workflows/build-windows.yml` renamed to `build-variants.yml` (three variants, D10); `.github/scripts/affected-modules.sh` updated for the new name.

Classic code changed for V3, with no behavior change in classic: `NatsConnection.handleCommunicationIssue(NatsConnectionReader source, Exception)` (new, calls the existing one) and `NatsConnectionReader` passing itself to it. The Part B fixes are listed in Part B. Not changed otherwise: the classic writer's queues.

Not done: README / migration guide text for the new option, examples.

---

## 8. How the measurements were taken

- Throughput, allocation, request and bad-auth numbers: a scratch benchmark (`Bench.java`, kept outside the repo) using only the public API, one scenario per JVM run, `-Xmx2g`, classic and V3 alternated, three repetitions, median reported. Publish runs warm up with 200,000 messages, then time from the start of publishing to the return of `flush(0)`, which waits for the server's PONG — so the time includes every byte reaching the server. Subscriber runs time until the last message is counted by a dispatcher with no pending limits. Allocation is `com.sun.management.ThreadMXBean.getThreadAllocatedBytes` for the publishing thread only. Bad auth: a server started with `--auth`, a wrong token, `maxReconnects(0)`, time until `Nats.connect` throws.
- B-S1 was measured in a second scratch copy with only that one-line change applied to classic.
- Go reference: `nats bench pub` and `nats bench service` (CLI v0.3.2) against the same server.
- Raw socket test: a scratch program writing 4 GiB to a local Java reader that discards, three repetitions per variant.
- Environment: WSL2, 16 cores, 31 GB, Java 21, nats-server v2.16.0-dev. The absolute numbers are this machine's; the ratios are the result.
