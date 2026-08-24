# Audit — Primitive Numeric Setters Not Using `normalizeLong` / `normalizeULong`

**Scope:** public `int` / `long` setters in `*Creator` classes (boolean and Duration setters excluded). Only main sources. Already-normalized setters in `ConsumerCreator` / `ConsumerLimitsCreator` family are out of scope (those were the subject of the previous audit).

For each setter: what it does on the client side, how it serializes, and how the server interprets the wire value.

---

## TL;DR

There are **four distinct patterns** in use across these setters, plus one true outlier:

1. **Strict-throw with `-1` allowed as "explicit unlimited"** — `maxMessages`, `maxMessagesPerSubject`, `maxBytes`, `maxMessageSize` (StreamCreator). Throws on `0` or `< -1`. Passes everything else through unchanged. KV/OS wrap these.
2. **Strict-throw range** — `replicas` (StreamCreator). Must be 1–5; throws otherwise. KV/OS wrap this.
3. **Silent clamp to `-1`** — `maxConsumers` (StreamCreator). Anything `< 1` becomes `-1`. **Inconsistent with peer "max" fields** which throw.
4. **Silent clamp to `1`** — `firstSequence` (StreamCreator). Anything `≤ 1` becomes `1`. Fine.
5. **Inherit-or-validate** — `numReplicas` (AbstractEphemeralConsumerCreator). `≤ 0` → `0` ("inherit from stream"); 1–5 validated; throws otherwise.
6. **Raw assign** — `startSequence` (StreamSourceCreator). No validation. Saved by the `addFieldWhenGtZero` serializer which never sends a non-positive value.

Two real consistency questions surface at the end. No silent correctness bugs found.

---

## Per-setter detail

### `StreamCreator.maxConsumers(long)` — silent clamp

```java
public StreamCreator maxConsumers(long maxConsumers) {
    this.maxConsumers = maxConsumers < 1 ? -1 : maxConsumers;
    return this;
}
```
- **Client effect**: `< 1` → `-1` (silent); ≥ 1 → kept. **Cannot send a value < -1 or 0 — they're absorbed.**
- **Serialization**: `addField(sb, MAX_CONSUMERS, maxConsumers)` (line 219). `addField(Long)` suppresses values `< 0`. So `-1` (the silent-clamp result) is actually NOT sent. Only explicit positive values reach the wire.

> **Verify:** the field is initialized in the constructor — does it default to `-1`? If so the initial state is already "not sent" and the clamp is just normalising bad input.

- **Server** (`stream.go:1696-1700`): `MaxConsumers int \`json:"max_consumers"\`` (no omitempty). Server treats `0` or `< -1` as → `-1` (unlimited). Positive = explicit cap.
- **Inconsistency with peers**: `maxMessages`, `maxBytes`, `maxMessagesPerSubject`, `maxMessageSize` all *throw* on `0`/`< -1` instead of clamping. `maxConsumers` is the odd one — silent clamp.
- **Recommendation**: pick one. Either switch `maxConsumers` to `validateMaxConsumers` (which already exists in JsValidator at line 107) for consistency with peers, or change the peers to silent-clamp (less defensive but less surprising for users).

---

### `StreamCreator.maxMessages(long)` — validator throws

```java
public StreamCreator maxMessages(long maxMessages) {
    this.maxMessages = validateMaxMessages(maxMessages);
    return this;
}
```
- `validateMaxMessages` → `validateGtZeroOrMinus1` (Validator.java:224): throws `IllegalArgumentException` if value is `0` or `< -1`. `-1` allowed (explicit unlimited).
- **Serialization**: `addField(MAX_MSGS, maxMessages)` — suppresses < 0. So if user passes `-1`, the validator allows it, but `addField` suppresses it. End result: -1 is NOT actually transmitted.

> **Open question:** is that intentional? The user can `.maxMessages(-1)` without a throw, but the wire payload omits the field. The server then defaults to `-1` itself. Functionally identical but conceptually confusing.

- **Server** (`stream.go`): `0` → `-1` (default unlimited); positive = cap; `< -1` rejected in pedantic.

---

### `StreamCreator.maxMessagesPerSubject(long)` — validator throws

Same pattern as `maxMessages`. Calls `validateMaxMessagesPerSubject` → `validateGtZeroOrMinus1`.

---

### `StreamCreator.maxBytes(long)` — validator throws

Same pattern. Calls `validateMaxBytes` → `validateGtZeroOrMinus1`.

---

### `StreamCreator.maxMessageSize(int)` — validator throws

Same pattern. Calls `validateMaxMessageSize` → `validateGtZeroOrMinus1`.

---

### `StreamCreator.replicas(int)` — strict range

```java
public StreamCreator replicas(int replicas) {
    this.replicas = validateNumberOfReplicas(replicas);
    return this;
}
```
- `validateNumberOfReplicas` (JsValidator.java:142): throws unless `1 ≤ x ≤ 5`.
- **Initial value**: `1` (constructor: `replicas = 1`).
- **Serialization**: `addField(NUM_REPLICAS, replicas)` — emits values `≥ 0`. Default `1` is sent.
- **Server** (`stream.go:1663-1669`): `0` → `1`; negative rejected. So sending `1` explicitly = sending nothing. No bug; just redundant.

---

### `StreamCreator.firstSequence(long)` — silent clamp to 1

```java
public StreamCreator firstSequence(long firstSeq) {
    this.firstSequence = firstSeq > 1 ? firstSeq : 1;
    return this;
}
```
- `≤ 1` → `1`; otherwise kept.
- **Serialization**: `addFieldWhenGreaterThan(FIRST_SEQ, firstSequence, 1)` — only emit if `> 1`. So the `1` default is never sent.
- **Server**: `FirstSeq uint64 \`json:"first_seq,omitempty"\``. uint64 — negatives impossible on wire. `0`/absent = default (start at 1).
- **Behaviour is correct.** Could be rewritten with `normalizeULong`-style logic (`< 1` → 0, then suppress 0 in JSON) but the current form is readable and works.

---

### `StreamSourceCreator.startSequence(long)` — raw assign

```java
public T startSequence(long startSequence) {
    this.startSequence = startSequence;
    return (T) this;
}
```
- No validation. The raw value is stored.
- **Serialization**: `addFieldWhenGtZero(OPT_START_SEQ, startSequence)` — only positive values sent. Negatives and zero are suppressed at write time.
- **Server** (`consumer.go:93`): `OptStartSeq uint64 \`json:"opt_start_seq,omitempty"\``. Negative wire values are impossible (uint64); the client's `addFieldWhenGtZero` gate prevents them.
- **No correctness bug** — the serialization gate is doing the work. But **inconsistent with `ConsumerCreator.startSequence`**, which uses `normalizeULong` to normalise the internal state too. Today `getStartSequence()` will hand back whatever the user passed (including negatives); in ConsumerCreator it would be normalised to `0`.
- **Recommendation**: switch to `normalizeULong(startSequence)` for symmetry. Low priority — there's no real bug.

---

### `AbstractEphemeralConsumerCreator.numReplicas(int)` — inherit-or-validate

```java
// public setter:
public T numReplicas(int numReplicas) {
    _numReplicas(numReplicas);
    return (T)this;
}

// protected helper in ConsumerCreator:
protected void _numReplicas(int numReplicas) {
    this.numReplicas = numReplicas <= 0 ? 0 : validateNumberOfReplicas(numReplicas);
}
```
- `≤ 0` → `0` silently ("inherit from stream"); `1`–`5` kept; `> 5` throws via `validateNumberOfReplicas`.
- **Serialization**: `addField(NUM_REPLICAS, numReplicas)` (line 202). Emits any value `≥ 0`. So `0` IS sent.
- **Server** (`consumer.go:123, 403-410, 718-723`): `Replicas int \`json:"num_replicas"\`` (no omitempty). `0` means "inherit from stream Replicas". Negative rejected.
- **Sending `0` is functionally identical to not sending.** Slightly redundant but harmless.
- **Recommendation**: if you want to apply the "don't send default-meaning values" policy uniformly, switch `_numReplicas` to use `normalizeLong(x, 1)` and let `addField` suppress the resulting `-1`. Today's behaviour is fine — just a bit chattier on the wire.

---

### KV and OS wrappers

These all delegate to a StreamCreator setter and inherit its behaviour. Listed here for completeness; no independent analysis needed.

| Setter | Delegates to |
|---|---|
| `KeyValueConfigurationCreator.maxBucketSize(long)` | `streamCreator.maxBytes(validateMaxBucketBytes(x))` — validateMaxBucketBytes is `validateGtZeroOrMinus1` aliased |
| `KeyValueConfigurationCreator.replicas(int)` | `streamCreator.replicas(x)` — same strict 1–5 validator |
| `KeyValueConfigurationCreator.maxHistoryPerKey(int)` | `streamCreator.maxMessagesPerSubject(validateMaxHistory(x))` |
| `KeyValueConfigurationCreator.maximumValueSize(int)` | `streamCreator.maxMessageSize(validateMaxValueSize(x))` |
| `ObjectStoreConfigurationCreator.maxBucketSize(long)` | `streamCreator.maxBytes(...)` |
| `ObjectStoreConfigurationCreator.replicas(int)` | `streamCreator.replicas(x)` |

---

## Summary table

| Setter | Pattern | 0 input → | Negative input → | Server's view of 0 on wire | Recommendation |
|---|---|---|---|---|---|
| `StreamCreator.maxConsumers(long)` | silent clamp | -1 (internal); not sent | -1 (internal); not sent | -1 (unlimited) | **Switch to `validateMaxConsumers` for peer consistency** |
| `StreamCreator.maxMessages(long)` | throws | throws | throws (unless -1) | -1 (unlimited) | OK — minor: `-1` is allowed by validator but suppressed by serializer; document or tighten |
| `StreamCreator.maxMessagesPerSubject(long)` | throws | throws | throws (unless -1) | -1 (unlimited) | Same as above |
| `StreamCreator.maxBytes(long)` | throws | throws | throws (unless -1) | -1 (unlimited) | Same |
| `StreamCreator.maxMessageSize(int)` | throws | throws | throws (unless -1) | -1 (unlimited) | Same |
| `StreamCreator.replicas(int)` | throws | throws | throws | -> 1 (default) | OK |
| `StreamCreator.firstSequence(long)` | silent clamp to 1 | 1; not sent | 1; not sent | default (1) | OK |
| `StreamSourceCreator.startSequence(long)` | raw assign | kept as 0; not sent | kept; not sent | n/a (uint64) | **Recommend `normalizeULong` for parity with ConsumerCreator** |
| `AbstractEphemeralConsumerCreator.numReplicas(int)` | clamp ≤0 → 0, else validate 1–5 | 0; sent on wire | 0; sent | inherit from stream | Optional: switch to `normalizeLong(x, 1)` to avoid sending `0` |

---

## Two policy questions for you

Both echo the discussion from the previous audit about `maxAckPending`:

1. **Should the StreamCreator `max*` fields still accept `-1`?** They currently do — `validateGtZeroOrMinus1` lets `.maxBytes(-1)` through, but `addField(Long)` suppresses it from the wire anyway. So `-1` is a no-op rather than an error. For consistency with `maxAckPending` (where you said "don't expose -1 to developers"), you'd want these to *throw* on `-1` too. Or accept that "explicit unlimited" is a public API contract for these fields. Pick one.
2. **Should `numReplicas` stop sending `0`?** Right now `.numReplicas(0)` (or default) puts `"num_replicas":0` on the wire. The server treats that identically to omission ("inherit from stream"). Switching `_numReplicas` to `normalizeLong(x, 1)` would cut the redundant payload. Behaviourally a no-op for the server.

No silent bugs to fix. The action items are all stylistic / consistency cleanups, gated on your answers to the two questions above.
