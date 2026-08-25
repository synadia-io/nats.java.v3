# OSGi / JPMS TODO — split packages and module identity

Tracking doc for the module-system problems in the V3 jar layout. Nothing here is urgent for correctness on the classpath; all of it bites consumers who use the JPMS module path or OSGi. Split out of EXCEPTIONS_AUDIT.md §4b/D4, where it surfaced as a (rejected) argument for where to put the exception types.

## 1. Three split packages across the core and jetstream jars

A *split package* is one package whose classes are delivered by more than one jar. Measured from `*/src/main/java`:

| Package | Contributed by |
|---|---|
| `io.synadia.client.api` | **core**, **jetstream** |
| `io.synadia.client.impl` | **core**, **jetstream** |
| `io.synadia.client.utils` | **core**, **jetstream** |

Non-split, for contrast: `io.synadia.client` and `io.synadia.client.global` (core only); `io.synadia.client.kv`, `io.synadia.client.os` (jetstream only); `io.synadia.service` (service only); `io.synadia.examples` (examples only).

## 2. Why it matters

**JPMS.** Every subproject publishes an `Automatic-Module-Name` (root `build.gradle:171`) and there is no `module-info.java` anywhere, so each jar is an *automatic module*. The JPMS rule is that a package may be exported by at most one module on the module path. Two automatic modules containing `io.synadia.client.api` is a hard resolution failure:

```
java.lang.module.ResolutionException: Modules io.synadia.jnats3.core and
io.synadia.jnats3.jetstream export package io.synadia.client.api to module ...
```

**So `jnats3-core` + `jnats3-jetstream` cannot currently be placed on the module path together.** This is a pre-existing condition, not a regression. On the classpath (unnamed module) everything works fine, which is why it has gone unnoticed — most consumers are still on the classpath.

**OSGi.** The `biz.aQute.bnd.builder` plugin is applied to all subprojects (`build.gradle:51`) and generates `Export-Package` from the sources. Two bundles exporting the same package means the OSGi resolver picks *one* wiring per importer, so a consumer importing `io.synadia.client.api` gets whichever bundle wins and will get `NoClassDefFoundError` for the half that lives in the other. `Require-Bundle` or fragments can paper over it; neither is something to ask users to do.

## 3. FIXED — module identity: every subproject published as `io.synadia:jnats3`

**Found and fixed 2026-07-15.** Two distinct problems, both in the identity plumbing.

**3.1 — All four subprojects shared one artifactId.** The root build hardcoded the name:

```groovy
def jarAndArtifactName = "jnats3" + jarEnd   // build.gradle:158 — jarEnd is only "-jdk25" or ""
```

`jarEnd` has no per-module component, so `archiveBaseName`, `pom.name` and `pom.artifactId` were identical everywhere. The build printed the same coordinates four times:

```
Output: io.synadia:jnats3:3.0.0-SNAPSHOT   x4   (core, jetstream, service, examples)
```

Every module would have published to the same Maven coordinates and overwritten the others. Presumably inherited from V2's single-jar layout.

**3.2 — `examples` carried jetstream's entire identity.** `examples/build.gradle` was an unedited copy-paste: `moduleNameExt` and `bundleNameExt` both `io.synadia.jnats3.jetstream`, and a POM description claiming to be the JetStream library. Since `maven-publish` and `signing` apply to **all** subprojects (`build.gradle:47-53`), `:examples` is publish-configured and would have collided with the real jetstream jar.

**The fix** follows the existing design rather than working around it: subprojects already declare their identity in `ext` and the root reads it in `afterEvaluate`, so artifact name simply joined that set.

```groovy
// root build.gradle:158
def jarAndArtifactName = artifactNameExt + jarEnd

// each subproject's ext block
artifactNameExt = 'jnats3-core'   // -jetstream, -service, -examples
```

Verified — four distinct artifacts, correct manifests, `:examples:compileJava` green:

| Module | Artifact | Automatic-Module-Name |
|---|---|---|
| core | `io.synadia:jnats3-core` | `io.synadia.jnats3.core` |
| jetstream | `io.synadia:jnats3-jetstream` | `io.synadia.jnats3.jetstream` |
| service | `io.synadia:jnats3-service` | `io.synadia.jnats3.service` |
| examples | `io.synadia:jnats3-examples` | `io.synadia.jnats3.examples` |

The `-jdk25` variant still composes correctly (`jnats3-core-jdk25`). New modules must declare `artifactNameExt` — **kv and os get `jnats3-kv` / `jnats3-os` when they are split out.**

**3.3 — `examples` is still publish-configured.** Scott: examples are part of the repo only, never a Maven artifact. But `maven-publish` and `signing` apply to **every** subproject (`build.gradle:47-53`), so `:examples` still declares a `mavenJava` publication and would publish `io.synadia:jnats3-examples`.

A `publishExt` flag (declared per subproject, read by the root's `afterEvaluate` to skip the publication) was implemented and then **reverted** — Scott's call was to keep the Gradle change scoped strictly to the artifact id and nothing else. Recorded here as O1, not as a decision that examples *should* publish.

Note the shape of the fix if it is ever taken: since every library is published individually and always, there is no per-library publish *decision*. The only predicate is whether a subproject is a library at all — so the flag should be named for that (`libraryExt`), not for the mechanism (`publishExt`). Declaring no publication also leaves signing nothing to sign, so the `isRelease` signing block needs no separate guard.

## 4. Options for the split packages

Not costed yet — this section is a sketch, not a recommendation.

1. **Do nothing, document it.** Classpath users are unaffected. Cost: no module-path story, and it quietly gets worse as KV/OS split into their own jars (both currently in jetstream-only packages, so the split count doesn't grow — but any new shared package would).
2. **Rename to un-split.** Give each module its own package root — e.g. core keeps `io.synadia.client.*`, jetstream moves to `io.synadia.client.jetstream.*`. Clean and permanent; large, breaking, mechanical rename touching every import. Best done in a major, i.e. now or never.
3. **Merge the split packages into one jar.** Move the core-side `io.synadia.client.api` / `.impl` / `.utils` classes into whichever jar should own them, or merge core+jetstream. Contradicts the modularity goal.
4. **Real `module-info.java`.** Does not fix split packages by itself — JPMS still rejects them. Only viable after 2 or 3.

Note option 2 interacts with the **KV/OS split** (they become separate projects) and with **EXCEPTIONS_AUDIT.md D4**, which chose `io.synadia.client.api` for the JetStream exception family. That choice was made knowing `.api` is already split; it adds classes to an existing split package but does not create a new one. If option 2 is ever taken, the exception types move with everything else in `.api` — no extra cost, no reason to pre-emptively place them elsewhere.

## 5. Action items

- [x] **O2 — Per-module identity** (§3.1/3.2). **DONE 2026-07-15.** Every subproject had artifactId `jnats3`; `examples` additionally carried jetstream's module/bundle name and POM description. Now `artifactNameExt` per subproject, read by the root's `afterEvaluate`. Verified green — including that `jnats3-jetstream`'s POM now correctly depends on `jnats3-core` instead of on itself.
- [ ] **O1 — Stop `:examples` publishing** (§3.3). Scott: examples are repo-only, never a Maven artifact — but the build still declares a publication for them. A `publishExt` implementation was reverted to keep the Gradle change scoped to the artifact id; see §3.3 for the shape this should take (`libraryExt`, not `publishExt`) whenever it is picked up.
- [ ] **O3 — Decide the split-package strategy** (§4). Gate: if option 2, it should land in V3 before release, since it is a breaking rename.
- [ ] **O4 — Add a module-path smoke test** so this can't regress silently. A trivial consumer with a `module-info.java` requiring both jars fails today; it should be the thing that proves O3 worked.
- [ ] **O5 — Decide whether V3 ships real `module-info.java`** or stays on `Automatic-Module-Name` (§4 option 4). Depends on O3.
