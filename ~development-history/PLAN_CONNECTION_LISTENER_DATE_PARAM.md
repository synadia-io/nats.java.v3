# Plan: align `ConnectionListener.connectionEvent` 3rd param to `date`

## Context
The `ConnectionListener.connectionEvent(...)` 3rd parameter was renamed `Long time` → `Long date` (epoch-millis, matching `java.util.Date(long date)`; the `Millis` suffix is for durations, not timestamps). This is a **pure name change** — renaming an interface method parameter does **not** break implementors (an override may name its parameter anything), so this is a consistency cleanup, **not** a compile fix. Nothing is currently broken.

Interface (already done): `core/src/main/java/io/synadia/client/ConnectionListener.java`
```java
void connectionEvent(NatsConnection conn, ConnectionEvents type, Long date, String uriDetails);
```

## Scope (verified)
- All implementors are in **test code** — no `ConnectionListener` implementations exist in main.
- No lambda implementors (the `NatsConnection` `-> listener.connectionEvent(...)` is the *invoker*, not an implementor).
- All three implementor bodies **ignore** the param (they throw / check `type` / use `event`), so each is a **signature-only** rename — no method-body edits.

## Changes — implementors (rename 3rd param `time` → `date`)

| # | File | Line | Method | Change |
|---|---|---|---|---|
| 1 | `core/src/test/java/io/synadia/client/BadHandler.java` | 19 | `connectionEvent` override | `Long time` → `Long date` |
| 2 | `core/src/test/java/io/synadia/client/impl/TLSConnectTests.java` (inner class `SslTestConnectionListener`) | 589 | `connectionEvent` override | `Long time` → `Long date` |
| 3 | `core/src/test/java/io/synadia/client/utils/Listener.java` | 254 | `connectionEvent` override | `Long time` → `Long date` |

## Change — invoker (optional, for consistency)

`core/src/main/java/io/synadia/client/impl/NatsConnection.java` — `processConnectionEvent(...)`:
```java
long time = System.currentTimeMillis();
for (ConnectionListener listener : connectionListeners) {
    makeCallback(() -> listener.connectionEvent(this, type, time, uriDetails));
}
```
Rename the local `time` → `date` (2 references — the declaration and the lambda use). It is the epoch-millis value passed as `date`, so renaming keeps the invoker consistent with the contract.

## Not in scope (flagged, pre-existing)
- `Listener.java:254` also names the **2nd** param `event` instead of `type` (its own pre-existing choice). Leave unless explicitly aligning naming too.

## Risk / verification
- No behavior change, no `@Override` breakage, no `Duration`/nanos involvement — pure naming.
- Verify with `./gradlew compileTestJava` (param renames can't change behavior; this just confirms nothing was fat-fingered).

## Status
- [ ] 1. BadHandler
- [ ] 2. TLSConnectTests / SslTestConnectionListener
- [ ] 3. Listener
- [ ] 4. (optional) NatsConnection.processConnectionEvent local
- [ ] compileTestJava green
