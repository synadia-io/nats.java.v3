# Plan: `IllegalArgumentException` documentation & cleanup

## Decision (settled)

Every invalid argument — **including null** — throws `IllegalArgumentException`. One exception type for "you passed a bad argument" is clearer than two, and IAE names the problem better than `NullPointerException`. (A null→NPE split was prototyped and rolled back; don't re-propose it.) `IllegalArgumentException` is unchecked, so it is **never** listed in a `throws` clause; it is declared to callers only via a Javadoc `@throws` tag (*Effective Java* Items 72/74).

**The rule:** on every public/protected builder, creator, factory, or argument-validating method that can throw IAE, add `@throws IllegalArgumentException if <condition, in caller terms>`. Parse/protocol throwers (Group C) throw on bad wire data rather than caller input — a separate, lower-priority contract.

**Documentation style (decided):**
- **Class-level statement — class-level only, no `package-info`.** Each validating class gets one line in its class Javadoc, e.g. *"Setter/builder methods validate their arguments and throw {@link IllegalArgumentException} for a null or otherwise invalid value; see each method for specifics."* This is the general policy statement; it does not replace the per-method tags.
- **Per-method `@throws` enumerates every reason.** For each `ADD` method, the tag must state **all** the ways it can throw — one reason or several. Where there are multiple, combine them into the single `@throws` with "… if A, or B, or C" (Java allows only one `@throws` per exception type). The reason(s) column is the source list; the *Multi-reason methods* section below is the checklist of the ones with 2+ reasons so none is dropped.
- A method-level tag does not repeat the class blurb — it names the concrete condition(s). The class statement is the umbrella; the method tag is the precision. (This is why we do both, not just the class line: only a method `@throws` populates that method's "Throws:" section with its specific reasons.)

## Work tracker

| # | Work item | Status |
|---|---|---|
| 1 | Remove the `throws IllegalArgumentException` clause from `OptionsBuilder(Properties)` (`OptionsBuilder.java:145`) — the only signature site | ✅ done |
| 2 | `@throws` sweep — Group A (builders / creators / factories / options) | ✅ done (class statement + per-method `@throws`, all reasons; compiles clean) |
| 2a | `@throws` sweep — `StreamCreator` (missed by the original audit) | ✅ done (class statement + `@throws` on ctor, `subjects` ×2, `maxAge` ×2, `replicas`, `duplicateWindow` ×2, `subjectDeleteMarkerTtl` ×2) |
| 3 | `@throws` sweep — Group B (connection / JetStream / context API) | ✅ done (60 tags: `NatsConnection` 22, `Dispatcher` interface 8, `JetStream` 20, `BaseConsumerContext` interface 6, `JetStreamPullSubscription` 4; documented on the public interface where one exists; IAE added alongside existing `@throws InterruptedException`/`IOException`; compiles clean) |
| 4 | Group C (parse/protocol throwers) | ✅ decided — **leave as-is, undocumented**. They throw on bad wire data (a protocol invariant), not a caller precondition, so no `@throws`. |

**Plan complete.** All items done: signature clause removed (1); Group A incl. `StreamCreator` documented (2, 2a); Group B documented (3); Group C intentionally left undocumented (4). Everything is javadoc-only and compiles clean.

## Scope

- ~80 public/protected methods can throw IAE — almost all **undocumented** today (only the rows marked `✓` already have the tag).
- **Signature cleanup was one site** (done): `OptionsBuilder(Properties props)` no longer declares `throws IllegalArgumentException`; its `@throws` javadoc is kept.
- IAE also arrives via `NumberFormatException` (a subclass) in the `OptionsProperties` numeric parsers.

Legend — **nulls?**: `yes` = rejects a null arg with IAE · `both` = one branch fires on null AND empty/invalid ("null or empty") · `no→NPE` = a null arg incidentally NPEs via a dereference, so the `@throws` should describe the *non-null* failure (e.g. "url malformed"), not null · `no` = no null path. **action**: `ADD` = add `@throws` · `✓` = already documented.

---

## Group A — Builders / creators / factories / options (primary `@throws` targets)

**What this group is:** the configuration-time API a user calls directly — `OptionsBuilder` setters, the `*Creator` builders (`ConsumerCreator`, `PullRequestOptions`, `PurgeOptions`, `StreamCreator`), the service/endpoint builders (`ServiceBuilder`, `Endpoint`, `Group`, `ServiceEndpoint`), `MessageTtl`, and the static property parsers/factories (`OptionsProperties`). These are the highest-value methods to document: a user reaches for them first, and the IAE is a precondition entirely within their control.

**The work:** add `@throws IllegalArgumentException if <condition>` to every row marked `ADD` (~25 methods). Javadoc only — no behavior change. Phrasing comes straight from the *reason(s)* column ("if the subject is not a valid subject", "if replicas is not 1–5", etc.). Skip the four already-documented `OptionsBuilder` rows (`✓`). Special cases: the `OptionsProperties` parser rows throw on an unparseable/instantiable property value (the IAE arrives as a `NumberFormatException` subclass — say "if the property value is not a valid …"); `MessageTtl#custom` and the multi-reason builders below need combined conditions.

| Class#method | reason(s) — multiple = needs care | nulls? | via | @throws? | action |
|---|---|---|---|---|---|
| `OptionsBuilder(Properties)` | properties object null | yes | direct(`properties()`) | ✓ (added) | ✓ |
| `OptionsBuilder#properties(Properties)` | properties object null | yes | direct | ✓ | ✓ |
| `OptionsBuilder#server(String)` | url malformed | no→NPE (null→`.trim()`) | direct(`servers()`) | ✓ | ✓ |
| `OptionsBuilder#servers(String[])` | a url malformed | no→NPE (null array) | direct | ✓ | ✓ |
| `OptionsBuilder(Options)` | options null | yes | direct | no | ADD |
| `Nats#connectAsynchronously(Options,boolean)` | no ConnectionListener in options | no→NPE (null options) | direct | ✓ | ✓ |
| `OptionsProperties#millisProperty(...)` | value neither millis nor ISO-8601 duration | no | direct + NFE | no | ADD |
| `OptionsProperties#intProperty / intGtEqZeroProperty(...)` | value not a parseable int | no | NFE (IAE subclass) | no | ADD |
| `OptionsProperties#longProperty / longGtEqZeroProperty(...)` | value not a parseable long | no | NFE (IAE subclass) | no | ADD |
| `OptionsProperties#createInstanceOf(String)` / `classnameProperty(...)` | class missing / no no-arg ctor / instantiation failed | no | direct | no | ADD |
| `MessageTtl#seconds(int)` | value < 1 | no | direct | no | ADD |
| `MessageTtl#custom(String)` | custom null or empty | **both** | direct | no | ADD |
| `Headers#add/put(String,String...)` · `add/put(String,Collection)` · `put(Map)` (5) | **3 reasons**: key empty; key has invalid char; value has invalid char | no→NPE (null key→`isEmpty()`; null values/map return early) | direct(`validateKeyAndCollect`) | ✓ (4 of 5; `put(Map)`=no) | ADD on `put(Map)` |
| `ConsumerCreator#filterSubjects(String...)` · `filterSubjects(List)` | invalid subject term (wildcard/whitespace/dots) | no | Validator.validateSubjectTermStrict | no | ADD |
| `ConsumerCreator#idleHeartbeat(Duration)` · `idleHeartbeat(long)` (+ `_idleHeartbeat`) | idle heartbeat below 100ms min | no | direct | no | ADD |
| `ConsumerCreator#_durable(String)` | **2 reasons**: not printable / has `*.>\/`; name/durable mismatch | no | JsValidator.validateDurable; validateMustMatchIfBothSupplied | no | ADD |
| `ConsumerCreator#_name(String)` | **2 reasons**: not printable / has `*.>\/`; name/durable mismatch | no | JsValidator.validateConsumerName; validateMustMatchIfBothSupplied | no | ADD |
| `ConsumerCreator#_flowControl(Duration)` | **2 reasons**: idleHeartbeat null w/ flow control; idleHeartbeat below min | yes (idleHeartbeat) | direct | no | ADD |
| `ConsumerCreator#_flowControl(long)` | **2 reasons**: idleHeartbeat unset/≤0 w/ flow control; below min | no | direct | no | ADD |
| `ConsumerCreator#_numReplicas(int)` | replicas not 1..5 (when ≥1) | no | JsValidator.validateNumberOfReplicas | no | ADD |
| `ConsumerCreator#_backoff(Duration...)` · `_backoff(long...)` | a backoff value is negative | no | direct | no | ADD |
| `PullRequestOptions.Builder#build()` | **4 reasons**: batch ≤0; priority not 0..9; idleHB without expiration; idleHB > ½ expiration | no | Validator.validateGtZero; direct | no | ADD |
| `PurgeOptions.Builder#subject(String)` | subject not strict-valid | no | Validator.validateSubjectStrict | no | ADD |
| `PurgeOptions.Builder#build()` | seq and keep both >0 (mutually exclusive) | no | direct | no | ADD |
| `ServiceBuilder#name(String)` | null/empty/non-restricted-term name | yes | Validator.validateIsRestrictedTerm | no | ADD |
| `ServiceBuilder#version(String)` | null/empty/invalid SemVer | yes | Validator.validateSemVer | no | ADD |
| `ServiceBuilder#build()` | **3 reasons**: conn null; name null/empty; version null/empty | yes | Validator.required | no | ADD |
| `Endpoint(...)` (5 public ctors) | **3 reasons**: blank/invalid name; invalid subject; blank/invalid queueGroup (5-arg only) | yes (name); both (queueGroup, 5-arg) | Validator.validateIsRestrictedTerm/validateSubjectTermStrict | no | ADD |
| `Endpoint.Builder#build()` | blank/invalid name; invalid subject; blank/invalid queueGroup | both | (via `new Endpoint`) | no | ADD |
| `Group(String)` | **2 reasons**: null/empty name; name invalid strict-term (incl. `>`) | yes | direct + Validator.validateSubjectTermStrict | no | ADD |
| `ServiceEndpoint.Builder#build()` | **reasons**: handler null; + Endpoint name/subject/queueGroup invalid | both (handler; name/queueGroup) | Validator.required + Endpoint validators | no | ADD |
| `ServiceResponse(String,JsonValue)` (protected, parse) | **5 reasons**: type null/empty; type mismatch; id/name/version null/empty | yes | direct + Validator.required | no | ADD |
| **`StreamCreator(String name)`** *(audit gap — added after the fact)* | stream name null/empty/invalid | yes | JsValidator.validateStreamName | no | ADD (item 2a) |
| **`StreamCreator#subjects(...)`** | a subject not strict-valid | no | Validator.validateSubjectTermStrict | no | ADD (item 2a) |
| **`StreamCreator#numReplicas(int)`** | replicas not 1–5 | no | JsValidator.validateNumberOfReplicas | no | ADD (item 2a) |
| **`StreamCreator#maxAge` / `duplicateWindow` / `subjectDeleteMarkerTtl`** | negative / below-minimum duration | no | Js/Validator duration validators | no | ADD (item 2a) |

> Everything above `StreamCreator` is **done** (class statement + per-method `@throws`, all reasons combined; `./gradlew compileJava` clean). `StreamCreator` was missing from the original audit (its source wasn't scanned) — it's the one remaining Group A item.

---

## Group B — Connection / JetStream / context public API (validates args)

**What this group is:** the runtime messaging API — `publish` / `subscribe` / `request` / `requestAsync` on `NatsConnection` and `NatsDispatcher`, plus JetStream `publish` / `subscribe` / context-getters / `NatsConsumerContext` consume methods and `JetStreamPullSubscription` pulls. They validate subjects, queue names, stream/consumer names, handlers, and message/options objects up front, before doing I/O.

**The work:** add `@throws` to every `ADD` row. Two things make this group different from A: (1) most are **overload families** (`publish` ×6, the `request`/`requestAsync` families, the JS push/pull `subscribe` overloads) — document each overload with the same condition text so the contract is visible wherever a user lands. (2) Several of these methods **already declare `@throws InterruptedException`** (the `request` family) — add the IAE tag alongside, don't replace it. Conditions again come from the *reason(s)* column; combine on the multi-reason rows. Javadoc only.

| Class#method | reason(s) | nulls? | via | @throws? | action |
|---|---|---|---|---|---|
| `NatsConnection#publish(...)` (6 overloads) | invalid subject; (some) invalid replyTo chars; (some) headers unsupported by server; (Message overloads) message null | both (subject; message) | Validator.validateSubject*/validateReplyTo/validateNotNull + direct | no | ADD (all 6) |
| `NatsConnection#subscribe(String)` | invalid subject (null/empty/bad chars) | yes | Validator.validateSubject* | no | ADD |
| `NatsConnection#subscribe(String,String)` | **2 reasons**: invalid subject; invalid queueName | yes | Validator.validateSubject* + validateQueueName | no | ADD |
| `NatsConnection#request / requestAsync(String...)` (family) | invalid subject | yes | Validator.validateSubject* | no (only `@throws InterruptedException`) | ADD |
| `NatsConnection#request / requestAsync(Message...)` (family) | **2 reasons**: message null; invalid subject | both | Validator.validateNotNull + validateSubject* | no | ADD |
| `NatsConnection#closeDispatcher(Dispatcher)` | **2 reasons**: not a NatsDispatcher; already closed | no | direct | no | ADD |
| `NatsDispatcher#subscribe(String)` | invalid subject | yes | Validator.validateSubject* | no | ADD |
| `NatsDispatcher#subscribe(String,MessageHandler)` | **2 reasons**: invalid subject; handler null | both | Validator.validateSubject* + Validator.required | no | ADD |
| `NatsDispatcher#subscribe(String,String)` | **2 reasons**: invalid subject; invalid queueName | yes | Validator.validateSubject* + validateQueueName | no | ADD |
| `NatsDispatcher#subscribe(String,String,MessageHandler)` | **3 reasons**: invalid subject; invalid queueName; handler null | both | Validator + direct | no | ADD |
| `NatsDispatcher#unsubscribe(String,int)` | subject null or empty | yes | direct | no | ADD |
| `NatsDispatcher#unsubscribe(Subscription,int)` | not a NatsSubscription impl | no | direct | no | ADD |
| `JetStream#publish/publishAsync(Message[,PublishOptions])` (4) | message null | yes | Validator.validateNotNull | no | ADD |
| `JetStream#push/pullSubscribe(String subject[,…])` (subject overloads) | subject invalid/empty | yes | Validator.validateSubject | no | ADD |
| `JetStream#push/pullSubscribe(String stream,*Creator[,…])` + `createConsumer(...)` (stream overloads) | stream name invalid/empty/null | yes | JsValidator.validateStreamName | no | ADD |
| `JetStream#getStreamContext(String)` | stream name invalid/empty/null | yes | JsValidator.validateStreamName | no | ADD |
| `JetStream#getConsumerContext(String,String)` | **2 reasons**: stream name invalid; consumer name null/empty | both | JsValidator.validateStreamName + Validator.required | no | ADD |
| `NatsConsumerContext#next(long)` | maxWait below MIN_EXPIRES_MILLS (1000ms) | no | direct | no | ADD |
| `NatsConsumerContext#fetch(FetchConsumeOptions)` | fetchConsumeOptions null | yes | Validator.required | no | ADD |
| `NatsConsumerContext#iterate(ConsumeOptions)` | consumeOptions null | yes | Validator.required | no | ADD |
| `NatsConsumerContext#consume(ConsumeOptions,…,MessageHandler)` | **2 reasons**: consumeOptions null; handler null | both | Validator.required | no | ADD |
| `JetStreamPullSubscription#pullNoWait/pullExpiresIn/fetch/iterate(int,long)` | expires/maxWait ≤ 0 | no | direct | no | ADD |

---

## Group C — Parse / protocol / lazy-getter throwers (malformed wire data, not user input)

**What this group is:** internal parse/protocol constructors and lazy getters that throw when *server/wire* data is malformed — bad header bytes, non-JSON server INFO, a reply subject that isn't a JetStream ack/FC subject. The IAE reflects a **protocol violation, not a caller precondition**; the "argument" is data the library itself produced or received, not something the user passed.

**The work is a decision first (item 4), then maybe documentation.** Because these aren't user-argument validation, the value of an `@throws` is lower and the wording is different — it would read "if the serialized headers are malformed" / "if the server INFO is not valid JSON", not "if X is invalid". Options: (a) leave undocumented — they're effectively internal invariants (default lean); or (b) document them as protocol-parse preconditions for the few that are reachable on public types (e.g. `ServerInfo`, `JetStreamMetaData` getters). Decide as a group, then apply if (b).

| Class#method | reason | nulls? | @throws? |
|---|---|---|---|
| `Status(Token,Token)` | code token missing/empty; not parseable int | both | no |
| `ServerInfo(String)` | json null/too-short/not-JSON; parse fail | yes | no |
| `Token(...)` (2 ctors) · `Token#mustBe(TokenType)` | malformed header line / token type mismatch | no | no |
| `IncomingHeadersProcessor(byte[])` | serialized header null/empty; bad version; malformed composition | yes | no |
| `JetStreamMetaData(NatsMessage)` + 11 lazy getters (`getMetaType/getDomain/getStream/getConsumer/deliveredCount/streamSequence/consumerSequence/pendingCount/timestamp/toString`) | not a JS message / reply subject not a valid JS ACK/FC subject | no | no |
| `AbstractListReader#nextJson(String)` | filtering not supported (filterFieldName unset) | no | no |

---

## Multi-reason methods (write the `@throws` to cover all reasons)

Java allows only one `@throws` per exception type, so combine the conditions ("… if X, or Y, or Z"):

- 4 reasons: `PullRequestOptions.Builder#build()` (batch ≤0 · priority range · idleHB-without-expiration · idleHB > ½ expiration).
- 5 reasons: `ServiceResponse(String,JsonValue)` (type/id/name/version null-or-empty · type mismatch).
- 3 reasons: `Headers#add/put` (key empty · key bad char · value bad char); `ServiceBuilder#build()` (conn · name · version); `NatsDispatcher#subscribe(String,String,MessageHandler)`; `Endpoint` ctors/builder (name · subject · queueGroup).
- 2 reasons: `ConsumerCreator#_durable` / `_name` (format · name/durable mismatch); `ConsumerCreator#_flowControl` (idleHB null/unset · idleHB below min); `Group(String)`; `closeDispatcher`; `getConsumerContext`; `NatsConnection`/`NatsDispatcher` subscribe(String,String) and the Message request overloads; `Status`. (`PurgeOptions#build` is one reason — mutually-exclusive fields.)

> `Headers#put(Map)` is the one `add/put` overload **missing** the `@throws` its 4 siblings already have.

---

## Sequencing

1. ✅ **Signature clause removed** — `OptionsBuilder.java:145`.
2. **`@throws` sweep, Group A then Group B**, per class:
   - add the **class-level statement** to the class Javadoc (class-level only, no `package-info`);
   - add a per-method `@throws` to every `ADD` row, **enumerating all reasons** for that method. For `via Validator.*` rows, phrase the condition from the "reason(s)" column; for `no→NPE` rows, describe the real validated failure (not null); combine reasons on the multi-reason methods above.
3. **Group C decision (item 4)** — document as protocol/parse preconditions or leave undocumented; apply if yes.

Notes for the sweep: nulls stay IAE, so a method's `@throws` can simply say the arg "is null or invalid" where applicable — no separate NPE contract to describe. `@throws` documents a precondition, not a `throws` clause; do not add `IllegalArgumentException` to any signature.

*(Audit gathered by reading sources via grep/Read across ~25 files. Line numbers are HEAD at audit time.)*
