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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

public class MasterCard {

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder().server("nats://localhost:4222").build();
        String stream = "mc-stream";
        String subject = "mc-subject";
        int batchSize = 100; // 10 100 500 1000 other examples
        int repullAt = 50;   // try 10% 50% 80% of batch size

        try (Connection nc = Nats.connect(options)) {
            //setupStreamAndData(nc, stream, subject);

            JetStream js = nc.jetStream();
            JetStreamSubscription sub = js.subscribe(subject, PullSubscribeOptions.builder().build());
            JetStreamReader reader = sub.reader(batchSize, repullAt);
            long start = System.currentTimeMillis();
            int readCount = 0;
            Message m = reader.nextMessage(1000);
            while (m != null) {
                if (++readCount % 1000 == 0) {
                    System.out.println("Read message # " + readCount);
                }
                m.ack();
                m = reader.nextMessage(1000);
            }
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Finished reading " + readCount + " messages in " + elapsed + " milliseconds.");
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    public static void setupStreamAndData(Connection nc, String stream, String subject) throws IOException, JetStreamApiException {
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(stream)
                .storageType(StorageType.Memory)
                .subjects(subject)
                .build();
            nc.jetStreamManagement().addStream(sc);
            System.out.println("Created stream: '" + stream + "'");

            JetStream js = nc.jetStream();
            System.out.print("Publishing...");
            for (int x = 0; x < 300_000; x++) {
                if ((x+1) % 3000 == 0) {
                    System.out.print(".");
                }
                js.publish(subject, ("mc-data-" + x).getBytes());
            }
            System.out.println("complete.");
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + stream + "' " + e);
        }
    }
}
