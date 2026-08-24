# Plan: ResourceUtils missing-resource diagnostics (V3)

**State: COMPLETE — shipped in `c68ca6cb`, verified and archived 2026-08-14.** Both items landed: the vestigial `@SuppressWarnings("DataFlowIssue")` is gone (no `SuppressWarnings` remains anywhere in the file), and the optional regression test was ported as `core/src/test/java/io/synadia/client/utils/ResourceUtilsTests.java` — two tests, both green, and it does assert on the **cause** as this plan warned, since `open()` throws inside a try-with-resources whose `catch (IOException)` wraps it in a `RuntimeException`.

**The drift check in "Current exact delta between the two files" was run at archive time and passes.** Diffing V2's `ResourceUtils` against V3's, ignoring package and import lines, they differ in exactly the two places predicted once the suppression was removed: (1) V3's `configResource` / `jwtResource` and their `CONFIG_FILE_BASE` / `JWT_FILE_BASE` constants, which build filesystem paths rather than classpath resources and are correctly absent from V2; and (2) V3's `in.readAllBytes()` against V2's manual buffer loop, forced by V2's Java 8 pin. No third difference, so neither side has drifted.


## Punchline: V3 already has this fix. There is one small leftover to clean up, and that's it.

## Background — what the problem was in V2

In `nats.java` (V2/main), `ResourceUtils` resolved classpath resources like this:

```java
ClassLoader classLoader = ResourceUtils.class.getClassLoader();
File file = new File(classLoader.getResource(fileName).getFile());
return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
```

`getResource(...)` returns `null` when the resource isn't on the classpath, so `.getFile()` threw a bare `NullPointerException` — no message, no filename. The whole class carried `@SuppressWarnings("DataFlowIssue")` specifically to keep the IDE quiet about that unguarded deref.

That cost real time: PR #1585 failed CI from June 16 to Aug 4 because two test JSON files were never committed, and the only clue in the log was `java.lang.NullPointerException at ResourceUtils.java:38`. Nothing said which file was missing.

Fixed in V2 by nats-io/nats.java PR #1603 (branch `resource-utils-not-found`).

## What I found in V3

`core/src/test/java/io/synadia/client/utils/ResourceUtils.java` **already does the right thing**, lines 72-78:

```java
private static InputStream open(String fileName) throws FileNotFoundException {
    InputStream in = ResourceUtils.class.getClassLoader().getResourceAsStream(fileName);
    if (in == null) {
        throw new FileNotFoundException(fileName);
    }
    return in;
}
```

All three accessors (`resourceAsLines`, `resourceAsString`, `resourceAsInputStream`) route through it. A missing resource surfaces as `FileNotFoundException: data/Whatever.json` — exactly the behavior V2 just adopted. In fact the V2 fix was written to match this, not the other way around.

I also grepped every `.java` file in the V3 repo (excluding `build/`) for `getResource(` and found **zero** occurrences outside of `getResourceAsStream`. So there is no other instance of the unguarded-deref pattern anywhere in V3.

## The one thing actually left to do

Line 10 of that file still has:

```java
@SuppressWarnings("DataFlowIssue")
```

It is vestigial. It existed to silence the `getResource(...).getFile()` deref, and that code is gone. Nothing else in the class needs it — `configResource` and `jwtResource` both null-check their argument, and `deleteFileOrFolder` null-checks `file.listFiles()`.

**Action:** delete line 10.

This is an IDE-only annotation and has zero effect on compilation, so the risk is nil. If the IDE re-flags something after removal, that's new information worth looking at rather than re-suppressing.

## Optional: port the regression test

V2's PR added `src/test/java/io/nats/client/utils/ResourceUtilsTests.java`, which pins the behavior so it can't silently regress:

```java
private static final String MISSING = "ThisResourceDoesNotExist.json";

@Test
public void testMissingResourceIdentifiesTheFile() {
    assertMissing(assertThrows(RuntimeException.class, () -> dataAsString(MISSING)));
    assertMissing(assertThrows(RuntimeException.class, () -> dataAsLines(MISSING)));
    assertMissing(assertThrows(RuntimeException.class, () -> dataAsInputStream(MISSING)));
}

private void assertMissing(RuntimeException e) {
    Throwable cause = e.getCause();
    assertInstanceOf(FileNotFoundException.class, cause);
    assertTrue(cause.getMessage().contains(MISSING));
}
```

Note the assertion is on the **cause**, not the top-level exception: `open()` throws inside a try-with-resources whose `catch (IOException e)` wraps it in a `RuntimeException`. That wrapping is identical in V3, so this test shape transfers as-is.

The second V2 test just loads a known-good fixture to confirm normal reads still work. In V3 you'd point it at whatever fixture is appropriate for the module — I did not go looking for one, since you said you'd have the V3 side sort out package/module structure.

Whoever picks this up: figure out the right module and package for the test (V3 is multi-module and uses `io.synadia.*`; V2 is single-module `io.nats.*`), and confirm JUnit is new enough for `assertInstanceOf` (5.8+) or swap in `assertTrue(cause instanceof FileNotFoundException)`.

## Current exact delta between the two files

As of nats-io/nats.java PR #1603 (commits `5de9b3fd` + `90eed680`), V2's `ResourceUtils` was deliberately aligned to V3's. Ignoring the package line, the two files now differ in exactly three places, all intentional:

1. **`resourceAsString`** — V3 uses `in.readAllBytes()` (Java 9+); V2 reads through a buffer loop because it is pinned to Java 8. Forced difference. See the next section.
2. **`configResource` / `jwtResource` and the `CONFIG_FILE_BASE` / `JWT_FILE_BASE` constants** — V3-only. These build filesystem paths, not classpath resources, and have no callers in V2. Correctly absent there; nothing to do.
3. **`@SuppressWarnings("DataFlowIssue")`** — V3 still has it, V2 dropped it. This is the item below that V3 should follow V2 on, not the reverse.

`open()`, `resourceAsInputStream`, `resourceAsLines`, and the entire import block are now byte-identical. So a plain diff of the two files should show only the above; anything else means one side drifted.

## Do NOT copy V2's read loop into V3

V2's `resourceAsString` reads through a manual buffer loop:

```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
byte[] buffer = new byte[8192];
int len;
while ((len = in.read(buffer)) != -1) {
    out.write(buffer, 0, len);
}
```

That is **only** because V2 is pinned to `sourceCompatibility = JavaVersion.VERSION_1_8` and `InputStream.readAllBytes()` is Java 9+. V3's `build.gradle` sets `VERSION_21`, so V3's existing `in.readAllBytes()` on line 56 is correct and simpler. Leave it alone.

## Verification

1. Remove line 10.
2. Build the test sources for the module.
3. Run the existing test classes that consume `ResourceUtils` — nothing should change, since the annotation has no runtime effect.

## What I did and didn't verify

Verified directly: the contents of `core/src/test/java/io/synadia/client/utils/ResourceUtils.java`, the repo-wide `getResource(` grep, and `VERSION_21` in `build.gradle`.

Not verified: anything about V3's module layout, test conventions, or where a new test class belongs. That was out of scope by request.
