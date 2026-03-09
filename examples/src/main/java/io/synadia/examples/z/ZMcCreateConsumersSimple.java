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
import io.synadia.client.api.StreamConfiguration;

public class ZMcCreateConsumersSimple {

    public static final String SERVERS = "localhost:4222";

    public static void main(String[] args) {
        try (Connection nc = Nats.connect(SERVERS)) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            try {jsm.deleteStream("stream");} catch (Exception ignore) {}

            jsm.addStream(StreamConfiguration.builder()
                .name("stream")
                .subjects("sub1", "sub2")
                .build());

            System.out.println("\n");
            JetStreamManagement jsmIn = nc.jetStreamManagement(JetStreamOptions.builder().optOut290ConsumerCreate(false).build());
            jsmIn.addOrUpdateConsumer("stream", ConsumerConfiguration.builder().build());

            System.out.println("\n");
            JetStreamOptions jsoOptOut = JetStreamOptions.builder().optOut290ConsumerCreate(true).build();
            JetStreamManagement jsmOptOut = nc.jetStreamManagement(jsoOptOut);
            JetStream jsOptOut = nc.jetStream(jsoOptOut);
            jsmOptOut.addOrUpdateConsumer("stream", ConsumerConfiguration.builder().filterSubject("sub1").build());
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }
}
