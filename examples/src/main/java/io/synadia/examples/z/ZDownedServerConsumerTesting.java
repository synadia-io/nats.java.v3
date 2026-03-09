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
import java.util.List;

@SuppressWarnings({"BusyWait", "InfiniteLoopStatement"})
public class ZDownedServerConsumerTesting {

    static final String STREAM = "ConStream";
    static final String SUBJECT = "ConSubject";
    static final long INACTIVE_THRESH = 1000 * 60 * 60;

    static final StorageType STORAGE_TYPE = StorageType.File;
    static final int STREAM_REPLICAS = 3;
    static final int CONSUMER_REPLICAS = -1;
    static final int MESSAGE_COUNT = 1000;

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222,nats://localhost:5222,nats://localhost:6222,nats://localhost:7222,nats://localhost:8222")
            .errorListener(new ErrorListener() {})
            .maxReconnects(3)
            .build();

        try (Connection nc = Nats.connect(options)) {

            Thread.sleep(600_000);

            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            createStream(jsm); Thread.sleep(1000);
            for (int x = 0; x < MESSAGE_COUNT; x++) { js.publish(SUBJECT, ("data" + (x + 1)).getBytes()); }

            int mc = MESSAGE_COUNT;
            clearConsumers(jsm);
            String consumerName = NUID.nextGlobalSequence();
            ConsumerConfiguration.Builder builder = ConsumerConfiguration.builder()
                .name(consumerName)
                .inactiveThreshold(INACTIVE_THRESH);
            if (CONSUMER_REPLICAS > 0) {
                builder.numReplicas(CONSUMER_REPLICAS);
            }
            JetStreamSubscription sub = js.subscribe(SUBJECT, builder.buildPullSubscribeOptions());
            System.out.println("Consumer: " + consumerName);
            checkConsumer(jsm, consumerName, mc);

            int round = 0;
            while (true) {
                Thread.sleep(1000);
                System.out.println("\nRound " + (++round));
                try {
                    System.out.println("  Server: " + nc.getServerInfo().getPort());
                    System.out.println("  Consumers: " + jsm.getConsumerNames(STREAM));
                }
                catch (Exception e) {
                    continue;
                }
                checkConsumer(jsm, consumerName, mc);
                sub.pull(1);
                Message m = sub.nextMessage(1000);
                if (m == null) {
                    System.out.println("  NO MESSAGE");
                }
                else {
                    System.out.println("  Got Message");
                    m.ack();
                    mc--;
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void clearConsumers(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        List<String> list = jsm.getConsumerNames(STREAM);
        for (String c : list) {
            try {
                jsm.deleteConsumer(STREAM, c);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static void checkConsumer(JetStreamManagement jsm, String name, long pending) throws IOException, JetStreamApiException {
        boolean active = pending != -1;
        try {
            ConsumerInfo ci = jsm.getConsumerInfo(STREAM, name);
            if (!active) {
                System.err.println("  Consumer should NOT be active.");
            }
            if (pending == ci.getNumPending()) {
                System.out.println("  Consumer got matching pending: " + pending);
            }
            else {
                System.err.println("  Incorrect pending, expected " + pending + " got " +ci.getNumPending());
            }
        }
        catch (JetStreamApiException e) {
            System.out.println("  server error: " + e.getMessage());
            if (active) {
                System.err.println("  Consumer should be active.");
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
                .replicas(STREAM_REPLICAS)
                .storageType(STORAGE_TYPE)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
