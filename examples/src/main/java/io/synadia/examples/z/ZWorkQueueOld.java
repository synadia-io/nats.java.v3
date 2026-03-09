// Copyright 2015-2018 The NATS Authors
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
import io.synadia.client.api.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class ZWorkQueueOld {
    public static final String STREAM = "wqstream";
    public static final String MIRROR_STREAM = "wqmirror";
    public static final String SUBJECT = "wqsub";
    public static final String CONSUMER = "wqcon";

    public static final boolean FIRST_RUN = false;
    public static final boolean DO_NAK = false;

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            if (FIRST_RUN) {
                setupStream(jsm);
                publishMessages(jsm.jetStream());
            }

//            oldPull(js);

            StreamContext sc = jsm.jetStream().getStreamContext(STREAM);
            ConsumerContext cc = sc.createOrUpdateConsumer(newConsumerConfiguration());
            cc.fetchMessages(1);
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private static void oldPull(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        createConsumer(jsm);

        PullSubscribeOptions pso = PullSubscribeOptions.bind(STREAM, CONSUMER);
        JetStreamSubscription sub = jsm.jetStream().subscribe(null, pso);

        for (int x = 1; x <= 10; x++) {
            List<Message> messages = sub.fetch(1, 1000);
            if (messages.isEmpty()) {
                System.out.println("No Message");
            }
            else {
                Message m = messages.get(0);
                System.out.println(m);
                if (x == 10) {
                    if (DO_NAK) {
                        m.nak();
                    }
                    System.exit(-1);
                }
                m.ack();
            }
        }
    }

    private static void createConsumer(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        jsm.addOrUpdateConsumer(STREAM,
            newConsumerConfiguration());
    }

    private static ConsumerConfiguration newConsumerConfiguration() {
        return ConsumerConfiguration.builder()
            .name(CONSUMER)
            // .durable(CONSUMER)
            .ackWait(Duration.ofMinutes(2))
            .filterSubject(SUBJECT)
            .build();
    }

    private static void publishMessages(JetStream js) {
        // seed the stream with messages.
        int counter = 0;
        while (counter++ < 100) {
            try {
                js.publish(SUBJECT, ("data" + counter).getBytes());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void setupStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
        try { jsm.deleteStream(MIRROR_STREAM); } catch (Exception ignore) {}

        // Create the mirror
        jsm.addStream(
            StreamConfiguration.builder()
                .name(MIRROR_STREAM)
                .subjects(SUBJECT)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(Duration.ofDays(1))
                .replicas(1)
                .storageType(StorageType.File)
                .discardPolicy(DiscardPolicy.Old)
                .allowDirect(true)
                .build()
        );

        // Create the main stream
        jsm.addStream(
            StreamConfiguration.builder()
                .name(STREAM)
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .replicas(1)
                .storageType(StorageType.File)
                .discardPolicy(DiscardPolicy.Old)
                .allowDirect(true)
                .mirrorDirect(true)
                .mirror(
                    Mirror.builder()
                        .name(MIRROR_STREAM)
                        .build()
                )
                .build()
        );

        // Publish messages to the stream
        JetStream jetStream = jsm.jetStream();
        for (int i = 0; i < 100; i++) {
            jetStream.publish(SUBJECT, String.valueOf(i).getBytes());
        }
    }
}
