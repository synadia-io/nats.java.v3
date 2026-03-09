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

public class ZPullMc {

    static String STREAM = "McStream";
    static String SUBJECT = "McSubject";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            // a) Create a File-Backed Stream with a LIMITS policy.
            createStream(jsm);

            // b) Create an Ephemeral PULL Consumer and Subscription with an Inactive Threshold of 5 secs.
            String consumerName = "sub-still-alive";
            System.out.println();
            PullSubscribeOptions plso = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(5000)
                .buildPullSubscribeOptions();
            JetStreamSubscription sub = js.subscribe(SUBJECT, plso);
            checkConsumer(jsm, consumerName, "after consumer creation");

            // c) Publish 100 messages.
            for (int x = 0; x < 100; x++) {
                js.publish(SUBJECT, ("data" + (x+1)).getBytes());
            }
            checkConsumer(jsm, consumerName, "after publish");

            // d) Consume 50 messages with the first PULL (pull(50)), 50 more messages are still pending in the stream to be consumed.
            sub.pull(PullRequestOptions.builder(50).expiresIn(10000).idleHeartbeat(1000).build());
            for (int ss = 1; ss <= 50; ss++) {
                checkMessage(ss, sub);
            }
            checkConsumer(jsm, consumerName, "after consume first 50");

            // e) Wait for 10 secs, the subscription is still active.
            Thread.sleep(10000);

            // what is the state of the sub?
            checkConsumer(jsm, consumerName, "after sleep");

            sub.pull(PullRequestOptions.builder(50).expiresIn(10000).idleHeartbeat(1000).build());
            for (int ss = 51; ss <= 100; ss++) {
                checkMessage(ss, sub);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkMessage(long ss, JetStreamSubscription sub) throws InterruptedException {
        Message m = sub.nextMessage(1000);
        if (m != null) {
            m.ack();
            if (ss != m.metaData().streamSequence()) {
                System.err.println("Incorrect stream sequence, expected: " + ss + " received: " + m.metaData().streamSequence());
            }
        }
        else {
            System.err.println("No Message");
        }
    }

    private static void checkConsumer(JetStreamManagement jsm, String name, String note) throws IOException, JetStreamApiException {
        System.out.println(note);
        try {
            ConsumerInfo ci = jsm.getConsumerInfo(STREAM, name);
            System.out.println("  Consumer is active. Pending: " + ci.getNumPending());
        }
        catch (JetStreamApiException e) {
            System.err.println("  server error: " + e.getMessage());
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
