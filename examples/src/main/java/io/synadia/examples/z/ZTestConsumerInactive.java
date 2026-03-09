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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

public class ZTestConsumerInactive {

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStream js = nc.jetStream();

            createStream(nc.jetStreamManagement());

//            MessageHandler mh = msg -> System.out.println("Received " + new String(msg.getData()).substring(2));
//            JetStreamSubscription sub = js.subscribe("sub", nc.createDispatcher(), mh, true);
//            sub.getConsumerInfo();
//            Thread.sleep(600000);

            JetStreamSubscription sub = js.subscribe("sub", ConsumerConfiguration.builder()
                .name("eph-pull")
                .ackPolicy(AckPolicy.None)
                .buildPullSubscribeOptions()
            );

//            JetStreamReader reader = sub.reader(100, 50);
//            long start = System.currentTimeMillis();
//            while (true) {
//                Message msg = reader.nextMessage(1000);
//                if (msg == null) {
//                    System.out.println();
//                }
//                System.out.println("Received " + new String(msg.getData()).substring(2));
//            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try {
            jsm.deleteStream("stream");
        }
        catch (Exception ignore) {}

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name("stream")
                .storageType(StorageType.File)
                .subjects("sub")
                .build();
            jsm.addStream(sc);
            System.out.println("Created stream: 'stream'");
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '' " + e);
        }
    }
}
