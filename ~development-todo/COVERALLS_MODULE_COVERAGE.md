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
- Not observable from here: the per-flag view. The public build JSON has totals only; the build page renders client-side. Needs a look in the browser.
- Not yet exercised: carryforward. Every flag reported in this run, so nothing was carried. It needs a partial run, e.g. an os-only change.

## Unrelated failure in the same run

All 5 `Publish Snapshot` steps failed: the repo has none of `OSSRH_USERNAME`, `OSSRH_TOKEN`, `SIGNING_KEY_ID`, `SIGNING_KEY`, `SIGNING_PASSWORD` (only `COVERALLS_REPO_TOKEN`; no org secret supplies them). `synadia-io/orbit.java` has all 5 as repo secrets. They have to be added to `synadia-io/nats.java.v3`.

## Open

1. Look at build 82066815 in the browser: does it show per-flag coverage?
2. Run a partial build (one module) and check the total stays ~90.8% rather than dropping to that module's lines alone. That is the carryforward test.
3. If either fails, fall back to building everything on any code change (one job, one report).
