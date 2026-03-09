package io.synadia.examples.z;

import io.synadia.client.*;
import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.impl.ErrorListenerConsoleImpl;
import io.synadia.client.impl.NatsJetStreamMetaData;

import java.io.IOException;
import java.util.List;

import static io.synadia.client.support.JsonUtils.getFormatted;

public class ZNvidiaLargePullSlow {

    static String URL = "js:js@vnd.east.nats.dev";
//    static String URL = "js:js@vnd.west.nats.dev";
    static String STREAM = "test";
//    static String STREAM = "test-limits";
    static String CONSUMER_PREFIX = "nv-java-";
    static final int MAX_ACK_PENDING = 65536;
    static final int MAX_MESSAGES = 500;
    static final int MAX_BYTES = 32 * 1024 * 1024;
    static final long CONSUMER_CHECK_TIME = 10000;
    static final String MANUAL_CONSUMER = "A"; // null if not using

    @SuppressWarnings("ConstantValue")
    public static void main(String[] args) {
        Options options = Options.builder()
            .server(URL)
            .connectionListener((conn, event) -> System.out.println("CL: " + event))
            .errorListener(new ErrorListenerConsoleImpl())
            .build();
        try (Connection nc = Nats.connect(options)) {
            System.out.println("CONNECTED: " + nc.getServerInfo());
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            removeExistingTestConsumers(jsm);

            // MAKE A NEW CONSUMER TO TRY

            String consumerName = MANUAL_CONSUMER == null
                ? CONSUMER_PREFIX + NUID.nextGlobalSequence() // random suffix
                : MANUAL_CONSUMER;

            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(consumerName)
                .maxAckPending(MAX_ACK_PENDING)
                .ackPolicy(AckPolicy.Explicit)
                .build();

            StreamContext sc = js.getStreamContext(STREAM);
            ConsumerContext ctx = null;
            if (MANUAL_CONSUMER == null) {
                ctx = sc.createOrUpdateConsumer(cc);
            }
            else {
                ctx = sc.getConsumerContext(consumerName);
            }
            System.out.println("CONSUMER: " + getFormatted(ctx.getCachedConsumerInfo().getConsumerConfiguration()));
            Thread checkConsumerThread = new Thread(() -> checkConsumer(consumerName));
            checkConsumerThread.start();

            FetchConsumeOptions fco = FetchConsumeOptions.builder()
                .max(MAX_BYTES, MAX_MESSAGES)
                .build();
            while (true) {
                System.out.println("START FETCH: " + fco.toJson());
                long bytes = 0;
                FetchConsumer fc = ctx.fetch(fco);
                while (!fc.isFinished()) {
                    Message msg = fc.nextMessage();
                    if (msg == null) {
                        System.out.println("FETCH: No message. Finished? " + fc.isFinished());
                        Thread.sleep(50);
                    }
                    else {
                        long cbc = msg.consumeByteCount();
                        bytes += cbc;
                        System.out.println("FETCHED: (" + cbc + "/" + bytes + ") " + toString(msg));
                        msg.ack();
                    }
                }
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void checkConsumer(String consumerName) {
        boolean sleep = true;
        Options options = Options.builder()
            .server(URL)
            .connectionListener((conn, event) -> System.out.println("CL: " + event))
            .errorListener(new ErrorListenerConsoleImpl())
            .build();
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            while (true) {
                try {
                    Thread.sleep(CONSUMER_CHECK_TIME);
                    ConsumerInfo ci = jsm.getConsumerInfo(STREAM, consumerName);
                    System.out.println("CHECK CONSUMER: " + toString(ci));
                }
                catch (IOException | JetStreamApiException e) {
                    System.out.println("CHECK CONSUMER EX: " + e.getMessage());
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void removeExistingTestConsumers(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        List<ConsumerInfo> ciList = jsm.getConsumers(STREAM);
        for (ConsumerInfo ci : ciList) {
            if (ci.getName().startsWith(CONSUMER_PREFIX)) {
                jsm.deleteConsumer(STREAM, ci.getName());
            }
        }
    }

    private static String toString(Message msg) {
        NatsJetStreamMetaData meta = msg.metaData();
        return "StreamSeq: " + meta.streamSequence() + " | "
            + "ConSeq: " + meta.consumerSequence() + " | "
            + "Delivered: " + meta.deliveredCount() + " | "
            + "Pending: " + meta.pendingCount();
    }

    private static String toString(ConsumerInfo ci) {
        return "Waiting: " + ci.getNumWaiting() + " | "
            + "Delivered: " + ci.getDelivered() + " | "
            + "Redelivered: " + ci.getRedelivered() + " | "
            + "Pending: " + ci.getNumAckPending();
    }
}