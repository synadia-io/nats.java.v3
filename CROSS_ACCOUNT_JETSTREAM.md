# Reading a JetStream stream from another account

When the stream lives in one account and the client connects to another, what the client can read depends on what the stream's account exports. Push and pull are carried by two different exports, so a setup that works for one can fail completely for the other. This is the part people get wrong, and the failure is quiet: the client connects, creates its consumer, and then receives one message, or none, with no error.

## The two exports

**Push delivery needs the inbox exported as a stream.** A push consumer has a deliver subject, which is the reading client's inbox, and JetStream publishes to it inside the *stream's* account. It only reaches the reading account if that inbox subject is exported and imported.

**Pull delivery, which includes the simplified `consume`, `fetch` and `next`, needs the JetStream api exported with a stream response.** A pull request is a service request, and its answer is many messages on one reply subject. A service export defaults to `response_type: Singleton`, which permits exactly one reply.

An account that serves both needs both exports.

```
accounts: {
  SOURCE: {
    jetstream: enabled
    users: [ {user: src, password: spass} ]
    exports [
        # needed by pull, fetch, next and the simplified consume
        { service: "$JS.API.>", response_type: Stream },
        # needed by push
        { stream: "_INBOX.>" },
    ]
  },
  TARGET: {
    users: [ {user: tar, password: tpass} ]
    imports [
      { service: { account: SOURCE, subject: "$JS.API.>" }, to: tar.api.> }
      { stream: { account: SOURCE, subject: "_INBOX.>" }, to: "_INBOX.>" }
    ]
  }
}
```

`response_type: Stream` is safe for the whole api. The request and reply calls - create consumer, consumer info, stream info - answer with a single message either way.

If the reading client uses a custom inbox prefix, export that prefix instead of `_INBOX.>`. A client connecting with an inbox prefix of `ForI` needs `{ stream: "ForI.>" }` exported and imported.

## What each failure looks like

| Symptom | Cause |
|---|---|
| A consume, fetch or next receives **exactly one message** and then nothing, with no error | The api is exported with the default singleton response. Add `response_type: Stream`. |
| The same one message and then nothing, but **it also happens reading a stream in your own account** | Not an account problem. A `ConsumeOptions.batchSize(1)` consume stopped after its first message in earlier clients, because the re-pull threshold worked out to zero. Use a batch size of 2 or more, or take a client with the fix. |
| A push subscription receives **nothing**, though the consumer was created successfully | The client's inbox subject is not exported as a stream, so deliveries never leave the stream's account. |
| Management calls work, reads do not | Both of the above. Creating a consumer only needs the singleton service export, which is why setup appears to succeed. |

The consumer is created in every one of these cases, so `nats consumer info` looks healthy while the client sits idle.

The first two rows look the same from the application's side, so start by reading the same stream from its own account. If that works, the exports are the problem. If it stops after one message there too, the batch size is.

## Do not import the same subject twice

Each import is a separate mapping, and a request that matches two of them is delivered twice. With these imports:

```
      { service: { account: SOURCE, subject: "$JS.API.>" }, to: tar.api.> }
      { service: { account: SOURCE, subject: "$JS.API.CONSUMER.MSG.NEXT.>" }, to: tar.api.CONSUMER.MSG.NEXT.> }
```

a pull request published to `tar.api.CONSUMER.MSG.NEXT.<stream>.<consumer>` matches both, so JetStream receives two pull requests, each with its own reply mapping. The consumer then alternates messages between the two, and whichever mapping is a singleton delivers its first message and drops the rest. Asking for five messages returns three of them - sequences 1, 2 and 4 - followed by a `404 No Messages` while two are still unread. It looks like message loss; it is one request arriving twice.

Export and import the api once, as `$JS.API.>` with a stream response. Do not add a narrower export or import for the pull subject.

## Checking a setup

Five messages, one pull request, count the answers. Run this from the reading account.

```bash
SRC="nats --server nats://src:spass@localhost:4222"
TAR="nats --server nats://tar:tpass@localhost:4222"

$SRC stream add CHECK --subjects check.subject --storage memory --defaults
for x in 1 2 3 4 5; do $SRC pub check.subject "data$x"; done
$SRC consumer add CHECK PULLER --pull --deliver all --ack none --filter check.subject --defaults

timeout 4 $TAR sub check.inbox &
sleep 1
$TAR pub 'tar.api.CONSUMER.MSG.NEXT.CHECK.PULLER' '{"batch":5,"no_wait":true}' --reply check.inbox
```

Five messages back means the api export is right. One message back means it is still a singleton export.

## On the client

Point the client at the imported prefix, which is the `to:` of the service import.

```java
JetStreamOptions jso = JetStreamOptions.builder().prefix("tar.api").build();
JetStream js = new JetStream(connection, jso);
```

Key value takes the same prefix through its own options.

```java
KeyValueOptions kvo = KeyValueOptions.builder().jsPrefix("tar.api").build();
```

A key value watcher reads with the simplified consume by default, so a bucket in another account needs the stream response export. `KeyValueWatchOption.PUSH_CONSUME` switches that watcher to a push consumer, which needs the inbox stream export instead. A bucket that worked with a watcher before this option existed was using push, so check the exports before switching it.
