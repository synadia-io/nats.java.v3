# Discover Many Improvement



## 1. `discoverMany` mishandles a no responders status — same bug in v3

`service/src/main/java/io/synadia/service/Discovery.java:203`

```java
Message msg = sub.nextMessage(timeoutMillis);
if (msg == null) {
    return;
}
dataConsumer.accept(msg.getData());
```

`discoverMany` treats every message arriving on its reply inbox as a service response. When the discovery subject has no interest registered, the server answers with a 503 no responders status, which carries no payload — so `dataConsumer.accept(msg.getData())` hands an empty payload to `new PingResponse(...)` and it throws `IllegalArgumentException: Type cannot be null or empty`.

The fix, matching what v2 now does:

```java
if (msg == null || msg.isStatusMessage()) {
    return;
}
```

No responders means no results, which is the same outcome as the wait expiring — so it folds into the existing null check rather than needing its own branch.

**Why this is easy to get wrong.** Everywhere else in the client a 503 becomes an exception: `conn.request()` and `js.publish()` route replies through `deliverReply`, where the status fails the response future (`CancellationException`, `JetStreamStatusException`, or `IOException: Error Publishing: 503 No Responders Available For Request`). That leads naturally to the assumption that a status message can never turn up as a plain message. `discoverMany` is the exception: it rolls its own reply subscription with `subscribe` + `publish`, so there is no future for the 503 to fail and it arrives as an ordinary zero-payload message in the subscription queue. `discoverOne` already guards the equivalent case via `RequestFailureMessage`; the many-path never did.

## 2. Port the evidence test

v2 added `testNoRespondersStatusIsDeliveredToASubscription` to `NatsMessageTests`, beside `testFactoryProducesStatusMessage`. The v3 home is `core/src/test/java/io/synadia/client/impl/NatsMessageTests.java`.

```java
@Test
public void testNoRespondersStatusIsDeliveredToASubscription() throws Exception {
    jsServer.run(connection -> {
        // A request routes a no responders 503 through the response future, where it turns
        // into an exception. A plain publish with a reply to has no future behind it, so the
        // status is delivered to the reply subscription like any other message, carrying no
        // payload. Anything reading such a subscription has to expect a status message.
        String replyTo = connection.createInbox();
        Subscription sub = connection.subscribe(replyTo);
        connection.publish(subject(), replyTo, null);

        Message m = sub.nextMessage(1000);
        assertNotNull(m);
        assertTrue(m.isStatusMessage());
        assertEquals(503, m.getStatus().getCode());
        assertEquals(0, m.getData().length);
    });
}
```

Adapt the harness call to v3's shared-server helpers. No flush is needed before the publish: SUB and PUB go out on the same connection in order, so the server has the interest registered before it processes the publish.

## 3. `testInboxSupplier` documents the bug as expected behavior

`service/src/test/java/io/synadia/service/ServiceTests.java:1586` has the same swallow v2 had:

```java
try {
    discovery.ping("servicename");
}
catch (Exception e) {
    // we know it will throw exception b/c there is no service
    // running, we just care about it make the call
}
```

That comment is describing the bug. Once `discoverMany` is fixed, this becomes an assertion:

```java
assertTrue(discovery.ping("servicename").isEmpty());
```

It is the existing test that covers the fix — no new test needed for it.

## 4. Flaky and environment-specific test notes

These came out of running the v2 suite repeatedly on Windows while triaging. They cost real time to rule out, so they are worth knowing before chasing anything similar in v3.

**Transient "Unable to connect to NATS servers" — the main source of noise.** Seen four or more times across unrelated classes (`ConnectionStateConsistencyTests`, `NatsMessageTests` × 3, and others):

```
java.io.IOException: Unable to connect to NATS servers: [nats://127.0.0.1:12958]
    at io.nats.client.utils.TestBase$LongRunningNatsTestServer.run(TestBase.java:348)
```

It fails at connection setup, so the test body never runs, and it always passes on retry. It is not a property of the test that reports it — treat it as environment noise and re-run before investigating. It is also why the `test-retry` plugin matters locally.

**`maxFailures` can silently disable retrying.** v2's `build.gradle` sets `maxRetries = 4` alongside `maxFailures = 4`; `maxFailures` switches retrying off once that many tests have failed. The first Windows CI run failed exactly four tests, all with a single recorded attempt — so nothing was retried and there was no way to tell flaky from deterministic. Worth checking the equivalent setting in v3 before reading a failure count as meaningful.

**Windows local runs are Java 21, CI is Java 8** (v2). In v2 this makes four `ServiceTests` EqualsVerifier tests fail locally with *"Java 21 (65) is not supported by the current version of Byte Buddy"* — `equalsverifier:3.12.3` only supports up to Java 20. **v3 is not affected**: it already uses `equalsverifier:4.2.2`. v2 has now bumped to **3.19.4**, not 4.x — EqualsVerifier 4.x ships Java 17 bytecode (class major 61) and v2 still tests on Java 8, whereas 3.19.4 is Java 8 bytecode (major 52) with a Byte Buddy new enough for Java 21. v3 runs on Java 21 so 4.2.2 is correct there; just be aware the two repos are on different major lines for a reason.

**`nkill.bat` cannot see WSL.** Already recorded in v3's `FLAKY_TESTS_ANALYSIS.md`, and it bit again: WSL2 forwards localhost, so a stray `nats-server` under WSL holds a port invisibly to `taskkill`. Clean both sides — `pkill -9 nats-server` and `rm -f /tmp/nats_java_test*.conf` in WSL as well as `nclean` on Windows.

## What is not settled

`DrainTests.testSlowAsyncDuringDrainCanBeInterrupted` failed once on Windows CI with an unexpected exception reaching the error listener during drain, and has never reproduced locally (20/20 passing). v2 changed the assertion to report `listener.getExceptions()` so the next occurrence names the exception. If v3 has an equivalent assertion on a bare exception count during drain, the same change is cheap and worth making pre-emptively.
