# testing.java: overview, and what running it against V3 takes

Written 2026-10-06 from a read of `/mnt/c/nats/testing.java` at `70b853c "Testing 452"`. Read-only; nothing in that repo was changed. Its working tree has uncommitted changes: `build.gradle` modified, `aws.json`, `c1-3.bat`, `gen/`, `generator*.json` untracked.

## What it is

One Gradle project, Java 21, about 12,900 lines. It depends on `io.nats:jnats` (version from env `JNATS_VERSION`, else the `jnatsVersion` gradle property, else `2.25.1`; `mavenLocal()` and the Sonatype snapshot repo are listed), plus `io.synadia:direct-batch:0.1.4`, `io.synadia:chaos-runner:0.0.7` and jfreechart.

Entry point: `io.synadia.Runner --workload <Name> [--action <a>] [--id <n>] --params <file> --params <file> ...`. It instantiates `io.synadia.workloads.<Name>` (a `Workload` subclass) by reflection. The params files are JSON, merged in order, last value wins.

Workloads (`io.synadia.workloads`): `Multi`, `CustomMulti`, `CustomWorkload`, `Setup`, `Watch`, `SaveTracking`, `ListTracking`, `ChartProfile`, `ConsumerInfoSim`, `ObjectSim`, `FailgroundSim`, `CoreMessageLoss`, `DeployTest`, `DebugWorkload`. `io.synadia.z` holds scratch programs. `io.synadia.chaos` is a separate chaos test app.

## The cloud setup

- `bin/get-aws` runs `aws ec2 describe-instances` into `aws.json`.
- `bin/gen` runs `io.synadia.utils.Generator full`. It reads `aws.json` and `generator.json` (instance prefix, server/client name filters, ssh key, users, ports), finds the running server and client instances, and fills `templates/*` into `params/*.json` and `gen/*` scripts. Placeholders look like `<Bootstrap>`, `<Server0>`, `<TestingStreamSubject>`. Clients get the private IPs, a Windows machine gets the public ones.
- `templates/server.sh`: per server, install nats-server (v2.12.1 pinned there), a systemd unit, and `/etc/nats.conf` with client port 4222, http 8222, cluster port 7222 and routes to the other two servers. JetStream is on (`-js`).
- `bin-tools/client-init.sh`: the client machine (Amazon Linux, `ec2-user`) gets Corretto 21, git and Gradle 8.14.3, and a `~/bin/r` script that re-clones `synadia-io/testing.java` from GitHub, then runs `bin/make` (`gradle make --refresh-dependencies`, copies the runtime classpath to `build/libs`), `bin/get-aws` and `bin/gen`. So the client always runs what is pushed to GitHub, not a local build.
- `templates/start-clients-bat.txt` / `c1-3.bat`, `s0-2.bat`: ssh windows from the Windows desktop.

## The benchmark (`templates/bench-sh.txt`, `bench-direct-sh.txt`)

`gen/setup-tracking` once, then 5 iterations of:
1. `Setup --action testing` with `stream-unlimited.json`: delete and recreate the testing stream (file storage, R1).
2. `Multi --action publish` with `bench-publish.json`: `PubAsync` (JetStream `publishAsync`), 10,000,000 messages, 250 B payload, 1 thread, `round_size` 100, `BenchHeaderSupplier` (15 headers per message, built new each call), virtual-thread executor.
3. `Watch --action ReportStats`: prints the stats.
4. `Multi --action fetch` (`bench-fetch.json`), report; `Multi --action iterate` (`bench-iterate.json`), report. The direct variant adds `Multi --action direct` (`DirectQueue`, uses `direct-batch`).

How results are recorded:
- `Multi` writes the client version to the stats KV bucket under `CV` (from `JNATS_VERSION` or `Nats.CLIENT_VERSION`).
- `TestingApplication.track` writes each `Stats` (and optionally `ProfileStats`) as JSON to the KV buckets `statsBucket` / `profileBucket` and to the profile stream. Non-final stats go out on the connection's executor.
- The engine is `io.nats.jsmulti` (a copy of the jsmulti tool). `Action` has `PubSync`, `PubAsync`, `PubCore`, `Pub`, `Request`/`Reply`, `RTT`, `SubCore`, `SubCoreQueue`, push, pull, fetch, iterate, `DirectQueue`, `Custom`. Connection options come from `TestingOptionsFactory` → `OptionsFactory.getOptionsBuilder` (`new Options.Builder()`, server, creds, connection timeout, reconnect wait, error listener).

## What running it against V3 takes

Measured facts:
- 62 source files import `io.nats.client...`. In V3 everything is `io.synadia.client...`, published as `io.synadia:jnats3-core` / `jnats3-jetstream` / `jnats3-kv` / `jnats3-os`.
- The code uses the V2 API shapes that V3 changed: `Connection` (V3 has no `Connection` interface), `new Options.Builder()` (V3: `Options.builder()` returning `OptionsBuilder`), `Duration` options, `io.nats.client.impl.Headers`, `NoOpStatistics`, and `support` JSON classes (`JsonValue`, `JsonParser`, `JsonValueUtils`, 58 `support` imports in all).
- `direct-batch` and `chaos-runner` are built against V2 jnats and pull it in transitively. A V3 build cannot use them; `DirectQueue` and `io.synadia.chaos` would have to be left out or ported.
- V3's own `connectionImplementation` choice is read from the system property `io.nats.client.connectionImplementation` by every `OptionsBuilder`. So one V3 build can run Classic and V3 with `java -Dio.nats.client.connectionImplementation=Classic ...`, no code change.

Inference, not measured:
- One source tree cannot compile against both V2 and V3, because the package names differ. The choices are a V3 branch of testing.java, or two source sets (a V2 one and a V3 one) behind a small shared harness. A V3 branch is the least work; the `~/bin/r` re-clone would need to check out that branch.
- V2 vs V3 compares two whole clients. Classic vs V3 inside the V3 jar isolates the connection rewrite. Both are worth recording; the stats should record which implementation ran (today only the client version goes into `CV`).
- The current bench is JetStream `publishAsync` with 15 freshly built headers per message, so server acks and header building dominate. The V3 gains measured locally (small core publishes, allocation) show up most in `PubCore` / `Pub` / `SubCore` / `Request` runs, which the bench scripts do not run today.
