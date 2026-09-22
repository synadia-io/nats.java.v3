# Plan: remove `NatsMessage.next` — make `accumulate` return an explicit batch

## Problem

`NatsMessage` carries a `protected NatsMessage next` field (`NatsMessage.java:42`) that is pure writer-queue plumbing. During outgoing writes, `WriterMessageQueue.accumulate()` polls a head message off the queue and chains the following messages onto it via `cursor.next = peeked`, then returns the head — which is secretly a linked list. `NatsConnectionWriter.sendMessageBatch()` walks `msg = msg.next` until null to write the batch.

Smells this creates:
- A message object owns list linkage for a structure it doesn't conceptually belong to (leaked concern / single-responsibility violation).
- The "batch" is implicit in the return type: `accumulate` returns one `NatsMessage` that callers must *know* to walk. The type doesn't express "this is a list."
- `toString`/`toDetailString` have to account for `next` (`nextToString()`), polluting debug output.
- The reconnect control signal (`END_RECONNECT`) is smuggled into the same chain as real messages and pattern-matched *inside* the byte-copy loop (`NatsConnectionWriter.java:134`), relying on the `msg == END_RECONNECT` check firing before anyone calls `getProtocolBab()` on a marker. Fragile.

## TODO added 2026-09-07 — serialize the message at publish hand-off

Scott's ask, to be done **as part of this plan** because the two are the same change: once the queue stops holding a chain of live `NatsMessage` objects, it can hold serialized bytes instead.

**What:** serialize a user message to its wire form the moment it is handed to the publish API, not when the writer drains it.

**Why:** today the client holds the caller's `NatsMessage` — including its `Headers` — in the writer queue until the writer gets to it. Anything the caller still has a reference to, it can still mutate in that window, so what goes on the wire is not necessarily what was passed to `publish`. Serializing at hand-off makes the published message immutable from the caller's point of view by construction, rather than by asking callers not to touch it. Headers are the specific concern.

**Consequence for the queue:** it stops being a queue of one thing. It has to carry at least three kinds:

| kind | example | form on the queue |
|---|---|---|
| internal control messages | `MarkerMessage` (`END_RECONNECT`, `POISON_PILL`) | not serializable — stays an object, must remain identity-comparable |
| internal protocol messages | `ProtocolMessage` (PING/PONG/SUB/UNSUB/CONNECT) | serializable at enqueue like user messages |
| user messages | published `NatsMessage` | serialized at hand-off |

So the batch type this plan introduces has to model that variety rather than assume a homogeneous `NatsMessage` list. That is the design decision to settle first — the marker case is the awkward one, since `sendMessageBatch` currently identifies `END_RECONNECT` by reference (`msg == END_RECONNECT`) and a serialized form has no identity to compare.

**Marker representation — Scott's direction, 2026-09-07.** Make the markers serialized bytes too, so the queue really is homogeneous and nothing has to stay an object for identity's sake. Every real outgoing message begins with one of the client-sent ops in `NatsConstants` (`:73-98`) — `CONNECT`, `SUB`, `PUB`, `HPUB`, `UNSUB`, `PING`, `PONG` — and therefore has a minimum length and a known leading byte pattern. A marker can be a short byte sequence that no legal outgoing message can start with, so it is recognizable at the head of the batch by content rather than by reference.

That removes the awkward case above: `sendMessageBatch` stops doing `msg == END_RECONNECT` and instead recognizes the marker pattern, and `MessageBatch` no longer needs to model "object or bytes". Worth confirming while implementing: the ops the *client* sends are the only ones that can appear on this queue (`MSG`/`HMSG`/`INFO`/`+OK`/`-ERR` are inbound), so the set of leading patterns a marker must avoid is small.

**Open questions, none decided:**
- Where the serialization happens: inside `publish` on the caller's thread (moves the cost to the caller, keeps the queue cheap) or at enqueue.
- Which byte pattern each marker gets, and where those constants live (`NatsConstants` alongside the ops is the obvious home).
- Whether the byte/count accounting in `accumulate` gets simpler (it would be counting real bytes rather than estimating from a message) or whether anything depends on the current estimate.
- Whether anything currently reads a queued message's fields after enqueue and before write, which serializing would break.

## Current mechanism (for reference)

- `WriterMessageQueue.accumulate(maxBytes, maxMessages, timeoutMillis)` — `WriterMessageQueue.java:103`:
  - `_poll` the head (`MessageQueueBase.java`); returns `null` on not-running / timeout / `POISON_PILL`.
  - If head is `null` or a `MarkerMessage`, return it as-is (an `END_RECONNECT` head is returned directly).
  - Else peek/poll more, chaining via `cursor.next = peeked`, stopping on: byte cap, message-count cap, `flushImmediatelyAfterPublish`, empty queue, or a `MarkerMessage` (appended to the chain unless it's `POISON_PILL`).
  - Decrement `length` / `sizeInBytes` counters by the accumulated totals.
- `NatsConnectionWriter.sendMessageBatch(head, ...)` — `NatsConnectionWriter.java:127`: walks `msg = msg.next`, copying each into `sendBuffer`; `msg == END_RECONNECT` switches mode to `Normal` and breaks.

## Constraint that led to the intrusive list

The writer is the hot outgoing path, so the intrusive list avoided allocating a second structure per batch. Two facts make that argument weak and make a clean fix free:

1. The queue is a `LinkedBlockingQueue` (`MessageQueueBase.java:18`), so the **enqueue side already allocates a node per message**. The `next` trick only saves an allocation on the *drain* side.
2. `accumulate()` → `sendMessageBatch()` is strictly **single-threaded and sequential** — one writer thread calls one then the other, then loops (`NatsConnectionWriter.run()`). Nothing else touches the batch.

Fact 2 is the key: a **reusable, writer-thread-confined batch buffer** gives zero steady-state allocation without the intrusive field.

## Proposed design

Introduce a small, writer-owned, reused batch and delete `next`.

```java
// new class in io.synadia.client.impl
// writer-thread-confined; must not outlive one accumulate→send cycle
final class MessageBatch {
    final ArrayList<NatsMessage> messages = new ArrayList<>();
    boolean endReconnect;                 // a terminal END_RECONNECT marker was seen
    void reset() { messages.clear(); endReconnect = false; }   // clear() keeps capacity
}
```

`accumulate` fills a caller-supplied batch instead of chaining, and separates the control signal from the data:

```java
boolean accumulate(long maxBytes, long maxMessages, @Nullable Long timeoutMillis, MessageBatch batch)
        throws InterruptedException {
    batch.reset();
    if (!isRunning()) return false;

    NatsMessage head = _poll(timeoutMillis, TimeUnit.MILLISECONDS);
    if (head == null) return false;                    // not running / timeout / poison → nothing to send
    if (head instanceof MarkerMessage) {               // END_RECONNECT arriving as the head
        batch.endReconnect = true;
        return true;
    }

    if (maxBytes < 1) maxBytes = Long.MAX_VALUE;
    long accumulatedMessages = 1;
    long accumulatedSize = head.getSizeInBytes();
    batch.messages.add(head);

    NatsMessage last = head;
    while (!last.flushImmediatelyAfterPublish && accumulatedMessages < maxMessages) {
        NatsMessage peeked = queue.peek();
        if (peeked == null) break;
        if (peeked instanceof MarkerMessage) {
            queue.poll();
            if (peeked != POISON_PILL) batch.endReconnect = true;   // END_RECONNECT, not written
            break;
        }
        long size = peeked.getSizeInBytes();
        if (accumulatedSize + size > maxBytes) break;
        queue.poll();
        accumulatedMessages++;
        accumulatedSize += size;
        batch.messages.add(peeked);
        last = peeked;
    }

    length.addAndGet(-accumulatedMessages);
    sizeInBytes.addAndGet(-accumulatedSize);
    return true;
}
```

The writer owns one reusable `MessageBatch` and reuses it for both `normalOutgoing` and `reconnectOutgoing` (never accumulated concurrently):

```java
// NatsConnectionWriter field
private final MessageBatch batch = new MessageBatch();

// in run()
boolean have = (mode.get() == Mode.Normal)
    ? normalOutgoing.accumulate(sendBufferLength.get(), MAX_MESSAGES_IN_NETWORK_BUFFER, outgoingTimeoutMillis, batch)
    : reconnectOutgoing.accumulate(sendBufferLength.get(), MAX_MESSAGES_IN_NETWORK_BUFFER, reconnectTimeoutMillis, batch);
if (have) sendMessageBatch(batch, dataPort, stats);
```

```java
void sendMessageBatch(MessageBatch batch, DataPort dataPort, StatisticsCollector stats) throws IOException {
    writerLock.lock();
    try {
        int sendPosition = 0;
        int sbl = sendBufferLength.get();
        for (NatsMessage msg : batch.messages) {
            ... unchanged byte-copy / mid-batch flush logic ...
        }
        if (sendPosition > 0) { dataPort.write(sendBuffer, sendPosition); stats.registerWrite(sendPosition); }
        if (batch.endReconnect) mode.set(Mode.Normal);   // pulled OUT of the write loop
    }
    finally { writerLock.unlock(); }
}
```

Note the write loop no longer contains the `msg == END_RECONNECT` special case — markers never enter `batch.messages`, so the loop only ever sees writable messages.

## File-by-file changes

- **New** `core/src/main/java/io/synadia/client/impl/MessageBatch.java` — the class above.
- `NatsMessage.java` — delete the `next` field (`:42`) and its `// for accumulate` comment, delete `nextToString()` (`:394`), remove the `next=` line from `toDetailString()` (`:365`). `flushImmediatelyAfterPublish` stays (still read by `accumulate`).
- `WriterMessageQueue.java` — change `accumulate` signature to take a `MessageBatch` and return `boolean`; body as above. Update the method comment (drop the "Use the NatsMessage.next field" note). `filter()` / `clear()` untouched (`filter` already uses its own `ArrayList`).
- `NatsConnectionWriter.java` — add the reusable `MessageBatch` field; update `run()` call sites (`:209`, `:212`); change `sendMessageBatch` to iterate the batch and move the `END_RECONNECT` handling after the loop; drop the `msg == END_RECONNECT` check inside the loop (`:134`) and the `msg = msg.next` advance (`:183`).

## Edge cases to preserve

- `POISON_PILL`: never written, never signals reconnect. As head → `_poll` returns `null` → `accumulate` returns `false`. As a peeked element → polled and dropped, loop breaks, `endReconnect` stays false. (Matches today.)
- `END_RECONNECT` as head vs. trailing: both set `batch.endReconnect = true`; head case yields an empty `messages` list + flag.
- `flushImmediatelyAfterPublish`: still stops accumulation after the triggering message (checked on `last`), and `sendMessageBatch` still flushes mid-batch per message.
- Counter decrements (`length` / `sizeInBytes`) unchanged.
- Ordering / single-reader invariant unchanged — still one writer thread.

## Testing

- Existing writer / publish / reconnect tests should pass unchanged (behavior is identical; only the batch representation changes).
- Add/confirm coverage for: multi-message accumulation up to the byte cap and the message-count cap; `flushImmediatelyAfterPublish` cutting a batch short; `END_RECONNECT` as head and as trailing element switching mode back to `Normal`; `POISON_PILL` during pause/drain producing no write.
- Sanity: grep confirms `next` has no other readers (only `NatsMessage`, `WriterMessageQueue`, `NatsConnectionWriter` reference it today).

## Alternatives considered

- **Return a fresh `List` each call** — simplest; one array per *batch* (not per message), so still far cheaper than per-message allocation. Acceptable if we prefer an obvious immutable-ish return over a mutable reused object. Rejected only to keep the hot-path allocation profile identical to today.
- **Wrap each queued message in a `Node`** — per-*message* allocation, strictly worse than the intrusive list. Rejected.

## Risk

Low. Change is confined to three impl classes on a single-threaded path; no public API change; no allocation regression (reused buffer). Main care point is faithfully preserving the marker/poison/flush edge cases enumerated above.
