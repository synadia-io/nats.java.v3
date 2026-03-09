// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.examples.z;

import io.synadia.client.*;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.impl.NatsJetStreamMetaData;
import io.synadia.client.support.Status;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ZFetchNoWaitPlusExpiresRaw {
    private static final String SERVER = "nats://localhost:4222";
    private static final String STREAM = "example";
    private static final String SUBJECT = "sub";
    private static final String CONSUMER = "con";

    public static void main(String[] args) {
        TestErrorListener pullStatusWarningListener = new TestErrorListener();
        Options options = Options.builder().server(SERVER).errorListener(pullStatusWarningListener).build();
        try (Connection conn = Nats.connect(options)) {
            JetStreamManagement jsm = conn.jetStreamManagement();;
            JetStream js = jsm.jetStream();
            resetStream(jsm);

            jsm.addOrUpdateConsumer(STREAM, ConsumerConfiguration.builder()
                .name(CONSUMER)
                .inactiveThreshold(100000) // I could have used a durable, but this is long enough for the test
                .filterSubject(SUBJECT)
                .build());

            JetStreamSubscription sub = js.subscribe(null,
                PullSubscribeOptions.fastBind(STREAM, CONSUMER));

            PullRequestOptions pro = PullRequestOptions.builder(10).noWait().build();

            start("No Wait, No Messages");
            pullStatusWarningListener.resetLatch();
            sub.pull(pro);
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();

            start("No Wait, One Message");
            js.publish(SUBJECT, "DATA-A".getBytes());
            sub.pull(pro);
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();

            start("No Wait, Two Messages");
            js.publish(SUBJECT, "DATA-B".getBytes());
            js.publish(SUBJECT, "DATA-C".getBytes());
            sub.pull(pro);
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();

            start("With Expires, No Messages");
            pro = PullRequestOptions.noWait(10).expiresIn(100).build();
            sub.pull(pro);
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();

            start("With Expires, One to Three Message");
            pro = PullRequestOptions.noWait(10).expiresIn(1000).build();
            sub.pull(pro);
            js.publish(SUBJECT, "DATA-D".getBytes());
            js.publish(SUBJECT, "DATA-E".getBytes());
            js.publish(SUBJECT, "DATA-F".getBytes());
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();

            start("With Expires, Leftovers");
            sub.pull(pro);
            readMessages(sub);
            pullStatusWarningListener.waitForLatch();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readMessages(JetStreamSubscription sub) throws InterruptedException {
        int count = 0;
        long start = System.nanoTime();
        Message m = sub.nextMessage(1000);
        while (m != null) {
            ++count;
            m.ack();
            info(messageString(m));
            m = sub.nextMessage(1000);
        }
        long elapsed = System.nanoTime() - start;
        long ems = elapsed / 1_000_000;
        info("Got " + count + " messages in " + elapsed + " ns (" + ems + " ms)");
    }

    private static String messageString(Message m) {
        NatsJetStreamMetaData meta = m.metaData();
        return "Message data='" + new String(m.getData()) + '\'' +
            ", delivered=" + meta.deliveredCount() +
            ", streamSeq=" + meta.streamSequence() +
            ", consumerSeq=" + meta.consumerSequence() +
            ", pending=" + meta.pendingCount();
    }

    private static void start(String s) {
        System.out.println("\n[" + System.nanoTime() + "] " + s);
    }

    private static void info(String s) {
        System.out.println("[" + System.nanoTime() + "] " + s);
    }

    static class TestErrorListener implements ErrorListener {

        public CountDownLatch pullStatusWarningLatch;
        public Status lastStatus;
        public long statusTime;

        public void resetLatch() {
            pullStatusWarningLatch = new CountDownLatch(1);
            lastStatus = null;
            statusTime = 0;
        }

        public void waitForLatch() throws InterruptedException {
            pullStatusWarningLatch.await(10, TimeUnit.SECONDS);
            info("Pull Status Warning: " + lastStatus + " @ " + statusTime);
        }

        @Override
        public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
            statusTime = System.nanoTime();
            pullStatusWarningLatch.countDown();
            lastStatus = status;
        }
    }

    private static void resetStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}

        // Create the stream
        StreamConfiguration streamConfig =
            StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.Memory)
                .subjects(SUBJECT)
                .build();
        jsm.addStream(streamConfig);
    }
}