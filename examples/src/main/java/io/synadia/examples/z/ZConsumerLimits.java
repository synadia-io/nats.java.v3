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

import io.synadia.client.Connection;
import io.synadia.client.JetStreamManagement;
import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.api.*;

import java.time.Duration;

public class ZConsumerLimits {
    public static void main(String[] args) {
        Options options = Options.builder()
            .server("nats://localhost:4222")
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            StreamConfiguration sc = StreamConfiguration.builder()
                .name("test-consumer-limits")
                .subjects("subject")
                .storageType(StorageType.Memory)
                .consumerLimits(ConsumerLimits.builder()
                    .inactiveThreshold(Duration.ofSeconds(30))
                    .build())
                .build();

            StreamInfo si = jsm.addStream(sc);
            System.out.println(si);

            ConsumerConfiguration cc = ConsumerConfiguration.builder().build();
            System.out.println(cc.toJson());

            ConsumerInfo ci = jsm.addOrUpdateConsumer("test-consumer-limits", cc);
            System.out.println(ci);

            cc = ConsumerConfiguration.builder().inactiveThreshold(Duration.ofSeconds(10)).build();
            System.out.println(cc.toJson());

            ci = jsm.addOrUpdateConsumer("test-consumer-limits", cc);
            System.out.println(ci);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
