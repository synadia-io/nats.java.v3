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

import java.io.IOException;

public class ZFetchNoWaitPlusExpiresSimple {
    private static final String SERVER = "nats://localhost:4222";
    private static final String STREAM = "example";
    private static final String SUBJECT = "sub";
    private static final String CONSUMER = "con";

    public static void main(String[] args) {
        Options options = Options.builder().server(SERVER).errorListener(new ErrorListener() {}).build();
        try (Connection conn = Nats.connect(options)) {
            JetStreamManagement jsm = conn.jetStreamManagement();;
            JetStream js = jsm.jetStream();
            resetStream(jsm);

            jsm.addOrUpdateConsumer(STREAM, ConsumerConfiguration.builder()
                .name(CONSUMER)
                .inactiveThreshold(100000) // I could have used a durable, but this is long enough for the test
                .filterSubject(SUBJECT)
                .build());

            ConsumerContext cc = conn.getConsumerContext(STREAM, CONSUMER);
            FetchConsumeOptions fco = FetchConsumeOptions.builder().maxMessages(10).noWait().build();

            start("No Wait, No Messages");
            FetchConsumer fc = cc.fetch(fco);
            readMessages(fc);

            start("No Wait, One Message");
            js.publish(SUBJECT, "DATA-A".getBytes());
            fc = cc.fetch(fco);
            readMessages(fc);

            start("No Wait, Two Messages");
            js.publish(SUBJECT, "DATA-B".getBytes());
            js.publish(SUBJECT, "DATA-C".getBytes());
            fc = cc.fetch(fco);
            readMessages(fc);

            start("With Expires, No Messages");
            fco = FetchConsumeOptions.builder().maxMessages(10).noWaitExpiresIn(1000).build();
            fc = cc.fetch(fco);
            readMessages(fc);

            start("With Expires, One to Three Message");
            fco = FetchConsumeOptions.builder().maxMessages(10).noWaitExpiresIn(1000).build();
            fc = cc.fetch(fco);
            js.publish(SUBJECT, "DATA-D".getBytes());
            js.publish(SUBJECT, "DATA-E".getBytes());
            js.publish(SUBJECT, "DATA-F".getBytes());
            readMessages(fc);

            start("With Long (Default) Expires, Leftovers");
            fc = cc.fetch(fco);
            readMessages(fc);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readMessages(FetchConsumer fc) throws InterruptedException, JetStreamStatusCheckedException {
        int count = 0;
        long start = System.nanoTime();
        while (!fc.isFinished()) {
            Message m = fc.nextMessage();
            if (m != null) {
                info(messageString(m));
                m.ack();
                ++count;
            }
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