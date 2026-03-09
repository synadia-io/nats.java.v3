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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Z1148 {
    public static String STREAM = "stream";
    public static String SUBJECT = "subject";
    public static String GROUP = "group";
    public static String DURABLE = "durable";

    public static void main(String[] args) {
        try {
            try (Connection connection = Nats.connectReconnectOnConnect()) {
                JetStreamManagement jsm = connection.jetStreamManagement();
                createStream(jsm);
                for (int x = 0; x < 20; x++) {
                    jsm.jetStream().publish(SUBJECT, null);
                }

                ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                    .filterSubject(SUBJECT)
                    .durable(DURABLE)
                    .deliverGroup(GROUP)
                    .maxAckPending(1)
                    .build();

                StreamContext streamContext = connection.getStreamContext(STREAM);

                streamContext.createOrUpdateConsumer(consumerConfiguration);

                ConsumerContext consumerContext1 = streamContext.getConsumerContext(DURABLE);
                ConsumerContext consumerContext2 = streamContext.getConsumerContext(DURABLE);

                CountDownLatch latch1 = new CountDownLatch(5);
                CountDownLatch latch2 = new CountDownLatch(5);

                MessageHandler handler1 = msg -> {
                    System.out.println("Handler 1 received " + msg.metaData().streamSequence());
                    msg.ack();
                    latch1.countDown();
                };

                MessageHandler handler2 = msg -> {
                    System.out.println("Handler 2 received " + msg.metaData().streamSequence());
                    msg.ack();
                    latch2.countDown();
                };

                consumerContext1.consume(handler1);
                consumerContext2.consume(handler2);

                latch1.await();
                latch2.await();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.Memory)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
            System.out.println("Created stream: '" + STREAM + "'");
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
