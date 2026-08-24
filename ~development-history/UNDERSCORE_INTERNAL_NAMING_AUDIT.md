# Audit: internal-marker naming — `_`, `Impl`, and `internal`

Scope: `main` sources of `core`, `jetstream`, `kv`, `os`, `service`. Test sources use `_` for a different purpose and are out of scope (last section).

**Settled 2026-08-15.** Earlier drafts of this audit spent a long time trying to separate "delegate" from "impl" and give each its own marker. That distinction is real in the code but it is **not something the name has to carry** — the call shape already shows it. The rule that came out of the exercise is one line:

> ## `_` means internal. A delegate is an internal thing, so it gets `_` too.

One marker, one meaning. Nothing else needs deciding at naming time.

## What that settles

Everything the earlier drafts agonised over stops mattering, because none of it changes what you write:

* **Delegate or impl?** Both are internal, both get `_`. The difference — whether callers forward the whole operation or use it as a step — is visible at the call sites and does not need spelling in the name.
* **How many callers?** Irrelevant. `_connect`-shaped methods with one caller and `_publishSync` with twelve are equally internal.
* **Does it cross a module boundary?** Irrelevant. `_nextMessage` is reached from jetstream, but through `JetStreamSubscription extends NatsSubscription` — inherited, unqualified. `_subscribeByFactory` is reached from `JetStream` on a dispatcher instance. Both are still internal to the library.
* **Prefix, suffix, or infix?** Gone. `_` is always a prefix.

## The one thing the name still has to carry: the stem

When a method is the inner half of a public one, **keep the public method's stem**. The `_` says "internal", the stem says "of what". `_nextMessage` ← `nextMessage`. `_publishSync` ← `publish`. `_getStreamNames` ← `getStreamNames`.

This is the rule the earlier passes broke worst. Removing a marker is licence to change the marker, not to rename the method. Three names were rewritten when only the marker was in question:

| Was | Became (wrong) | Now |
|---|---|---|
| `getStreamNamesInternal` | `fetchStreamNames` | `_getStreamNames` |
| `internalEndpoint` | `buildDiscoveryEndpoint` | `_endpoint` |
| `nextMessageInternal` | `pollMessage` | `_nextMessage` |

`fetch` is not better than `get`; it just severed the link to the public `getStreamNames` the method backs. Same for the other two. A composed, intention-revealing name is only right where there **is** no public counterpart to share a stem with — `requireActiveConnection` is the one such case here, and it keeps its composed name because there is nothing named `requireActive` for it to be the inside of.

## What `internal` still means

`internal` survives as an **adjective**, describing the thing rather than marking the method. It was never freeable for anything else: several of these are `public` API.

| Method | Sense |
|---|---|
| `Options.executorIsInternal` + 5 siblings | public predicates — did we create this executor, or did the user supply it? |
| `Options.getInternalExecutor`, `getInternalScheduledExecutor` | the factories for those internally-created executors |
| `ByteArrayBuilder.internalArray` | the internal backing array, public no-copy accessor |
| `EndpointContext.isNotInternalDispatcher`, and its `internalEndpoint` constructor parameter | is this one we created? |
| `queueInternalOutgoing` / `queueInternalMessage` | an internal message, as against `queueOutgoing`'s user message |
| `makeInternalRequestResponseRequired` | the internal variant, beside `makeRequestResponseRequired` |

## `NatsConnection` keeps `Impl`

The connection's own lifecycle — `connectImpl`, `forceReconnectImpl`, `reconnectImpl`, `reconnectImplConnect`, `closeSocketImpl` — is unchanged and stays unchanged. Those names carry history for the people who have read the code, and that is worth more than uniformity. Detail in `NATSCONNECTION_NAMING_TABLE.md`.

Also still marked, deliberately: `beforeQueueProcessorImpl` with its two overrides, and `NatsDispatcher.startImpl` — which moved `internal` → `Impl` rather than to `_`, see below.

## What was renamed

All package-private or private **except the last row**, which is `protected` and is a deliberate, eyes-open API change for the v3 release. One *other* rename briefly changed a public method by accident and was reverted; see trap 3.

| Class | Was | Now |
|---|---|---|
| `NatsConnection` | `createSubscriptionInternal` | `_createSubscriptionByFactory` |
| `NatsDispatcher` | `subscribeImplCore` | `_subscribeCore` |
| `NatsDispatcher` | `subscribeImplByFactory` | `_subscribeByFactory` |
| `NatsDispatcher` | `_subscribeImplHandlerProvided` | `_subscribeByFactoryAndTrack` |
| `NatsDispatcher` | `checkBeforeSubImpl` | `requireActiveConnection` |
| `NatsSubscription` | `nextMessageInternal` | `_nextMessage` |
| `JetStream` | `publishSyncInternal` | `_publishSync` |
| `JetStream` | `publishAsyncInternal` | `_publishAsync` |
| `JetStream` | `createSubscription` | `_createJsSubscription` |
| `JetStreamImpl` | `getStreamNamesInternal` | `_getStreamNames` |
| `Service` | `internalEndpoint` | `_endpoint` |
| `Options` | `_getInternalExecutor`, `_getInternalScheduledExecutor` | `getInternalExecutor`, `getInternalScheduledExecutor` |
| `ListRequestEngine` | `internalNextJson` (×2 overloads) | `_nextJson` |
| `NatsDispatcher` | `internalStart` | `startImpl` — the one rename that changes public API, see below |

Reference counts at the time of writing: `_createJsSubscription` 15, `_publishSync` 13, `_publishAsync` 11, `_nextMessage` 10, `_createSubscriptionByFactory` 6, `_subscribeCore` 5, `_getStreamNames` 4, `_endpoint` 4, `_subscribeByFactoryAndTrack` 3, `requireActiveConnection` 3, `_subscribeByFactory` 2.

`_createSubscriptionByFactory` and `_createJsSubscription` are the core/JetStream pair — `JetStream:631` calls `conn._createSubscriptionByFactory(...)`, `:636` calls `dispatcher._subscribeByFactory(...)`, so the layering across the two modules is visible on adjacent lines.

`_subscribeByFactoryAndTrack` keeps the stem of the method above it and says what it adds. It replaced a proposal to mark depth with `__`; counting underscores marks the least interesting property, and `_`/`__` are near-identical to read.

## Three renaming traps, all found the expensive way

Every one compiled cleanly. A green build catches none of them.

1. **A method name can be a variable elsewhere.** Reverting `beforeQueueProcessor` renamed a **field**, a **parameter** and a **comment** in `NatsSubscription` that had always been correct. The tell was available before running it: the identifier appeared in 7 files while the method existed in 3.
2. **The same name can be a parameter in another class.** Renaming `Service.internalEndpoint` hit `EndpointContext`'s constructor parameter of the same name, producing a parameter called `_endpoint`.
3. **`protected` in a base can be `public` in an override.** `beforeQueueProcessorImpl` is `protected` in `MessageManager` and **`public`** in `PushMessageManager`, on a public class with no internal-class banner — so renaming it changed public API. Checking the declaration alone was not enough.

**The procedure:** before running an identifier rename, list every file it will touch and confirm each hit is the method. Check every override for widened visibility, not just the declaration. Then read the diff — do not trust the compiler.

## Next, not started

**Visibility: package vs protected vs private — belongs to `PLAN_CORE_JETSTREAM_BOUNDARY.md`, not here.** That plan already owns the problem: its §1 names "a small tail of genuinely `protected`/package-private member access that only compiles because the package is split", and its §3 says the member-level pass "still needs to run for `NatsDispatcher`, `Headers`, `NatsMessage`, `NatsSubscription`". The findings below are that pass, partly done, and have been written into it as §3a. Do not start a separate visibility plan — it would duplicate that one and miss its ratchet, which is `module-info` turning every remaining illegal reach into a compile error.

Marking the internal surface with `_` is what made the mismatches greppable. Three categories showed up.

**1. Wider than its use.** `_subscribeCore` is package-private, but all five references are inside `NatsDispatcher`. `private` would match its actual use; package-private invites a reader to assume something external once called it.

**2. Reached by inheritance across a module.** `_nextMessage` is package-private and called by subclasses in jetstream — `JetStreamSubscription extends NatsSubscription`, unqualified. It works only because both share the package `io.synadia.client.impl`. `protected` would say "for subclasses"; package-private says "for this package", and the code leans on the weaker one happening to cover the stronger.

**3. Reached on another object — internal access between collaborators.** Found mechanically by grepping qualified calls to `_` methods, plus the two package-private non-`_` cases. Eleven distinct relationships:

| Target | Declared in | Called from |
|---|---|---|
| `_createSubscriptionByFactory` | `NatsConnection` (core) | `JetStream:631` **(crosses module)**, `NatsDispatcher:254,:273` |
| `_subscribeByFactory` | `NatsDispatcher` (core) | `JetStream:636` **(crosses module)** |
| `_createJsSubscription` | `JetStream` | `NatsConsumerContext:101` |
| `_createConsumer` | `JetStreamImpl` | `NatsConsumerContext:75`, `PushOrderedMessageManager:82` |
| `_pull` | `JetStreamPullSubscription` | `NatsFetchMessageConsumer:61`, `NatsMessageConsumer:217`, `NatsNextConsumer:19` |
| `_nextUnmanagedNoWait` | `JetStreamSubscription` | `NatsFetchMessageConsumer:100,:120` |
| `_nextUnmanaged` | `JetStreamSubscription` | `NatsFetchMessageConsumer:128` |
| `_nextJson` | `ListRequestEngine` | `AbstractListReader:64,:71` |
| `subscribeDeleteConsumerOnException` | `JetStream` | `NatsConsumerContext:100` |

Most of this is **pre-existing**, not introduced by the renames — `_pull`, `_nextUnmanaged*` and `_createConsumer` were already `_` methods called between classes.

**Only two of the eleven cross a module boundary**, and both are `JetStream` reaching into core: `conn._createSubscriptionByFactory` and `dispatcher._subscribeByFactory`. Those are the two that break if the split packages are ever separated per module — `OSGi_JPMS_TODO.md` O3 from the other side.

Both are recorded in `PLAN_CORE_JETSTREAM_BOUNDARY.md` §3a. The first was already in its §3; the second is new and confirms what its §8 predicted. **That plan intends to make both `public` and drop the `_`**, since they stop being internal — so for these two members the boundary plan's end state supersedes the rule at the top of this document. Everything else keeps its `_`.

**The last two items from the original audit are now resolved**, in opposite directions.

`ListRequestEngine.internalNextJson` → **`_nextJson`**. Package-private, and the stem exists as `AbstractListReader.nextJson` / `nextJson(filter)` — thin `protected` forwards. Straight application of the rule. It appears in both open lists above: it was an unaddressed marker *and* a cross-object internal access, and only the first is settled by the rename.

`NatsDispatcher.internalStart` → **`startImpl`**, staying `protected`. It looked like the easy case — one in-repo caller, `@SuppressWarnings("SameParameterValue")` on the `threaded` parameter, no override in `NatsDispatcherWithExecutor` — which reads as a dead flag behind a decorative `protected`. It is the opposite. `threaded=false` is never passed *in this repo* because it is the case that exists **for external implementors**: `nats-java-vertx-client`'s `VertxDispatcher extends NatsDispatcher` overrides `start(String)` and calls it with `false` to run the drain loop on the Vert.x event loop instead of letting the dispatcher submit itself to the executor.

So this method is genuinely outward-facing extension API, which makes it the case `PLAN_CORE_JETSTREAM_BOUNDARY.md` §3b describes: **an internal marker must not be published as API.** `_start` would have been actively wrong — it would tell the one audience that needs this method that it is not for them.

`Impl` is what it took instead, matching the `NatsConnection` lifecycle set. That is the right read of the marker: `Impl` here says *the implementation half of `start`, which subclasses may drive* — a statement about structure, not about audience — where `_` would have made a claim about audience that is false. It keeps the `start` stem, so the stem rule is satisfied either way.

**This is the one rename in the audit that changes public API**, and it needs a downstream edit in `nats-java-vertx-client` (one line). Deliberate: v3 is a new release, and the alternative was leaving the word `internal` on the least internal method in the file.

Two consequences beyond the name:

* The package-private constructor does **not** make `protected` unreachable. `VertxDispatcher` declares itself into `io.nats.client.impl` and subclasses from inside the package. Any "can anyone actually extend this?" reasoning about the dispatcher has to account for that, and so does the boundary plan — a sealed module would break that project.
* It has no javadoc. If `threaded=false` is the supported way to bring your own scheduler, that contract is currently only discoverable by reading the body — and now that the method is being renamed anyway, that is the moment to write it down.

Everything else still carrying a marker is deliberate: `beforeQueueProcessorImpl` (public API) and the five `NatsConnection` lifecycle methods.

**Possible fine-tuning back out of `_`.** Individual methods may move to a composed name later. The stem rule is what keeps that safe: a composed name is only right where there is no public counterpart to point at, as with `requireActiveConnection`. Moving one method under that constraint does not cost the set its coherence.

## Out of scope

Test sources use `_` for shared bodies invoked by several `@Test` methods — `_test*`, `_run*`, `_cover*`. Its own consistent pattern, unrelated. `ConsumerCreator`'s 21 `_` setters are original and were never touched. Variables are out of scope entirely.
