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
import java.time.ZonedDateTime;

public class ZDowngradeMeta {
    private static final String SERVER = "nats://localhost:4222";

    private static final String STREAM = "stream";
    private static final String SUBJECT = "sub";
    private static final String CONSUMER = "con";

    public static void main(String[] args) throws Exception {
        Options options = Options.builder().server(SERVER).build();
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            // only run setup(jsm, js); once, against a 2.10 server
            // setup(jsm, js);

            // run this part always
            // - first time, with the setup call against a 2.10 server
            // - second time, against 2.10 just do not run setup()
            // - third time, after upgrade to 2.11 (do not run setup())
            // - fourth time, after downgrade to 2.10 (do not run setup())
            // just getting the consumer info, the server will have return metadata in 2.11 but not in 2.10
            publish(js, 100);
            subscribeAndRead(js);
            reportConsumerInfo(jsm);
        }
    }

    // setup
    // - creates all streams fresh
    // - publishes messages to consume
    // - subscribes to consumers
    //   - creating them
    //   - read all the messages to advance their sequence state
    private static void setup(JetStreamManagement jsm, JetStream js) throws Exception {
        try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
        try {
            jsm.addStream(StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .build());
        }
        catch (Exception e) {
            System.err.println("Fatal error, cannot create stream.");
            System.exit(-1);
        }
    }

    private static void publish(JetStream js, int count) throws IOException, JetStreamApiException {
        String prefix = ZonedDateTime.now() + "-" + NUID.nextGlobal() + "-";
        for (int m = 1; m <= count; m++) {
            js.publish(SUBJECT, (prefix + m).getBytes());
        }
    }

    private static void subscribeAndRead(JetStream js) throws IOException, JetStreamApiException, InterruptedException {
        PushSubscribeOptions pso = getPushSubscribeOptions();
        JetStreamSubscription sub = js.subscribe(null, pso);
        int count = 0;
        long startStreamSeq = Long.MAX_VALUE;
        long startConsumerSeq = Long.MAX_VALUE;
        long lastStreamSeq = -1;
        long lastConsumerSeq = -1;
        Message m = sub.nextMessage(1000);
        while (m != null) {
            ++count;
            lastStreamSeq = m.metaData().streamSequence();
            lastConsumerSeq = m.metaData().consumerSequence();
            startStreamSeq = Math.min(startStreamSeq, lastStreamSeq);
            startConsumerSeq = Math.min(startStreamSeq, lastConsumerSeq);
            m.ack();
            m = sub.nextMessage(1000);
        }
        sub.unsubscribe();
        System.out.println("Received: " + count + " messages | Stream Seq: " + startStreamSeq + "-" + lastStreamSeq + " | Consumer Seq: " + startConsumerSeq + "-" + lastConsumerSeq);
    }

    private static PushSubscribeOptions getPushSubscribeOptions() {
        return PushSubscribeOptions.builder()
            .stream(STREAM)
            .configuration(
                ConsumerConfiguration.builder()
                    .filterSubject(SUBJECT)
                    .durable(CONSUMER)
                    .build())
            .build();
    }

    private static void reportConsumerInfo(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        ConsumerInfo ci = jsm.getConsumerInfo(STREAM, CONSUMER);
        System.out.println("Consumer: " + ci.getName());
        System.out.println("  sequence info");
        System.out.println("    stream sequence: " + ci.getDelivered().getStreamSequence());
        System.out.println("    consumer sequence: " + ci.getDelivered().getConsumerSequence());
        System.out.println("  meta data: " + ci.getConsumerConfiguration().getMetadata());
    }
}
