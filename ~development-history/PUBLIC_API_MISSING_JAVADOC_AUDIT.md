# Audit: public API declarations missing Javadoc

Scope: `main` source of `core`, `jetstream` (incl. `kv`, `os`), and `service`. Test sources and `examples` excluded. "Public API surface" = every declaration reachable by a consumer: `public` and `protected` types/members, plus interface/`@interface` members (which are implicitly public even without the keyword). A declaration counts as **documented** if the line immediately above it (skipping annotation and blank lines) closes a `/** … */` Javadoc block.

## Method

Parsed each file with a comment/string-stripping scanner + brace-scope tracker, so `public`/`protected` inside strings or comments, and local statements inside method bodies, don't produce false hits. `@Override` methods are counted separately — they inherit their supertype's doc, so a missing Javadoc on them is not a real gap. Constants (`public static final`) are broken out from other fields because they're the most debatable "does this even need a doc" category. Spot-checked against `Message.java` (fully documented → 0 flags) and against each headline finding below.

## Headline numbers

> **SUPERSEDED — these are the original heuristic's numbers and they are wrong.** They over-counted. The compiler-exact measurement is **712** missing-comment declarations, not 1232; see "Re-verification" below. Kept for provenance only. **Current state is in "Where things stand" at the end of this document.**

| | count |
|---|---|
| Public-API declarations total | 3966 |
| Missing Javadoc (raw) | 1575 |
| — of those, `@Override` (inherit doc, not a real gap) | 343 |
| **Missing Javadoc, excluding `@Override`** | **1232** |

Split by tier (excluding `@Override`):

| Tier | total | types | methods | fields |
|---|---|---|---|---|
| **1 — public API** (`client`, `*/api`, `kv`, `os`, `service`) | **227** | 11 | 107 | 109 |
| 2 — `utils` packages | 415 | 21 | 249 | 145 |
| 3 — `impl` packages | 590 | 44 | 279 | 267 |

The `impl` and `utils` tiers dominate the raw count but are implementation detail — Java-`public` for cross-package reach, not "API a user is meant to call." The **227 in tier 1 is the number that matters**; within it, by visibility: 137 `public`, 80 `protected` (subclass-facing builder state — lower priority), 10 implicit-public interface members.

## Tier 1 — the real gaps

### A. Public types with no class-level Javadoc (11)

Every one of these is a type a consumer touches, with no doc comment on the type itself.

| Type | File | Note |
|---|---|---|
| `Status` | `core/api/Status.java` | class + both ctors + all getters/`isX` undocumented |
| `ServiceConstants` | `service/ServiceConstants.java` | interface of constants |
| `KeyValue` | `jetstream/kv/KeyValue.java` | the KV feature class |
| `KeyValueManagement` | `jetstream/kv/KeyValueManagement.java` | |
| `KeyValueUtils` | `jetstream/kv/KeyValueUtils.java` | |
| `KeyValueUtils.BucketAndKey` | `jetstream/kv/KeyValueUtils.java:110` | nested public class |
| `KeyValueWatchSubscription` | `jetstream/kv/KeyValueWatchSubscription.java` | |
| `ObjectStore` | `jetstream/os/ObjectStore.java` | the OS feature class |
| `ObjectStoreManagement` | `jetstream/os/ObjectStoreManagement.java` | |
| `ObjectStoreUtil` | `jetstream/os/ObjectStoreUtil.java` | |
| `ObjectStoreWatchSubscription` | `jetstream/os/ObjectStoreWatchSubscription.java` | |

### B. The dominant pattern: `kv` and `os` are undocumented top-to-bottom

8 of the 11 undocumented types are in `kv`/`os`, and it's not just the types — **every public method** in those packages is undocumented too:

| File | Undocumented public members |
|---|---|
| `jetstream/kv/KeyValueUtils.java` | 14 static helpers (`extractBucketName`, `toStreamName`, `toStreamSubject`, `toKeyPrefix`, `hasPrefix`, `trimPrefix`, `getOperationHeader`, `getNatsMarkerReasonHeader`, `getOperation`, `getDeleteHeaders`, `getPurgeHeaders`, `getPublishOptions`, + `BucketAndKey` ctors) |
| `jetstream/kv/KeyValue.java` | 9 (`create`, 4× `watch`, 2× `watchAll`, 2× `keys`) |
| `jetstream/os/ObjectStoreUtil.java` | 8 static helpers (`extractBucketName`, `toStreamName`, `toMetaStreamSubject`, `toChunkStreamSubject`, `toMetaPrefix`, `toChunkPrefix`, `encodeForSubject`, `getMetaHeaders`) |
| `jetstream/kv/KeyValueManagement.java` | 2 ctors |
| `jetstream/os/ObjectStoreManagement.java` | 2 ctors |
| `jetstream/kv/KeyValueWatchSubscription.java` | 2 ctors |
| `jetstream/os/ObjectStoreWatchSubscription.java` | 1 ctor |

This is a single coherent gap: the KV/Object-Store public surface has essentially **no** Javadoc, in contrast to the rest of `jetstream/api`. (Consistent with these packages being slated to split into their own projects — they haven't had the doc pass the core API got.)

### C. `jetstream/api` is well-documented at the type level — gaps are narrow

Of ~70 files in `jetstream/api`, only **1 type** lacks class-level Javadoc, and its public *constants* are documented (e.g. `ConsumerCreator`'s `DEFAULT_ACK_POLICY` etc. all have docs). The 81 `jetstream/api` flags are almost entirely:
- **`protected` builder-state fields** — `ConsumerCreator` alone accounts for ~40 (`isPush`, `deliverPolicy`, `durable`, `name`, …). These are subclass-visible internal state; documenting them is optional/low-value.
- a handful of **copy-constructors** and fluent setters: `PushConsumerCreator`/`PushOrderedConsumerCreator.deliverSubject`, `PushDeliverSubjectInterface` (both methods), the `Pull*`/`Push*ConsumerCreator(creator)` copy ctors, `ConsumerConfiguration.getDefaultInstance`, `Error.optionalInstance`.

### D. `core/client` and `service` — small, targeted list

| File | Undocumented public/implicit members |
|---|---|
| `core/client/api/Status.java` | class, 2 ctors, `getCode`, `getMessage`, `getMessageWithCode`, `isFlowControl`, `isHeartbeat`, `isNoResponders`, `isEob` (+ many constants) |
| `core/client/OptionsProperties.java` | `getPropertyValue`, `stringProperty`, `charArrayProperty`, `booleanProperty`, `booleanPropertyIfTrue` (interface — implicitly public) |
| `core/client/Options.java` | `DefaultThreadFactory(String)` ctor, `DefaultTokenSupplier()` / `(char[])` ctors (nested public helpers) |
| `core/client/HostnameResolveMode.java` | `get(String)`, + 3 public enum fields (`resolve`, `maxOneResult`, `includeIPV6`) |
| `service/EndpointContext.java` | `onMessage(Message)` |
| `service/ServiceEndpoint.java` | 5 `protected` getters + 1 ctor |
| `service/ServiceResponse.java` | 3 `protected` ctors/helpers (`subToJson`, `parseMessage`) |

## Tier 1 constants

66 of the tier-1 field gaps are `public static final` constants. The bulk are `Status`'s protocol codes/texts (`FLOW_CONTROL_TEXT`, `NO_RESPONDERS_CODE`, `EOB_CODE`, …) and a few enum instance fields. Whether these need per-constant Javadoc is a style call — they're self-describing, but they are user-visible. Listed in `Status.java` specifically since that whole class is undocumented.

## Tiers 2 & 3 (context, not recommended for the same bar)

- **`utils` (415):** concentrated in `NatsConstants` (89 — a wall of protocol constants), `Debug` (71), `Validator` (51), `JsValidator` (24), `JetStreamClientError` (23). These are internal helpers; `NatsConstants`/`Debug` are effectively never a documentation target.
- **`impl` (590):** implementation classes. Not part of the consumer-facing contract; documenting is optional and mostly for maintainers.

## Caveats / limitations of the heuristic

1. **`/* … */` (non-Javadoc) block comments count as "documented."** A plain block comment immediately above a decl suppresses the flag even though it isn't a real Javadoc. This *under*-counts gaps slightly; a handful of decls have explanatory `/* */` comments that a `-Xdoclint` run would still flag.
2. **Association is positional, not compiler-exact.** A blank line between a Javadoc and its decl technically detaches it; the scanner is lenient and treats it as attached. In practice the codebase puts Javadoc directly above (or above the annotations), so this is not a material source of error in the spot checks.
3. **`@Override` excluded from the "real gap" total.** 343 missing-doc decls are overrides that inherit their parent's doc — correctly not counted as gaps. (If a parent is itself undocumented, that parent shows up separately.)
4. Enum *constants* are not individually enumerated (rarely documented per-constant); enum types and their methods/fields are.

---

# Work plan (added 2026-08-04) — **COMPLETE, pushed**

**Result: tier 1 is at 0 of 142.** All groups S/KV/OS/JS/C done; `:core:javadoc :jetstream:javadoc :service:javadoc` report **0 warnings in tier-1 packages and 0 errors**; `compileJava`/`compileTestJava` clean. 22 files changed, +476 lines, comments only. The 570 `impl`/`utils` warnings remain by design.

## Extension beyond tier 1 (2026-08-04)

Scott added three tier-2 files by name — the validators and the client-error catalogue — **97 declarations, now all at 0**:

| File | Was | Now |
|---|---|---|
| `core/utils/Validator.java` | 49 | 0 |
| `jetstream/utils/JsValidator.java` | 25 | 0 |
| `jetstream/utils/JetStreamClientError.java` | 23 | 0 |

Running total: **712 → 473**. Tier 1 remains 0; javadoc errors 0; compile clean.

## Can gradle/javadoc just ignore a place? (investigated, not applied)

**Yes — `-Xdoclint/package:` works.** Verified: core went 337 → 0 warnings, 0 errors.
```groovy
javadoc { options.addStringOption('Xdoclint/package:-io.synadia.client.utils,-io.synadia.client.impl', '-quiet') }
```

**The Javadoc task's `exclude` does NOT work here.** Both `'**/utils/NatsConstants.java'` and the source-relative `'io/synadia/client/utils/NatsConstants.java'` left all 90 of its warnings in place — something in the build re-establishes the task's source after the config is applied. Do not reach for `exclude`.

**Two caveats that make this a real trade, not a free win:**
1. `-Xdoclint/package:` disables **every** doclint group for those packages, not just the missing-comment check — reference and HTML validation go with it. The `<pUnlike` malformed-HTML bug (in `impl`) and the `package-info.java` broken `@link` would both have gone unreported.
2. **Doclint scoping is per-package, with no per-file granularity, and `NatsConstants`, `Validator` and `JsValidator` all live in `io.synadia.client.utils`.** So there is no configuration that silences the constants wall while still enforcing the validators. That is why the validators were documented instead of the package being switched off.

Untested third option if per-file silencing is ever wanted: the Java 9+ `@hidden` tag on `NatsConstants`/`JetStreamConstants` — ~2 comments instead of 148, keeps doclint on elsewhere, but drops those classes from the published javadoc jar.

## SECOND CATEGORY FOUND (2026-08-04) — incomplete javadoc, 263 warnings

**This audit, and every "tier 1 = 0" claim in it, measured only `warning: no comment` — a declaration having *no* doc comment at all.** Javadoc emits a second, entirely separate class of warning for a comment that exists but is incomplete. Widening the grep past `no comment` exposed **263** of them.

**161 of those 263 are in tier 1** — the packages this plan just drove to zero. Tier 1 is therefore complete for *missing* javadoc and **not** complete for *well-formed* javadoc. State it that way; do not claim the API is fully documented.

| Warning | Count | What it means |
|---|---|---|
| `no main description` | 151 | The comment is tags only, no prose. Very common in fluent getters: `/** @return the description. */` |
| `no @param for <name>` | 73 | A parameter (or type parameter) has no tag |
| `no @return` | 18 | A non-void method has no return tag |
| `use of default constructor, which does not provide a comment` | 11 | A public class relies on the implicit no-arg constructor, which cannot be documented — needs an explicit one |
| `no @throws for <name>` | 10 | A declared checked exception has no tag |

Split: **tier 1 161 / tier 2-3 102.** Top files: `impl/JetStream.java` 62, `api/ConsumerConfiguration.java` 35, `api/ConsumerCreator.java` 34, `client/OptionsProperties.java` 20, `kv/KeyValueConfigurationCreator.java` 16, `kv/KeyValueConfiguration.java` 16, `impl/NatsConnection.java` 15, `os/ObjectStoreConfiguration.java` 11.

**Six of the 263 were self-inflicted and are already fixed:** three `@param <T>` omissions on the generic `Validator` methods documented in this pass, and three missing `@param` on `classnameListProperty` — the latter authored in the **already-pushed** listener commit `7e203856`, so that helper shipped with incomplete javadoc. Both were only caught by widening the grep, which is the lesson: **`no comment` alone is not a sufficient oracle.**

### Tier-1 breakdown — the decision splits it 134 / 27

| File | Total | `no main description` | real missing tags |
|---|---|---|---|
| `api/ConsumerConfiguration.java` | 35 | 35 | 0 |
| `api/ConsumerCreator.java` | 34 | 33 | 1 |
| `client/OptionsProperties.java` | 20 | 0 | **20** |
| `kv/KeyValueConfigurationCreator.java` | 16 | 16 | 0 |
| `kv/KeyValueConfiguration.java` | 16 | 16 | 0 |
| `os/ObjectStoreConfiguration.java` | 11 | 11 | 0 |
| `os/ObjectStoreConfigurationCreator.java` | 10 | 10 | 0 |
| `api/StreamSourceCreator.java` | 7 | 7 | 0 |
| `client/Dispatcher.java` | 6 | 6 | 0 |
| `os/ObjectStore.java` | 4 | 0 | **4** |
| `api/AbstractOrderedConsumerCreator.java` | 1 | 0 | **1** |
| `api/AbstractEphemeralConsumerCreator.java` | 1 | 0 | **1** |
| **tier 1 total** | **161** | **134** | **27** |

The split is nearly clean: the `Configuration`/`Creator` fluent classes are *entirely* `no main description`, while the genuinely missing `@param`/`@return`/`@throws` sit in just five files. All 11 `use of default constructor` warnings are tier 2/3, none in tier 1.

*(Work items for this section have moved to **# Work Items

## COMPLETE — 492 of 492 done (2026-08-04)

**Final state: 11 warnings, 0 errors.** The 11 are `DebugJs.java`, the permanent gitignored floor. Every other public declaration in `core`, `jetstream` and `service` is documented.

| Group | Warnings | Result |
|---|---|---|
| B — `core/impl` user-facing | 63 | 0 |
| J — `jetstream/impl` user-facing (incl. SF's 11) | 43 | 0 |
| K — protocol constants walls | 148 | 0 |
| R — remaining public internals | 238 | 0 |
| `DebugJs.java` | 11 | untouched, by design |

Run in parallel by six agents on disjoint file sets (74 files). Verified after a `./gradlew clean`, since concurrent builds had left stale classes.

### Non-comment changes — 11 constructors and 3 enum reformats

Everything else was comments. Two categories of real code change:

**11 explicit constructors**, needed because `use of default constructor` cannot be silenced by a comment — the implicit constructor has nowhere to attach one.
* `private` (pure static holders, matching the existing `/* ensures cannot be constructed */` idiom): `NatsImpl`, `HappyEyeballsConnector`, `SSLUtils`. **Independently verified safe: 0 `new` calls and 0 subclasses each** across main, test and examples. Making these private would be breaking otherwise.
* `public` (API-identical to the implicit constructor they replace): `DefaultReconnectDelayHandler`, `DispatcherFactory`, `ErrorListenerConsoleImpl`, `NatsMessageBuilder`, `ReaderListenerConsoleImpl`, `SocketDataPort`, `WebsocketFrameHeader`, and `SSLContextFactoryProperties.Builder`. `SocketDataPort` was deliberately kept public — it is instantiated reflectively as the configured data port type and subclassed by four test classes.

**3 enums reformatted one-line to multi-line** — `CancelAction`, `ManageResult`, `TokenType`. Unavoidable: `public enum TokenType {SPACE, CRLF, KEY, WORD, TEXT}` has nowhere to attach per-constant javadoc. Same constants, same order.

Full suite after: `core` + `jetstream` + `service`, 540 tests, one failure — `AuthTests.testWsJWTAuthWithCredsFile`, which references **none** of the changed files, passes on rerun, and is the sibling of the already-listed `testWssJWTAuthWithCredsFile` (the wss+TLS+JWT handshake, the slowest connect path in the suite).

### Two traps found by the agents, beyond the four already known

5. **`{@value}` requires `static final`.** `WebsocketFrameHeader.MAX_FRAME_HEADER_SIZE` is `public static int` — not final — so `{@value}` there is a hard `error:`, not a warning. Also inapplicable to `byte[]`, `List`, `String[]` and any `.length`-derived value.
6. **A `{@link}` to a package-private type is fine in `@return` but an error in a main description.** `NatsMessageSink` is package-private; moving such a link into the first sentence caused it to be copied into public subclasses' method summaries, producing four `reference not accessible` warnings. Keep links to non-public types out of main descriptions.

### Parallel-run lesson

**Five of six agents hit build-tree contention** — `Unable to delete directory`, `NoSuchFileException` on freshly deleted `.class` files, and a `cannot find symbol` cascade when one agent's `NatsConstants` rewrite landed mid-compile for another. All were spurious and cleared on retry, but they look exactly like real code errors. For any future fan-out, give each agent its own `layout.buildDirectory` via an init script, and skip `--rerun-tasks`, which forces the colliding recompiles. Caveat found by one agent: relocating the build dir breaks `compileTestJava` classpath resolution (bnd/java-library interaction), so use the normal build dir for tests.

## Never in scope

* **`DebugJs.java` (11 warnings)** — matches the gitignored `**/Debug*.java`; `git ls-files` returns nothing. Present only in Scott's working tree, so CI reports **0** where this tree reports 11. Documenting it would be lost work. See [[feedback_debug_classes_never_in_tests]].
* **package-private and private members** — javadoc's default `-protected` visibility never considers them, except via the serialized form (now handled).
* **`examples` module** — 7 warnings, 0 errors, on the `Zxx` sample classes. Outside the audit's scope from the start.
* **test sources** — outside scope from the start.

---

# Historical work items

Retained for provenance. All groups below are complete.
** at the end of this document.)*

## What is left, and where it is



**473 remaining**, all `impl`/`utils`, still out of scope. The concentration is unchanged: `NatsConstants` 90 and `JetStreamConstants` 58 are **148 of the 473** and are the walls of protocol constants this audit never intended to target.

**Two mistakes worth remembering for the tier-2/3 pass, if it happens:**
1. **Javadoc must precede annotations, not sit between them and the declaration.** Anchoring inserts on the method signature put 11 blocks *after* `@NonNull`/`@Nullable` in `KeyValueUtils`, which javadoc silently ignores — the file still reported 23 warnings after "documenting" it. Anchor above the annotation, or post-process with a swap. **This bit twice:** the first swap regex only matched `@NonNull|@Nullable`, so `Validator` and `JsValidator` class docs landed under `@SuppressWarnings("UnusedReturnValue")` and were ignored. Match *any* annotation: `( *@[A-Za-z_]\w*(?:\([^)]*\))?\n)( */\*\*(?:.|\n)*?\*/\n)` → swap the groups.
2. **Do not add `@throws` without checking the signature.** Assuming the KV/OS management constructors threw `JetStreamException`/`InterruptedException` produced 8 `error: exception not thrown`. The two *WatchSubscription* ctors genuinely do declare them; the two *Management* ctors do not.

Both were caught only because every step was re-verified with a javadoc run — the warning count is the oracle, not the edit count.

---

# Work plan (original)

## Re-verification: use javadoc's own warnings, not the heuristic

The original audit used a comment/brace scanner and flagged its own limitations (§Caveats). Before planning, the whole surface was re-measured with **javadoc's `warning: no comment`**, which is compiler-exact and resolves caveats 1 and 2 outright.

**Trap that must not be repeated:** javadoc caps output at **100 warnings per module** by default. A naive `./gradlew :core:javadoc :jetstream:javadoc :service:javadoc` reports **102** warnings total and looks like the problem is nearly solved. It is truncated. Lift the cap with an init script (keeps the repo clean):

```groovy
// /tmp/.../maxwarns.gradle
allprojects {
    tasks.withType(Javadoc).configureEach {
        options.addStringOption('Xmaxwarns', '100000')
    }
}
```
```
./gradlew --init-script <path>/maxwarns.gradle :core:javadoc :jetstream:javadoc :service:javadoc --rerun-tasks 2>&1 | grep "warning: no comment"
```

**True total: 712** undocumented public declarations (vs the heuristic's 1232 excluding `@Override` — the heuristic over-counted).

## Why the javadoc oracle is the right scope

javadoc's doclint flags undocumented **`public`** members but **not `protected`** ones. Verified: `ConsumerCreator.java` produces **0** warnings despite ~40 undocumented `protected` builder-state fields (`isPush`, `deliverPolicy`, `durable`, …).

> **CORRECTION (2026-08-04): that rule is incomplete.** Protected — and even private — members **are** flagged when the enclosing class implements `Serializable`, because javadoc documents a serializable class's fields on the *Serialized Form* page regardless of visibility. `ConsumerCreator` escapes only because it implements `JsonSerializable` but **not** `Serializable`. The real rule is: *doclint flags public members, plus any field of a `Serializable` class whatever its visibility.* Measured consequence — 11 of the remaining warnings are exactly this (`BaseConsumeOptions` 10 protected fields + `FetchConsumeOptions.noWait` private, class is `implements JsonSerializable, Serializable`). They are the one place the "protected is out of scope" decision cannot reach zero.

That is exactly the line the audit itself recommended (§Suggested priority #5: "Explicitly out of scope … `protected` builder-state fields"). So the tool's definition and the audit's intent already agree, and "zero `no comment` warnings in tier-1 packages" becomes a **machine-checkable definition of done** rather than a judgement call.

This also explains the audit-vs-javadoc gap in `jetstream/api`: the audit counted 81, javadoc reports 14 — the difference is almost entirely those protected fields. The 14 that remain are precisely the user-facing items the audit's §C called out.

## Current state — tier 1 = 142 warnings across 21 files

| # | File | Count | Group |
|---|---|---|---|
| 1 | `core/…/client/api/Status.java` | 55 | S |
| 2 | `jetstream/…/kv/KeyValueUtils.java` | 23 | KV |
| 3 | `jetstream/…/os/ObjectStoreUtil.java` | 16 | OS |
| 4 | `jetstream/…/kv/KeyValue.java` | 10 | KV |
| 5 | `core/…/client/OptionsProperties.java` | 7 | C |
| 6 | `jetstream/…/api/StreamCreator.java` | 5 | JS |
| 7 | `core/…/client/HostnameResolveMode.java` | 4 | C |
| 8 | `jetstream/…/os/ObjectStoreManagement.java` | 3 | OS |
| 9 | `jetstream/…/kv/KeyValueWatchSubscription.java` | 3 | KV |
| 10 | `jetstream/…/kv/KeyValueManagement.java` | 3 | KV |
| 11 | `jetstream/…/os/ObjectStoreWatchSubscription.java` | 2 | OS |
| 12 | `jetstream/…/api/PushDeliverSubjectInterface.java` | 2 | JS |
| 13 | `service/…/service/ServiceConstants.java` | 1 | C |
| 14 | `jetstream/…/os/ObjectStore.java` | 1 | OS |
| 15-21 | `jetstream/…/api/` — `PushOrderedConsumerCreator`, `PushConsumerCreator`, `PullOrderedConsumerCreator`, `PullConsumerCreator`, `Error`, `ConsumerConfiguration`, `AbstractOrderedConsumerCreator` | 1 each | JS |

Tier 2/3 (`core/utils` 257, `jetstream/impl` 169, `core/impl` 80, `jetstream/utils` 64) = **570**, out of scope for this pass.

**Note on kv/os:** `settings.gradle:20` has `// include 'kv'` commented out and the top-level `kv/` and `os/` directories contain only a `README.md`. KV/OS still ship **inside jetstream** (`jetstream/src/main/java/io/synadia/client/{kv,os}`), so documenting them now is worthwhile and is not blocked by the deferred split ([[project_kv_os_split]]).

*(Work items for this section have moved to **# Work Items** at the end of this document.)*

## Decisions needed before starting

1. **SETTLED — constants ARE documented** (Scott: "let's comment the constants"). S3 and JS3 are in scope, so the target is a genuine **zero** tier-1 warnings. Style: terse one-liner, `{@value}` where the constant is a String or primitive (it renders the value automatically); `byte[]` constants cannot use `{@value}` — cross-reference the String they encode instead.
2. **SETTLED — `protected` members are out of scope** (Scott: "I don't care about protected ones"). No adjustment needed: doclint never flags `protected`, so the javadoc oracle already enforces this for free. Documenting `ConsumerCreator`'s ~40 protected builder fields, and the `service` protected accessors from the audit's §D, is explicitly **not** part of this work.
3. **Scope confirmation: tier 1 only.** The 570 tier-2/3 warnings (`impl`, `utils`) stay out. `NatsConstants` (89) and `Debug` (71) in particular are not documentation targets — and `Debug*` is gitignored anyway ([[feedback_debug_classes_never_in_tests]]).

## Suggested priority (original, all now DONE)

1. **`kv` + `os` public surface (B)** — one focused pass documents 8 types and ~40 public methods/ctors; biggest single win and the most visible inconsistency vs. the rest of the API.
2. **`Status` (A/D)** — small, fully-public class, currently zero docs.
3. **`jetstream/api` copy-ctors + fluent setters (C)** — a dozen genuinely user-facing methods.
4. Optional: `service` protected accessors, tier-1 constants.
5. Explicitly out of scope for an "API docs" bar: `impl`, `utils`, and `protected` builder-state fields — note them, don't hold them to the same standard.

~~Raw per-declaration lists in scratchpad: `miss_all.txt` (all 1232), `miss_t1.txt`, `miss_t1_nonconst.txt`.~~ **Gone** - those were a previous session's scratchpad and the 1232 figure is the discredited heuristic count. Regenerate from javadoc instead; the command is in "Re-verification".


---

# Where things stand (2026-08-04, verified)

Latest push `dc10cb9e`. Full run: `:core:javadoc :jetstream:javadoc :service:javadoc` with `-Xmaxwarns` lifted.

| | count |
|---|---|
| **errors** | **0** |
| warnings, total | 503 |
| — `no comment` | 463 |
| — `no @param` | 17 |
| — `use of default constructor` | 11 |
| — `no main description` | 7 |
| — `no @return` | 3 |
| — `no @throws` | 2 |

(471 unique source locations; some carry more than one warning.)

**Tier 1 (by package) is 0 in both categories, and `impl/JetStream.java` is now 0 too.** Every remaining warning is in `impl`/`utils` — but see "TIERING FLAW FOUND" above: 106 of them are on user-facing types that the package-based tiering mislabelled, and are now tracked as tier 1b work items B1-B5 / J1-J5. Package accounting:

| package | warnings | of which tier 1b (work items) |
|---|---|---|
| `core/utils` | 211 | 0 |
| `jetstream/impl` | 162 | 43 (J1-J5) |
| `core/impl` | 114 | 63 (B1-B5) |
| `jetstream/utils` | 16 | 0 |
| **total** | **503** | **106 tier 1b + 397 X** |

Nothing falls outside those two tiers — checked by inverting both tier patterns and getting an empty set, so there is no package quietly unaccounted for.

## TIERING FLAW FOUND (2026-08-04) — `.impl` is not all internal

Scott: "public things in JetStream need comment for sure." He is right, and it exposes a defect in this audit's premise.

**The tiers were assigned by package**, treating `io.synadia.client.impl` as implementation detail. In this codebase that is false: primary user-facing types live in `.impl`. This is the same fact `PLAN_CORE_JETSTREAM_BOUNDARY.md` documents — every touched core type is a `public class` in `.impl`, so the boundary is unenforced and package name says nothing about audience.

The clearest case: **`impl/JetStream.java` has 72 warnings, 64 public methods, is constructed directly by users (`new JetStream(nc)`), and carries usage examples in its own class javadoc.** It is tier 1 in substance and was filed tier 3 on a naming technicality. `impl/NatsConnection.java` (21) is the same story — it is *the* object a user holds.

### Proposed tier 1b — user-facing types sitting in `.impl`

| File | Warnings | Why it is user-facing |
|---|---|---|
| `jetstream/impl/JetStream.java` | 72 | the publish/subscribe API |
| `core/impl/NatsConnection.java` | 21 | the connection object |
| `core/impl/SSLContextFactoryProperties.java` | 19 | user TLS configuration |
| `core/impl/AckType.java` | 10 | public enum used when acking |
| `jetstream/impl/BaseConsumeOptions.java` | 10 | user consume options |
| `jetstream/impl/AbstractBucketFeature.java` | 9 | base of `KeyValue`/`ObjectStore` |
| `jetstream/impl/JetStreamSubscribeConfig.java` | 8 | user subscribe config |
| `jetstream/impl/JetStreamManagement.java` | 8 | stream/consumer management API |
| `core/impl/DataPort.java` | 6 | SPI users can implement |
| `jetstream/impl/StreamContext.java` | 4 | user-facing context API |
| `core/impl/NatsMessage.java` | 4 | the message implementation |
| `core/impl/NatsSubscription.java` | 3 | the subscription implementation |
| `jetstream/impl/NatsWatchSubscription.java` | 3 | base of the KV/OS watch subscriptions |
| **total** | **177** | |

Still genuinely internal and staying out: `JetStreamConstants` 58 (constants wall), `MessageManager` 22, `PushMessageManager` 5, `PullManagerObserver` 4, `ConsumerCreateRequest` 5, `AbstractListReader` 4, `StreamListReader` 3, `NatsMessageSink` 7, `ServerPoolEntry` 6, `DefaultReconnectDelayHandler` 4, `NatsImpl` 3, and the rest of `core/utils` / `jetstream/utils`.

**`JetStream.java` is DONE — 72 → 0** (2026-08-04). Overall 575 → 503, 0 errors, compile clean. Its gaps were the publish/subscribe surface: `@param`/`@return`/main descriptions across the publish overloads, `createConsumer`, `getConsumerContext` and both constructors, driven from a shared parameter table so the overloads stay worded identically.

### Visibility of what remains — 98% is public API

Classified all 471 remaining flagged declarations:

| kind | count |
|---|---|
| explicitly `public` | 278 |
| no modifier — enum constants and interface fields (implicitly public) | 127 |
| interface methods including `default` (implicitly public) | 55 |
| **subtotal: public API** | **460** |
| `protected` (all `BaseConsumeOptions`, reachable only via `Serializable`) | 10 |
| `private` (`FetchConsumeOptions.noWait`, same reason) | 1 |

So the answer to "are they all public interfaces or public methods" is **yes — 460 of 471, and the other 11 are the serialized-form fields, which are also user-visible.**

**Swept for other back doors and found none.** A first classification suggested 15 package-private declarations, which would have been a surprise. All 15 were an artifact of the classifier reading only the *top-level* type: they are members of **nested** `public interface`s (`NatsConnection.ErrorListenerCaller`, `SSLUtils.TrustManagerDelegate`), constants of **nested** `public enum`s (`WebsocketFrameHeader.OpCode`, `ConsumerCreateRequest`), and one method declared `abstract public` rather than `public abstract`. **Zero genuinely package-private or otherwise-hidden declarations are being flagged**, so nothing is being asked for that Scott wanted ignored. javadoc's default `-protected` visibility is doing exactly what it should; the serialized form is the single documented exception.

*(Work items for this section have moved to **# Work Items** at the end of this document.)*

---

# Work Items

Everything actionable lives here. The narrative above is findings and history.

## Status at a glance

| Group | Scope | Count | State |
|---|---|---|---|
| S, KV, OS, JS, C | tier 1 missing comments | 142 | **DONE** |
| — | validators + `JetStreamClientError` (added by Scott) | 97 | **DONE** |
| M, N | tier 1 incomplete comments | 161 | **DONE** |
| — | `impl/JetStream.java` (tier 1b) | 72 | **DONE** |
| **B** | `core/impl` user-facing | **63** | open |
| **J** | `jetstream/impl` user-facing | **43** | open |
| SF | serialized-form fields | (11, **subset of J1**) | open |
| **K** | protocol constants walls | **148** | open |
| **R** | remaining public internals | **238** | open |
| **VB** | verify | — | open |
| — | `DebugJs.java` | 11 | **never** — untracked |

Current totals: **503 warnings, 0 errors.** Arithmetic reconciles exactly:

```
B 63 + J 43 + K 148 + R 238 + DebugJs 11 = 503
```
SF's 11 are inside J1, not additional. **Open work = 492** (everything but `DebugJs`).

## How to verify (read before starting)

```groovy
// init script, keeps the repo clean
allprojects { tasks.withType(Javadoc).configureEach {
    options.addStringOption('Xmaxwarns', '100000') } }
```
```
./gradlew --init-script <path>/maxwarns.gradle :core:javadoc :jetstream:javadoc :service:javadoc --rerun-tasks > out.txt 2>&1
grep -c "warning:" out.txt ; grep -c "error:" out.txt
```

Four traps, each of which cost a full cycle:

1. **`-Xmaxwarns` must be lifted** or javadoc truncates at 100 per module and reports 102 total, which looks nearly solved.
2. **Grep all warnings, not `no comment`** — that filter hid an entire 263-warning category.
3. **Redirect as `> file 2>&1`, not `2>&1 > file`** — javadoc writes to stderr, so the wrong order silently produces an empty file and a false "0 warnings".
4. **Javadoc must precede annotations.** Anchoring an insert on the declaration puts the block *under* `@NonNull`/`@SuppressWarnings`, where javadoc ignores it — the file still reports every warning after being "documented". Match any annotation: `( *@[A-Za-z_]\w*(?:\([^)]*\))?\n)( */\*\*(?:.|\n)*?\*/\n)` and swap the groups.

And one correctness rule: **never add `@param`/`@throws` without reading the signature** — a tag that does not match *creates* an `error:`. That produced 8 `exception not thrown` errors once, and a near-miss where five methods shared an identical `@throws` line but only four needed the new tag.

## Completed

Kept for the record; detail of what each covered is in the narrative above.

**Tier 1, missing comments — 142, all `[x]`**
- **S1-S3** `Status` 55 — class, both ctors, the 7 accessors, and all protocol constants (`{@value}` on String/primitive; `byte[]` cross-references the String it encodes).
- **KV1-KV3** `kv` 39 — `KeyValueUtils` 23, `KeyValue` 10, `KeyValueManagement`/`KeyValueWatchSubscription` 6.
- **OS1-OS2** `os` 22 — `ObjectStoreUtil` 16, plus `ObjectStoreManagement`/`ObjectStoreWatchSubscription`/`ObjectStore` 6.
- **JS1-JS3** `jetstream/api` 14 — copy-constructors, `PushDeliverSubjectInterface`, `ConsumerConfiguration.getDefaultInstance`, `Error.optionalInstance`, `StreamCreator` defaults.
- **C1-C3** 12 — `OptionsProperties` 7, `HostnameResolveMode` 4, `ServiceConstants` 1.
- **V1-V3** verified: tier 1 = 0, errors = 0, comments-only so no test run.

**Added by Scott mid-pass — 97, all `[x]`**
- `Validator` 49, `JsValidator` 25, `JetStreamClientError` 23.

**Tier 1, incomplete comments — 161, all `[x]`**
- **D1** scope: main descriptions count, nothing exempted.
- **M1-M3** 27 missing tags — `OptionsProperties` 20, `ObjectStore` 4 `@throws`, 3 class-level `@param <T>`.
- **N1-N3** 134 main descriptions — convention set on `KeyValueConfiguration` then reused; the 68 in `ConsumerConfiguration`/`ConsumerCreator` came from one shared table keyed on the existing `@return` text so both classes stay worded identically.
- **V1-V3** verified: tier 1 = 0 in both categories, errors = 0.

**Tier 1b started — 72, `[x]`**
- `impl/JetStream.java` 72 → 0. Publish/subscribe surface, `createConsumer`, `getConsumerContext`, both constructors.

## Open groups

### B — `core/impl`, user-facing (63)

- [x] **B1** `NatsConnection.java` (21) — *the* object a user holds. Highest value here.
- [x] **B2** `SSLContextFactoryProperties.java` (19) — user-supplied TLS configuration.
- [x] **B3** `AckType.java` (10) — public enum used when acking; 7 enum constants plus the public fields `text`, `bytes`, `terminal`.
- [x] **B4** `DataPort.java` (6) — an SPI users implement, so method contracts matter more than most.
- [x] **B5** `NatsMessage.java` (4), `NatsSubscription.java` (3) — the message and subscription implementations users receive.

### J — `jetstream/impl`, user-facing (43)

- [x] **J1** `BaseConsumeOptions.java` (10) + `FetchConsumeOptions.java` (1) — these are the serialized-form fields; done as group SF.
- [x] **J2** `AbstractBucketFeature.java` (9) — base of `KeyValue` and `ObjectStore`.
- [x] **J3** `JetStreamSubscribeConfig.java` (8) — user subscribe configuration.
- [x] **J4** `JetStreamManagement.java` (8) — the stream/consumer management API.
- [x] **J5** `StreamContext.java` (4), `NatsWatchSubscription.java` (3) — context API and the KV/OS watch base.

### SF — serialized-form fields (11)

The one route by which a non-public member is published: a `Serializable` class gets a **Serialized Form** page listing every non-transient field regardless of visibility, and doclint checks it. Proven against the generated `serialized-form.html` — `BaseConsumeOptions` and its fields appear; `ConsumerCreator` does not, because it implements `JsonSerializable` but not `Serializable`.

- [x] **SF1** `BaseConsumeOptions` — 10 `protected final` fields. Several are millisecond values and one is a percentage, so state units.
- [x] **SF2** `FetchConsumeOptions.noWait` — `private`, but inherits `Serializable` from its parent so it lands on the same page.
- [x] **SF3** Note the exception wherever "protected is out of scope" is recorded; any new `Serializable` class re-opens this.

### K — protocol constants walls (148)

Public constants, so in scope by the standing rule. Mechanical: a terse one-liner each, `{@value}` renders the value automatically.

- [x] **K1** `core/utils/NatsConstants.java` (90).
- [x] **K2** `jetstream/impl/JetStreamConstants.java` (58).

### R — remaining public internals (238)

Previously listed as "still out of scope". They are **public**, so by the standing rule they are in scope regardless of package. ~58 files, long tail. **R total is verified at 238; the R1-R5 groupings below are an indicative split for sequencing, not audited sub-totals** - regenerate per-file counts before starting one.

- [x] **R1** Wire/protocol handling — `WebsocketFrameHeader` 24, `Token` 11, `TokenType` 6, `WebSocket` 3, `WebsocketInputStream` 2, `WebsocketOutputStream` 2, `IncomingHeadersProcessor` 5, `ProtocolMessage` 1, `Headers` 1 — 55.
- [x] **R2** JetStream internals — `MessageManager` 22, `PushMessageManager` 5, `PullManagerObserver` 4, `PullMessageManager` 1, `PullOrderedMessageManager` 1, `PushOrderedMessageManager` 1, `ConsumerCreateRequest` 5, `JetStreamApiUtils` 5, `JetStreamImpl` 2, `JetStreamMessage` 2, `JetStreamSubscription` 2, `JetStreamPushSubscription` 1, `JetStreamMetaData` 1, `JetStreamStatusException` 1 — 53.
- [x] **R3** Readers/list engine — `AbstractListReader` 4, `StreamListReader` 3, `ConsumerListReader` 3, `StringListReader` 1, `StreamNamesReader` 1, `ConsumerNamesReader` 1, `ListRequestEngine` 1 — 14.
- [x] **R4** `core/utils` — `SSLUtils` 18, `Digester` 16, `ScheduledTask` 13, `NatsRequestCompletableFuture` 12, `RandomUtils` 5, `ApiUtils` 3 — 67.
- [x] **R5** `core/impl` remainder — **51**: `NatsMessageSink` 7, `ServerPoolEntry` 6, `DefaultReconnectDelayHandler` 4, `NatsImpl` 3, `NatsConnectionReader` 3, then `SSLContextFactory`, `NatsStatistics`, `NatsServerPool`, `NatsMessageBuilder`, `MemoryAuthHandler`, `ErrorListenerConsoleImpl`, `ReaderListenerConsoleImpl`, `SimplifiedSubscriptionMaker` at 2 each, and a tail of single-warning files.
- [x] **R6** The **10 `use of default constructor`** warnings are scattered through R and are the **only warnings anywhere needing a code change** — an explicit public no-arg constructor — rather than a comment. Handle deliberately; adding a constructor is not a comment-only edit and needs a test run.

### VB — verify

- [x] **VB1** Target count reaches 0 for the groups attempted.
- [x] **VB2** `error:` stays 0.
- [x] **VB3** Comment-only changes need no test run; confirm with a diff showing zero non-comment lines. **R6 is the exception** — it changes code, so run `:core:test :jetstream:test`.

## Never in scope

* **`DebugJs.java` (11 warnings)** — matches the gitignored `**/Debug*.java`; `git ls-files` returns nothing. Present only in Scott's working tree, so CI sees **386** in group R+K, not 397. Documenting it would be lost work. See [[feedback_debug_classes_never_in_tests]].
* **`protected` members** — Scott's call, and doclint does not flag them anyway. SF is the sole exception, via the serialized form.
* **package-private and private** — javadoc's default `-protected` visibility never considers them. Swept and confirmed: zero are being flagged.
* **`examples` module** — 7 warnings, 0 errors, on the `Zxx` sample classes. Outside the audit's scope from the start.
* **test sources** — outside scope from the start.
