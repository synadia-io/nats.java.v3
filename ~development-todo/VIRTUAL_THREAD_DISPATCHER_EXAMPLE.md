# Dispatcher: Non-blocking Handler Dispatch Examples

## Current behavior (blocks on user callback)

```java
// Dispatcher loop - blocks until handler returns
while (running) {
    Message msg = queue.take();  // platform thread, fine
    MessageHandler handler = lookupHandler(msg);
    handler.onMessage(msg);      // BLOCKS here if user handler is slow
}
```

## Option 1: Virtual thread per handler invocation

```java
// Executor backed by virtual threads - unlimited concurrency, no pooling overhead
private final ExecutorService handlerExecutor =
    Executors.newVirtualThreadPerTaskExecutor();

while (running) {
    Message msg = queue.take();
    MessageHandler handler = lookupHandler(msg);
    handlerExecutor.submit(() -> handler.onMessage(msg));
    // dispatcher immediately returns to draining the queue
}
```

**Pros:** Simplest change. Each callback runs on its own virtual thread. If the user's
handler does I/O or sleeps, it only blocks a virtual thread (cheap). The dispatcher
never stalls.

**Cons:** No backpressure. A slow handler under high message rate means unbounded
virtual threads and growing memory. Message ordering per-subject is not guaranteed
since handlers run concurrently.

## Option 2: Virtual threads with bounded concurrency

```java
// Semaphore limits how many handler calls are in-flight
private final Semaphore permits = new Semaphore(maxConcurrentHandlers);
private final ExecutorService handlerExecutor =
    Executors.newVirtualThreadPerTaskExecutor();

while (running) {
    Message msg = queue.take();
    MessageHandler handler = lookupHandler(msg);
    permits.acquire();  // blocks dispatcher if too many in-flight — backpressure
    handlerExecutor.submit(() -> {
        try {
            handler.onMessage(msg);
        } finally {
            permits.release();
        }
    });
}
```

**Pros:** Backpressure. Won't blow up memory under load. Dispatcher blocks only when
the user can't keep up, which is the right signal.

**Cons:** Still no ordering guarantee. The `acquire()` blocks the dispatcher loop,
which is intentional backpressure but means all addresses sharing the dispatcher stall
together.

## Option 3: Per-address serial executor on virtual threads

```java
// Each address gets its own single-threaded virtual executor,
// preserving message order per-address while allowing cross-address concurrency.
private final Map<String, ExecutorService> addressExecutors =
    new ConcurrentHashMap<>();

private ExecutorService executorFor(String address) {
    return addressExecutors.computeIfAbsent(address, k ->
        Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
}

while (running) {
    Message msg = queue.take();
    MessageHandler handler = lookupHandler(msg);
    executorFor(msg.getSubject()).submit(() -> handler.onMessage(msg));
}
```

**Pros:** Preserves per-subject ordering. Cross-subject concurrency. Dispatcher never
blocks. Each per-address executor uses a virtual thread, so idle addresses cost nothing.

**Cons:** More complex. Still no global backpressure without adding a semaphore.
Cleanup needed when addresses are unsubscribed.

## Option 4: Hybrid — per-address queue + virtual thread pool + backpressure

```java
private final Semaphore globalPermits = new Semaphore(maxConcurrentHandlers);
private final Map<String, ExecutorService> addressExecutors =
    new ConcurrentHashMap<>();

private ExecutorService executorFor(String address) {
    return addressExecutors.computeIfAbsent(address, k ->
        Executors.newSingleThreadExecutor(Thread.ofVirtual().factory()));
}

while (running) {
    Message msg = queue.take();
    MessageHandler handler = lookupHandler(msg);
    globalPermits.acquire();
    executorFor(msg.getSubject()).submit(() -> {
        try {
            handler.onMessage(msg);
        } finally {
            globalPermits.release();
        }
    });
}
```

**Pros:** Per-subject ordering + global backpressure + dispatcher only blocks under
overload. Virtual threads mean idle subjects cost nothing.

**Cons:** Most complex. Same head-of-line blocking concern on the semaphore as Option 2.
