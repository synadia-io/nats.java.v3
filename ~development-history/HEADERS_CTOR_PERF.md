# `Headers(@Nullable Headers, boolean, String[])` — speed review

File: `src/main/java/io/nats/client/impl/Headers.java`, lines 75–101.
Target: Java 8 (per `build.gradle` `sourceCompatibility = VERSION_1_8`).

## Why this matters

This constructor is on the **publish hot path**, not just a one-off init:

| Caller | Pattern |
|---|---|
| `NatsMessage.java:126` | `headers.isReadOnly() ? headers : new Headers(headers, true, null)` — defensive read-only snapshot for every outgoing message whose caller supplied a mutable `Headers`. |
| `NatsPublishableMessage.java:34` | Same pattern, also per-message. |
| `NatsJetStream.java:203` | `new Headers(headers)` to merge JS publish headers — per-publish. |
| `MessageInfo.java:116` | `new Headers(msgHeaders, true, MESSAGE_INFO_HEADERS)` — uses `keysNotToCopy`. |
| `NatsMessage.java:112` | Static singleton — one-time. |

So small per-call wins multiply over message throughput.

## Current code

```java
public Headers(@Nullable Headers headers, boolean readOnly, String @Nullable [] keysNotToCopy) {
    Map<String, List<String>> tempValuesMap = new HashMap<>();   // (A)
    Map<String, Integer> tempLengthMap = new HashMap<>();        // (B)
    if (headers != null) {
        tempValuesMap.putAll(headers.valuesMap);                  // (C)
        tempLengthMap.putAll(headers.lengthMap);                  // (D)
        dataLength = headers.dataLength;
        if (keysNotToCopy != null) {
            for (String key : keysNotToCopy) {
                if (key != null) {
                    if (tempValuesMap.remove(key) != null) {
                        dataLength -= tempLengthMap.remove(key);  // (E) auto-unbox
                    }
                }
            }
        }
    }
    this.readOnly = readOnly;
    if (readOnly) {
        valuesMap = Collections.unmodifiableMap(tempValuesMap);   // (F)
        lengthMap = Collections.unmodifiableMap(tempLengthMap);   // (G)
    }
    else {
        valuesMap = tempValuesMap;
        lengthMap = tempLengthMap;
    }
}
```

## Where the cycles go

1. **(A)+(B)**: Two `HashMap` allocations, always. Even when `headers == null` and the result will stay empty. JDK 8 `HashMap()` is lazy — it does NOT allocate the bucket table — so it's just the wrapper object + a few fields, but it's still two allocations per construction.

2. **(C)+(D)**: `putAll` calls `putMapEntries(m, /*evict=*/true)`. Because the table is still `null` on first call, JDK does pre-size the threshold from the source size — so the rehash-during-fill cost is already amortized. The remaining cost is one `putVal` per entry, including the `evict ? afterNodeInsertion(...) : nothing` branch.

3. **(E)**: `tempLengthMap.remove(key)` returns the boxed `Integer`; subtracting from `int` unboxes. One unbox per removed key. Negligible because `keysNotToCopy` is small in every real caller (1–3 keys, e.g. `MESSAGE_INFO_HEADERS`).

4. **(F)+(G)**: Two `Collections$UnmodifiableMap` wrapper allocations on every read-only copy. **These wrappers protect nothing observable to callers** — `valuesMap` / `lengthMap` are `private final`, and every public mutator already short-circuits with an explicit `if (readOnly) throw new UnsupportedOperationException()`. They are belt-and-suspenders against future internal regressions. Every internal access (`valuesMap.get`, `valuesMap.entrySet()`, etc.) takes one extra method indirection through the wrapper.

## Realistic wins, ranked

### Win 1 — Replace `new HashMap<>() + putAll` with the copy-constructor.
```java
Map<String, List<String>> v = new HashMap<>(headers.valuesMap);
Map<String, Integer>      l = new HashMap<>(headers.lengthMap);
```
Same pre-sizing as today (both use `putMapEntries`), but `evict=false`, so the per-entry `afterNodeInsertion` branch goes away and the JIT has a cleaner shape to inline. Also one less constructor call. **Small but free** — code is shorter too.

### Win 2 — Skip map allocations entirely when there is nothing to copy.
For `headers == null`, the only sensible state is empty. Hoist the `readOnly` field write, then collapse both maps into a ternary — `Collections.emptyMap()` for the read-only case (JDK singleton, zero allocation), a fresh `HashMap` for the mutable case so later `add`/`put`/`remove` have something to write to:
```java
this.readOnly = readOnly;
if (headers == null) {
    this.valuesMap = readOnly ? Collections.emptyMap() : new HashMap<>();
    this.lengthMap = readOnly ? Collections.emptyMap() : new HashMap<>();
    return;
}
```
What this actually saves, by path:

| Call | Before | After | Saved |
|---|---|---|---|
| `new Headers(null, true, null)` (read-only) — `NatsMessage:112` singleton, hit once at class init | 2 `HashMap` + 2 `UnmodifiableMap` wrappers = 4 objects | 0 new objects (shares JDK `emptyMap` singleton) | 4 |
| `new Headers()` → `(null, false, null)` (mutable) — common no-arg use | 2 `HashMap` | 2 `HashMap` | 0 |

So this win is real but narrow: it pays off only on the read-only-empty path. The mutable-empty path can't be made cheaper while `valuesMap` / `lengthMap` are `final` — you'd have to drop `final` and lazily fork from `emptyMap` to a `HashMap` on first write, which is a bigger refactor and out of scope here.

### Win 3 — Drop the `Collections.unmodifiableMap(...)` wrappers (behavioural).
The wrappers are not visible to callers (the maps are `private final` and never returned by reference). The `readOnly` flag is already enforced at every public mutator and at the start of `_add` / `_put` via the public callers. Removing the wrappers:

- saves **2 wrapper object allocations** per read-only copy (i.e. once per outgoing message whose caller passed mutable headers, since `NatsMessage:126` and `NatsPublishableMessage:34` go through this path);
- removes one method-call indirection on every internal `get` / `entrySet` / `keySet` iteration of a read-only `Headers`;
- costs you one safety net — if anyone later adds a private write path that bypasses the public `readOnly` guards, this won't catch it.

It's a defensible trade because the existing `readOnly` checks are exhaustive (every public mutator: `add`, `put(Map)`, `put`, `remove`, `clear`).

> **Important caveat.** Win 3 only saves the *wrapper* allocations. It does **not** remove the need to copy the maps in the first place. The copy is required for aliasing safety — the caller still holds a reference to the source `Headers` and could mutate it after `publish()` returns. The copy and the wrapper-drop are independent wins; see the next section for the structural alternative that actually avoids the copy.

### Win 4 — Hoist `keysNotToCopy` null-check (micro).
Today: `if (key != null)` inside the loop. `HashMap.remove(null)` is itself safe and returns `null`, so the outer `if (tempValuesMap.remove(key) != null)` would already false-out. The inner null-check guards `tempLengthMap.remove(key)` from being called. Tiny; only matters if the array regularly has nulls (it doesn't).

### Wins NOT worth taking
- **Manual pre-sizing with `(int)(n / 0.75f) + 1`**: `new HashMap<>(map)` already does this. No additional win.
- **Switching `lengthMap` to a primitive-int map (e.g. Eclipse Collections `ObjectIntHashMap`)**: meaningful save (kills boxing on every `_add` / `_put` / removal), but it's a class-wide refactor and out of scope for this constructor.
- **Single-pass copy that skips `keysNotToCopy` during the entrySet iteration**: would replace the bulk `putAll` fast path with a hand-rolled loop. Almost certainly a wash unless `keysNotToCopy` is large; today's callers pass arrays of size 1.
- **`Headers` sharing `List<String>` instances with the source after `putAll`**: this is a real aliasing concern (`_add` mutates the list in-place via `addAll`), but it's a correctness question, not a speed question.

## Proposed rewrite

```java
public Headers(@Nullable Headers headers, boolean readOnly, String @Nullable [] keysNotToCopy) {
    this.readOnly = readOnly;

    if (headers == null) {
        this.valuesMap = readOnly ? Collections.emptyMap() : new HashMap<>();
        this.lengthMap = readOnly ? Collections.emptyMap() : new HashMap<>();
        return;
    }

    // Copy-construct: pre-sized + no per-entry afterNodeInsertion branch.
    // Assign straight to the final fields — no local v/l/dl needed.
    // Wrappers dropped — readOnly is enforced at every public mutator.
    this.valuesMap = new HashMap<>(headers.valuesMap);
    this.lengthMap = new HashMap<>(headers.lengthMap);
    this.dataLength = headers.dataLength;

    if (keysNotToCopy != null) {
        // keysNotToCopy elements are validated non-null by the caller (JSpecify-annotated).
        for (String key : keysNotToCopy) {
            if (this.valuesMap.remove(key) != null) {
                this.dataLength -= this.lengthMap.remove(key);   // auto-unbox; key existed in valuesMap so it exists in lengthMap
            }
        }
    }
}
```

Notes on this version:
- Drops the early double-allocation when `headers == null`.
- Uses the `HashMap` copy-constructor.
- Assigns directly to the `final` fields — no `v` / `l` / `dl` locals.
- Drops the `if (key != null)` guard inside the `keysNotToCopy` loop — the caller's annotation says elements are non-null.
- **Removes the `unmodifiableMap` wrappers.** If you want to keep them as a defensive shim, wrap the maps in the read-only branch as today — the rest of the wins still apply.

## Suggested microbenchmark

If you want a number before changing this, a JMH harness like the following will exercise the two hot patterns (defensive read-only snapshot, and `MessageInfo` filter copy):

```java
@State(Scope.Thread)
public class HeadersCopyBench {
    Headers src5, src20;
    static final String[] DROP = {"Status", "Description"};

    @Setup public void setup() {
        src5  = new Headers();  for (int i = 0; i < 5;  i++) src5.put("k" + i, "v" + i);
        src20 = new Headers();  for (int i = 0; i < 20; i++) src20.put("k" + i, "v" + i);
    }

    @Benchmark public Headers readonlySnapshot5()  { return new Headers(src5,  true, null); }
    @Benchmark public Headers readonlySnapshot20() { return new Headers(src20, true, null); }
    @Benchmark public Headers filteredCopy20()     { return new Headers(src20, true, DROP); }
}
```

Run with `-prof gc` to see allocation reduction directly — that's where the wrapper-drop win shows up most clearly.

## Beyond per-construction wins: making the copy unnecessary

The biggest opportunity on the publish hot path isn't making the copy faster — it's not making it at all. The defensive copy at `NatsMessage.java:126` exists solely to isolate the library from caller mutations after publish:

```java
headers = headers.isReadOnly() ? headers : new Headers(headers, true, null);
```

The short-circuit for read-only inputs already exists and costs nothing. The optimization is to get more publishes onto that short-circuit.

### Option A — implicit flag-flip on publish

Idea: when publish receives a mutable `Headers`, flip its `readOnly` flag in place instead of copying. Subsequent publishes of the same `Headers` then take the existing fast path.

**Wins**
- O(N) → O(1) on the first publish (no map copy, no new `Headers` instance).
- All subsequent publishes are free (already true today once read-only).

**Costs**
- **Breaks the publish-then-mutate-then-publish pattern.** Today this works because publish snapshots; after flag-flip the next `put`/`add`/`remove` on the user's `Headers` throws `UnsupportedOperationException`.
- **Mutates an argument.** Action-at-a-distance is something Java APIs usually avoid even when documented.
- **`readOnly` is `final` today.** A flip needs it non-`final` and probably `volatile` for cross-thread visibility, which also loses some safe-publication guarantees the JVM gives you with `final`.
- **No effect on the JetStream publish path.** `NatsJetStream.mergePublishOptions` at `NatsJetStream.java:197-212` copies precisely so it can merge internal headers (`Nats-Expected-Stream`, `Nats-Msg-Id`, etc.) into the result without polluting the caller's object. Flipping the user's flag doesn't help — JS still needs a writable copy to merge into.

### Option B — explicit `Headers.freeze()` (recommended)

Add a `freeze()` (or `asReadOnly()`) method that flips the flag and returns `this`. Power users opt in:

```java
Headers h = new Headers().put("trace", traceId).freeze();
for (...) conn.publish(subj, payload, h);   // zero-copy from publish #1
```

**Wins**
- Same O(1) publish path when callers opt in.
- Backwards-compatible: existing code keeps today's defensive-copy behaviour.
- Discoverable and explicit; no surprising argument mutation.
- The library can use `freeze()` internally too — e.g. `NatsJetStream.mergePublishOptions` can freeze its merged result before returning, so subsequent serialization sees it as already read-only.

**Cost**
- Users have to know about it to benefit.

### Option C — transparent copy-on-write

Keep `readOnly` as today, add a `shared` flag. Publish sets `shared = true` without copying. The next mutation lazily clones the maps before proceeding.

**Wins**
- Build-once-publish-many gets the fast path with no API change.
- No new surface area; no surprises for users that don't reuse the object.

**Costs**
- Extra branch on every mutator.
- More code to maintain (the COW fork logic).

### Option D — opt-in publish overload

`publishOwning(subject, payload, headers)` — semantically "I hand this over, don't expect to use it again." Internally flips and uses without copying. Keeps existing `publish(...)` unchanged.

### Recommendation

**Option B** — explicit `Headers.freeze()`. It captures most of the available win (build-once-publish-many becomes zero-copy), is backwards-compatible, discoverable, and uses the existing `isReadOnly()` short-circuit at `NatsMessage.java:126` without changes to that path. The JS merge path can also call `freeze()` on its merged Headers, eliminating wasted re-copies on retries / resends.

The per-constructor wins (Win 1 + Win 2 + Win 3) remain worth taking in parallel — they reduce the cost of the copies that still happen (the JS merge path, non-frozen reuse, the `MessageInfo` filtered copy).

---

## Bottom line

Two independent layers of improvement:

**Layer 1 — Inside the constructor (always applicable):**
1. Use `new HashMap<>(source)` instead of `new + putAll` — pure cleanup, free win.
2. Early-return on `headers == null` — saves 4 allocations on the readOnly singleton init.
3. Optionally drop the two `unmodifiableMap` wrappers (≈2 fewer allocations per read-only copy + small access-time win) — small behavioural risk.
4. Everything else (loop micro-optimizations, primitive-int map, single-pass filter copy) is below the noise floor for typical header sets.

**Layer 2 — Avoiding the copy altogether (structural):**
5. Add `Headers.freeze()` and use it both in user code (build-once-publish-many becomes zero-copy from publish #1) and inside `NatsJetStream.mergePublishOptions` (so JS-merged headers are read-only and don't get re-copied downstream). This is where the biggest real-world win lives.
