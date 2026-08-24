# `NatsConnection` naming — the file that keeps `Impl`

Companion to `UNDERSCORE_INTERNAL_NAMING_AUDIT.md`. Settled 2026-08-15.

The project rule is **`_` means internal, and a delegate is an internal thing**. `NatsConnection`'s connection lifecycle is the deliberate exception: those methods keep their historical `Impl` names.

**The reason is not that they fail the rule — it is that the names carry history.** They have been read, debugged and reasoned about under these names, and that is worth more than making one file match a convention settled later. Renaming them was tried during this exercise and reverted.

## Current state

| # | Method | Refs | Marker | Note |
|---|---|---|---|---|
| 1 | `connectImpl(boolean)` | 2 | `Impl` | `connect(boolean)` is a pure `tryingToConnect` guard around a 69-line body |
| 2 | `forceReconnectImpl(ForceReconnectOptions)` | 2 | `Impl` | behind the two public `forceReconnect` overloads |
| 3 | `reconnectImpl()` | 5 | `Impl` | `reconnect()` guards with `tryingToConnect`; `:403` deliberately bypasses that guard, which is why the split exists — the comment there records it |
| 4 | `reconnectImplConnect()` | 2 | `Impl` (infix) | a sub-step of `reconnectImpl`, not the impl of a `reconnectConnect` that exists |
| 5 | `closeSocketImpl(boolean)` | 4 | `Impl` | the raw teardown, behind the guarded `closeSocket` and `close` |
| 6 | `_createSubscriptionByFactory(...)` | 6 | `_` | **changed** — was `createSubscriptionInternal`. Not lifecycle; it is what the public `subscribe` is built on, so it follows the project rule |
| 7 | `_publish(InternalPublishableMessage)` | 6 | `_` | unchanged, already correct |
| 8 | `queueInternalOutgoing(NatsMessage)` | 5 | adjective | unchanged. Sits beside `queueOutgoing` — an internal message, not a marker |

## The line

**Connection lifecycle keeps `Impl`. Everything below it that the public API is built on follows the project rule and takes `_`.**

Rows 1-5 are the connection's own machinery. Rows 6-8 are not, and are named accordingly. Row 6 pairs with `JetStream._createJsSubscription`, so `JetStream:631` reads `conn._createSubscriptionByFactory(inbox, …)` and the core/JetStream layering shows on the page.

## What was tried and reverted

The Composed Method pass renamed rows 1-5 to `establishConnection`, `tearDownAndReconnect`, `attemptReconnect`, `tryServersUntilConnected`, `tearDownSocket`. All reverted. The names read well in isolation and were worse in place: they severed the link to `connect`, `reconnect`, `forceReconnect` and `closeSocket`, and cost the reader the history.

Row 1 also passed briefly through `_connect`, which is defensible under the project rule — `connect(boolean)` is a guard wrapper and the body is the complete operation. It was reverted with the rest to keep the lifecycle uniform. If this file is ever revisited, that is the row with the strongest case for moving.

## Not in this file

`ListRequestEngine.internalNextJson` became `_nextJson`. `NatsDispatcher.internalStart` became **`startImpl`**, keeping its `protected` — it is extension API for external dispatcher implementors, so `_` would have been wrong and `Impl` is right for the same reason it is right in this file. Both settled in `UNDERSCORE_INTERNAL_NAMING_AUDIT.md`.

That makes the `Impl` set six methods, not five, and gives it a second rationale. Rows 1-5 keep `Impl` because the names carry history. `NatsDispatcher.startImpl` takes `Impl` because it is the accurate marker: it names the implementation half of a public method without claiming anything about who may call it. Those are different arguments reaching the same marker, and both are worth keeping — the second is the one that generalises.
