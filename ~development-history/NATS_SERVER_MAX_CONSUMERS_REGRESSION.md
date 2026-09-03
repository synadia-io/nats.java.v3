# nats-server: default `MaxConsumers` limit is a backward-compatibility break

Written 2026-09-02. Findings only — no client change made, and the recommendation is that this be fixed server-side.

## Summary

nats-server PR [#8337](https://github.com/nats-io/nats-server/pull/8337) — *"(2.15) [ADDED] JetStream default MaxConsumers limit"*, by @MauriceVanVeen, merged to `main` **2026-09-02T08:58Z** as `2ec24005` (work landed 2026-08-31 as `ab283bda` + `1b91a05d`) — introduces a **default cap of 1000 consumers per stream**, applied to every stream that does not set a positive `max_consumers`.

**A stream that explicitly asks for unlimited consumers (`max_consumers: -1`) is capped at 1000 anyway.** The documented "unlimited" value is silently overridden, with no way for a client to opt out. That is the backward-compatibility break.

Stated intent from the PR: *"to protect future users from apps that create runaway consumers into the many millions."* Reasonable goal — the issue is the treatment of `-1`, and that it applies to streams that already exist.

## How it was found

`nats.java.v3`'s `JetStreamManagementTests.testGetConsumers` creates 600 consumers, asserts `getConsumers` pages at 256, then adds 500 more (1100 total) to assert `getConsumerNames` pages at 1024. Against a head server it now fails on consumer #1001:

```
io.synadia.client.impl.JetStreamApiException: maximum consumers limit reached [10026]
    at io.synadia.client.impl.JetStreamManagement.createOrUpdateConsumer(JetStreamManagement.java:225)
```

Passes against nats-server v2.14.4. Fails against head. The client did not change.

## The mechanism

**1. The default is 1000** — `server/jetstream_api.go`:

```go
// JSDefaultMaxConsumersPerStream is the default maximum number of consumers per stream.
const JSDefaultMaxConsumersPerStream = 1_000
```

**2. It is applied whenever the option is unset** — `server/opts.go`, `setBaselineOptions`:

```go
if opts.JetStreamLimits.DefaultMaxConsumers == 0 {
    opts.JetStreamLimits.DefaultMaxConsumers = JSDefaultMaxConsumersPerStream
}
```

So any server started without an explicit `default_max_consumers` gets 1000. No config change is required to be affected.

**3. `-1` on the stream does not opt out** — `server/consumer.go`, `addConsumerWithAssignmentAndMode`:

```go
maxc := cfg.MaxConsumers
if maxConsumers := srvLim.DefaultMaxConsumers; maxConsumers > 0 && maxc <= 0 {
    maxc = maxConsumers
}
```

The guard is `maxc <= 0`, so a stream config of `MaxConsumers: -1` (unlimited) is treated identically to `0` (unset) and is overwritten with 1000. **There is no stream-level value that means "unlimited" any more.**

## Why this is a compatibility break, not just a new default

1. **It silently redefines a documented value.** `max_consumers: -1` has always meant unlimited. It now means 1000. A client that explicitly requested unlimited gets capped, with no error at stream-create time and no indication anything was overridden — the failure surfaces later, on the consumer that crosses the line.

2. **It applies to streams that already exist.** The cap is evaluated at *consumer-add* time against the server's current limits, not stamped into the stream at creation. So upgrading the server retroactively caps every existing stream. A workload that has been running with 5000 consumers on a stream starts failing at 1000 after a server upgrade, with no change on the client side.

3. **The only opt-out is server-side config.** A client cannot express "I really do want unlimited"; an operator has to set `jetstream { limits { default_max_consumers: -1 } }`. That inverts the existing model, where the stream config was authoritative and account/server limits only *lowered* it.

4. **The fallout has already started.** Follow-up commit `2ad0e27b` — *"[FIXED] Sourcing consumers rejected at stream MaxConsumers limit"* — had to exempt internal consumers (`!config.Direct && !config.Sourcing`) in both the standalone and clustered paths, because the server's own sourcing machinery was being rejected by the new cap. That is a signal that the blast radius is wider than intended.

## Suggested resolution

Honour `-1` as unlimited: change the guard from `maxc <= 0` to `maxc == 0`, so the new default applies only to streams that never specified a value and an explicit `-1` continues to mean what it has always meant. That keeps the runaway-consumer protection for the unspecified case, which is where it is actually aimed, while leaving the documented opt-out working.

If the cap is intended to apply even to explicit `-1`, then it should at minimum be rejected or warned at *stream create/update* time rather than silently accepted and enforced later, so operators find out before production traffic does.

## Client-side note (nats.java.v3)

Not a fix for the above, but adjacent and worth recording: v3 cannot send `max_consumers: -1` even if it wanted to. `StreamCreator` defaults `maxConsumers = -1`, `maxConsumers(long)` normalizes anything `< 1` to `UNSET` (`-1`), and `JsonWriteUtils.addField(StringBuilder, String, Long)` omits negative values entirely — so the field is simply absent from the stream-create request. Given the server ignores `-1` anyway, sending it would not help today; but if the guard is fixed as suggested above, v3 would need `addFieldWhenGteMinusOne` for `MAX_CONSUMERS` to be able to use the opt-out. The same omission applies to `maxMessages`, `maxMessagesPerSubject`, `maxBytes` and `maxMessageSize`.

## References

| | |
|---|---|
| PR | [nats-io/nats-server#8337](https://github.com/nats-io/nats-server/pull/8337) |
| Merge to main | `2ec24005`, 2026-09-02T08:58Z |
| Feature commit | `ab283bda` *[ADDED] JetStream default MaxConsumers limit* |
| Default value | `1b91a05d` *[IMPROVED] Set default for default MaxConsumers limit* |
| Follow-up fix | `2ad0e27b` *[FIXED] Sourcing consumers rejected at stream MaxConsumers limit* |
| Constant | `JSDefaultMaxConsumersPerStream = 1_000`, `server/jetstream_api.go` |
| Enforcement | `addConsumerWithAssignmentAndMode`, `server/consumer.go` ~`:1160` |
| Config key | `jetstream { limits { default_max_consumers: N } }` (`-1` = unlimited) |
