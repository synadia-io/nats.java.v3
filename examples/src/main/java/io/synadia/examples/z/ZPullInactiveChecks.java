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
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

public class ZPullInactiveChecks {

    static String STREAM = "InactiveStream";
    static String SUBJECT = "InactiveSubject";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        long threshold = 500;
        long thresholdSleep = threshold + (int)(threshold * .1); // 10% more
        // long thresholdSleep = threshold + (threshold * 2); // double

        String consumerName = null;
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            createStream(jsm);

            js.publish(SUBJECT, "data1".getBytes());
            js.publish(SUBJECT, "data2".getBytes());

            // never do a pull, go inactive
            consumerName = "no-pull-before-inactive";
            System.out.println("\nConsumer: " + consumerName);
            PullSubscribeOptions plso = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(threshold)
                .buildPullSubscribeOptions();
            js.subscribe(SUBJECT, plso);
            checkConsumer(jsm, consumerName, 2, "after creation");

            Thread.sleep(thresholdSleep);
            checkConsumer(jsm, consumerName, -1, "after sleep");

            // pull 1 of 2, go inactive
            consumerName = "pull-then-inactive-with-pending";
            System.out.println("\nConsumer: " + consumerName);
            plso = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(threshold)
                .buildPullSubscribeOptions();
            JetStreamSubscription sub = js.subscribe(SUBJECT, plso);
            checkConsumer(jsm, consumerName, 2, "after creation");

            sub.pull(1);
            checkMessage(1, sub);
            checkConsumer(jsm, consumerName, 1, "after messages");

            Thread.sleep(thresholdSleep);
            checkConsumer(jsm, consumerName, -1, "after sleep");

            // pull 2 of 2, go inactive
            consumerName = "pull-then-inactive-none-pending";
            System.out.println("\nConsumer: " + consumerName);
            plso = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(threshold)
                .buildPullSubscribeOptions();
            sub = js.subscribe(SUBJECT, plso);
            checkConsumer(jsm, consumerName, 2, "after creation");

            sub.pull(2);
            checkMessage(1, sub);
            checkMessage(2, sub);
            checkConsumer(jsm, consumerName, 0, "after messages");

            Thread.sleep(thresholdSleep);
            checkConsumer(jsm, consumerName, -1, "after sleep");

            // closing connection should deactivate the consumer
            consumerName = "close-connection-consumer-will-go-away";
            System.out.println("\nConsumer: " + consumerName);
            plso = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(threshold)
                .buildPullSubscribeOptions();
            js.subscribe(SUBJECT, plso);
            checkConsumer(jsm, consumerName, 2, "after creation");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try (Connection nc = Nats.connect(options)) {
            Thread.sleep(thresholdSleep);
            checkConsumer(nc.jetStreamManagement(), consumerName, -1, "after sleep");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkMessage(long ss, JetStreamSubscription sub) throws InterruptedException {
        Message m = sub.nextMessage(1000);
        m.ack();
        if (ss == m.metaData().streamSequence()) {
            System.out.println("    Message got matching stream sequence: " + ss);
        }
        else {
            System.err.println("    Incorrect stream sequence, expected: " + ss + " received: " + m.metaData().streamSequence());
        }
    }

    private static void checkConsumer(JetStreamManagement jsm, String name, long pending, String note) throws IOException, JetStreamApiException {
        System.out.println("  Checking consumer " + note);
        boolean active = pending != -1;
        try {
            ConsumerInfo ci = jsm.getConsumerInfo(STREAM, name);
            if (!active) {
                System.err.println("    Consumer should NOT be active.");
            }
            if (pending == ci.getNumPending()) {
                System.out.println("    Consumer got matching pending: " + pending);
            }
            else {
                System.err.println("    Incorrect pending, expected " + pending + " got " +ci.getNumPending());
            }

            System.out.println("    Expecting pending of " + pending + ". Got " + ci.getNumPending());
        }
        catch (JetStreamApiException e) {
            System.out.println("    server error: " + e.getMessage());
            if (active) {
                System.err.println("    Consumer should be active.");
            }
        }
    }

    public static void createStream(JetStreamManagement jsm) {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
