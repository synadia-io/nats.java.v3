package io.synadia.client.other;

import io.synadia.client.*;
import io.synadia.client.impl.NatsConnection;

import java.time.Duration;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;

/* Program to reproduce #231 */
public class ReconnectCheck {
    private static long received;
    private static long published;

    private static long lastReceivedId;

    @SuppressWarnings("resource")
    public static void main(String []args) throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            NatsConnection natsIn = Nats.connect(buildOptions("IN", ts));
            NatsConnection natsOut = Nats.connect(buildOptions("OUT", ts));
            Dispatcher natsDispatcher = natsIn.createDispatcher(m -> {
                long receivedId = Long.parseLong(new String(m.getData()));

                if (receivedId < lastReceivedId) {
                    System.out.printf("##### Tid: %d, Received stale data: got %d, last received %d%n", Thread.currentThread().threadId(), receivedId, lastReceivedId);
                }
                lastReceivedId = receivedId;
                if (received++ % 1_000_000 == 0) {
                    System.out.printf("Tid: %d, Received %d messages%n", Thread.currentThread().threadId(), received);
                }
            });

            natsDispatcher.subscribe("foo");

            long id = 0;

            //noinspection InfiniteLoopStatement
            while (true) {
                for (int i = 0; i < 100_000; i++) {
                    natsOut.publish("foo", ("" + id++).getBytes());
                    if (published++ % 1_000_000 == 0) {
                        System.out.printf("Tid: %d, Published %d messages.%n", Thread.currentThread().threadId(), published);
                    }
                }
                sleep(1);
            }
        }
    }

    private static Options buildOptions(String name, NatsTestServer ts) {
        return optionsBuilder(ts)
            .connectionName(name)
            .reconnectWait(Duration.ofSeconds(1))
            .connectionTimeout(Duration.ofSeconds(5))
            .pingInterval(Duration.ofMillis(100))
            .reconnectBufferSize(-1) // Do not cache any messages when Nats connection is down.// Do not cache any messages when Nats connection is down.
            .connectionListener((conn, event, time, details) ->
                System.out.printf("Tid: %d, %s, NATS: connection event - %s, connected url: %s. servers: %s %n", Thread.currentThread().threadId(), name, event, conn.getConnectedUrl(), conn.getServers()))
            .errorListener(new ErrorListener() {
                @Override
                public void slowConsumerDetected(NatsConnection conn, Consumer consumer) {
                    System.out.printf("Tid: %d, %s, %s: Slow Consumer%n", Thread.currentThread().threadId(), name, conn.getConnectedUrl());
                }

                @Override
                public void exceptionOccurred(NatsConnection conn, Exception exp) {
                    System.out.printf("Tid: %d, %s, Nats '%s' exception: %s%n", Thread.currentThread().threadId(), name, conn.getConnectedUrl(), exp.toString());
                }

                @Override
                public void errorOccurred(NatsConnection conn, String error) {
                    System.out.printf("Tid: %d, %s, Nats '%s': Error %s%n", Thread.currentThread().threadId(), name, conn.getConnectedUrl(), error);
                }
            })
            .build();
    }
}
