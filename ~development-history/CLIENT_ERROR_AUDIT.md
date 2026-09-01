# ClientError Audit — core candidates, and whether the JetStream/ObjectStore errors belong in `JetStreamException`

Two questions, asked 2026-08-31:

1. Are there places in **core** that could use the `ClientError` construct?
2. Should the **JetStream / ObjectStore** errors be taken out of the construct and replaced with something in the `JetStreamException` hierarchy?

Short answers, argued below. **(1) Yes, but narrowly** — about 14 lifecycle conditions in `NatsConnection` / `NatsDispatcher` / `NatsSubscription` / message binding, covering ~22 throw sites. Not `Validator`, and not the protocol-internal throws. **(2) Mostly no** — these are *client* API exceptions (the client catching misuse of its own API), not JetStream API exceptions (the server returned an error), and unchecked is the right shape for 9 of the 12. The exception is the digest/chunks/size trio, which is neither: it reports data corruption found after a successful download, and calling that `IllegalArgumentException` is the one indefensible label. Fix it by changing the kind, not the hierarchy. **Done 2026-08-31** — see §3b/§3c.

Nothing here is decided or started. Related: `EXCEPTIONS_AUDIT.md` (which does not mention `ClientError` at all), `PLAN_CORE_JETSTREAM_BOUNDARY.md`, `OSGi_JPMS_TODO.md` O3.

> **Audited against in-flight work.** The three-class split — `ClientError` extracted to core, `JetStreamClientError` reduced to it, `ObjectStoreClientError` created — was uncommitted when this was written (`ClientError.java` and `ObjectStoreClientError.java` staged-and-modified, with `ObjectStore` / `JetStream` / `JetStreamSubscribeConfig` / `ConsumerCreator` modified alongside). Everything below is measured against the **working tree**. That also means the answer to Q1 is the reason the split exists rather than a criticism of it: core holds the base class and no constants yet because the extraction is what just happened.

---

## 1. What the construct is, measured

`ClientError` (`core`, `io.synadia.client.utils`, public) is a catalog entry: a stable `GROUP-CODE` id, a message that begins with `[id]`, and an `int kind` selecting which unchecked exception `instance()` builds — `KIND_ILLEGAL_ARGUMENT` (0) or `KIND_ILLEGAL_STATE` (1). It also exposes `instance(String extra)`, `id()`, `message()`, `getKind()`, and `matches(Exception)`.

| Class | Module | Constants | Throw sites |
|---|---|---|---|
| `ClientError` | core | 0 (base only) | — |
| `JetStreamClientError` | jetstream | 3 | 5 |
| `ObjectStoreClientError` | jetstream | 9 | 16 |

**Core holds the base class and no constants.** `grep -rn ClientError core/src` returns only the file that declares it — expected, since extracting that base is the change in flight. KV uses it for nothing either (one ad-hoc `IllegalStateException` in `KeyValueEntry:38`). The open question is whether core has anything worth putting in it; §2 says yes, about 14 conditions.

**Three constants left the catalog for two different reasons.** `JsConsumerCreate290NotAvailable` (`CON-90301`) and `JsMultipleFilterSubjects210NotAvailable` (`CON-90303`) were dropped deliberately with the split: v3 requires nats-server **2.10 or later** (2.14 preferred), so neither condition can occur and both errors were unused. Documented in `README.md` and at the top of `MIGRATION_GUIDE.md`. `JsConsumerNameDurableMismatch` (`CON-90302`) went separately and for its own reasons — §5. That empties the `CON` group.

**Follow-on the 2.10 floor implies, not yet done:** `JetStreamImpl:57` still reads `consumerCreate290Available = si.isSameOrNewerThanVersion("2.9.0") && !jso.isOptOut290ConsumerCreate()`. At a 2.10 floor the version half is always true, so the only thing that can still disable it is `JetStreamOptions.optOut290ConsumerCreate`. The gate and that option are candidates for removal on the same reasoning that removed the constants. The 2.11 gate (`directBatchGet211Available`) stays meaningful and should not be touched.

For scale: v2's single `NatsJetStreamClientError` carried **49** constants across `SUB` / `SO` / `OS` / `CON` groups. v3 is down to 12 across two classes, because the `SUB`/`SO` families died with `PushSubscribeOptions` / `PullSubscribeOptions`. So the construct is currently carrying a quarter of the load it was designed for.

The five JetStream sites are `JetStream:788`, `JetStream:1023`, `JetStreamSubscribeConfig:67` (direct `.instance()`), and `ConsumerCreator:627`/`:637`, which pass the error *object* into `JsValidator.validateMustMatchIfBothSupplied(s1, s2, JetStreamClientError err)` — the one place the catalog entry is treated as a value rather than a throw-site constant. All 16 ObjectStore sites are in `ObjectStore.java`.

`matches(Exception)` — the payoff for having stable ids, since it lets a test assert on identity instead of a message string — is called from **six** places, all in `JetStreamSubscribeTests`, two for each of the three `JetStreamClientError` constants. No `ObjectStoreClientError` constant is matched anywhere.

---

## 2. Question 1 — core candidates

Core throws **70** ad-hoc `IllegalArgumentException` and **62** ad-hoc `IllegalStateException`. They are not one population; they are three, and only one is a candidate.

### 2a. Not candidates — argument validation (~31 sites)

`Validator.java` alone accounts for 31 of the 70 `IllegalArgumentException` throws. These already have a single home, the messages are parameterized by field name, and a caller never distinguishes one from another — they mean "you passed something bad, the message says what." Giving each a `GROUP-CODE` would add ceremony and a maintenance surface for no reader benefit.

### 2b. Not candidates — protocol and transport internals (~40 sites)

`NatsConnectionReader` (10 ISE), `WebSocket` (9 ISE), `Token` (5 IAE), `WriterMessageQueue` (4 ISE), `IncomingHeadersProcessor` (3 IAE), `HttpRequest` (3 IAE): `"Bad socket data, no LF after CR"` (×4), `"Bad MSG control line, missing required fields"`, `"Protocol line is too long"`, `"Exceeded max HTTP headers="`, and so on.

These fire on a malformed frame or a torn connection. The user cannot act on the distinction, does not catch them individually, and they are already effectively fatal to the connection. A stable id buys a support-ticket grep and nothing else. Leave them.

### 2c. The real candidates — lifecycle state (~22 sites, ~14 distinct conditions)

These are the ones a user actually catches, a test actually asserts on, and that repeat verbatim across call sites — which is exactly the duplication the construct exists to remove.

| Condition | Sites | File | Kind |
|---|---|---|---|
| `NatsConnection is Closed` | 5 | `NatsConnection` | STATE |
| `NatsConnection is Draining` | 3 | `NatsConnection` | STATE |
| `NatsConnection is not active.` | 1 | `NatsConnection` | STATE |
| `A connection can't be drained during close.` | 1 | `NatsConnection` | STATE |
| `NatsConnection can only manage its own dispatchers` | 1 | `NatsConnection` | ARG |
| `Dispatcher is closed` | 3 | `NatsDispatcher` | STATE |
| `Dispatcher is draining` | 1 | `NatsDispatcher` | STATE |
| `Dispatcher was made without a default handler.` | 1 | `NatsDispatcher` | STATE |
| `Dispatcher is already closed.` | 1 | `NatsDispatcher` | ARG |
| `Subscription is not managed by this Dispatcher` | 1 | `NatsDispatcher` | ARG |
| `This Subscription implementation class type is not managed by the Dispatcher implementation` | 1 | `NatsDispatcher` | ARG |
| `This subscription is inactive.` / `became inactive.` | 3 | `NatsSubscription` | STATE |
| `Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.` | 1 | `NatsSubscription` | STATE |
| `Message is not bound to a subscription.` / `to a connection` | 2 | `JetStreamMessage` | STATE |

Two supporting observations:

- **`"NatsConnection is Closed"` ×5 and `"Dispatcher is closed"` ×3 are the case for the construct in miniature.** Five copies of a literal is five chances for one to drift, and today a test that wants to assert *which* state error fired has to string-match. The `V2_V3_TEST_METHOD_AUDIT` work hit this directly: the new `DispatcherTests.testThrowOnWrongSubscription` asserts with `getMessage().contains("Subscription is not managed by this Dispatcher")`, which is precisely what `matches()` exists to replace.
- **`FileAuthHandler` and `StringAuthHandler` each throw `"problem signing nonce"` and `"problem getting public key"`** — 2 sites each, verbatim. Borderline: they wrap a cause, and `ClientError.instance()` has no cause-carrying overload. If core adopts the construct, adding `instance(Throwable cause)` would bring these in; otherwise leave them. (Note the third apparent copy of each, in `AuthHandler.java:20,31`, is inside the javadoc example, not code.)

**Recommendation for Q1:** a `CoreClientError` catalog with the ~14 conditions in the table, groups something like `CON` (connection), `DIS` (dispatcher), `SUB` (subscription), `MSG` (message binding). Explicitly out of scope: `Validator`, the reader/WebSocket/HTTP internals. That keeps the catalog to things a user can name and act on, which is the only reason a stable id earns its keep.

---

## 3. Question 2 — should these be recoded into `JetStreamException`?

**Mostly no.** The counter-argument raised on 2026-08-31 is right and it resolves most of the list: these are **client API exceptions** — the client detecting misuse of its own API — not JetStream API exceptions, which mean the *server* returned an error or the transport failed. `JetStreamException` and its subtypes have a crisp meaning today ("something happened out there": `JetStreamApiException`, `JetStreamStatusException`, `JetStreamTimeoutException`, `JetStreamProtocolException`). Folding caller-misuse errors into that hierarchy would blur it, and would force `try`/`catch` on conditions whose only correct response is to change the calling code. `EXCEPTIONS_AUDIT` §1-3 spent its effort getting *lies* out of that hierarchy; putting a different category in would be the same mistake pointed the other way.

The construct's own javadoc states the rule: *"Each constant is a distinct error condition the client detects itself, before or instead of a server round trip."* Judged against that rule, here is where each of the 12 actually lands.

### 3a. Correct as-is — client API misuse, unchecked (9 of 12)

| Constant | Why unchecked is right |
|---|---|
| `JsConsumerNameDurableMismatch` | caller supplied two conflicting values |
| `JsSubDispatcherNoHandlerCantReceiveMessages` | caller wired a dispatcher that cannot deliver |
| `JsSubNoMatchingStreamForSubject` | subscribing to a subject no stream carries is a setup mistake |
| `OsCantLinkToLink` | caller passed a link as the link target |
| `OsLinkNotAllowOnPut` | caller put a link in the metadata |
| `OsObjectNotFound` | asked for an object that is not there |
| `OsObjectAlreadyExists` | name collision on create/rename |
| `OsGetLinkToBucket` | called `get` on a bucket link |
| `OsObjectIsDeleted` *(the `addLink:414` use)* | caller passed a deleted `ObjectInfo` |

*(This table settles checked vs unchecked only. Which unchecked type each raises is §3e — most of these are `IllegalStateException`, not `IllegalArgumentException`.)*

`OsObjectNotFound` / `OsObjectAlreadyExists` / `OsGetLinkToBucket` / `JsSubNoMatchingStreamForSubject` are the debatable end of this group — each is discovered by a server lookup and each is racy, since another client can change the state between your check and your call. An earlier draft of this audit argued they should therefore be checked. On reflection that is not strong enough to justify checked exceptions: raciness makes a condition *unpreventable*, not *not-a-usage-error*, and `NoSuchElementException` / `IllegalStateException` are the conventional Java answers for exactly this shape. Leave them.

### 3b. The residue that fits neither box — RESOLVED 2026-08-31

`OsGetDigestMismatch`, `OsGetChunksMismatch`, `OsGetSizeMismatch` (`ObjectStore:267,292,293,295`) are the one place the "client API exception" reading does not hold. They fire **after** a successful download, when the reassembled bytes do not match the stored metadata. The caller's arguments were correct, the API was used correctly, and no change to the calling code prevents them. They are data-integrity failures — truncation or corruption — and they were reported as `IllegalArgumentException`, which is the one label that is definitely wrong.

They do not belong in `JetStreamException` either: nothing out there reported an error. So the fix was the minimal one — **all three flipped to `KIND_ILLEGAL_STATE`**, one argument each, no new type, no signature churn, no javadoc change (none of the affected methods documented `@throws IllegalArgumentException`). The ids and messages are unchanged, so `OS-90205` / `OS-90206` / `OS-90207` still mean what they meant.

The fuller option — an unchecked `ObjectStoreIntegrityException` so a caller can retry a corrupted download specifically — was **not** taken. It is more API surface for three conditions and only pays off if retry-on-corruption is a real use case. Revisit if one shows up.

### 3c. `OsObjectIsDeleted` was doing two jobs — RESOLVED 2026-08-31

- `ObjectStore:348`, in `updateMeta` — after `getInfo(objectName, true)`, the *stored* object is deleted. State.
- `ObjectStore:414`, in `addLink` — the caller passed an `ObjectInfo` that is already deleted. Argument.

One catalog entry, one hardcoded kind, two different meanings. Inherited from v2, which throws it from the same two places (`NatsObjectStore:306`, `:365`).

Split as follows:

| Constant | Id | Kind | Site |
|---|---|---|---|
| `OsObjectIsDeleted` | `OS-90202` *(unchanged)* | `KIND_ILLEGAL_STATE` *(was ARGUMENT)* | `ObjectStore:348` `updateMeta` |
| `OsCantLinkToDeletedObject` | `OS-90210` *(new)* | `KIND_ILLEGAL_ARGUMENT` | `ObjectStore:414` `addLink` |

`OS-90202` keeps its id and message because the stored-state case is the natural reading of "The object is deleted."; the new constant is named to parallel the `OsCantLinkToLink` sitting beside it in `addLink`. `ObjectStoreClientError` gained the four-argument constructor it needed (it only had the three-argument one), with javadoc matching `JetStreamClientError`.

**Carry-over note:** `tdb/io/synadia/client/impl/ObjectStoreTests.java:371` still asserts `OsObjectIsDeleted` for the `addLink` case. It is a verbatim v2 staging copy and was left untouched; it needs `OsCantLinkToDeletedObject` when that file is ported. Line 126 (`updateMeta`) is still correct.

### 3e. The second axis — argument or state? RESOLVED 2026-08-31

Question 2 settled *checked vs unchecked* (all unchecked, §3a). It left the other axis untouched: given unchecked, is it `IllegalArgumentException` or `IllegalStateException`? Raised 2026-08-31 — *"maybe these should all be IllegalStateExceptions"* — and it turns out to be the same test that decided the digest/chunks/size trio, applied consistently:

> **Is the argument wrong, or is the receiver's state wrong?** If the value the caller passed is malformed or the wrong kind of thing, that is `IllegalArgumentException`. If the value is perfectly valid on its own and the call is only invalid because of what the receiver already holds, that is `IllegalStateException`.

`new PushConsumerCreator().durable("name").name("different")` is the clearest case. `"different"` is a *valid consumer name*; it is rejected solely because `durable("name")` was called first. The creator's state rejects it, not the argument. Same for `OsObjectAlreadyExists`: the name is well formed, the bucket already holds it.

Applied to all 13 constants:

| Constant | Kind | Why |
|---|---|---|
| `JsSubNoMatchingStreamForSubject` | STATE | no stream carries the subject |
| `JsSubDispatcherNoHandlerCantReceiveMessages` | STATE | the dispatcher as configured cannot deliver |
| ~~`JsConsumerNameDurableMismatch`~~ | STATE, then **removed** | both values valid; prior state conflicts — now a plain `IllegalStateException` from `JsValidator`, see §5 |
| `OsObjectNotFound` | STATE *(flipped)* | name is well formed, bucket lacks it |
| `OsObjectIsDeleted` | STATE *(flipped earlier)* | the stored object is deleted |
| `OsObjectAlreadyExists` | STATE *(flipped)* | name is well formed, bucket already holds it |
| `OsGetDigestMismatch` | STATE *(flipped earlier)* | download completed, bytes disagree |
| `OsGetChunksMismatch` | STATE *(flipped earlier)* | download completed, bytes disagree |
| `OsGetSizeMismatch` | STATE *(flipped earlier)* | download completed, bytes disagree |
| `OsGetLinkToBucket` | STATE *(flipped)* | the stored object decides this, not the argument |
| `OsCantLinkToLink` | **ARGUMENT** | the `toInfo` passed is itself a link |
| `OsLinkNotAllowOnPut` | **ARGUMENT** | the `meta` passed contains a link |
| `OsCantLinkToDeletedObject` | **ARGUMENT** | the `toInfo` passed is itself deleted |

Ten STATE, three ARGUMENT. The three that stay share one property, which is a good sign the rule is real rather than fitted: **the caller handed over an object or metadata whose own content is wrong**, and all three are in the link/put operations.

Blast radius of the flips was small and is done: `ConsumerCreator._durable` / `._name` javadoc split into `@throws IllegalArgumentException` (malformed) plus `@throws IllegalStateException` (mismatch); `JetStreamSubscribeTests.testJetStreamSubscribeErrors` now asserts `IllegalStateException`. `ObjectStore` documents neither exception anywhere, so nothing to update there. `:jetstream:test` green.

Two incidental fixes made in the same pass: the two name/durable assertions in that test were **identical copy-paste**, so the second now sets `name` then `durable` and actually exercises the `_durable` path; and a stale `// JsSubDispatcherWoHandlerCantReceiveMessages` comment was updated to the renamed constant.

### 3d. What is worth keeping either way

The stable id, the single catalog, and `matches()` are orthogonal to the checked/unchecked question and are the parts that are working. Nothing in §3 argues for dismantling the construct — only for two constants changing kind and one splitting in two.

One thing to fix while the class is open: **`int kind` with two `KIND_*` constants should be an enum.** It is a pre-enum idiom on a public class, and any change here touches it anyway.

---

## 4. Open decisions

- **D1** — adopt the construct in core for the §2c lifecycle conditions? (Recommended: yes, ~14 constants, `CoreClientError`.)
- **D2 — DONE 2026-08-31.** `OsGetDigestMismatch` / `OsGetChunksMismatch` / `OsGetSizeMismatch` flipped to `KIND_ILLEGAL_STATE`. Dedicated type deferred (§3b).
- **D2a — DONE 2026-08-31.** Kind reviewed across all 13 constants against the argument-vs-state test; `JsConsumerNameDurableMismatch`, `OsObjectNotFound`, `OsObjectAlreadyExists` and `OsGetLinkToBucket` also flipped to `KIND_ILLEGAL_STATE`. Final split is 10 STATE / 3 ARGUMENT (§3e).
- **D3 — DONE 2026-08-31.** `OsObjectIsDeleted` split; `OsCantLinkToDeletedObject` (`OS-90210`) added for the `addLink` argument case (§3c).
- **D4** — `int kind` → enum. Still open, and now more attractive: with the defaulting constructor gone, every declaration names the kind explicitly, so an enum would be read at every one of the 12 sites.
- **D7 — DONE 2026-08-31.** Defaulting three-argument constructors removed; kind is explicit at every declaration (§6).
- **D8 — DONE 2026-08-31, superseded 2026-09-01.** Construction is closed to outside callers. The constructors were briefly `protected`; with the subclasses gone (§7) there is one package-private constructor on `ClientError`.
- **D10 — DONE 2026-09-01.** `ClientError` is no longer subclassed; the two domain classes are constant holders (§7). Dead `validateNotSupplied` deleted, `assertClientError` widened.
- **D11 — DONE 2026-09-01.** `ClientError`'s constructor is `public` so the holders survive a JPMS/OSGi package split, with an explicit "internal use, API not guaranteed" note on the class and the constructor (§7). Supersedes the closed-catalog part of D8.
- **D9 — DONE 2026-08-31.** `JsConsumerNameDurableMismatch` removed in favour of a label-parameterized `IllegalStateException` from `JsValidator` (§5).
- **D5** — add `instance(Throwable cause)`? Only matters if the auth-handler messages come into the catalog.
- **D6** — sequencing against `PLAN_CORE_JETSTREAM_BOUNDARY.md`: all three classes sit in the split `io.synadia.client.utils` package. If that plan is going to move them, this work should follow it rather than lead.

**Not recommended:** moving any of these into the `JetStreamException` hierarchy. See §3.

---

## 5. Where the line falls — what belongs in the catalog at all

Asked 2026-08-31, from both directions: should `JsConsumerNameDurableMismatch` drop to a plain `IllegalArgumentException` (it is now raised at `ConsumerCreator` setter time, not at subscribe time, so it looks like ordinary validation), or should the rest of the `ConsumerCreator` / `StreamCreator` validation be promoted *into* `JetStreamClientError`?

**Neither. There is a structural line between the two, and the current state already sits on the right side of it.**

`Validator` builds every message from a **field label**, and several interpolate the offending value:

```java
throw new IllegalArgumentException(label + " cannot be null or empty.");
throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '.' or '>' [" + s + "]");
```

One code path serves every field that uses it — `validateDurable` is `validatePrintableExceptWildDotGtSlashes(s, "Durable", required)`, `validateConsumerName` the same with `"Name"`, `validateStreamName` with `"Stream"`. A `ClientError` constant, by contrast, carries a **fixed** message.

That difference decides both questions:

- **Promoting the creator validation is the worse direction.** `ConsumerCreator` and `StreamCreator` reach `validateSubjectTermStrict` (5 call sites), `validatePrintableExceptWildDotGtSlashes` (3 labels), the duration validators (6 call sites) and the numeric ones. Each of those validators has several distinct failure messages, and each is multiplied by the labels that use it — so a constant per condition is on the order of 40-60 entries. Worse, a fixed message either bakes in one field name (combinatorial) or drops it (a strictly less useful message), and the value interpolation cannot be expressed at all except by appending through `instance(String)`, which changes the text shape. Do not do this.
- **Dropping `JsConsumerNameDurableMismatch` is defensible but slightly wrong.** It is not label-parameterized field validation. It is a **cross-field invariant** — name and durable disagree — with a fixed message and no field label. That is exactly the shape the other two `JetStreamClientError` constants have (`JsSubNoMatchingStreamForSubject`, `JsSubDispatcherNoHandlerCantReceiveMessages`), and it is why the catalog has three entries rather than sixty.

**The rule, stated:** a fixed-message, condition-specific error is a catalog entry; label-parameterized field validation stays in `Validator`. All three surviving `JetStreamClientError` constants satisfy it; none of the creator field validations do.

**And the same line predicts the exception kind** (§3e), which is the strongest evidence it is a real seam and not a rationalization. Label-parameterized field validation rejects *the value you passed* — `IllegalArgumentException`. A fixed-message cross-field invariant rejects *the combination given what was already set* — `IllegalStateException`. `JsConsumerNameDurableMismatch` was the only one of the three catalog constants still on `KIND_ILLEGAL_ARGUMENT`; it has been flipped, so all three now agree.

Two honest costs of keeping it:

- At `ConsumerCreator._durable` / `._name` the two validations sit side by side and read differently — `Name must be in the printable ASCII range …` from `validateConsumerName`, `[CON-90302] Name must match durable if both are supplied.` from the catalog. Cosmetic, and the price of the id.
- `JsValidator.validateMustMatchIfBothSupplied(String, String, JetStreamClientError)` takes the error as a parameter because in v2 it served **five** different mismatch constants (`JsSoDeliverGroupMismatch`, `JsSoDeliverSubjectMismatch`, `JsSubQueueDeliverGroupMismatch`, and the `SubscribeOptions` builder-vs-config pair). In v3 only one caller condition survives, so the parameter is now a generalization with a single instantiation. Harmless, but it is the reason the method looks over-built.

Against dropping it there is also real test coupling: `JetStreamSubscribeTests:96,102` assert through `JsConsumerNameDurableMismatch.matches(iae)`. Four of the six `matches()` calls in the repo are on the constants this question covers.

**Resolved (Scott, 2026-08-31): removed from the catalog, replaced with a plain validation.** My recommendation above was to keep it; Scott's call went the other way, and the reasoning is that once the check moved from subscribe time to `ConsumerCreator` setter time it reads as validation and should look like the validation beside it.

What that means in practice — the *cross-field invariant* argument above did not survive as a reason to keep a catalog entry, but it did survive as a reason for the exception kind:

- `JsConsumerNameDurableMismatch` (`CON-90302`) deleted; the `CON` group went with it, so `JetStreamClientError` is down to two `SUB` constants.
- `JsValidator.validateMustMatchIfBothSupplied` swapped its `JetStreamClientError err` parameter for a `String label1, String label2` pair and now throws `new IllegalStateException(label1 + " must match " + label2 + " if both are supplied.")` — the label-parameterized shape every other validator in `Validator` uses. Call sites read `validateMustMatchIfBothSupplied(name, durable, "Name", "Durable")`.
- Still `IllegalStateException`, not `IllegalArgumentException` — dropping the catalog entry does not change what kind of failure it is (§3e).
- The message loses its `[CON-90302]` prefix and gains a capital D: `Name must match Durable if both are supplied.`
- That parameter was the last thing keeping `validateMustMatchIfBothSupplied` generic over the catalog; in v2 it served **five** different constants, so its shape was a v2 leftover.
- `JetStreamSubscribeTests` now asserts the message directly instead of `matches()`.

---

## 6. Constructing catalog entries — closed, and always explicit (2026-08-31)

Two changes to how entries are declared, both at Scott's request.

**No defaulting constructor.** `ClientError(String, int, String)` and the matching three-argument constructors on both subclasses defaulted the kind to `KIND_ILLEGAL_ARGUMENT`. All three are gone, so every constant now names its kind at the declaration. Given §3e turned on exactly that field being wrong on four constants, having it default silently was the thing that let them drift.

**No public construction.** The surviving four-argument constructors on `JetStreamClientError` and `ObjectStoreClientError` are `protected`, so the catalogs are closed to callers outside `io.synadia.client.utils`. `ClientError`'s own constructor was already `protected`.

Note this is *closed to callers*, not sealed: `protected` still permits a subclass in another package to add entries, and permits same-package construction — which is how `JsValidatorTests.testNatsJetStreamClientError` still builds a throwaway `TEST-999999` entry. If the intent is to make it genuinely impossible, the constructors go `private` and the classes go `final`; that costs a rewrite of that one test to use a real constant instead.

Final state of the catalog: **12 constants** — 2 in `JetStreamClientError` (both `SUB`, both STATE), 10 in `ObjectStoreClientError` (7 STATE, 3 ARGUMENT).

---

## 7. The subclasses are gone — holders only (2026-09-01)

`JetStreamClientError` and `ObjectStoreClientError` no longer extend `ClientError`. Each is now an uninstantiable holder declaring `public static final ClientError` constants. `ClientError` is the only type.

**Why the subclass earned nothing.** Stripped of comments and constants, each subclass was a private group string plus a constructor delegating to `super` — no overrides, no state, and no `instanceof` on any of the three types anywhere in the repo. Only three places referenced the subtypes at all:

- `JsValidator.validateNotSupplied(String, JetStreamClientError)` — the only production signature constraining to a subtype, and it had **zero callers**. Deleted.
- `JetStreamTestBase.assertClientError(JetStreamClientError, Executable)` — **already wrong.** Of its 17 callers (all in `tdb/`), 16 pass `Os*` constants, which were `ObjectStoreClientError` and would not have compiled against a `JetStreamClientError` parameter. The subtype distinction was actively blocking the OS port. Widened to `ClientError`.
- `JsValidatorTests` — a throwaway `TEST-999999` construction, now `new ClientError(...)`; the test was renamed `testNatsJetStreamClientError` → `testClientError`.

`ObjectStoreClientError` was already namespace-only in practice: it is consumed exclusively as `import static ...ObjectStoreClientError.*` and its type name never appeared in a signature.

One inherited-constant smell went with it — `JetStreamTestBase` imported `KIND_ILLEGAL_ARGUMENT` / `KIND_ILLEGAL_STATE` *through* `JetStreamClientError`, which only worked by inheritance. They now come from `ClientError`, where they are declared.

**On the constructor modifier — now `public`, deliberately (2026-09-01).** Three options were on the table and only one survives a package split:

| Modifier | Works today (split package) | Survives O3 separating the packages |
|---|---|---|
| package-private | yes | no |
| `protected` | yes | **no** — the holders are not subclasses, so `protected` reaches them only through its same-package half |
| `public` | yes | yes |

`protected` was the tempting middle option and it buys nothing here: with the subclasses gone there is no subclass relationship for it to grant access through, so it degrades to exactly package-private. That left a choice between staying closed and staying portable, and portability won — the constructor is **`public`**, so the per-domain catalogs keep compiling if `OSGi_JPMS_TODO.md` O3 ever gives core and jetstream distinct internal packages. This reverses the "closed catalog" intent of §6; the compensation is documentation rather than access control.

**`ClientError` is now marked internal in its javadoc.** The class comment says plainly that it is intended for internal library use, that its API is not guaranteed and may change without notice, that applications should catch the `IllegalArgumentException` / `IllegalStateException` that `instance()` produces rather than construct or subclass the type, and that the constructor is public only so the catalogs can live in other modules. The constructor carries a shorter version of the same note. That is the whole of what stops a user declaring their own entries now — worth knowing if this ever gets revisited.

Static imports at `ObjectStore:18` and `JetStreamSubscribeTests:15` were unaffected — a static import of a field does not care about its declared type.
