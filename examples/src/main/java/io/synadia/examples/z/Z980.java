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
import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.DeliverPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.synadia.examples.z.Z0Utils.createOrReplaceStream;

public class Z980 {

    public static final String STREAM = "str980-";
    public static final String SUBJECT = "sub980-";

    public static void main(String[] args) {
        try {
            int numberOfThreads = 5;

            Options options = new Options.Builder().build();

            List<String> streams = new ArrayList<>();
            List<String> subjects = new ArrayList<>();
            try (Connection nc = Nats.connect(options)) {
                JetStream js = nc.jetStream();
                for (int x = 0; x < numberOfThreads; x++) {
                    String stream = STREAM + x;
                    String subject = SUBJECT + x;
                    createOrReplaceStream(nc, stream, subject);
                    streams.add(stream);
                    subjects.add(subject);
                    js.publish(subject, null);
                }

                Callable<String> consumerRegistrationCallable = () -> {
                    try {
                        int random = ThreadLocalRandom.current().nextInt(0, 5);
                        String stream = streams.get(random);
                        String subject = subjects.get(random);
                        String durable = NUID.nextGlobalSequence();

                        System.out.println("Trying to register consumer for topic " + subject);
                        JetStreamSubscription sub = registerMessageHandler(nc, stream, subject, durable);
                        System.out.println("Registered " + sub.getConsumerName());
                        sub.pull(1);
                        Message m = sub.nextMessage(1000);
                        System.out.println("Message Received? " + m);
                    } catch (Exception e) {
                        System.out.println("!!! " + e);
                    }
                    return "registered";
                };

                List<Callable<String>> callables = new ArrayList<>();
                for (int i = 0; i < numberOfThreads; i++) {
                    callables.add(consumerRegistrationCallable);
                }

                ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
                List<Future<String>> futures = executor.invokeAll(callables);

                for (Future<String> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }

                System.exit(0);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    static JetStreamSubscription registerMessageHandler(Connection nc, String stream, String subject, String durableName) {
        try {
            ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                .durable(durableName)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(30)
                .maxAckPending(1)
                .maxDeliver(1)
                .deliverPolicy(DeliverPolicy.All)
                .filterSubject(subject)
                .build();

            return nc.jetStream().subscribe(subject,
                PullSubscribeOptions.builder()
                    .configuration(consumerConfiguration)
                    .stream(stream)
                    .build());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
