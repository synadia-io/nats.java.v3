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
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.support.JsonUtils;

import java.time.Duration;

@SuppressWarnings({"CallToPrintStackTrace"})
public class Z1058 {

    public static void main(String[] args) {
        try (Connection nc = Nats.connect("nats://localhost:4222")) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            // Build the configuration
            StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name("hello")
                .subjects("world")
                .storageType(StorageType.Memory)
                .build();

            // Create the stream
            StreamInfo streamInfo = jsm.addStream(streamConfig);
            JsonUtils.printFormatted(streamInfo);

            JetStream js = nc.jetStream();
            PublishAck ack = js.publish("world", "one".getBytes());
            JsonUtils.printFormatted(ack);

            ack = js.publish("world", "two".getBytes());
            JsonUtils.printFormatted(ack);

            JetStreamSubscription sub = js.subscribe("world");
            Message m = sub.nextMessage(Duration.ofSeconds(3));
            m.ack();
            System.out.println("Message: " + m.getSubject() + " " + new String(m.getData()));
            JsonUtils.printFormatted(m.metaData());

            m = sub.nextMessage(Duration.ofSeconds(3));
            m.ack();
            System.out.println("Message: " + m.getSubject() + " " + new String(m.getData()));
            JsonUtils.printFormatted(m.metaData());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}