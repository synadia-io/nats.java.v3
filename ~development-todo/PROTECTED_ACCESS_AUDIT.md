# Audit: `protected` → package-private in core, jetstream, kv, os, service

Written 2026-10-07. No code changed.

**Decision 2026-10-07 (Scott): keep `protected`.** `NatsServerPool` is a class a user would actually subclass, and `protected` leaves users room for cases not envisioned. Nothing in this audit is applied. The earlier package-private change to the `NatsConnection` / `NatsConnectionV3` internals was reverted to the HEAD access levels. Principle (Scott, 2026-10-07): the library has a specific public API and is not meant to be extended except by this library and its projects. Subclasses always live in the same package as their base, so `protected` only adds access for out-of-package subclasses, which should not exist.

## Method (measured)

- `javap -p -c -s` over every compiled class of core, jetstream, kv, os, service and examples (main and test, 611 classes).
- For each `protected` member declared in main sources of the 5 library modules, three checks were made:
  - Is it overridden by a subclass in another package?
  - Does it override a `protected` member of a class in another package, or of a JDK class?
  - Is it referenced (call, field access, constructor) from a class in another package?
- A "yes" to any of these means it must stay `protected`.
- Experiment in an isolated copy: every `protected` member not in §1 was converted to package-private. `compileTestJava` (all modules, main and test) and `javadoc` passed, with no new warnings. The conversion covered nested class declarations too.
- Bytecode count: 335 `protected` members. 25 must stay (§1); 310 can become package-private (§2 and §3). The gitignored `Debug.java` is excluded.

## 1. Must stay `protected` (25): kv / os subclass jetstream bases across packages

Every case is kv or os (packages `io.synadia.client.kv` / `.os`) extending a jetstream base in `io.synadia.client.impl`, or jetstream `impl` using a base in `io.synadia.client.api`.

| Declaring class | Members | Why |
|---|---|---|
| `impl.FeatureOptions` | constructor; nested class `Builder`, its 2 constructors and `getThis` | `KeyValueOptions` / `ObjectStoreOptions` (and their builders) extend it; `getThis` is overridden in kv / os (your decision: leave protected) |
| `kv.KeyValueOptions.Builder`, `os.ObjectStoreOptions.Builder` | `getThis` | overrides the protected base from another package |
| `impl.AbstractBucketFeature` | 2 constructors, `toStreamName`, `_getLast`, `_getBySeq`, 2 × `visitSubject` | `KeyValue` / `ObjectStore` extend it and call these; `toStreamName` overridden |
| `kv.KeyValue`, `os.ObjectStore` | `toStreamName` | overrides the protected base from another package |
| `impl.NatsWatchSubscription` | `finishInit`; nested class `WatchMessageHandler`, its constructor and `endOfDataSent` | `KeyValueWatchSubscription` / `ObjectStoreWatchSubscription` extend it |
| `api.SubscribeBehavior` | `dispatcher`, `handler`, copy constructor | `impl.JetStreamSubscribeConfig` (jetstream, other package) extends it |
| `api.ApiResponse` | `ljv`, 3 constructors (`Message`, `LazyJsonValue`, no-arg) | `impl.MessageInfo` and `impl.ListRequestEngine` (jetstream, other package) extend it |

The last two rows are jetstream to jetstream: `api` and `impl` are different packages. They could become package-private only by moving the subclass into the base's package (or the reverse), which is not proposed.

## 2. Can become package-private: groups that need a decision

- **2a. Subscription internals (your 2026-10-07 decision: keep protected, do not change the underscore methods).** `JetStreamSubscription`: `js`, `stream`, `consumerName`, `manager`, `invalidate`, `_nextUnmanagedWaitForever`, `_nextUnmanagedNoWait`, `_nextUnmanaged`. `JetStreamPullSubscription._pull`. Package-private would compile, and only the access keyword changes; no name changes. Listed so the earlier decision is either kept or revisited.
- **2b. Base classes in `io.synadia.client.api` that are public builders or creators:** `ConsumerCreator` (about 30 builder fields), `AbstractEphemeralConsumerCreator`, `AbstractOrderedConsumerCreator`, `LazyApiObject`, plus the protected constructors of `MessageGetRequest`, `PeerInfo`, `StreamSource` and `StreamSourceInfo`. Today a user could subclass these; package-private ends that.
- **2c. `NatsServerPool`** (`listLock`, `entryList`, `options`, `maxConnectAttempts`, `hasSecureServer`, `lastConnected`, `defaultScheme`, `afterListChanged`, `findEquivalent`). `ServerPool` is a public interface users implement. Users who subclassed `NatsServerPool` for a custom pool, rather than implementing the interface, lose these 9 members.

## 3. Can become package-private: no decision beyond the principle

Same-package internals; no out-of-package subclass or reference exists:
- core `impl`: `IncomingMessage`, `InternalPublishableMessage`, `MessageQueueBase`, `NatsConnectionReader.setConnection`, `NatsDispatcher` (12), `NatsDispatcherWithExecutor`, `NatsMessage` (16), `NatsMessageSink`, `SocketDataPort` (7), `StatusException`, `WriterMessageQueue` (5).
- core `utils`: `ApiUtils` / `Validator` constructors and `Validator` patterns, `ByteArrayBuilder.allocationSize`, `ScheduledTask` (6).
- jetstream `impl`: `AbstractListReader`, `BaseConsumeOptions` (fields, constructor, nested `Builder`, `getThis`, `subclassSpecificToJson`), `ConsumeOptions` / `FetchConsumeOptions` builders (`getThis`, `noWait`, `subclassSpecificToJson`), `ListRequestEngine`, `MessageManager` (20), `NatsMessageConsumer` (about 20), `NatsMessageConsumerBase` (10), `PullMessageManager`, `PullOrderedMessageManager`, `PushMessageManager`, `PushOrderedMessageManager`, `PullRequestOptions.getPinId` with its override `PinnablePullRequestOptions.getPinId`, `PurgeOptions`, `StringListReader`.
- jetstream `api`: `ApiResponse` helpers (`parseMessage`, `invalidJson`, `stringRequired`, `dateRequired`, `valueRequired`), `SubscribeBehavior.messageAlarmTime`. jetstream `utils`: `JsValidator` constructor.
- service: `ServiceEndpoint` getters (5), `ServiceResponse` (fields, constructor, `parseMessage`, `subToJson`), `InfoResponse.subToJson`, `StatsResponse.subToJson`.

## 4. Notes

- Override safety: a package-private method is overridden only by a subclass in the same package, and a subclass in another package that declares the same signature defines a new method instead, with no error. The bytecode check found no out-of-package overrides apart from those in §1, so the conversion changes no dispatch.
- Making members of a `public` class package-private removes them from the published javadoc.

## 5. Open decisions

- **P1.** Apply §3.
- **P2.** §2a: keep the subscription members protected (current decision) or convert.
- **P3.** §2b: convert the `api` builders and creators.
- **P4.** §2c: convert `NatsServerPool`.
