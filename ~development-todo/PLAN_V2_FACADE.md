# Plan — V2 Facades over V3

**Status: idea only.** Nothing designed, nothing started, no code written. This doc holds the idea, and collects the v3 removals the facade is the intended home for as those removals happen.

## The idea

Build a **v2 facade over v3**, as its own project outside this repo's current module set — probably two: a **V2 Core Facade** and a **V2 JetStream Facade**, mirroring the v3 core/jetstream split so a core-only user doesn't drag JetStream in.

The goal is to let a v2 developer put v3 underneath existing v2-shaped code with **less** migration work. Explicitly **not seamless**. v3 removed and reshaped things on purpose, and the facade is not licensed to undo those decisions — where a v2 API has no honest v3 mapping, the facade either doesn't carry it or carries it with a documented behavior difference.

Why a separate project rather than a compatibility layer inside `core`/`jetstream`:

- v3's own surface stays clean — nothing ships in `jnats3-core` / `jnats3-jetstream` that exists only to serve a v2 shape
- the facade can be deprecated-from-birth, which is the point: it's a migration ramp, not a destination
- it versions and gets dropped independently of the client
- it keeps the "what did v3 remove" story in one place, next to the code that softens each removal

**Sequencing: after the v3 API settles.** Same gate as the KV/OS split — building a facade against a moving surface just means rebuilding it. This is also why the doc is structured as a collector: removals get recorded here as they land, and the facade gets built later from the accumulated list.

## Open questions

None of these are decided.

- **Package naming.** Reuse `io.nats.client.*` so v2 imports compile unchanged, or use a distinct package? Reusing it is the ramp's main value and also its main hazard — it would collide with a real v2 jar on the classpath, and it re-creates the split-package problem `OSGi_JPMS_TODO.md` is trying to get rid of.
- **Compatibility target.** Source compatibility only, or binary too?
- **Delegate vs. re-implement.** How much v2 behavior is a thin adapter over a v3 call, and how much has to be re-implemented because v3 has no equivalent (the pull behaviors below are the second kind).
- **Artifact identity.** Names under the existing per-module `artifactNameExt` scheme, if the facade lives in this build at all.
- **The forbidden list.** Which v3 removals were deliberate API corrections that the facade must *not* resurrect, even though it technically could.

## What lands here

A running list of things removed from v3 that the facade is meant to be the home for. One section per removal.

### Legacy advanced pull behaviors

Comes from `PLAN_REMOVE_LEGACY_PULL_METHODS.md` — that doc owns the v3-side removal (what comes out, what stays, the orphans, the test and javadoc fallout). This section owns what the facade does with it afterwards.

The behaviors: `fetch(int, long)`, `iterate(int, long)` and `reader(int, int)` on the pull subscription, plus the `JetStreamReader` interface and its `JetStreamReaderImpl`. All three wrap the primitive pull API, and all three are superseded in v3 by `ConsumerContext` — `fetch`/`fetchMessages`/`fetchBytes`, `iterate`, and `consume`.

**No copies need to be made when they are removed.** The originals stay in v2 and get lifted from there when the facade is built:

- `io.nats.client.JetStreamSubscription` — the `fetch` / `iterate` / `reader` declarations, including the `Duration` overloads v3 already dropped for the millis convention
- `io.nats.client.JetStreamReader` — the interface
- `io.nats.client.impl.NatsJetStreamPullSubscription` — the implementation; its nested `JetStreamReaderImpl` at `:313` is the same class v3 carries at `:290`

Pinned reference: **nats.java 2.26.3**.

**Non-obvious semantics to preserve.** All of these are in the current v3 copy and were inherited from v2; none are obvious from the signatures, and all of them are easy to lose in a rewrite:

- `fetch` and `iterate` both start by draining messages already buffered on the subscription and only pull for the shortfall — a fully-buffered batch issues no pull at all
- `fetch` shortens the *server-side* expiry (`maxWaitMillis - EXPIRE_ADJUSTMENT`) whenever the wait exceeds `MIN_EXPIRE_MILLIS`, so the client's timeout outlives the server's
- both discriminate status messages by comparing against the pull subject returned by `_pull`; a status belonging to a *different* pull is ignored rather than treated as terminal
- on `InterruptedException` both swallow it, re-set the interrupt flag, and return what they already have rather than throwing — deliberate, so the caller keeps the messages it received
- `reader`'s `repullAt` is clamped into `1..batchSize`; `stop()` only stops further pulls, so buffered messages still drain through `nextMessage`

**Test seed.** `tdb/io/synadia/client/impl/OldJetStreamPullTests.java` holds the only `reader` coverage (`testReader:435`), and the removal plan drops it from the v3 port list rather than porting it — so it lands here. `JetStreamPullTests.testFetch` / `testIterate` go the same way.

**Open, facade-side:** whether the facade re-implements these over v3's primitive `pull*` methods (which survive the removal) or over `ConsumerContext`. The primitive route reproduces v2 behavior exactly and is close to a straight lift; the `ConsumerContext` route gets real flow control but changes observable behavior, which conflicts with the point of a facade.
