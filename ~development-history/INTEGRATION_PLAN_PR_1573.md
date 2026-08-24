# Integration Plan — PR #1573 (Handle consume initial subscription failure)

Goal: port upstream `nats-io/nats.java` PR #1573 ("Handle consume initial subscription failure", MERGED 2026-05-11) into `nats.java.v3`.

**Status: Integrated — no action required.** The exact guard from the merged PR is already present in v3.

## What the PR does

PR #1573 changes a single production file: `src/main/java/io/nats/client/impl/NatsMessageConsumer.java`. The whole diff is three lines added to the `catch` block of `doSub(boolean first)`:

```diff
         catch (JetStreamApiException | IOException e) {
+            if (first) {
+                throw e;
+            }
             resetOnException();
         }
```

### Failure path it handles

`doSub(boolean first)` is called from two places:
1. The constructor — `doSub(true)` — this is the **initial** subscription created when a `consume()` is started.
2. `pullTerminatedByError()` — `doSub(false)` — a re-subscription after an in-flight error.

`doSub` performs `subscriptionMaker.subscribe(...)` + `initSub` + `rePull`, all of which can throw `JetStreamApiException` / `IOException` (e.g. the JetStream API rejects the consumer create/bind, or the connection is down).

Before the fix, the `catch` always called `resetOnException()` (which resets pending counters and the heartbeat timer) and swallowed the exception. That is correct for the *re-subscribe* case (case 2): an endless `consume()` should self-heal and keep retrying rather than die. But for the *initial* subscription (case 1, `first == true`) swallowing meant the constructor returned a half-built consumer that had silently failed to subscribe — the caller of `consume()` got back an apparently-live `MessageConsumer` that would never deliver messages, and never learned the subscribe had failed.

The fix: when `first` is true, **rethrow** the exception so it propagates out of the constructor to the `consume()` caller (the method already declares `throws IOException, JetStreamApiException`). For the re-subscribe case (`first == false`) behavior is unchanged — still `resetOnException()` and keep going.

## V3 status — already integrated

V3 equivalent file: `jetstream/src/main/java/io/synadia/client/impl/NatsMessageConsumer.java`.

The guard is present at **lines 150–155**:

```java
catch (JetStreamApiException | IOException e) {
    if (first) {
        throw e;
    }
    resetOnException();
}
```

Supporting evidence that the surrounding flow matches the upstream pre/post-fix structure exactly:

| Concern | v3 location | Matches upstream |
|---|---|---|
| `doSub(boolean first)` signature `throws JetStreamApiException, IOException` | line 133 | yes |
| Initial subscribe via `doSub(true)` in constructor | line 47 | yes |
| Re-subscribe via `doSub(false)` in `pullTerminatedByError()` | line 124 | yes |
| `if (first) throw e;` guard before `resetOnException()` | lines 151–153 | yes (the PR's entire change) |
| `resetOnException()` body (reset pending + heartbeat timer) | lines 158–162 | yes |

V3 differs from v2 only in cosmetic / structural ways that do not touch this fix:
- Package `io.synadia.client.impl` vs `io.nats.client.impl`.
- Constructor / field type `NatsDispatcher` (v3) vs `Dispatcher` (v2). The `doSub` body is byte-for-byte identical apart from this.
- No license header in the v3 file.

No v3 convention items apply here — the change introduces no nullable fields and no `equals()`.

## File inventory

None. No files to create or modify.

## Step-by-step

No steps. The fix is already in the tree.

If a future regression check is wanted, the guard can be exercised by a test that starts a `consume()` against a consumer-maker whose first `subscribe()` throws, and asserting the exception propagates out of the start call (rather than returning a dead consumer). This is optional and not part of porting PR #1573 — upstream #1573 shipped without a new test.

## Open decisions

None.

## Risks

None — nothing to change. (Verification only: confirm no later v3 refactor removed the `if (first) throw e;` guard before relying on this report.)

## Size estimate

Zero LOC. Already integrated.
