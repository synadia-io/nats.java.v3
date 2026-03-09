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
import java.util.concurrent.ThreadLocalRandom;

public class ZTestConsumerInactive2 {

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStream js = nc.jetStream();

            createStream(nc.jetStreamManagement());

            byte[] data = new byte[2000];
            ThreadLocalRandom.current().nextBytes(data);
            js.publish("sub", data);

            JetStreamSubscription sub = js.subscribe("sub", ConsumerConfiguration.builder()
                .name("eph-pull")
                .ackPolicy(AckPolicy.None)
                .buildPullSubscribeOptions()
            );

            sub.pull(PullRequestOptions.builder(-1)
                .maxBytes(1000)
                .build());

            Message m = sub.nextMessage(1000);
            if (m == null) {
                System.out.println("No Message");
            }
            else {
                System.out.println(m.metaData());
            }
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
