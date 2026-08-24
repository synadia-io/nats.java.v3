# Plan — `OptionsBuilder` / `Options` defaults consistency

Goal: establish a single, consistent rule for how default values are applied across `OptionsBuilder` and `Options`, audit every field against that rule, and list the specific changes needed to bring the four current violators into line. Includes the `DefaultReconnectDelayHandler` fallback that surfaced in `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`.

## Today's patterns (de-facto, mixed)

Surveying every field in `OptionsBuilder` (lines 57–129), four distinct defaulting patterns are in use:

**Pattern A — Field-declaration constant default.** Most numeric, boolean, and enum fields:
- `boolean noRandomize = false;`
- `int maxReconnect = DEFAULT_MAX_RECONNECT;`
- `Duration reconnectWait = DEFAULT_RECONNECT_WAIT;`
- `HostnameResolveMode hostnameResolveMode = HostnameResolveMode.ResolveToAll;`
- `SubjectValidationType subjectValidationType = SubjectValidationType.Lenient;`
- `ReconnectDelayBehavior reconnectDelayBehavior = ReconnectDelayBehavior.BeforeSubsequentRounds;`
- `String dataPortType = DEFAULT_DATA_PORT_TYPE;`

**Pattern B — Explicit `= null` sentinel** (user-unset markers):
- `String connectionName = null;`
- `SSLContext sslContext = null;`
- `char[] username = null;`
- `ServerPool serverPool = null;`
- `ErrorListener errorListener = null;`
- … and ~10 others.

**Pattern C — Implicit Java default** (no initialiser; Java fills in `null` / `0` / `false`):
- `AuthHandler authHandler;`
- `ReconnectDelayHandler reconnectDelayHandler;`
- `ExecutorService userExecutor;` (plus the other four executor / thread-factory fields)
- `boolean useDefaultTls;`
- `String keystore;`
- `String credentialPath;`

**Pattern D — Allocation at field declaration:**
- `final List<NatsUri> natsServerUris = new ArrayList<>();` — legitimate; the list is appended to throughout the builder lifecycle.
- `final List<String> unprocessedServers = new ArrayList<>();` — legitimate; same reason.
- `Supplier<char[]> tokenSupplier = new Options.DefaultTokenSupplier();` — **wasteful**. Allocates a `DefaultTokenSupplier` on every `OptionsBuilder` construction. If the user supplies their own via `.tokenSupplier(...)`, the constructed default is thrown away.

**Pattern E — Build-time fallback / derivation** in `build()`:
- `if (inboxPrefix == null) inboxPrefix = DEFAULT_INBOX_PREFIX;` (line 1234) — **but the field also has `= DEFAULT_INBOX_PREFIX` at declaration**, so this is a defensive double-default that only matters if the fluent setter is called with null. Today's setter at line 418 unconditionally assigns and then dereferences (`.endsWith(".")`) — NPE on null, never reaches build(). The double-default is dead code.
- `if (natsServerUris.isEmpty()) server(DEFAULT_URL);` — legitimate derivation.
- SSL context resolution at lines 1246–1294 — legitimate multi-input derivation.

`Options(OptionsBuilder b)` (Options.java:190–259) is a pure straight-through copy. No defaulting logic there. Good — keep it that way.

## Proposed consistency rule

Three positions, applied consistently per category:

### R1. **Constant defaults** (primitives, enums, well-known constants): set at field declaration.
Already true for Pattern A. No change needed for those fields.

### R2. **"User-unset" sentinels** (the field is meant to be null/unset until the user provides it): set explicitly to `null` at field declaration, even though Java would do it implicitly.
This collapses Pattern B and Pattern C into one. The explicit `= null` is documentation: *"yes, the default really is null, this is intentional."* Skipping it leaves the reader wondering whether the omission is deliberate.

Apply to:
- `AuthHandler authHandler;` → `AuthHandler authHandler = null;`
- `ReconnectDelayHandler reconnectDelayHandler;` → `ReconnectDelayHandler reconnectDelayHandler = null;`
- `ExecutorService userExecutor;` → `... = null;`
- `ScheduledExecutorService userScheduledExecutor;` → `... = null;`
- `ExecutorService userConnectExecutor;` → `... = null;`
- `ExecutorService userCallbackExecutor;` → `... = null;`
- `ThreadFactory userConnectThreadFactory;` → `... = null;`
- `ThreadFactory userCallbackThreadFactory;` → `... = null;`
- `List<Consumer<HttpRequest>> httpRequestInterceptors;` → `... = null;`
- `Proxy proxy;` → `... = null;`
- `String keystore;` → `... = null;`
- `char[] keystorePassword;` → `... = null;`
- `String truststore;` → `... = null;`
- `char[] truststorePassword;` → `... = null;`
- `String credentialPath;` → `... = null;`
- `boolean useDefaultTls;` → `boolean useDefaultTls = false;`
- `boolean useTrustAllTls;` → `boolean useTrustAllTls = false;`

### R3. **Defaulting an "unset" sentinel to a runtime singleton or non-trivial default**: do it in `build()`, not at field declaration.
Reason: the field-declaration value should mean "the user didn't set this," and the resolution should be explicit and centralised in `build()`. This avoids:
- Wasted allocations when the user overrides the default (Pattern D's `tokenSupplier` problem).
- Double-defaulting (the `inboxPrefix` problem — field-decl AND build()).
- Confusion about whether a field is "actually null" or "defaulted somewhere."

Apply to:
- `reconnectDelayHandler` — add `if (reconnectDelayHandler == null) reconnectDelayHandler = new DefaultReconnectDelayHandler();` in `build()`. Field stays `null`. Note: `DefaultReconnectDelayHandler` is stateful (tracks LDM); each `build()` produces a fresh instance, and sharing one `Options` across multiple `Nats.connect()` calls now means the connections share LDM state.
- `tokenSupplier` — change field to `Supplier<char[]> tokenSupplier = null;`. Add `if (tokenSupplier == null) tokenSupplier = new Options.DefaultTokenSupplier();` in `build()`. Also fix the null-reset path in `tokenSupplier(...)` (line 890) to leave it `null` rather than instantiating: `this.tokenSupplier = tokenSupplier;` (then build() will pick it up). And likewise the property-class-name parser at line 188 — let null reach build() rather than substituting eagerly.
- `inboxPrefix` — pick one of the two existing defaults: keep the build()-time fallback (line 1234), drop the field-declaration default. Field becomes `String inboxPrefix = null;`. Also harden the setter at line 418 to be null-tolerant:
  ```java
  public OptionsBuilder inboxPrefix(String prefix) {
      if (prefix == null || prefix.isEmpty()) {
          this.inboxPrefix = null;   // build() will re-default
          return this;
      }
      this.inboxPrefix = prefix.endsWith(".") ? prefix : prefix + ".";
      return this;
  }
  ```

### Why this rule (and not "everything at field declaration")

Field-declaration defaults for everything would be tempting (simple, single-place-to-look). It breaks down for two cases:
1. **Constants vs. instances.** A constant like `BeforeSubsequentRounds` or `DEFAULT_MAX_RECONNECT` is fine to inline. An instance like `new DefaultTokenSupplier()` allocates on every builder construction. For the rare instance default, lazy resolution in `build()` is the right call. Today we have one such case (`tokenSupplier`); if the handler ever needed a stateful default, it would be a second.
2. **Stateful instances.** `DefaultReconnectDelayHandler` carries per-campaign LDM state — sharing one across builds means sharing the flag. Putting the instantiation in build() means each `Options` gets a fresh handler, which is what we want. It also makes the meaning of `reconnectDelayHandler == null` *in the builder* unambiguous: "user didn't set anything."

### Why this rule (and not "everything resolved in build()")

Pushing every default into `build()` would mean a 50-line if-cascade and would obscure the simple defaults (`maxReconnect = 60`, `pingInterval = 2 minutes`) that any reader benefits from seeing inline at the field. The current pattern of "constants at field, instances/singletons in build()" reads well at both extremes.

## Auxiliary fix while we're here

The current `tokenSupplier(...)` setter (line 890) explicitly substitutes when the user passes null:
```java
this.tokenSupplier = tokenSupplier == null ? new Options.DefaultTokenSupplier() : tokenSupplier;
```
With R3 above, the setter should simply assign null and let `build()` resolve:
```java
this.tokenSupplier = tokenSupplier;
```

Same for the `PROP_TOKEN_SUPPLIER_CLASS` classname-property parser at line 188 (only reachable when the property is set, so it always assigns a non-null value, but still — no need to instantiate `DefaultTokenSupplier` defensively).

## Item-by-item change list

| Field | File:line | Change |
|---|---|---|
| `reconnectDelayBehavior` | OptionsBuilder.java:106 | Per `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`: flip the default from `BeforeSubsequentRounds` to `LameDuckAware` (R1 — stays at field declaration; just a value change). |
| `reconnectDelayHandler` | OptionsBuilder.java:105 | Stays implicit null today. Make explicit `= null` (R2). Resolve in `build()` to `new DefaultReconnectDelayHandler()` (R3). Fresh instance per build — handler carries LDM state. |
| `tokenSupplier` | OptionsBuilder.java:85 | Change field default to `= null` (R3). Add `if (tokenSupplier == null) tokenSupplier = new Options.DefaultTokenSupplier();` in `build()`. Simplify the setter at line 890 and the property parser at line 188 to leave null alone. |
| `inboxPrefix` | OptionsBuilder.java:93, 1234 | Drop the field-declaration default. Keep the `build()` fallback. Make the setter at line 418 null-tolerant (set field to null on null-or-empty input so build() can re-default). |
| `AuthHandler authHandler` | OptionsBuilder.java:104 | Add explicit `= null` (R2). |
| `ExecutorService userExecutor` (and the 5 other executor / thread-factory fields) | OptionsBuilder.java:113–118 | Add explicit `= null` (R2). |
| `httpRequestInterceptors` | OptionsBuilder.java:119 | Add explicit `= null` (R2). |
| `Proxy proxy` | OptionsBuilder.java:120 | Add explicit `= null` (R2). |
| `boolean useDefaultTls` | OptionsBuilder.java:122 | Add explicit `= false` (R2). |
| `boolean useTrustAllTls` | OptionsBuilder.java:123 | Add explicit `= false` (R2). |
| `keystore`, `keystorePassword`, `truststore`, `truststorePassword`, `credentialPath` | OptionsBuilder.java:124–129 | Add explicit `= null` (R2). |
| Everything else | — | Leave as-is. |

`Options(OptionsBuilder b)` (Options.java:190–259) does not change — the constructor remains a pure straight-through copy. All defaulting still happens before it runs, in `build()`.

## Implementation order (build-safe)

1. Apply R2 — add explicit `= null` / `= false` to the implicit-default fields in `OptionsBuilder`. Pure cosmetic; tree stays green at every step.
2. Apply the `reconnectDelayBehavior` value flip to `LameDuckAware` and update the `ReconnectDelayBehavior.get(...)` default. (Already part of `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`.)
3. Move `tokenSupplier` default to `build()`. Update the setter at line 890 and the `PROP_TOKEN_SUPPLIER_CLASS` parser at line 188.
4. Move `reconnectDelayHandler` default to `build()` — fall back to `new DefaultReconnectDelayHandler()`. (Consolidates the corresponding step from `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`.)
5. Move `inboxPrefix` default to `build()` only. Drop the field-decl default. Harden the setter against null/empty.
6. Run `OptionsTests`. Any test that asserts on the builder field value (before `build()`) of `tokenSupplier` / `inboxPrefix` / `reconnectDelayHandler` will need updating — the field is now null until build().

## Risks

- **`tokenSupplier` reachability**: line 1230 does `if (this.username != null && tokenSupplier.get() != null)`. If we defer the default to build(), this line needs to run *after* the fallback resolution. Today both happen inside `build()` — make sure the fallback runs first.
- **`inboxPrefix` null-tolerant setter**: any caller that previously relied on the NPE-on-null behaviour (defensive: "they'll find out at the setter") loses that signal. The new contract is "null/empty means re-default at build." Document in the Javadoc.
- **R2 is cosmetic** but touches ~17 fields. A linter / formatter pass should not undo the explicit `= null`. Add a quick `git diff --stat` review note: this commit is supposed to be roughly "+17 trivial diffs, 0 net behaviour change."

## What this plan does NOT touch

- Field ordering, grouping, or comments above the `BUILDER VARIABLES` block. Leave alone.
- Property names or property-parser layout.
- The fluent setters' parameter validation (other than `inboxPrefix`).
- The `Options` getters and `Options(OptionsBuilder b)` constructor.
- `Options.DefaultTokenSupplier` itself (still instantiated lazily — could be made a singleton if it's stateless, but that's outside the scope of this audit).
