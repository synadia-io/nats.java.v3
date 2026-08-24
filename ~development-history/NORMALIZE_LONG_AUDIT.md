# normalizeLong / normalizeULong Audit Report (v2)

**Scope:** every callsite of `normalizeLong` / `normalizeULong` in `/mnt/c/nats/nats.java.v3` (main sources only), cross-referenced against `/mnt/c/nats/nats-server/server/consumer.go` and `stream.go` to determine, for each field:

1. What value range is **semantically meaningful** on the server?
2. What value range does the server **treat as "default" / "absent"**?
3. Is the client's current `min` parameter correct, or is it sending values that the server will ignore as "default"?

**Headline:** five of six `normalizeLong` callsites use `STANDARD_MIN = 0`, but on every one of those fields the server treats `0` as **"use server default"**, not as an explicit value. The client is therefore sending redundant `:0` payloads that have no effect beyond what omitting the field would do. Recommended fix: switch these from `min=0` to `min=1`. The `STANDARD_MIN` constant should then be deleted in favor of inline `1` (its name is misleading once every callsite uses `1`).

A separate concern emerges around `MaxAckPending` and `MaxDeliver`: the server distinguishes `-1` (= explicit "unlimited") from `0` (= "use default"). The client cannot currently send `-1`; whether that matters is a design decision called out at the bottom.

---

## Method definitions (after the recent dedup)

`core/src/main/java/io/synadia/client/utils/ApiUtils.java`:

```java
public static final long UNSET        = -1;    // signed sentinel
public static final long ULONG_UNSET  =  0;    // unsigned sentinel
public static final int  STANDARD_MIN =  0;    // questionable — see below

// returns UNSET (-1) if l == null or l < min; else l.
public static long normalizeLong(Long l, long min);

// returns ULONG_UNSET (0) if l == null or l <= 0; else l.
public static long normalizeULong(Long u);
```

`MAX_DELIVER_MIN = 1` (in `ConsumerCreator`).

---

## All callsites and their behaviour

| # | Class | Field | Call | Behaviour |
|---|---|---|---|---|
| 1 | ConsumerCreator | startSequence | `normalizeULong(x)` | null/`<= 0` → 0; positive kept |
| 2 | ConsumerCreator | rateLimit | `normalizeULong(x)` | null/`<= 0` → 0; positive kept |
| 3 | ConsumerCreator | maxDeliver | `normalizeLong(x, MAX_DELIVER_MIN=1)` | null/`< 1` → -1; `>= 1` kept |
| 4 | ConsumerCreator | maxAckPending | `normalizeLong(x, STANDARD_MIN=0)` | null/`< 0` → -1; `>= 0` kept (0 is preserved and sent) |
| 5 | ConsumerCreator | maxPullWaiting | `normalizeLong(x, STANDARD_MIN=0)` | null/`< 0` → -1; `>= 0` kept |
| 6 | ConsumerCreator | maxBatch | `normalizeLong(x, STANDARD_MIN=0)` | null/`< 0` → -1; `>= 0` kept |
| 7 | ConsumerCreator | maxBytes | `normalizeLong(x, STANDARD_MIN=0)` | null/`< 0` → -1; `>= 0` kept |
| 8 | ConsumerLimitsCreator | maxAckPending | `normalizeLong(x, STANDARD_MIN=0)` | null/`< 0` → -1; `>= 0` kept |

All Long-serialized fields use `addField(Long)`, which suppresses any value `< 0`. So a normalized `-1` is correctly omitted from JSON; a normalized `0` **is** sent on the wire.

---

## Server semantics, field by field

Quoted line numbers refer to `/mnt/c/nats/nats-server/server/consumer.go` unless noted. The server uses `omitempty` JSON tags, so on receive `0` and "absent" deserialize identically — they cannot be distinguished after unmarshal.

### startSequence (`opt_start_seq`, uint64)

```go
OptStartSeq uint64 `json:"opt_start_seq,omitempty"`   // line 93
if config.OptStartSeq > 0 { ... }                     // line 899 etc.
```

- `0` = no explicit start sequence (use deliver-policy default).
- `> 0` = start at that sequence.
- Type is `uint64`, so negatives impossible on the wire.

**Client status:** ✓ correct. `normalizeULong` produces `0` for unset, and `addFieldWhenGtZero` suppresses `0`. Server default vs. explicit `0` are indistinguishable — and client never sends `0`.

### rateLimit (`rate_limit_bps`, uint64)

```go
RateLimit uint64 `json:"rate_limit_bps,omitempty"`    // line 102
if config.RateLimit != 0 { ... setRateLimit ... }     // line 1424
```

- `0` = no rate limit.
- `> 0` = rate limit (bits per second).

**Client status:** ✓ correct. Same pattern as `startSequence`.

### maxDeliver (`max_deliver`, int)

```go
MaxDeliver int `json:"max_deliver,omitempty"`         // line 97
// line 584-590:
if config.MaxDeliver == 0 || config.MaxDeliver < -1 {
    if pedantic && config.MaxDeliver < -1 { ...error... }
    config.MaxDeliver = -1                            // default
}
```

- `-1` = unlimited (explicit, valid).
- `0` = "use default" — the server **rewrites it to -1** immediately.
- `< -1` = treated as -1 (and pedantic mode rejects).
- `> 0` = explicit max delivery count.

**Client status:** ✓ correct. `min=1` means `0` and `-1` both normalize to internal `-1` (UNSET) and are not sent. The server applies its `-1` default in both cases.

**Caveat to flag:** there is no way through the current client API to send `-1` explicitly (to override an account default, for instance). All sub-1 inputs are squashed to "not sent". If the user ever wants to *explicitly* request unlimited (rather than rely on server defaulting), the API needs a special path. Today, that's probably a non-issue since the server's default is `-1` anyway.

### maxAckPending (`max_ack_pending`, int)

```go
MaxAckPending int `json:"max_ack_pending,omitempty"`  // line 105

// line 598-602:
if config.MaxAckPending < -1 {
    if pedantic { ...error... }
    config.MaxAckPending = -1
}

// line 656-660:
if config.MaxAckPending == 0 {
    ...
    config.MaxAckPending = streamCfg.ConsumerLimits.MaxAckPending  // inherit
}

// line 669-678:
if config.MaxAckPending == 0 && config.AckPolicy != AckNone {
    ackPending := JsDefaultMaxAckPending  // 1000
    // ... clamp by limits ...
    config.MaxAckPending = ackPending
}

// runtime enforcement (line 2338 etc.):
if o.maxp > 0 && len(o.pending) >= o.maxp { ... }
```

- `-1` = **unlimited** (real semantic value — runtime check `maxp > 0` never fires).
- `0` = "use default" — server replaces with stream-limit value or `JsDefaultMaxAckPending = 1000`.
- `> 0` = explicit cap.

**Client status:** ⚠ Two issues.

1. **`min=0` is wrong** for the "0 means default" reason. Sending `max_ack_pending:0` is the same as omitting the field — the server overwrites it with 1000 (or the stream's limit) either way. Recommend `min=1`.
2. **Cannot send `-1`.** With the current normalize logic any negative becomes UNSET (-1) which is suppressed by `addField(Long)`. So a user wanting to explicitly request "unlimited ack pending" cannot do so. The server will instead apply `JsDefaultMaxAckPending = 1000`. This is a behaviour change vs. an explicit `-1` send — flag for design decision.

### maxPullWaiting (`max_waiting`, int)

```go
MaxWaiting int `json:"max_waiting,omitempty"`         // line 104

// line 592-596:
if config.MaxWaiting < 0 {
    if pedantic { ...error... }
    config.MaxWaiting = 0
}

// line 642-643 (pull consumer only):
if config.DeliverSubject == _EMPTY_ && config.MaxWaiting == 0 {
    config.MaxWaiting = JSWaitQueueDefaultMax
}
```

- `0` = "use default" → `JSWaitQueueDefaultMax`.
- `> 0` = explicit cap.
- Negative = rejected (pedantic) or coerced to 0.

**Client status:** ⚠ `min=0` is wrong. `0` is just "use default"; client should not send it. Recommend `min=1`.

### maxBatch (`max_batch`, int — was `MaxRequestBatch` on server)

```go
MaxRequestBatch int `json:"max_batch,omitempty"`      // line 110

// line 604-608:
if config.MaxRequestBatch < 0 {
    if pedantic { ...error... }
    config.MaxRequestBatch = 0
}

// line 680-684 (pull consumer):
if config.DeliverSubject == _EMPTY_ && config.MaxRequestBatch == 0 && lim.MaxRequestBatch > 0 {
    config.MaxRequestBatch = lim.MaxRequestBatch  // inherit from JS limits
}

// runtime enforcement (line 4512):
if o.cfg.MaxRequestBatch > 0 && batchSize > o.cfg.MaxRequestBatch { ... 409 ... }
```

- `0` = "no per-consumer cap" — runtime check is gated on `> 0`. Server may inherit from JS account limits when `0`.
- `> 0` = explicit cap.

**Client status:** ⚠ `min=0` is wrong if you want the wire to reflect intent. Sending `max_batch:0` is indistinguishable from not sending. Recommend `min=1`.

There is one philosophical wrinkle: unlike `maxAckPending`, `0` here doesn't trigger a numeric default (no `JsDefault*` kicks in) — it just leaves the cap unset and possibly inherits from account limits. So sending `0` is genuinely a no-op vs. not sending. Either way, `min=1` is the cleaner choice.

### maxBytes (`max_bytes`, int — was `MaxRequestMaxBytes` on server)

```go
MaxRequestMaxBytes int `json:"max_bytes,omitempty"`   // line 112

// line 616-620:
if config.MaxRequestMaxBytes < 0 {
    if pedantic { ...error... }
    config.MaxRequestMaxBytes = 0
}

// runtime enforcement (line 4522):
if maxBytes > 0 && o.cfg.MaxRequestMaxBytes > 0 && maxBytes > o.cfg.MaxRequestMaxBytes { ... 409 ... }
```

- `0` = "no per-consumer cap". No inheritance from JS limits (unlike `maxBatch`).
- `> 0` = explicit cap.

**Client status:** ⚠ `min=0` is wrong, same as `maxBatch`. Recommend `min=1`.

### ConsumerLimitsCreator.maxAckPending (`stream.consumer_limits.max_ack_pending`, int)

```go
type StreamConsumerLimits struct {
    InactiveThreshold time.Duration `json:"inactive_threshold,omitempty"`
    MaxAckPending     int           `json:"max_ack_pending,omitempty"`   // stream.go:170
}

// line 657-660 (consumer config defaulting):
if pedantic && streamCfg.ConsumerLimits.MaxAckPending > 0 { ... }
config.MaxAckPending = streamCfg.ConsumerLimits.MaxAckPending

// line 840 (per-consumer validation):
if cfg.ConsumerLimits.MaxAckPending > 0 && config.MaxAckPending > cfg.ConsumerLimits.MaxAckPending { ... }
```

- `0` = no stream-level cap (every check is `> 0`).
- `> 0` = stream-imposed cap on consumers' `MaxAckPending`.

**Client status:** ⚠ `min=0` is wrong. `0` here is "no cap" which is identical to not sending. Recommend `min=1`.

---

## Summary of recommendations

| Field                    | Current `min`    | Recommended `min` | Why                                                   |
|--------------------------|------------------|-------------------|-------------------------------------------------------|
| startSequence            | (normalizeULong) | unchanged         | uint64; 0 is correct sentinel                         |
| rateLimit                | (normalizeULong) | unchanged         | uint64; 0 is correct sentinel                         |
| maxDeliver               | 1                | **1** (unchanged) | Server: 0 → -1 default; client correctly doesn't send |
| maxAckPending (Consumer) | 0                | **1**             | Server: 0 → default 1000 (or stream limit)            |
| maxPullWaiting           | 0                | **1**             | Server: 0 → JSWaitQueueDefaultMax                     |
| maxBatch                 | 0                | **1**             | Server: 0 → no cap (or inherit)                       |
| maxBytes                 | 0                | **1**             | Server: 0 → no cap                                    |
| maxAckPending (Limits)   | 0                | **1**             | Server: 0 → no stream cap                             |

**Net effect:** every remaining call ends up `normalizeLong(x, 1)`. The `STANDARD_MIN` constant becomes meaningless and should be deleted. `MAX_DELIVER_MIN` likewise — its value 1 is identical to the others'.

### On clarity (inline literal vs. constant)

Given that every call should be `min=1`, the constants `STANDARD_MIN` and `MAX_DELIVER_MIN` are now pure indirection — readers have to look them up to learn they're both `1`. Inline `1` is clearer:

```java
// Before:
this.maxAckPending = normalizeLong(maxAckPending, STANDARD_MIN);
this.maxDeliver    = normalizeLong(maxDeliver, MAX_DELIVER_MIN);

// After:
this.maxAckPending = normalizeLong(maxAckPending, 1);
this.maxDeliver    = normalizeLong(maxDeliver, 1);
```

If you want to keep the *concept* documented somewhere, do it on `normalizeLong`'s javadoc rather than in a poorly-named constant: "Pass `1` if zero would be treated as 'use default' by the server (the typical case)." Then the call sites speak for themselves.

---

## Two open design questions

1. **`-1` as "unlimited" for `maxDeliver` and `maxAckPending`.** Currently unreachable through the client API. For `maxDeliver` it doesn't matter (server's default is -1). For `maxAckPending` it might — sending `-1` is the only way to disable the ack-pending cap explicitly, since omitting falls back to `1000`. Decide whether to expose that.
2. **Keep the `STANDARD_MIN` constant?** Recommendation: delete it. Use literal `1`. Same for `MAX_DELIVER_MIN`.
