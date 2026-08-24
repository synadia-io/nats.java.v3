# Method renames — v3 internal-marker pass

Every method renamed during the internal-marker naming work (`UNDERSCORE_INTERNAL_NAMING_AUDIT.md`, archived to `z-claude-done/`), plus the one rename that came out of `ISSUE_1596_REVIEW.md`. Sorted by class. Verified 2026-08-17: every "was" name is absent from `core`/`jetstream`/`service`/`kv`/`os`/`examples`, every "now" name is present.

| Class | Was | Now                                  |
|---|---|--------------------------------------|
| `JetStream` | `createSubscription` | `_createJsSubscription`              |
| `JetStream` | `publishAsyncInternal` | `_publishAsync`                      |
| `JetStream` | `publishSyncInternal` | `_publishSync`                       |
| `JetStream` | `subscribeToNewConsumer` | `subscribeDeleteConsumerOnException` |
| `JetStreamImpl` | `getStreamNamesInternal` | `_getStreamNames`                    |
| `ListRequestEngine` | `internalNextJson` (×2 overloads) | `_nextJson`                          |
| `NatsConnection` | `createSubscriptionInternal` | `_createSubscriptionByFactory`       |
| `NatsDispatcher` | `checkBeforeSubImpl` | `requireActiveConnection`            |
| `NatsDispatcher` | `internalStart` | `startImpl`                          |
| `NatsDispatcher` | `subscribeImplByFactory` | `_subscribeByFactory`                |
| `NatsDispatcher` | `subscribeImplCore` | `_subscribeCore`                     |
| `NatsDispatcher` | `_subscribeImplHandlerProvided` | `_subscribeByFactoryAndTrack`        |
| `NatsSubscription` | `nextMessageInternal` | `_nextMessage`                       |
| `Options` | `_getInternalExecutor` | `newExecutor`              |
| `Options` | `_getInternalScheduledExecutor` | `newScheduledExecutor`     |
| `Service` | `internalEndpoint` | `newEndpoint`                        |

## Notes

**One row changes public API: `NatsDispatcher.internalStart` → `startImpl`.** It is `protected` and stays `protected` — real extension API for external dispatcher implementors. `nats-java-vertx-client`'s `VertxDispatcher` subclasses into the impl package and calls it with `threaded=false` to run the drain loop on the Vert.x event loop. **That repo needs a one-line edit at `VertxDispatcher:16` and has not been updated.** Everything else in the table is package-private or private.

**`subscribeToNewConsumer` → `subscribeDeleteConsumerOnException`** came from the `ISSUE_1596_REVIEW.md` work, not the naming audit, and was renamed by hand.

**`Options`'s two rows run the other way** — a marker was removed rather than added. Those are the public factories for internally-created executors, so `internal` is an adjective describing the executor, not a marker on the method.

**Not renames, so not listed:** `JetStreamImpl._deleteConsumer` was *added* in `c47bb0d7`. `EndpointContext`'s `internalEndpoint` constructor parameter still exists and is correct — variables were out of scope, and it is the trap that made the `Service.internalEndpoint` rename dangerous, since a naive identifier rename hits it too.
