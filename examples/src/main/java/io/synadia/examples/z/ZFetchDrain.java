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

public class ZFetchDrain {
    private static final String URL = "nats://localhost:4222";
    private static final String STREAM = "FetchDrain";
    private static final String SUBJECT = "fd";

    public static void main(String[] args) {
        try (Connection conn = Nats.connect(URL)) {
            JetStreamManagement jsm = conn.jetStreamManagement();;
            JetStream js = conn.jetStream();

            createOrResetStream(jsm);

            for (int x = 0; x < 1000; x++) {
                js.publish(SUBJECT, null);
            }

            StreamContext sc = conn.getStreamContext(STREAM);

            // generic consumer
            ConsumerContext cc = sc.createOrUpdateConsumer(ConsumerConfiguration.builder().build());

            int red = 0;

            FetchConsumer fc = cc.fetch(FetchConsumeOptions.builder().maxMessages(10).build());
            Message m = fc.nextMessage();
            while (m != null) {
                m.ack();
                if (++red == 5) {
                    fc.stop();
                }
                m = fc.nextMessage();
            }
            System.out.println("Fetch\n  Read: " + red + "\n  Consumer Pending: " + fc.getConsumerInfo().getNumPending());

            IterableConsumer ic = cc.iterate(ConsumeOptions.builder().batchSize(10).build());
            red = 0;
            m = ic.nextMessage(1000);
            while (m != null) {
                m.ack();
                if (++red == 5) {
                    ic.stop();
                }
                m = ic.nextMessage(1000);
            }
            System.out.println("Iterable\n  Read: " + red + "\n  Consumer Pending: " + ic.getConsumerInfo().getNumPending());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createOrResetStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
        StreamConfiguration streamConfig =
            StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.Memory)
                .subjects(SUBJECT)
                .build();
        jsm.addStream(streamConfig);
    }
}