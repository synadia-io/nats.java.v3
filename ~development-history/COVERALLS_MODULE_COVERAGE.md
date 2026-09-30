# Coveralls with module-based builds

Question: can Coveralls report coverage correctly when CI builds only the modules a change affects (`build-modules.yml`, commit `d865181e`)?

## What the docs say

- Parallel builds: Coveralls receives several reports for one build "until you tell it that all jobs were sent and it's time to close the build, merge the reports together and calculate total coverage" ([parallel builds](https://docs.coveralls.io/parallel-builds)). The close is the [parallel build webhook](https://docs.coveralls.io/api-parallel-build-webhook) (`status: done`), which is what `parallel-finished: true` calls.
- Flags: each parallel job gets a `flag-name`, which "will be shown in the Coveralls UI" ([github-action](https://github.com/coverallsapp/github-action)).
- Carryforward: a comma-separated flag list on the webhook; Coveralls will "carry forward the last coverage report for any flag (job) in the list" and "calculate total coverage like any other parallel build". Stated purpose is monorepos, "so you don't have to build every subproject ... every time" ([monorepo announcement](https://coveralls.io/better-monorepo-support)).
- Not stated anywhere I found: which previous build carryforward takes the report from (same branch, default branch, or most recent), and how per-flag coverage is displayed.
- The announcement says "no Coveralls integrations support Carryforward Flags out-of-the-box". The current github-action has a `carryforward` input, so I read that sentence as dated (inference).

## What the reporter source says (verified)

`coverage-reporter` `jacoco_parser.cr` names each file `<package>/<sourcefile>`, e.g. `io/synadia/client/kv/KeyValue.java`. `file_report.cr` `prepend_base_path` then prefixes `base-path` when the file exists there. With `base-path: <module>/src/main/java` every file is reported repo-relative, e.g. `kv/src/main/java/io/synadia/client/kv/KeyValue.java`. So modules sharing packages (core and jetstream both have `io.synadia.client.impl`, `.api`, `.utils`) do not collide.

## What run 36750499877 showed (measured)

- 5 module jobs (core, jetstream, kv, os, service) each posted with their flag; every response was "Coverage for parallel build uploaded" to build [82066815](https://coveralls.io/builds/82066815).
- The coverage job's `coveralls done` returned `{"done":true,...,"jobs":0}`. What `jobs: 0` counts is not documented.
- Build totals from the public API: 90.85% covered, 11500 relevant lines, change -0.6%.
- Per-flag view: verified by Scott on 2026-09-30 in the browser. Build [82070012](https://coveralls.io/builds/82070012) (commit `d3e5d65d`) shows an individual coverage score for each module flag. The public build JSON has totals only.
- Carryforward: verified 2026-09-30 by run 36771886306 (commit `0744f1f2`, an os-only javadoc change). Only the `changes`, `build (os)` and `coverage` jobs ran. Coveralls build [82070991](https://coveralls.io/builds/82070991) reports 90.85% over 11500 relevant lines, change 0.0%, identical to the full build 82070012, so the four modules not built were carried forward. Only `jnats3-os` got a new snapshot (20260930.202159); `jnats3-core` kept 20260930.194907.

## Unrelated failure in the same run

All 5 `Publish Snapshot` steps failed: the repo has none of `OSSRH_USERNAME`, `OSSRH_TOKEN`, `SIGNING_KEY_ID`, `SIGNING_KEY`, `SIGNING_PASSWORD` (only `COVERALLS_REPO_TOKEN`; no org secret supplies them). `synadia-io/orbit.java` has all 5 as repo secrets. They have to be added to `synadia-io/nats.java.v3`.

## Result

Module-based builds work with Coveralls: file paths are unique per module, each flag shows its own score, and carryforward keeps the total correct when only some modules build. Nothing open.
