# JNats Interfaces — Documentation & Implementation Tree

Source: `nats.java` (`src/main/java/io/nats/**`)

This report enumerates every Java `interface` declared under the JNats source tree and shows a tree of implementations for each. Trees use these conventions:

- `«interface»`   — an interface (sub-interface relationships are shown by indentation)
- `«abstract»`   — an abstract class
- everything else is a concrete class
- `→` marks where a class implements / extends the parent
- A name in parentheses (e.g. `(pkg-private)`) reflects the actual JDK visibility in source

Inner / nested types are shown with their enclosing type, e.g. `NatsJetStream.AsyncMessageHandler`.

---

## 1. Interface inventory by package

### `io.nats.client` (public client API)

The **v3 Status** column tracks each interface's disposition in v3. `TBD` = not yet triaged. (Only `io.nats.client` is triaged here; the other packages below are left alone for now.)

| Interface | Extends | Purpose (from Javadoc) | v3 Status |
|---|---|---|---|
| `AuthHandler` | — | Challenge-response auth handler that keeps NKey seeds outside the library. | Leave as-is |
| `BaseConsumerContext` | — | Shared base for the simplified consumer context. | TBD |
| `BaseMessageConsumer` | `AutoCloseable` | Core interface that replaces a subscription for simplified consumers. | TBD |
| `Connection` | `AutoCloseable` | The heart of the client — one connection per socket, with reader/writer/timer/dispatch threads. | Not an interface — refactored to concrete `NatsConnection` |
| `ConnectionListener` | — | Tracks status changes on a `Connection`. | Leave as-is |
| `Consumer` | — | Common surface of `Dispatcher` and `Subscription`; slow-consumer mechanics. | **Removed** |
| `ConsumerContext` | `BaseConsumerContext` | Convenient interface around a defined JetStream pull consumer. | TBD |
| `Dispatcher` | `Consumer` | Groups subscriptions onto a single delivery thread with one `MessageHandler`. | P1 — under consideration to change |
| `ErrorListener` | — | Sink for server `-err` messages, exceptions, slow consumers, fast producers. | Leave as-is |
| `FetchConsumer` | `MessageConsumer` | Simplified consumer; `nextMessage()` returns `null` when done. | TBD |
| `IterableConsumer` | `MessageConsumer` | Simplified consumer with endless iterator-style consume. | TBD |
| `JetStream` | — | JetStream publish + subscribe context. | Not an interface — refactored to concrete `JetStream` |
| `JetStreamManagement` | — | Stream and consumer CRUD (recommended way to manage JS resources). | Not an interface — refactored to concrete `JetStreamManagement` |
| `JetStreamReader` | — | Simple iterative access over a pull consumer (superseded by `ConsumerContext`). | Leave as-is |
| `JetStreamSubscription` | `Subscription` | A `Subscription` bound to a JetStream consumer. | Not an interface (concrete class) |
| `KeyValue` | — | KV bucket value access. | P2 — under consideration to change |
| `KeyValueManagement` | — | KV bucket creation / mgmt. | P2 — under consideration to change |
| `Message` | — | Wrapper for an incoming message (intended for internal implementation only). | Leave as-is |
| `MessageConsumer` | `AutoCloseable` | Core simplified-consumer surface. | Leave as-is |
| `MessageHandler` | — | Callback used by `Dispatcher` for delivery. | Leave as-is |
| `MessageInfoHandler` | — | Callback receiving a `MessageInfo`. | Leave as-is |
| `NatsSystemClockProvider` | — | Pluggable clock. | P3 — change gated on other behavior changes |
| `ObjectStore` | — | Object Store access (recommended via `ObjectStoreManagement`). | P2 — under consideration to change |
| `ObjectStoreManagement` | — | Object Store creation / mgmt. | P2 — under consideration to change |
| `OrderedConsumerContext` | `BaseConsumerContext` | Simplified surface for an ordered consumer. | Leave as-is |
| `ReadListener` | — | Tracks raw messages off the wire — intended for debugging. | Leave as-is |
| `ReconnectDelayHandler` | — | User-supplied reconnect back-off. | Leave as-is |
| `ServerPool` | — | User-supplied list / order of servers to (re)connect to. | P3 — change gated on other behavior changes |
| `Statistics` | — | Read-only view of per-connection metrics. | Leave as-is |
| `StatisticsCollector` | `Statistics` | Writable collector incremented by the connection. | Leave as-is |
| `StreamContext` | — | Stream and per-stream consumer operations. | Leave as-is |
| `Subscription` | `Consumer` | Synchronous or dispatcher-owned subscription on a single subject/queue. | P1 — under consideration to change |
| `TimeTraceLogger` | — | Optional debug hook for connection-time tracing. | **Removed** |

### `io.nats.client.api`

| Interface | Extends | Purpose |
|---|---|---|
| `Watcher<T>` | — | Generic update-watching callback. |
| `KeyValueWatcher` | `Watcher<KeyValueEntry>` | KV-typed watcher. |
| `ObjectStoreWatcher` | `Watcher<ObjectInfo>` | Object-store-typed watcher. |

### `io.nats.client.impl` (mostly package-private)

| Interface | Visibility | Purpose |
|---|---|---|
| `DataPort` | `public` | Wraps the network transport. |
| `NatsSubscriptionFactory` | pkg-private | Builds the appropriate `NatsSubscription` subtype (core vs JS). |
| `PullManagerObserver` | pkg-private | Hooks into pull-consumer manager state. |
| `SSLContextFactory` | `public` | User-supplied `SSLContext` factory. |
| `SimplifiedSubscriptionMaker` | pkg-private | Hooks for creating simplified-consumer subscriptions. |

### `io.nats.client.support`

| Interface | Purpose |
|---|---|
| `ApiConstants` | Marker — server API schema string constants. |
| `JsonSerializable` | Anything that can render itself as JSON. |
| `NatsConstants` | Marker — protocol byte / string constants. |
| `NatsInetAddressProvider` | Pluggable `InetAddress` provider. |
| `NatsJetStreamConstants` | Marker — JetStream subject / header constants. |

### `io.nats.service`

| Interface | Purpose |
|---|---|
| `ServiceMessageHandler` | Functional callback receiving a service request message. |

---

## 2. Interface inheritance tree

```
«interface» AutoCloseable                                (java.lang)
 ├── «interface» Connection
 ├── «interface» BaseMessageConsumer
 └── «interface» MessageConsumer
      ├── «interface» FetchConsumer
      └── «interface» IterableConsumer

// v3: the `Consumer` interface is removed — `Dispatcher` and `Subscription` no longer
// share an interface supertype. Their shared implementation lives in the abstract
// pkg-private base `NatsMessageSink` (was `NatsConsumer`); see §3.6.
«interface» Dispatcher
«interface» Subscription
 └── «interface» JetStreamSubscription

«interface» BaseConsumerContext
 ├── «interface» ConsumerContext
 └── «interface» OrderedConsumerContext

«interface» Statistics
 └── «interface» StatisticsCollector

«interface» Watcher<T>
 ├── «interface» KeyValueWatcher        (Watcher<KeyValueEntry>)
 └── «interface» ObjectStoreWatcher     (Watcher<ObjectInfo>)
```

All other interfaces above have no JNats sub-interfaces.

---

## 3. Implementation tree per interface

### 3.1 `AuthHandler`
```
«interface» AuthHandler
 ├── FileAuthHandler                    (impl, pkg-private)
 ├── MemoryAuthHandler                  (impl, public)
 └── StringAuthHandler                  (impl, pkg-private)
```

### 3.2 `BaseConsumerContext`
```
«interface» BaseConsumerContext
 ├── «interface» ConsumerContext
 │    └── NatsConsumerContext           (impl)  also implements SimplifiedSubscriptionMaker
 └── «interface» OrderedConsumerContext
      └── NatsOrderedConsumerContext    (impl)
```

### 3.3 `BaseMessageConsumer`
No direct implementors — implemented transitively via `MessageConsumer`. See §3.18.

### 3.4 `Connection`
```
«interface» Connection
 └── NatsConnection                     (impl, pkg-private)
```

### 3.5 `ConnectionListener`
```
«interface» ConnectionListener
 ├── DebugConnectionListener            (support)
 └── DebugListener                      (support)    also ErrorListener, ReadListener
```

### 3.6 `Consumer` — removed in v3 (impl base `NatsMessageSink`)
The `Consumer` interface is deleted (see `REMOVE_CONSUMER_INTERFACE.md`, done). The shared implementation it fronted is the abstract pkg-private base, renamed `NatsConsumer` → `NatsMessageSink`; it is never named in a public signature. `Dispatcher` and `Subscription` are now independent interfaces that each extend nothing, and both concrete classes extend this base:
```
«abstract» NatsMessageSink                             (impl, pkg-private) — was NatsConsumer
 ├── NatsDispatcher                                     → implements Dispatcher, Runnable
 │    └── NatsDispatcherWithExecutor                   (impl, pkg-private)
 └── NatsSubscription                                   → implements Subscription
      └── NatsJetStreamSubscription                     → implements JetStreamSubscription,
           │                                             NatsJetStreamConstants
           └── NatsJetStreamPullSubscription            (impl)
```

### 3.7 `Dispatcher`
No longer extends `Consumer` (removed); it declares its own contract — `start`/`isActive`/`drain(long)`, the subscribe/unsubscribe methods, and the full pending/observability group (`setPendingLimits`, pending/byte limits + counts, delivered/dropped counts) so a slow *async* consumer is bounded and monitored on the dispatcher directly.
```
«interface» Dispatcher
 └── NatsDispatcher                     (extends NatsMessageSink)
      └── NatsDispatcherWithExecutor
```

### 3.8 `ErrorListener`
```
«interface» ErrorListener
 ├── DebugErrorListener                 (support)
 ├── DebugListener                      (support)
 ├── ErrorListenerConsoleImpl           (impl)
 └── ErrorListenerLoggerImpl            (impl)
```

### 3.9 `FetchConsumer` *(extends MessageConsumer)*
```
«interface» FetchConsumer
 └── NatsFetchConsumer                  (extends NatsMessageConsumerBase)
```

### 3.10 `IterableConsumer` *(extends MessageConsumer)*
```
«interface» IterableConsumer
 └── NatsIterableConsumer               (extends NatsMessageConsumer)
```

### 3.11 `JetStream`
```
«interface» JetStream
 └── NatsJetStream                      (extends NatsJetStreamImpl)
```

### 3.12 `JetStreamManagement`
```
«interface» JetStreamManagement
 └── NatsJetStreamManagement            (extends NatsJetStreamImpl)
```

### 3.13 `JetStreamReader`
```
«interface» JetStreamReader
 └── NatsJetStreamPullSubscription.JetStreamReaderImpl   (static inner class)
```

### 3.14 `JetStreamSubscription` *(extends Subscription)*
```
«interface» JetStreamSubscription
 └── NatsJetStreamSubscription          (extends NatsSubscription)
      └── NatsJetStreamPullSubscription
```

### 3.15 `KeyValue`
```
«interface» KeyValue
 └── NatsKeyValue                       (extends NatsFeatureBase)
```

### 3.16 `KeyValueManagement`
```
«interface» KeyValueManagement
 └── NatsKeyValueManagement             (impl)
```

### 3.17 `Message`
```
«interface» Message
 └── NatsMessage                        (impl, public)
      ├── IncomingMessage                                (impl)
      │    ├── NatsJetStreamMessage                      (impl, pkg-private)
      │    └── StatusMessage                             (impl, public)
      ├── MarkerMessage                                  (impl, pkg-private)
      └── NatsPublishableMessage                         (impl, pkg-private)
           └── ProtocolMessage                            (impl, pkg-private)
```

### 3.18 `MessageConsumer` *(extends AutoCloseable)*
```
«interface» MessageConsumer
 └── «abstract» NatsMessageConsumerBase        also implements PullManagerObserver
      ├── NatsFetchConsumer              → implements FetchConsumer
      ├── NatsMessageConsumer            → implements PullManagerObserver
      │    └── NatsIterableConsumer      → implements IterableConsumer
      └── NatsNextConsumer               (impl, pkg-private)
```

### 3.19 `MessageHandler`
```
«interface» MessageHandler
 ├── NatsJetStream.AsyncMessageHandler                       (static inner class)
 └── «abstract» NatsWatchSubscription.WatchMessageHandler<T> (protected static inner)
      ├── (anonymous subclass) in NatsKeyValueWatchSubscription.getHandler(..)
      └── (anonymous subclass) in NatsObjectStoreWatchSubscription.getHandler(..)
```

Note: in practice, application code provides arbitrary `MessageHandler` lambdas / classes — only the in-library implementations are listed.

### 3.20 `MessageInfoHandler`
No source-tree implementations. Functional interface — users supply lambdas / inner classes.

### 3.21 `NatsSystemClockProvider`
No source-tree implementations. Functional interface — pluggable; default behaviour lives at the call site.

### 3.22 `ObjectStore`
```
«interface» ObjectStore
 └── NatsObjectStore                    (extends NatsFeatureBase)
```

### 3.23 `ObjectStoreManagement`
```
«interface» ObjectStoreManagement
 └── NatsObjectStoreManagement          (impl)
```

### 3.24 `OrderedConsumerContext`
See §3.2.

### 3.25 `ReadListener`
```
«interface» ReadListener
 ├── DebugListener                      (support)
 ├── DebugReadListener                  (support)
 └── ReaderListenerConsoleImpl          (impl)
```

### 3.26 `ReconnectDelayHandler`
No source-tree implementations. Functional interface.

### 3.27 `ServerPool`
```
«interface» ServerPool
 └── NatsServerPool                     (impl, public)
```

### 3.28 `Statistics`
Implemented only via the sub-interface `StatisticsCollector` (§3.29).

### 3.29 `StatisticsCollector` *(extends Statistics)*
```
«interface» StatisticsCollector
 ├── NatsStatistics                     (impl)
 └── NoOpStatistics                     (impl)
```

### 3.30 `StreamContext`
```
«interface» StreamContext
 └── NatsStreamContext                  (impl, pkg-private)
```

### 3.31 `Subscription`
No longer extends `Consumer` (removed); it declares only `isActive()` + `drain(long)`, and the pending/count machinery is inherited from the abstract base `NatsMessageSink` on the concrete class (§3.6). Concise:
```
«interface» Subscription
 └── NatsSubscription                   (extends NatsMessageSink)
      └── NatsJetStreamSubscription
           └── NatsJetStreamPullSubscription
```

### 3.32 `TimeTraceLogger`
No source-tree implementations. Functional interface — users supply a logger.

### 3.33 `Watcher<T>` (and its sub-interfaces)
```
«interface» Watcher<T>
 ├── «interface» KeyValueWatcher        (= Watcher<KeyValueEntry>)
 └── «interface» ObjectStoreWatcher     (= Watcher<ObjectInfo>)
```
`KeyValueWatcher` / `ObjectStoreWatcher` themselves have no library-side implementations — applications provide watchers and the library wraps them with `NatsWatchSubscription.WatchMessageHandler` (see §3.19).

### 3.34 `DataPort`
```
«interface» DataPort
 └── SocketDataPort                     (impl, public)
```

### 3.35 `NatsSubscriptionFactory` *(pkg-private)*
No declared `implements NatsSubscriptionFactory` classes; factory instances are constructed inline / via lambda by `NatsConnection` when creating subscriptions.

### 3.36 `PullManagerObserver` *(pkg-private)*
```
«interface» PullManagerObserver
 ├── «abstract» NatsMessageConsumerBase      (also implements MessageConsumer)
 └── NatsMessageConsumer                     (extends NatsMessageConsumerBase — declared again on the subclass)
```

### 3.37 `SSLContextFactory`
No source-tree implementations. Plug-in extension point.

### 3.38 `SimplifiedSubscriptionMaker` *(pkg-private)*
```
«interface» SimplifiedSubscriptionMaker
 └── NatsConsumerContext                (also implements ConsumerContext)
```

### 3.39 `ApiConstants`
Marker / constant pool — no implementers.

### 3.40 `JsonSerializable`
This is the most widely-implemented interface in the codebase. Direct implementers (grouped by package):

`io.nats.client`
 - `BaseConsumeOptions`
 - `PullRequestOptions`
 - `PurgeOptions`

`io.nats.client.api`
 - `ConsumerPauseRequest`, `ConsumerLimits`, `ConsumerCreateRequest`, `ConsumerConfiguration`, `ConsumerSource`
 - `Error`
 - `External`
 - «abstract» `FeatureConfiguration`
     - `KeyValueConfiguration`
     - `ObjectStoreConfiguration`
 - `MessageDeleteRequest`, `MessageGetRequest`
 - `ObjectLink`, `ObjectMeta`, `ObjectMetaOptions`, `ObjectInfo`
 - `OrderedConsumerConfiguration`
 - `Placement`
 - `Republish`
 - «abstract» `SourceBase`
     - `Mirror`
     - `Source`
 - `StreamInfoOptions`, `StreamConfiguration`
 - `SubjectTransform`

`io.nats.client.support`
 - `JsonValue`
 - `JsonValueUtils.MapBuilder`, `JsonValueUtils.ArrayBuilder`
 - `JwtUtils.UserClaim`, `JwtUtils.TimeRange`, `JwtUtils.ResponsePermission`, `JwtUtils.Permission`, `JwtUtils.Claim`

`io.nats.service`
 - `Endpoint`
 - `EndpointStats`
 - «abstract» `ServiceResponse`
     - `InfoResponse`
     - `PingResponse`
     - `StatsResponse`

### 3.41 `NatsConstants`
Marker / constant pool — no implementers.

### 3.42 `NatsInetAddressProvider`
No source-tree implementations. Plug-in extension point.

### 3.43 `NatsJetStreamConstants`
```
«interface» NatsJetStreamConstants
 ├── NatsJetStreamImpl                  (mixin — pulls in JS subject/header constants)
 │    ├── NatsJetStream                  → implements JetStream
 │    └── NatsJetStreamManagement        → implements JetStreamManagement
 └── NatsJetStreamSubscription          (mixin) — also implements JetStreamSubscription
      └── NatsJetStreamPullSubscription
```

### 3.44 `ServiceMessageHandler`
No source-tree implementations. Functional interface — supplied by service authors.

---

## 4. Cross-cutting notes

- **Multi-interface classes.** A few classes carry more than one interface and therefore appear in more than one tree above:
  - `DebugListener` → `ErrorListener` + `ConnectionListener` + `ReadListener`
  - `NatsConsumerContext` → `ConsumerContext` + `SimplifiedSubscriptionMaker`
  - `NatsDispatcher` → `Dispatcher` + `Runnable`
  - `NatsJetStreamSubscription` → `JetStreamSubscription` + `NatsJetStreamConstants`
  - `NatsMessageConsumerBase` → `MessageConsumer` + `PullManagerObserver`
- **Abstract bases that aren’t interfaces but matter for the hierarchy.** `NatsMessageSink` (v3; was `NatsConsumer`), `NatsMessageConsumerBase`, `NatsJetStreamImpl`, `NatsFeatureBase`, `NatsWatchSubscription<T>`, `MessageManager`, `AbstractListReader`, and `SourceInfoBase` sit between the public interface surface and the concrete classes; they are shown inline above where they participate in an interface tree.
- **Functional-interface extension points** (`MessageInfoHandler`, `NatsSystemClockProvider`, `ReconnectDelayHandler`, `TimeTraceLogger`, `SSLContextFactory`, `NatsInetAddressProvider`, `KeyValueWatcher`, `ObjectStoreWatcher`, `ServiceMessageHandler`) have no in-library implementations — they exist for callers to supply.
- **Constant interfaces** (`ApiConstants`, `NatsConstants`, `NatsJetStreamConstants`) are used in two ways: occasionally mixed into a class for ergonomic access (e.g. `NatsJetStreamImpl implements NatsJetStreamConstants`), but usually referenced via fully-qualified static usage.

---

## 5. Decisions (v3 refactor)

### 5.1 Remove the `Subscription` interface — the concrete class becomes `Subscription`

**Decision.** Delete the `io.synadia.client.Subscription` interface and make the concrete class (today `NatsSubscription`) *be* `Subscription`. Keep it in `io.synadia.client.impl` — it must live there because it needs package-private access to `NatsConnection`/`NatsDispatcher` to do its work (`reSubscribe`, `invalidate`, `nextMessageInternal`, `setUnsubLimit`, `getMessageQueue`, the `NatsConnection`/`NatsDispatcher` constructor, etc.). This mirrors the already-completed `Connection` interface removal.

**Why the interface isn't earning its keep.** There is exactly one implementation chain — `NatsSubscription` → `JetStreamSubscription` → `JetStreamPushSubscription`/`JetStreamPullSubscription` — and no in-repo or known external implementors. So the interface provides no polymorphism. Its only real value was encapsulation / package hygiene (a clean 9-method contract in the public package, machinery hidden in `.impl`), and that value has *already been deliberately set aside* elsewhere in the API: `ErrorListener`'s callbacks take `NatsConnection`, `subscribe()` returns `NatsSubscription`, and JetStream returns `JetStreamPushSubscription`. Dropping the interface is consistent with that established direction, not a new departure.

**On "leaky" (retracted).** An earlier concern that keeping the merged class in `.impl` would be "leaky" is withdrawn. Package-private methods are intentional encapsulation and are invisible outside the package — a dev is meant to read their absence from the public surface as "not for you," which is correct Java. The *only* path that would actually force internal machinery to widen to `public`/`protected` is moving the merged class up to `io.synadia.client` (so `.impl` collaborators could still reach it) — and that path is rejected. Keeping it in `.impl` costs nothing here. The lone residual is a naming nit — `.impl` no longer strictly means "internal" once the public concrete types live there — but that is already true today, so it's an observation for a possible future package rename, not a reason to keep the interface.

**Scope / sequencing.**
- Do `Subscription` now; it stands alone cleanly. `Consumer` is already gone (done — see `REMOVE_CONSUMER_INTERFACE.md`); `NatsSubscription` now `extends NatsMessageSink implements Subscription`, so removing the interface is just the rename `NatsSubscription` → `Subscription` plus dropping `implements Subscription`.
- `Dispatcher` / `NatsDispatcher` collapse is deferred (possibly indefinitely) — that split exists only for the vertx (V2) extension, which is deprioritized. Its former-`Consumer` members already live on the `Dispatcher` *interface* (added during the Consumer removal), so a future collapse would just let `NatsDispatcher` inherit them from `NatsMessageSink` and drop the re-declarations.
- External compat (vertx, third-party `Subscription` implementors) is explicitly **not** a concern: vertx is a V2 extension and out of scope; there are no known external implementors; anyone wanting a custom subscription can work inside the package or build their own client on top of core.

**Mechanical impact (for when this is implemented).**
- Rename `NatsSubscription` → `Subscription`; delete the interface `io.synadia.client.Subscription`.
- Drop the now-redundant `implements Subscription` from `JetStreamSubscription` and `JetStreamPullSubscription` (they inherit it by extending the class).
- Update references that name the type in signatures: `Message.getSubscription()`, the `ErrorListener` callbacks (`heartbeatAlarm`/`unhandledStatus`/`pullStatusWarning`/`pullStatusError`/`flowControlProcessed`/`supplyMessage`) and their impls (`ErrorListenerConsoleImpl`, `DebugListener`, `DebugErrorListener`), `Dispatcher.unsubscribe(Subscription)` / `unsubscribe(Subscription, int)` and `NatsDispatcher`, and the `unsubscribe(int after)` return type.
- Public-package interfaces (`Message`, `ErrorListener`, `Dispatcher`) will then reference `io.synadia.client.impl.Subscription` — already the pattern for `NatsConnection`.

### 5.2 `SubscribeBehavior` — keep it flat (no hierarchy)

**Decision (done).** Do **not** split `SubscribeBehavior` into a push/pull × sync/async hierarchy — the "a flat object lets you set knobs that do nothing" premise was wrong (the pending limit is meaningful in all four cells; there's no illegal combo to forbid). Kept the single flat class and reworded its pending comments (each setter now says it configures the sync push subscription's own queue, that async delivery is bounded by the dispatcher, and that pull doesn't use it); fixed the stray `@return the builder` tags.

**Still open (deferred).** A slow *async* handler backing up the dispatcher queue. Users can now bound/monitor a dispatcher **they create** via `Dispatcher.setPendingLimits` (done — Consumer-removal work put the pending/observability group on the `Dispatcher` interface). What remains: no way to configure pending on a JetStream **auto-created** dispatcher (push-async with a handler but no user-supplied dispatcher) through the subscribe call — plus any smarter slow-handler handling. Out of scope for now.
