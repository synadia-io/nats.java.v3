# Plan — Remove `noHeaders` / `noNoResponders`

## Why

v3 hard-codes the connect string at `core/src/main/java/io/synadia/client/Options.java:1026-1027`:

```java
appendOption(connectString, OPTION_HEADERS, "true", false, true);
appendOption(connectString, OPTION_NORESPONDERS, "true", false, true);
```

Compare v2 (`nats.java/.../Options.java:3371-3372`) which still respects the user's choice:

```java
appendOption(connectString, Options.OPTION_HEADERS, String.valueOf(!this.isNoHeaders()), false, true);
appendOption(connectString, Options.OPTION_NORESPONDERS, String.valueOf(!this.isNoNoResponders()), false, true);
```

So in v3 the flags are dead — the `noHeaders` and `noNoResponders` fields are read by nothing that influences the on-wire protocol. The builder methods, fields, getters, and properties still exist but do not change the connection. Every NATS server v3 is expected to talk to (2.10+, 2.11+, 2.12+) supports both headers and no-responders unconditionally, so the right modern behaviour is to always advertise `true` and drop the toggles.

This plan removes the dead surface area.

## Scope

Remove from `core`:
- The two boolean fields and their copy-constructor / getters / setters / property parsers.
- The two `PROP_*` constants on `OptionsProperties`.
- All test assertions and chained-builder calls that exercise these flags.

Do **not** change the hard-coded `"true"` strings in the connect string itself. Those become the only authoritative answer — what the protocol announces.

## Item-by-item change list

### `core/src/main/java/io/synadia/client/Options.java`

| Line | What | Action |
|---|---|---|
| 88 | `final boolean noHeaders;` | Remove |
| 89 | `final boolean noNoResponders;` | Remove |
| 222 | `this.noHeaders = b.noHeaders;` (in the `Options(OptionsBuilder b)` constructor) | Remove |
| 223 | `this.noNoResponders = b.noNoResponders;` | Remove |
| 648–652 | `isNoHeaders()` getter + Javadoc | Remove |
| 656–660 | `isNoNoResponders()` getter + Javadoc | Remove |
| 1026 | `appendOption(connectString, OPTION_HEADERS, "true", ...)` | **Keep as-is** (this is what makes the removal safe) |
| 1027 | `appendOption(connectString, OPTION_NORESPONDERS, "true", ...)` | **Keep as-is** |

### `core/src/main/java/io/synadia/client/OptionsBuilder.java`

| Line | What | Action |
|---|---|---|
| 90 | `boolean noHeaders = false;` | Remove |
| 91 | `boolean noNoResponders = false;` | Remove |
| 211 | `booleanProperty(props, PROP_NO_HEADERS, b -> this.noHeaders = b);` | Remove |
| 212 | `booleanProperty(props, PROP_NO_NO_RESPONDERS, b -> this.noNoResponders = b);` | Remove |
| 357–365 | `noHeaders()` setter + Javadoc | Remove |
| 367–375 | `noNoResponders()` setter + Javadoc | Remove |
| 1385 | `this.noHeaders = o.noHeaders;` (in the `OptionsBuilder(Options)` copy constructor) | Remove |
| 1386 | `this.noNoResponders = o.noNoResponders;` | Remove |

### `core/src/main/java/io/synadia/client/OptionsProperties.java`

| Line | What | Action |
|---|---|---|
| 125–128 | `PROP_NO_HEADERS` constant + Javadoc | Remove |
| 133–136 | `PROP_NO_NO_RESPONDERS` constant + Javadoc | Remove |

### `core/src/test/java/io/synadia/client/OptionsTests.java`

| Line | What | Action |
|---|---|---|
| 65 | `assertFalse(o.isNoHeaders(), "default header support");` | Remove |
| 66 | `assertFalse(o.isNoNoResponders(), "default no responders support");` | Remove |
| 98 | `.noEcho().noHeaders().noNoResponders()` (chained-setter test) | Strip `.noHeaders()` and `.noNoResponders()` from the chain |
| 111 | `assertTrue(o.isNoHeaders(), "chained no headers");` | Remove |
| 112 | `assertTrue(o.isNoNoResponders(), "chained report noResponders");` | Remove |
| 554–555 | `props.setProperty(PROP_NO_HEADERS, "true")` + the NORESPONDERS sibling | Remove both lines |
| 569–570 | `assertTrue(o.isNoHeaders());` + `assertTrue(o.isNoNoResponders());` | Remove |
| 816 | `Options o = new OptionsBuilder().noNoResponders().noHeaders().noEcho().pedantic().verbose().build();` | Strip the two removed setters from the chain |

### `core/src/test/java/io/synadia/client/impl/RequestTests.java`

`.noNoResponders()` is used at lines 286, 311, 459, 699. **These are already no-ops** today (v3 always announces `no_responders: true`), so the tests are passing in spite of the call rather than because of it. Action: drop the `.noNoResponders()` invocations and verify the tests still pass — they should, because nothing on the wire changes.

> **Open question for the implementer:** the test names / context around lines 286, 311, 459, 699 may reveal an *intent* to exercise no-responders semantics. If so, those tests were already misconfigured in v3 (the flag was dead) and the right fix is independent of this plan — leave a `// TODO:` comment for whoever owns the request semantics tests.

### `README.md`

| Line | What | Action |
|---|---|---|
| 37 | The `noHeaders` row in the property table | Remove |

(There is no `noNoResponders` row in this README — only the `noHeaders` entry needs removal.)

### `MIGRATION_GUIDE_OPTIONS.md`

| Line | What | Action |
|---|---|---|
| 38 | `PROP_NO_NORESPONDERS → PROP_NO_NO_RESPONDERS` rename row in §3 ("Constants whose names changed") | Move to §6 ("Constants removed") with reason: *"v3 always advertises no-responders support; the toggle was no longer wired"* |
| 50 | `PROP_NO_HEADERS` row in §4 ("Constants whose values changed") | Move to §6 ("Constants removed") with reason: *"v3 always advertises headers support; the toggle was no longer wired"* |

### `MIGRATION_GUIDE.md`

Add a new bullet to the **Core** section, alongside the other v2 → v3 builder-method removals:

```markdown
- **`noHeaders()` / `noNoResponders()` setters and corresponding properties are gone.** v3 always advertises both headers and no-responders support in its `CONNECT` payload — every NATS server v3 supports has those features unconditionally, so the toggles were no-ops in practice (the values were hard-coded in the connect string). Migration:

  | v2 / removed in v3 | v3 |
  |---|---|
  | `builder.noHeaders()` | (delete) |
  | `builder.noNoResponders()` | (delete) |
  | `options.isNoHeaders()` | (delete; treat as always `false`) |
  | `options.isNoNoResponders()` | (delete; treat as always `false`) |
  | `io.nats.client.noHeaders` property | (delete) |
  | `io.nats.client.noNoResponders` property | (delete) |
```

## Implementation order (build-safe)

Each step compiles on its own.

1. Drop the two `appendOption` calls' dependency on the fields. (Already done — they're hard-coded to `"true"`.)
2. Remove the test usages first (`OptionsTests` lines listed above + the `.noNoResponders()` calls in `RequestTests`). Tree still compiles.
3. Remove the setters (`OptionsBuilder.noHeaders()` / `noNoResponders()`). Tree still compiles (no callers left after step 2).
4. Remove the getters (`Options.isNoHeaders()` / `isNoNoResponders()`). Tree still compiles (no callers).
5. Remove the property parsers and the copy-constructor entries in `OptionsBuilder`. Tree still compiles.
6. Remove the `Options` constructor assignments. Tree still compiles.
7. Remove the `Options` fields and the `OptionsBuilder` fields.
8. Remove the two `PROP_*` constants from `OptionsProperties`.
9. Update `README.md` and the two migration guide files.
10. Grep verify no `noHeaders`, `noNoResponders`, `isNoHeaders`, `isNoNoResponders`, `PROP_NO_HEADERS`, `PROP_NO_NO_RESPONDERS`, or `OPTION_HEADERS`/`OPTION_NORESPONDERS` (in builder-method context) remain in `core/src/main` or `core/src/test`.

## Risks

- **`RequestTests.noNoResponders()` calls** may have been written to exercise old behaviour. Since v3 already hard-codes the connect string, those calls have been dead for some time. Removing the calls is mechanical; investigating whether the tests are actually exercising what they think they're exercising is a separate concern (call it out in `todo.md` if anything looks off).
- **No-op behaviour change at runtime.** None — `noHeaders=false` and `noNoResponders=false` are the only values the connect string responds to today, and even they don't matter (it always sends `true`). Removing the flags has zero observable impact on the wire.
- **API surface shrinks.** Any user code calling `builder.noHeaders()`, `builder.noNoResponders()`, `options.isNoHeaders()`, or `options.isNoNoResponders()` won't compile against v3 after the removal. Migration guide rows cover the translation.
- **Properties file format.** Properties files setting `io.nats.client.noHeaders` or `io.nats.client.noNoResponders` will now silently ignore those keys — the parser just won't recognise them. This matches the runtime reality (the value never mattered) but is worth noting in the migration guide so operators don't think they're disabling something.

## Out of scope

- Any change to `OPTION_HEADERS` / `OPTION_NORESPONDERS` *constants* themselves — those still appear in the connect string with `"true"` and stay as they are.
- The `noEcho` / `isNoEcho()` / `noEcho` property — still wired through to the connect string (line 1025) and still a meaningful toggle. Not touched here.
- Any rationalisation of other "is this still wired up?" flags. This plan only addresses the two the user identified.
