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
import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

public class ZPending {
    public static void main(String[] args) {
        try (Connection nc = Nats.connect("nats://[::FFFF:127.0.0.1]:4222")) {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name("stream")
                .subjects("subject")
                .storageType(StorageType.Memory)
                    .build();
            nc.jetStreamManagement().addStream(sc);

            nc.jetStreamManagement().addOrUpdateConsumer("stream", ConsumerConfiguration.builder()
                .ackPolicy(AckPolicy.None)
                .durable("durable")
                .deliverSubject("deliversubject")
                .build());

            JetStream js = nc.jetStream();

            for (int x = 1; x <= 10; x++) {
                nc.publish("subject", ("data" + x).getBytes());
            }

            PushSubscribeOptions pso = PushSubscribeOptions.bind("stream", "durable");
            JetStreamSubscription sub = js.subscribe(null, pso);
            for (int x = 1; x <= 5; x++) {
                System.out.println(sub.nextMessage(200).metaData());
            }

            for (int x = 11; x <= 20; x++) {
                nc.publish("subject", ("data" + x).getBytes());
            }

            for (int x = 6; x <= 20; x++) {
                System.out.println(sub.nextMessage(200).metaData());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
