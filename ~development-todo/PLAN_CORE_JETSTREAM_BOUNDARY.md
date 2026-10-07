# Core ↔ JetStream Boundary & Package Topology Plan

**Superseded in direction 2026-10-07.** JPMS module-path support was dropped as a requirement (`OSGi_JPMS_TODO.md`, decision at the top): modules share packages by design and may use each other's package-private and protected members. §5 Option 3 (distinct packages + `module-info`), Phase 4–5, the "JetStream uses core only like a user" goal, and §10's interface-driven cleanup no longer follow from a requirement. The coupling measurements (§2, §3, §10a, §10b) remain accurate as of their dates. The plan is to be re-looked at fresh on this basis.

**2026-10-07:** `NatsConnection` / `NatsConnectionV3` internals were made package-private, then reverted the same day (Scott): `protected` stays, because users may subclass (for example `NatsServerPool`) in cases not envisioned. Access is as before. See `PROTECTED_ACCESS_AUDIT.md`. JetStream reaches the internals through the shared package (OSGi: jetstream is a fragment of core).

**Status:** Plan / design only. No code changes proposed for now — this is the "make the plan" deliverable for `TODO.md` › `## Plans / Audits TBD` item 1. It is still gated on settling the in-flight work ([[project_connection_removal]] especially); see §7.

**Goal (Scott's words):** JetStream should consume core *just like any user would* — touching only core's public API, never a private/internal type or member. Today it is far from that, and nothing in the build stops it.

## 1. The problem in one paragraph

`io.synadia.client.impl`, `io.synadia.client.api`, and `io.synadia.client.utils` are all **split packages**: both the `core` jar and the `jetstream` jar contribute classes to the same package names, and there is **no `module-info.java` anywhere in the repo**. That combination has two consequences. (1) JPMS/OSGi blocker: two jars exporting the same package can't both sit on the module path — this is exactly `OSGi_JPMS_TODO.md` O3. (2) The boundary is invisible to the compiler: because the package name is shared and everything is on the classpath, jetstream code can call `protected` and package-private members of core classes with no import and no error. The coupling is real but nothing surfaces it, so it grows silently.

A subtlety that changes the framing: every core type jetstream reaches for is a **`public class`** (verified — `NatsConnection`, `NatsDispatcher`, `NatsMessage`, `Headers`, `NatsSubscription`, `StatusException`, `NatsSubscriptionFactory`, `JetStreamMetaData` are all `public`). So the leak is *not* mostly "jetstream calls private core code." It's "these types live in `.impl` (never meant as public API) yet are fully reachable, and jetstream leans on them — plus a small tail of genuinely `protected`/package-private member access that only compiles because the package is split." The fix is therefore mostly **building the public API surface jetstream actually needs**, then using package topology + `module-info` as the *ratchet* that proves the leak is gone (every remaining illegal reach becomes a compile error).

## 2. Verified coupling map (read-only, measured today)

Split-package file counts (main sources):

| package | core files | jetstream files |
|---|---|---|
| `io.synadia.client.api` | 2 (`ServerInfo`, `Status`) | 73 |
| `io.synadia.client.impl` | 40 | 56 |
| `io.synadia.client.utils` | 27 | 5 |

Core **`.impl`** types referenced from jetstream main sources (whole-word, distinct files / total refs):

| core-internal type | jetstream files | refs | note |
|---|---|---|---|
| `NatsConnection` | 18 | 35 | the connection handle — see §3 member breakdown |
| `NatsDispatcher` | 12 | 22 | created + driven by jetstream push consumers |
| `Headers` | 11 | 70 | user-facing message-header value type |
| `NatsMessage` | 5 | 5 | concrete message / builder |
| `NatsSubscription` | 2 | 2 | |
| `StatusException` | 1 | 1 | unchecked; parent of the internal JS status exception |
| `NatsSubscriptionFactory` | 1 | 1 | interface |
| `JetStreamMetaData` | 1 | 1 | actually a JS concept living in core `.impl` |

Core **`.utils`** types referenced from jetstream (distinct files):

| util type | jetstream files | note |
|---|---|---|
| `ApiUtils` | 21 | shared helpers |
| `Validator` | 17 | shared validation |
| `NatsConstants` | 12 | shared constants |
| `NatsRequestCompletableFuture` | 2 | holds `CancelAction` (used in `JetStream`, `JetStreamImpl`) |
| `ScheduledTask`, `MessageSupplier`, `IncomingHeadersProcessor`, `Digester`, `Debug` | 1 each | |

**Correction to the TODO note:** the counts it quoted — `MessageManager ×9`, `PullMessageManager ×6`, `NatsMessageConsumer ×3` — are **jetstream-internal** types (they live in `jetstream/.../impl`), i.e. jetstream's own cohesion, *not* core coupling. They are not part of this problem. The real core coupling is the two tables above.

## 3. The sharp end: what jetstream calls on `NatsConnection`

Distinct methods jetstream invokes on a `NatsConnection` receiver, with their real visibility in `NatsConnection.java`:

**Already `public`** (user-like operations — the legitimate surface):
`publish`, `request`, `requestAsync`, `createDispatcher`, `closeDispatcher`, `createInbox`, `getOptions`, `getServerInfo`, `isForceFlushOnRequest`, `notifyErrorListener`, `processException`.

**Genuinely internal — only compiles because the package is split** (this is the whole enforcement problem, and it's a *short* list):

| member | visibility | what jetstream needs it for |
|---|---|---|
| `_createSubscriptionByFactory(...)` | package-private | create a subscription with internal-only knobs |
| `getScheduledExecutor()` | protected | schedule heartbeat / pull timers |
| `isClosed()` | protected | guard operations during teardown |
| `isClosing()` | protected | same |

That is the entire hard-coupling tail on the biggest offender: **four members** — and §8 shrinks it further to **two**: `isClosed()`/`isClosing()` are redundant with the existing `public ConnectionStatus getStatus()` (the enum has `CLOSED`/`DISCONNECTED`/…), so jetstream can drop them for free. The two that genuinely remain — `getScheduledExecutor()` (one call site) and `_createSubscriptionByFactory(...)` (one call site) — are what §8 is about. Everything else jetstream does with the connection is already a public operation. This is the single most important finding — the "give core a real public API" job is much smaller than 18-files/35-refs suggests. (The same member-level pass still needs to run for `NatsDispatcher`, `Headers`, `NatsMessage`, `NatsSubscription` — that's Phase 0 below. `NatsConnection` was done here as the exemplar and because it dominates.)

### 3a. Phase 0 progress — the member-level pass, measured 2026-08-15

§3 says the same member-level pass still needs running for `NatsDispatcher` and friends. Part of it has now been done, as a by-product of `UNDERSCORE_INTERNAL_NAMING_AUDIT.md`: every **qualified call to an internal method** (`obj._method(...)`, plus the package-private non-`_` cases) was enumerated mechanically. Eleven distinct relationships exist across `core` + `jetstream` + `service`.

**Only two of the eleven cross the module boundary, and both are `JetStream` reaching into core:**

| Target | Declared in | Called from |
|---|---|---|
| `_createSubscriptionByFactory` | `NatsConnection` | `JetStream:631` |
| `_subscribeByFactory` | `NatsDispatcher` | `JetStream:636` |

The first is already the §3 entry. **The second is new** — it is the `NatsDispatcher` half of the same subscription-factory injection, and §8 already anticipates it ("`NatsDispatcher._subscribeByFactory` (the async path, package-private today, same factory) → public too"). So the dispatcher member-level pass is now done and yields exactly one member, the one §8 predicted.

The other nine are internal to a single module and survive a package split untouched: `_createJsSubscription`, `_createConsumer` (×2 callers), `_pull` (×3), `_nextUnmanagedNoWait` (×2), `_nextUnmanaged`, `nextJson` (×2), `subscribeDeleteConsumerOnException`.

Two caveats on the method used. It finds only *qualified* calls to `_`-marked or known package-private members, so it does **not** catch `getScheduledExecutor()`/`isClosed()`/`isClosing()` — those are `protected`, unmarked, and were found by §3's read. The two passes are complementary; neither alone is complete. It also does not cover **inheritance-based** reach, which §8 identifies as the real coupling for subscriptions (`JetStreamSubscription extends NatsSubscription`), nor `Headers`/`NatsMessage`, still outstanding for Phase 0.

**Naming note.** These members were renamed by the naming audit (`createSubscriptionInternal` → `_createSubscriptionByFactory`, `subscribeImplByFactory` → `_subscribeByFactory`) under the rule "`_` means internal". That is an *interim* state and points the opposite way from this plan, whose end state (§8, M3-factory) is to promote both to **public** and drop the marker entirely. When that lands, the `_` comes off — this plan's end state supersedes the naming convention for these two members specifically, because they stop being internal.

### 3b. The inheritance surface — measured 2026-08-15

§3a's sweep finds only *qualified* calls. The other half of the coupling is **inheritance**, which §8 already identifies as the real one: `JetStreamPushSubscription` / `JetStreamPullSubscription` → `JetStreamSubscription` → `NatsSubscription` → `NatsMessageSink`. Those subclasses reach core members with no qualifier at all, so nothing in §2 or §3a sees them.

Enumerated: **four package-private members of `NatsSubscription`, ten call sites.**

| Member | Visibility today | Refs in jetstream |
|---|---|---|
| `_nextMessage` | package | 5 — `JetStreamPullSubscription:139,:177`, `JetStreamSubscription:128,:143,:175` |
| `invalidate` | package | 2 |
| `setBeforeQueueProcessorFunction` | package | 2 |
| `reSubscribe` | package | 1 |

**`protected` is the mechanism that survives the split.** Package-private access works today only because the package is split; separate the packages and it breaks. `protected` does not break — a subclass reaches protected members across packages and modules.

But it is not free, and this is the adjustment this plan needs: **`protected` on an exported, subclassable type is public API.** Making these four `protected` publishes them as the contract for anyone extending `NatsSubscription`, alongside whatever else the base exposes. That is the same commitment §8 describes for the factory, now with a member list attached — and it means the subscription-extension decision is not "promote one factory method", it is "publish a four-member protected contract plus the factory".

Two consequences worth carrying into the decision:

* The names matter more once they are `protected`. `_nextMessage` carries an internal marker (`_` means internal per `UNDERSCORE_INTERNAL_NAMING_AUDIT.md`) and would be publishing that marker as API. Either the member does not become `protected`, or it gets a non-marked name — the same supersession already recorded for `_createSubscriptionByFactory` and `_subscribeByFactory`.
* If the goal is to *shrink* the contract rather than publish it, the alternative is to give jetstream a narrower extension point than "subclass `NatsSubscription`" — but that is a redesign of the subscription hierarchy, not a visibility change, and is out of scope for Phase 0.

### 3c. A third party already subclasses into `io.…client.impl` — measured 2026-08-16

Found while closing the naming audit, and it changes the risk profile of every "seal the package" move in this plan.

`nats-java-vertx-client` declares `VertxDispatcher` **into the `io.nats.client.impl` package** (V2's spelling of it) so that `VertxDispatcher extends NatsDispatcher` can override `start(String)` and call the `protected` inner start with `false`, running the drain loop on the Vert.x event loop instead of submitting the dispatcher to the executor. `threaded=false` exists solely for that caller; nothing in this repo ever passes it, which is why the parameter carries `@SuppressWarnings("SameParameterValue")` and reads as dead. (That method is `startImpl` as of 2026-08-16 — renamed from `internalStart` by the naming audit, which is itself a downstream break for this project.)

Three things follow:

1. **The dispatcher has an extension contract, and it is undocumented.** `startImpl` has no javadoc. Whatever this plan does to `NatsDispatcher`'s visibility, the `threaded` flag is a supported entry point that needs writing down before it can be reasoned about — or deliberately withdrawn.
2. **"Package-private constructor ⇒ not extensible" is not a valid argument here.** `NatsDispatcher`'s constructor is package-private, which looks like it forecloses outside subclassing. It does not, while the package is open: a third party puts its class in the package and reaches both the constructor and the `protected` members. Any visibility reasoning in this plan that leans on constructor access has to be re-checked against that.
3. **This is what `module-info` actually breaks.** §7's ratchet turns illegal reaches into compile errors, which is the point — but the same seal makes this out-of-tree subclass impossible, not merely non-compiling here. That is a downstream-compatibility decision for the v3 release, not a mechanical cleanup, and it should be made explicitly rather than discovered by the vertx client failing to build. It is also a live example of the audience §8's SPI tier is for.

## 4. Three categories of coupling — each has a different remedy

The coupling is not one problem; it's three, and lumping them together is what makes it look intractable.

**A. User-like connection operations** (`NatsConnection`, `NatsDispatcher`, `NatsSubscription`, `NatsSubscriptionFactory`).
Jetstream uses these the way any application would — publish, request, subscribe, create/close dispatchers. The types just happen to live in `.impl`. Remedy: a **public connection surface** in an exported package, and — per Scott's steer — promote the tiny irreducible internal tail (executors, subscription factory) to public API too rather than hiding it. This is the "clean up core first" work; **§8 is a full design exploration of it** (public-type hygiene, promote-to-public vs qualified-SPI strategy, the subscription extension point, and the interface-vs-concrete question). It is co-designed with [[project_connection_removal]].

**B. User-facing value types stuck in `.impl`** (`Headers`, `NatsMessage`, arguably `JetStreamMetaData`).
`Headers` (70 refs!) and the concrete message/builder are data types a *user* also holds — they were never really "internal." Remedy: **promote to a public API package**, not hide them. `JetStreamMetaData` is a JetStream concept that is currently misfiled in core `.impl`; it likely belongs in jetstream (or core public), TBD in Phase 1.

**C. Shared utility code with no proper home** (`ApiUtils`, `Validator`, `NatsConstants`, `NatsRequestCompletableFuture`/`CancelAction`, and the long tail).
These aren't "core internals jetstream shouldn't touch" — they're common helpers both modules legitimately need. Forcing jetstream to go through a public API here would be artificial. Remedy options: (1) a shared/common exported utils package both modules depend on; (2) accept `utils` as an intentionally-exported shared package (then it must be un-split — one owner); (3) inline/duplicate the few small ones. Decide per-type in Phase 1. `StatusException` sits between B/C — it's the parent of the internal JS status exception; its disposition falls out of the exceptions work already done (it stays unchecked/internal, but its *package* is part of this decision).

## 5. Package topology decision

This is the real decision point and it is Scott's to make. Three options:

**Option 1 — status quo (keep split `.impl`/`.api`/`.utils`, no module-info).** Fails JPMS/OSGi (O3), keeps the boundary unenforced. Not viable long-term; it's the thing we're trying to leave.

**Option 2 — flatten `.impl` up one level into `io.synadia.client`.** *Rejected.* This is the idea floated in the TODO note, and on inspection it makes things **worse**: `io.synadia.client` is *also* a shared/base package, so moving internals there makes them (a) still split and (b) more prominently public. It moves in the opposite direction from the goal.

**Option 3 — distinct internal packages per module + `module-info` (recommended).** Give each module's internals a package name it *alone* owns (e.g. core internals → `io.synadia.client.core.impl`; jetstream internals keep/rename to a jetstream-only internal package). Nothing is split anymore. Then add a `module-info.java` per module that `exports` only the public packages and keeps the internal package unexported. Now:
- the split-package JPMS blocker is gone (O3 resolved);
- every remaining illegal reach from jetstream into a core-internal type is a **compile error** — the compiler enforces exactly the boundary Scott wants;
- the D4 exception move (§6) falls out naturally rather than being hand-tuned.

Recommendation: **Option 3**, but note it is the *last* mechanical step — it only compiles cleanly *after* categories A/B/C in §4 have given jetstream a public path for everything it needs. Introducing `module-info` first would just produce a wall of errors with nowhere to land them. Build the API, repoint jetstream, *then* ratchet.

## 6. D4 falls out — do not hand-tune it now

D4 (move the JetStream exception subtypes from `.impl` to `.api`) is a **leaf** of this decision, not a standalone task. Current state (verified):

| type | package today | disposition |
|---|---|---|
| `JetStreamException` (base) | `io.synadia.client.api` | already public/api ✓ |
| `JetStreamTimeoutException` | `io.synadia.client.api` | already ✓ |
| `JetStreamProtocolException` | `io.synadia.client.api` | already ✓ |
| `JetStreamApiException` | `io.synadia.client.impl` | **move to `.api`** (public subtype in the wrong package) |
| `JetStreamStatusException` | `io.synadia.client.impl` | **move to `.api`** |
| `JetStreamStatusInternalException` | `io.synadia.client.impl` | **stays** — package-private, internal only |

The two moves should happen as part of settling the topology (Phase 4), so the exceptions land in whatever the final public-API package is, once, instead of being moved now and again later.

## 7. Sequencing & dependencies

This plan is **blocked on settling everything else first** — primarily [[project_connection_removal]] (category A's public connection surface is a direct output of the interface-removal / `NatsConnection`→`Connection` work) and the assorted in-flight items in `TODO.md`. It also *subsumes* `OSGi_JPMS_TODO.md` O3 (split-package strategy) — do not solve O3 separately.

Proposed phases when we do start:

- **Phase 0 — finish the coupling map (read-only).** Extend §3's member-level analysis to `NatsDispatcher`, `Headers`, `NatsMessage`, `NatsSubscription`, and the `.utils` types. Output: per-type "what jetstream uses + is it public + does core already have (or need) a public equivalent." (`NatsConnection` done here.)
- **Phase 1 — decide disposition per type** using the A/B/C buckets in §4: promote / provide-public-equivalent / share-via-common / inline. Also place `JetStreamMetaData` and the `.utils` types.
- **Phase 2 — build out core's public API** (additive, low breakage): the public connection surface (with [[project_connection_removal]]), public `Headers`/message types, a home for the shared utils, and a narrow public equivalent for the §3 four-member tail.
- **Phase 3 — repoint jetstream** onto the public API; delete every internal reach. Jetstream now compiles against only exported core packages.
- **Phase 4 — rename internal packages so nothing is split** (Option 3), and move the D4 exceptions (§6) into their final public package in the same pass.
- **Phase 5 — add `module-info.java`** per module, exporting only public packages. Compiler now enforces the boundary; O3 resolved. Optional: OSGi bnd metadata alignment.

Each of 2–5 is a breaking-ish or wide change and would be its own implementation item under the Recap, not one mega-commit.

## 8. Cleaning up core first — building the public connection surface (design exploration)

This is the direct answer to "clean up core first, and maybe go back to a `Connection` interface." Bucket A's end state is: **jetstream touches only public core API, expressed entirely in public types — exactly as any user would, with nothing reached around the back.** Getting there is mostly core-side cleanup and decomposes into four largely-independent moves. Two measured facts about today's `NatsConnection` drive it.

**Fact 1 — the public surface already leaks internal types.** `NatsConnection` has 52 public methods, but several are *typed* in `.impl`: `createDispatcher()` → `NatsDispatcher`, `subscribe(...)` → `NatsSubscription`, `publish(NatsMessage ...)`. So "program against public methods only" is not enough — you still receive `.impl` types. The fix is cheap: retype these signatures to the public interfaces that **already exist** — `Message`, `Dispatcher`, `Subscription` (all confirmed public in `io.synadia.client`). Returns are covariant, so the concrete `NatsX` is still what's returned at runtime; callers already holding the interface don't change.

**Fact 2 — the irreducible internal surface is two operations, not a class.** After dropping `isClosed()`/`isClosing()` in favor of public `getStatus()` (§3), exactly two genuine internal needs remain, each a *single* call site:
- **scheduling** — `MessageManager` schedules the heartbeat alarm on `conn.getScheduledExecutor()`.
- **subscription-factory injection** — `JetStream._createJsSubscription` calls `createSubscriptionInternal(inbox, deliverGroup, dispatcher, NatsSubscriptionFactory)` to plant its **own** `NatsSubscription` subtype into core's subscription machinery.

### Which strategy — and why the stated goal decides it

There are two ways to give jetstream what it needs: **(1) promote to public** — everything jetstream touches becomes real public API; or **(2) qualified-export SPI** — keep the hooks internal and expose them to the jetstream module *alone* via JPMS `exports … to …jetstream`. They pull opposite directions, and Scott's own goal settles it: *"JetStream should use core just like any user would."* A user **cannot** consume a qualified export — so a qualified SPI would make jetstream a privileged friend module, exactly what the goal rules out. Taken literally, the goal means **everything jetstream needs must be public** (Strategy 1). Both of Scott's steers point the same way — *"executors feel like they should be public, documented to use with care"* and *"we probably have to live with the factory version being public, so might have to rename it too."* So the plan adopts **Strategy 1** as primary. Note "public" here spans two conceptual tiers — everyday **API** and an extension-author **SPI** (see "Expressing … the SPI tier"); the factory is SPI-flavored but, per Scott, stays in the normal public package and is just **labeled advanced via javadoc** (no `.spi` package). Qualified export drops to a fallback for one case only — if we decide the SPI should be *closed* to first-party modules — and choosing it means conceding jetstream is a privileged friend, not a plain (advanced) user, for that hook.

### The four moves

**M1 — retype the public signatures to public interfaces** (Fact 1). Pure bucket-B hygiene, safe, helps users too. Do first.

**M2 — delete jetstream's redundant reach**: `isClosed()`/`isClosing()` → `getStatus()`. Removes half the §3 tail for free.

**M3 — expose the two irreducible needs, each by the right channel:**
- **Executors → public, documented "use with care."** `getScheduledExecutor()` (and its siblings `getExecutor`/`getReaderExecutor`/`getWriterExecutor`, all `protected` today) are a general advanced-use facility, not a privileged extension hook — a power user could legitimately want to schedule on the client's pool. Promote the accessors to public with a javadoc caveat (don't shut it down, don't block it, it's the client's own pool). Jetstream then uses them as a normal public caller. This is the simplest channel and it's *appropriate* here precisely because there's nothing dangerous to hide.
- **Subscription-factory injection → make public and rename** (Scott's call). This is the one need that isn't a plain method — it's how JetStream plants its own subscription subtype, so promoting it publishes core's whole subscription base as an extension point. Full blast radius in "The subscription extension point" below. Short version: rename `_createSubscriptionByFactory` → a public `createSubscription(...)` overload (no `Internal`), promote `NatsSubscriptionFactory` → public `SubscriptionFactory` (drop the `Nats` prefix), and accept that core's `Subscription` base becomes a public "bring your own subscription" API — kept in the normal public package but **labeled advanced via javadoc** (see "Expressing … the SPI tier" below; decision is javadoc-only, no special packaging).

**M4 — the type/module topology** (§5, Option 3) that makes M1–M3 enforceable: distinct per-module internal packages + `module-info`, applied last.

### The subscription extension point (why the factory hook is special)

`_createSubscriptionByFactory` is not a normal method — the real coupling is **inheritance**: `JetStreamPushSubscription` / `JetStreamPullSubscription` → `JetStreamSubscription` → `NatsSubscription` → `NatsMessageSink`. The factory just constructs that subclass with core's wiring (`sid`, connection, dispatcher). So "make the factory public" is bigger than one rename — promoting it commits the whole subscription base to public API:

- `NatsSubscriptionFactory` → public `SubscriptionFactory`; its method (`createNatsSubscription(sid, subject, queueName, NatsConnection, NatsDispatcher)`) retyped to public types (`Connection`, `Dispatcher`, returning `Subscription`).
- `_createSubscriptionByFactory` → public `createSubscription(...)` overload (no `Internal`).
- `NatsDispatcher._subscribeByFactory` (the async path, package-private today, same factory) → public too.
- **The load-bearing one:** `NatsSubscription` becomes a public, exported, **subclassable** base (it's already `public` with a `public` constructor), which also drags its super `NatsMessageSink` into the exported set and bakes `Connection`/`Dispatcher` into the public constructor contract.

That is the price, and it's worth naming plainly: you are publishing a **"bring your own Subscription" extension API**. Fine if we're prepared to support that contract for good. The `sid`/wiring constructor params are a bit raw for public API, so this is exactly where the rename/tidy effort should land — a cleaner `SubscriptionFactory` signature, and hide or encapsulate `sid` generation rather than passing it in.

The only way to make the factory public *without* exposing the subscription base is to stop extending it — have JetStream **compose** a plain core `Subscription` (wrap + delegate) instead of subclassing it. That's a genuine refactor (JS subscriptions currently *are* `NatsSubscription`s and rely on it), so it's a separate, larger question — note it as the alternative, don't assume it. Qualified export (JPMS `exports … to`) remains the fallback if we ever decide a specific hook must stay non-public; for the subscription factory, Scott's call is to accept it as public.

### Expressing "for client-library authors, not the average user" — the SPI tier

Scott's refinement: the subscription factory (and arguably executor access) isn't really "average-user public API" — it's *"for those who know what they're doing, specifically developers of jnats client libraries."* That is the definition of an **SPI** (Service Provider Interface), and it means the model has **three tiers**, not two:

1. **API** — the everyday public surface, for any user (`publish`, `request`, `subscribe(subject)`, …).
2. **SPI** — public and callable, but *for extension/library authors*: build-your-own-subscription, schedule on the client pool. JetStream is the canonical consumer; a third party layering on core is another.
3. **Internal** — nobody outside the module.

`_createSubscriptionByFactory` is tier 2. In its simplest form Scott's instinct is right — it's *"just a `subscribe` overload with extra api-doc"* (`subscribe(subject, queue, dispatcher, SubscriptionFactory)`). The design question is only **how loudly we label it tier-2**, and there's a spectrum from convention to compiler-hard. The codebase has none of this yet (it marks "advanced" things with plain javadoc, e.g. `Options`' "advanced setting"), so whatever we pick is a new, reusable convention.

| option | mechanism | enforcement | precedent | cost |
|---|---|---|---|---|
| **A. Javadoc only** | an `@apiNote` on the overload: "SPI for building higher-level clients; average users use plain `subscribe`" | none (prose) | matches today's "advanced setting" style | ~0 |
| **B. Marker annotation** | tag members/types `@ForClientDevelopers` (home-grown) or JetBrains `@ApiStatus.Internal`/`Experimental` | IDE-level: IntelliJ natively warns on `@ApiStatus.Internal` use from outside the module — and the audience *is* developers | Guava `@Beta`, JetBrains `@ApiStatus.*` | tiny (custom) or one compile-only dep |
| **C. Dedicated `spi` package** | put `SubscriptionFactory` + the subclassable `Subscription` base + the factory entry in `io.synadia.client.spi`, with a `package-info` stating the contract | none (naming signal), but keeps the everyday surface clean | `java.nio.channels.spi`, JDBC driver SPI, SLF4J `org.slf4j.spi` | small (a package + moves) |
| **D. Gate accessor** | group the ops behind `connection.spi()` → a `ConnectionSpi` object; nothing advanced shows in normal `Connection` autocomplete | none, but a real "you asked for it" speed bump | Netty `channel.unsafe()` | small |
| **E. Qualified export** | `exports io.synadia.client.spi to io.synadia.jetstream, …kv, …os;` | **compiler-hard** — only listed modules compile against it | JPMS friend modules | needs `module-info` (M4) |

**The fork that decides whether E is even on the table: is this SPI OPEN or CLOSED?**
- **CLOSED** (first-party only — our jetstream/kv/os): E is available and is the *only* option with real enforcement; it also keeps the SPI out of the public javadoc entirely. Cost: a third party can never build a custom subscription layer on core.
- **OPEN** (any client-library author, third parties included): enforcement is impossible *by construction* — you can't name unknown modules in a qualified export — so you're on A–D (convention + tooling). This is the more natural reading of "developers of jnats client libraries" if we want an ecosystem, and it avoids re-introducing the "jetstream is a privileged friend, not a plain (advanced) user" coupling we were trying to shed.

**Decision (Scott): keep it lightweight — Option A only, no special packaging.** The `.spi`-package / `ServiceLoader` / qualified-export machinery exists for SPIs implemented by *unknown third parties discovered at runtime* (JDBC drivers, SLF4J bindings). Here the only "provider" is us — JetStream, maybe our own KV/OS — so that ceremony would be ecosystem infrastructure for an ecosystem of one. Not worth it. The minimal correct move:
1. Make the overload public and **rename off `Internal`** (a public method can't honestly be `…Internal`) → e.g. `subscribe(subject, queue, dispatcher, SubscriptionFactory)`.
2. **Document it (Option A / `@apiNote`)**: "Advanced — for building higher-level clients on top of the core connection (e.g. JetStream); most users should use the plain `subscribe` methods." This is exactly the codebase's existing convention (`Options`' "advanced setting").
3. *Optional, later:* a one-line marker annotation (Option B) if we ever want an IDE nudge — additive, no rework.

**Key clarification: packaging is only a signpost, not a gate.** Promoting the factory makes `NatsSubscription` a public subclassable base *regardless* of which package it sits in (JetStream subclasses it either way), so a `.spi` package would change the *label*, not the public blast radius. The only option that is an actual gate is **E (qualified export)** — and that needs `module-info` *and* a deliberately *closed*, first-party-only audience. Skip it unless we ever decide we want that hard boundary; the javadoc-only path is the default. So this refines the earlier "promote to public" only in *how it's labeled* (a javadoc `@apiNote`, staying in the normal public package), not in what becomes public.

### The interface-vs-concrete question ("go back to a `Connection` interface")

Framed honestly against the just-completed work: the `Connection` interface has already been **removed** (verified — `Connection.java` is gone; `NatsConnection` now `implements AutoCloseable` only; [[project_connection_removal]] is essentially done). So this is *re-creating* it, not keeping it.

**Key insight: the boundary does not require the interface.** With M1–M4, a **concrete public `Connection` class** (rename `NatsConnection` → `Connection`, move to an exported package; all public methods already in public types after M1; executors public-with-care; the subscription factory promoted to public) hides internals from users just as completely as an interface would. The interface and the boundary are **orthogonal decisions**.

So reintroduce the interface only for an *independent* reason, not for the boundary:
- Justifies it: (i) more than one connection implementation — there is exactly one today; (ii) users/tests want to mock `Connection` — a concrete class is harder (not impossible) to mock; (iii) you want the public API to be a pure abstract contract with the concrete class fully unexported.
- Argues against: it reverses the simplification just finished, adds an indirection layer, and the "only ever one impl" rationale that drove removal still holds.

**Recommendation:** default to the **concrete public `Connection` class + executors-public-with-care + subscription factory promoted to public (renamed off `Internal`, javadoc-labeled advanced)**. It keeps the simplification, exposes only intentional public API, and needs no new interface. Reintroduce the interface only if Scott wants the fully-abstract public contract or first-class mockability — and if so it's cheap to layer on *later* (extract-interface is mechanical) once the type sits in the right package. Either way M1–M4 is the work; the interface is a thin optional cap on top.

### Ordering within bucket A

Under Strategy 1 almost all of M1–M3 is **additive** and does not need the module split: M1 (retype signatures), M2 (`isClosed/isClosing`→`getStatus`), M3-executors (visibility bump), and M3-factory (promote + rename `NatsSubscriptionFactory`→`SubscriptionFactory`, `_createSubscriptionByFactory`→`createSubscription`, publish the subscription base) are all things we can do while the packages are still split. Only **M4** — the internal-package rename + `module-info` that turns the boundary into compile errors — is the disruptive end step, and it goes with D4 (§6). So the whole "clean up core first" payoff (no type leak, no `Internal`-named public methods, a real public subscription-extension API) lands *before* the module rename; M4 then just proves there's nothing left reaching around the public API.

## 9. Risks / notes

- **Breaking rename.** Any internal-package rename (Phase 4) is source-breaking for anyone who reached into `.impl` — acceptable only on a V3 boundary, which is where we are. Same caveat `OSGi_JPMS_TODO.md` O3 already records.
- **Don't front-load `module-info`.** It's the ratchet, not the lever. Adding it before the API exists just produces unbuildable errors.
- **Category C is a genuine fork.** "Shared utils belong in a common exported package" vs "jetstream shouldn't touch core utils at all" are different philosophies; picking one shapes whether we grow a `-common` module. Flag for Scott in Phase 1.
- **This corrects the TODO note's coupling numbers** (§2) — the plan is built on the measured map, not the earlier estimate.

## 10. The `Connection` interface and the boundary together — re-measured 2026-10-07

§8 recommended a concrete public class over an interface because there was "exactly one" connection implementation. That is no longer true: `ConnectionImplementation` selects `NatsConnection` (Classic) or `NatsConnectionV3`, and active/passive (`PLAN_ACTIVE_PASSIVE_V3.md`) adds a third subclass. That is §8's justification (i), so this section plans the interface and the boundary cleanup as one piece of work: the interface is the public surface the other modules must use, so it has to hold everything they need, and nothing they reach today outside it may remain.

Subscription internals are out of scope here (Scott, 2026-10-07): they are not part of the connection boundary; the `_` names stay, and `protected` is acceptable for `NatsSubscription` members if a package split ever requires it.

### 10a. What the other modules call on the connection (measured)

45 call sites on a `NatsConnection` receiver across `jetstream`, `kv`, `os`, `service` main sources (receivers `connection`, `conn`, `nc`; inheritance and dispatcher calls are in 10b).

| Method | Calls | Visibility today | Modules |
|---|---|---|---|
| `publish` | 10 | public | jetstream, service |
| `notifyErrorListener` | 7 | public | jetstream |
| `isForceFlushOnRequest` | 4 | public | jetstream |
| `createDispatcher` | 4 | public, returns `NatsDispatcher` | jetstream, service |
| `request` | 3 | public | jetstream, service |
| `createInbox` | 3 | public | jetstream |
| `closeDispatcher` | 3 | public | jetstream, service |
| `getOptions` | 2 | public | jetstream, kv |
| `subscribe` | 1 | public, returns `NatsSubscription` | service |
| `requestAsync` | 1 | public | jetstream |
| `processException` | 1 | public | jetstream |
| `RTT` | 1 | public | service |
| `getServerInfo` | 1 | public | jetstream |
| `isClosing`, `isClosed` | 1 each | **protected** | jetstream `JetStreamImpl:44` |
| `getScheduledExecutor` | 1 | **protected** | jetstream `MessageManager:235` |
| `_createSubscriptionByFactory` | 1 | **package-private** | jetstream `JetStream:539` |

`kv` makes one connection call (`getOptions`, `KeyValue:523`), `os` none; both go through JetStream. `service` uses only public methods, but types its field as `NatsConnection` (15 declarations) and receives `NatsDispatcher`/`NatsSubscription`.

### 10b. Reach the qualified-call count does not see (measured)

- `NatsDispatcher._subscribeByFactory` (package-private), `JetStream:544`.
- Other extends across the boundary: `JetStreamStatusException extends StatusException`, `JsValidator extends Validator`, `DebugJs extends Debug`.

Core `.impl`/`.utils`/`.api` types referenced from the other modules (files): `Validator` 23, `NatsConnection` 23, `ApiUtils` 21, `NatsConstants` 15, `Headers` 12, `NatsDispatcher` 11, `Status` 9, `NatsMessage` 6, `StatusException` 3, `NatsSubscription` 3, `ServerInfo` 2, `NatsRequestCompletableFuture` 2, `ClientError` 2, and 1 each for `ScheduledTask`, `NatsSubscriptionFactory`, `MessageSupplier`, `JetStreamMetaData`, `IncomingHeadersProcessor`, `Digester`, `Debug`. Split packages are unchanged: jetstream still has 73 files in `io.synadia.client.api`, 55 in `.impl`, 5 in `.utils`.

### 10c. Proposed `Connection` interface content

In `io.synadia.client` (exported). `NatsConnection implements Connection`; `NatsConnectionV3` keeps extending `NatsConnection` (D1 of `PLAN_NATS_CONNECTION_V3.md` decides whether that changes). `Nats.connect` returns `Connection`.

1. The current public user API of `NatsConnection`, retyped to public interfaces (§8 M1): `createDispatcher` → `Dispatcher`, `subscribe` → `Subscription`, `publish(Message)` instead of `NatsMessage`.
2. The public methods the modules already use that are not user operations: `notifyErrorListener`, `processException`, `isForceFlushOnRequest`. Each needs a decision: on the interface (javadoc-labeled advanced, §8 Option A), or replaced (e.g. `isForceFlushOnRequest` is an options value).
3. The four non-public members, each with an action:
   - `isClosing` / `isClosed` → delete the use; `JetStreamImpl:44` uses `getStatus()` (§8 M2).
   - `getScheduledExecutor` → public on the interface, "use with care" javadoc (§8 M3). Same decision for `getExecutor`.
   - `_createSubscriptionByFactory` + `NatsDispatcher._subscribeByFactory` → public `subscribe(subject, queue, SubscriptionFactory)` on `Connection` and the same on `Dispatcher`; `NatsSubscriptionFactory` → public `SubscriptionFactory` (§8 M3-factory).

What the interface makes visible that §8's concrete class did not: every member in 2 and 3 becomes interface API that a second implementation (V3, AP) must also honor; today they are inherited from `NatsConnection` and come for free.

### 10d. Order

1. M2 (`isClosing`/`isClosed` → `getStatus`) and M1 (retype returns): additive, no interface yet.
2. Extract `Connection` with the members in 10c; `NatsConnection implements Connection`; `Nats.connect` returns `Connection`.
3. Repoint `jetstream`, `kv`, `os`, `service` fields and parameters from `NatsConnection` to `Connection`; any call that no longer compiles is a member 10c missed.
4. Per-module internal packages + `module-info` (§5 Option 3, Phase 4–5), D4 exceptions moved in the same pass. After step 3 the `NatsConnection` row of 10b is gone; the remaining split-package reach is the value types (`Headers`, `NatsMessage`) and the shared utils (§4 B and C).

### 10e. Open decisions

- **C1.** Interface (this section) or concrete public class (§8). Two implementations now exist; recommendation is the interface.
- **C2.** `notifyErrorListener`, `processException`, `isForceFlushOnRequest`: on the interface, or replaced.
- **C3.** Executors public on the interface: `getScheduledExecutor` only, or also `getExecutor` (reader/writer executors stay internal).
- **C5.** Does `Connection` replace `NatsConnection` in every public signature (JetStream, KV, OS, Service factories and constructors), with `NatsConnection` no longer named in any other module's source?
