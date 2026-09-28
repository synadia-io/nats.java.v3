# Cross account JetStream: what push needs, what pull needs

Written 2026-09-25, from switching the KV watcher from a push consumer to a simplified consume and finding `KeyValueTests.testWithAccount` failing. The client was never at fault. This is configuration, plus one thing worth asking the server team about.

## 1. The rule, for our team and for users

A stream in another account is read two different ways, over two different exports. Configure for the one you use, or both.

**Push delivery rides a stream export of the client's inbox.** The consumer's deliver subject is the reading client's inbox, and JetStream publishes to it inside the *stream's* account, so it only reaches the reading account if that account imports it.

```
SOURCE exports: { stream: "_INBOX.>" }
TARGET imports: { stream: { account: SOURCE, subject: "_INBOX.>" }, to: "_INBOX.>" }
```

Measured: with the api service export present but no inbox stream export, a push subscription reads **0 of 5** messages.

**Pull delivery rides the api service export, which must declare a stream response.** A pull request is a service request whose answer is many messages on one reply subject. The default `Singleton` response type permits one.

```
SOURCE exports: { service: "$JS.API.>", response_type: Stream }
TARGET imports: { service: { account: SOURCE, subject: "$JS.API.>" }, to: tar.api.> }
```

Measured: with `Singleton`, a simplified consume reads **1 of 5**. With `Stream`, it reads all of them, at every count from 1 to 20, matching what push reads and what a same account consume reads.

**Do not try to narrow it to the pull subject only.** Declaring the broad export `Singleton` and adding a more specific `$JS.API.CONSUMER.MSG.NEXT.>` export with `response_type: Stream` does not work - see §3.

Both shapes are covered by tests: `jetstream/src/test/java/io/synadia/client/impl/ConsumeInAccountTests.java` with `account_push.conf` and `account_pull.conf`. Each config deliberately carries only its own mechanism, and the class has a test for each one working and a test for the other one failing against it, so the split is pinned rather than described.

## 2. What this means for a configurable watcher

If the watcher becomes push or pull by choice, the choice carries an account requirement with it. A bucket in another account that works with today's push watcher will **not** work with a pull watcher unless `$JS.API.>` is exported with `response_type: Stream`. That is a deployment change on the exporting side, not something the client can arrange, so it belongs in the release notes for the option.

## 3. The overlapping import trap, and why it is not a server bug

Adding a narrower `$JS.API.CONSUMER.MSG.NEXT.>` export and import beside the broad ones looked like partial message loss: five messages asked for, three delivered - sequences 1, 2 and 4 - then a `404 No Messages` while two were unread.

**The cause is the duplicate import, not export precedence.** Measured: keeping both exports but importing only `$JS.API.>` gives exactly one message, plain singleton behaviour, with the narrow export having no effect at all. So the narrow export never mattered. What mattered is that a request to `tar.api.CONSUMER.MSG.NEXT.<stream>.<consumer>` matches **both** import mappings, so JetStream receives the request twice, each copy with its own reply mapping. The consumer alternates messages between the two waiting requests, the singleton copy delivers its first and drops the rest, and the 404 is the server answering the other copy once the stream runs dry. That accounts for every number: 1, 2, 4 of five, and the raw counts 2 of 2, 2 of 3 and 3 of 5.

Nothing here needs a server change. The one thing worth asking for some day is a config load warning when two service imports map onto the same effective subject, since the duplication is silent.

The user facing writeup is `CROSS_ACCOUNT_JETSTREAM.md` in the repo root, for sales engineers and users: what to export for push and for pull, what each failure looks like, the do-not-import-twice trap, a CLI check, and the client side prefix and watcher option.

## 4. Unrelated, found in the same sweep - FIXED

`ConsumeOptions.batchSize(1)` delivered the first message and then stalled, **same account as well as cross account**, so it was never about accounts. Every other batch size read everything.

Cause, in `NatsMessageConsumer`: the re-pull threshold is `batchSize - rePullSize`, where `rePullSize` is at least 1. A batch of 1 makes the threshold 0, and the pending count is clamped at 0, so `pendingProcessedMessages < thresholdMessages` is never true and the consumer never pulls again. The threshold is now clamped to at least 1, for bytes as well as messages.

`ConsumeBatchSizeTests` covers batch sizes 1, 2, 3, 5 and 10 against a five message stream; batch size 1 failed at 1 of 5 before the change and passes now.

**v2 has the same arithmetic** - `nats.java` `src/main/java/io/nats/client/impl/NatsMessageConsumer.java:54-56` is line for line the same, so it has the same defect. Not changed there yet.
