# Applying the AP reader hook to jnats V3

## Scope

This applies **one** internal hook to jnats V3 so the active/passive layer (`ApConnection`, single-passive) can repoint a live reader at a different connection during failover. It is the exact change already merged into jnats V2 — code in **PR #1592** (Reset Reader Connection), tests in **PR #1593** (Additional tests for reader repoint).

**Do NOT apply anything for the multi-passive facade (`ApMultiPassiveConnection`).** That was shelved — no `transferOutgoingTo`, no `transferRealMessagesTo`, no writer/queue message-transfer. This plan is reader-only.

Target package is **`io.synadia.client.impl`** in V3 (the rebrand — NOT V2's `io.nats.client.impl`). The class/field/method edits are otherwise identical to V2; only the package/path differs.
- `.../io/synadia/client/impl/NatsConnectionReader.java`
- `.../io/synadia/client/impl/NatsConnection.java`

Anchor on the class/method/field text below, not on line numbers or exact source root.

---

## ⚠️ Read this first: the read-listener code MUST match V2 verbatim

Copy the read-listener handling **exactly as it is in V2**: a single private method `refreshReadListener(NatsConnection)` that derives the listener from `connection.getOptions().getReadListener()`, called from the constructor and from `setConnection(...)`.

**DO NOT refactor it into a `setReadListener(...)` + `refreshReadListener(...)` pair.** If V3 has its own `setReadListener(ReadListener)` (e.g. a runtime read-listener path), leave it alone and separate — do **not** route this re-derivation through it, and do **not** split `refreshReadListener`'s logic across two methods. The reader's listener handling in V3 must end up byte-for-byte identical to V2. Any deviation here is what caused the previous rework.

---

## Edit 1 — `NatsConnectionReader`: repointable connection field + setter

### 1a. Make the connection field `volatile` (was `final`)

**Find:**
```java
    private final NatsConnection connection;
```

**Replace with:**
```java
    // volatile (was final) so a live reader can be repointed at a different connection - the reader
    // thread reads this on every delivered message and must see the new target. Repointing is done
    // through setConnection(...) rather than direct field access, so the field stays private; see
    // NatsConnection.setReaderConnection(...) for the entry point
    private volatile NatsConnection connection;
```

### 1b. Make `readListener` `volatile` and add `currentUserRl` (was `final`)

**Find:**
```java
    private final ReadListener readListener;
```

**Replace with:**
```java
    // The listener the reader thread invokes: either a no-op (no ReadListener configured) or a wrapper
    // that dispatches on the CURRENT connection (read from the volatile connection field at call time).
    // volatile because the reader thread reads it and the setConnection caller thread may replace it.
    private volatile ReadListener readListener;
    // The raw ReadListener (from Options) the current wrapper was built from - null when the no-op is in
    // place. Used by refreshReadListener to skip a needless rebuild when a repoint doesn't change it.
    // Only touched by the constructing / repointing thread, never the reader thread.
    private ReadListener currentUserRl;
```

### 1c. Constructor: build the listener via `refreshReadListener`

In the constructor, the inline block that builds `readListener` from `connection.getOptions().getReadListener()` (the `if (rl == null) { readListener = new ReadListener(){}; } else { ... }`) is **replaced by a single call**:

```java
        refreshReadListener(connection);
```

### 1d. Add `refreshReadListener(...)` — COPY VERBATIM (see the warning above)

```java
    // (Re)derive the readListener from the connection's Options, but replace it ONLY when the configured
    // ReadListener actually changed - so a repoint that doesn't change it costs nothing. The wrapper reads
    // the current connection from the volatile field at dispatch time (not a captured reference), so when
    // the source listener is unchanged the existing wrapper (or no-op) is already correct for the new
    // connection and is kept as-is.
    //   - first call (construction): readListener is still null, so we always build.
    //   - repoint, old null + new null: same (no-op already installed) - keep it.
    //   - repoint, old == new (same instance): the wrapper is fine - keep it.
    //   - otherwise: build the matching wrapper (or no-op) and remember the new source.
    private void refreshReadListener(NatsConnection connection) {
        final ReadListener newSuppliedUserRl = connection.getOptions().getReadListener();
        if (readListener != null && newSuppliedUserRl == currentUserRl) {
            return;
        }
        currentUserRl = newSuppliedUserRl;
        if (newSuppliedUserRl == null) {
            readListener = new ReadListener() {};
        }
        else {
            readListener = new ReadListener() {
                @Override
                public void protocol(String op, String text) {
                    NatsConnectionReader.this.connection.makeCallback(() -> newSuppliedUserRl.protocol(op, text));
                }

                @Override
                public void message(String op, Message message) {
                    NatsConnectionReader.this.connection.makeCallback(() -> newSuppliedUserRl.message(op, message));
                }
            };
        }
    }
```

Critical detail: the wrapper dispatches on `NatsConnectionReader.this.connection` (the live volatile field), **not** the `connection` parameter — that is what lets a kept wrapper still dispatch on the new connection after a repoint.

### 1e. Add the test-only accessor

```java
    // test access only - the listener the reader thread would invoke (no-op or wrapper)
    ReadListener readListenerForTesting() {
        return readListener;
    }
```

### 1f. Add `setConnection(...)`

```java
    // Repoint this reader at a different connection. The connection field is volatile, so the reader
    // thread sees the new target the next time it reads it, and the ReadListener is re-derived from the
    // new connection's Options only when it actually changed (see refreshReadListener).
    //
    // What follows the repoint: everything read from the connection field on use - message delivery,
    // statistics, protocol handling (OK/ERR/PONG/INFO), and the re-derived ReadListener. What does NOT
    // follow: the wire-parse buffers fixed at construction - the read buffer size and the max-control-line
    // buffers (msgLineChars / protocolBuffer). Those hold in-flight parse state and are used in tight loops
    // by the reader thread; swapping them from another thread mid-parse would corrupt the message being
    // decoded, so they are intentionally left as constructed. A repoint is therefore only fully safe across
    // connections whose buffer-size Options match; otherwise the reader keeps parsing per its original Options.
    //
    // INVARIANT: volatile gives visibility, NOT atomicity across a multi-step message parse. This method
    // does NOT require the reader to be stopped first - a caller may repoint a running reader - so if a
    // repoint lands mid-message it can straddle both connections (e.g. read stats registered on the
    // previous connection, delivery on the new one). A caller needing a clean handoff must ensure the
    // reader is between messages, or accept the split. When the two connections share the same underlying
    // socket, delivering to the new connection is correct and only a read-byte stat is split.
    protected void setConnection(NatsConnection connection) {
        this.connection = connection;
        refreshReadListener(connection);
    }
```

---

## Edit 2 — `NatsConnection`: protected accessor

Add near the other `protected` helpers (e.g. after the `*ExecutorIsClosed()` methods):

```java
    // Repoint this connection's reader at the given connection, without exposing the reader's private
    // connection field. Pass 'this' to (re)bind the reader to this connection. The repoint is a single
    // volatile write - visible to the reader thread but not atomic with respect to an in-flight message
    // parse; see NatsConnectionReader.setConnection for the invariant.
    protected void setReaderConnection(NatsConnection connection) {
        reader.setConnection(connection);
    }
```

---

## Edit 3 — test (port from V2 PR #1593)

Port `NatsConnectionReaderRepointTests` (from jnats V2 **PR #1593**; V3 package `io.synadia.client.impl`, under the V3 test root). It needs no server — it constructs unconnected `NatsConnection`s and exercises `setConnection` directly:
- `repoint_keepsListenerWhenUnchanged_rebuildsWhenChanged` — same listener instance → wrapper kept; different → rebuilt; null → no-op; null→null → no-op kept.
- `repoint_dispatchesToTheNewConnectionsListener` — after `setConnection`, callbacks fire on the new connection's listener, not the old.

(It uses the `readListenerForTesting()` accessor from 1e.)

---

## Verify

- `./gradlew compileJava` (or the V3 module's compile task) is clean.
- The new tests pass.
- No multi-passive code was added: `grep -rn "transferOutgoingTo\|transferRealMessagesTo" src` returns nothing.
- The reader's listener handling is identical to V2 (single `refreshReadListener(NatsConnection)`, no `setReadListener` split).
- No public API change — every addition is `protected`/package on non-public `impl` classes.

---

## Why (AP consumer-side, for reference — nothing to change in jnats for this)

On failover the active steals the passive's live socket. A socket-blocked `read()` can't be stopped without wrecking the socket, so `ApConnection` *adopts* the passive's reader — repoints it to deliver into the active connection via `setReaderConnection(this)`, and hands its own dead reader back to the passive. It restarts only the writer on the new port. (Also needs the `newPassive()` reorder — create the new passive before background-closing the old — so the pool the adopted reader runs on isn't shut to a 0 refcount under it. That's AP-side, not jnats.)
