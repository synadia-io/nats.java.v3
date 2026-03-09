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

public class ZForm3 {
    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            jsm.addStream(StreamConfiguration.builder()
                .name("stream_a")
                .storageType(StorageType.Memory)
                .subjects("stream_a.>")
                .build());
            jsm.addOrUpdateConsumer("stream_a", ConsumerConfiguration.builder()
                .durable("stream_a_consumer")
                .filterSubject("stream_a.>")
                .build());

            PullSubscribeOptions.Builder builder = PullSubscribeOptions.builder()
                .stream("stream_a")
                .bind(true)
                .durable("stream_a_consumer");

            JetStream js = nc.jetStream();
            js.publish("stream_a.A", "A1".getBytes());
            js.publish("stream_a.B", "B1".getBytes());
            js.publish("stream_a.A", "A2".getBytes());
            js.publish("stream_a.B", "B2".getBytes());

//            JetStreamSubscription sub = js.subscribe("stream_a.>", builder.build());
            JetStreamSubscription sub = js.subscribe(null, builder.build());
            sub.pullExpiresIn(4, 10000);
            System.out.println(sub.nextMessage(1000));
            System.out.println(sub.nextMessage(1000));
            System.out.println(sub.nextMessage(1000));
            System.out.println(sub.nextMessage(1000));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
