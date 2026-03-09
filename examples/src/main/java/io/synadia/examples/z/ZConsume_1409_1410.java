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
import java.util.HashMap;
import java.util.Map;

public class ZConsume_1409_1410 {
    private static final String STREAM = "stream1409";
    private static final String SUBJECT = "subject1409";
    private static final String CONSUMER_NAME = "name1409";
    private static final String SERVER = "nats://localhost:4222";

    public static void main(String[] args) throws JetStreamApiException, InterruptedException, IOException {
        Options options = Options.builder().server(SERVER).build();
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            if (nc.getServerInfo().isOlderThanVersion("2.11")) {
                try {
                    jsm.deleteStream(STREAM);
                }
                catch (Exception ignore) {
                }

                try {
                    jsm.addStream(StreamConfiguration.builder()
                        .name(STREAM)
                        .storageType(StorageType.File)
                        .subjects(SUBJECT)
                        .build());
                }
                catch (Exception e) {
                    System.err.println("Fatal error, cannot create stream.");
                    System.exit(-1);
                }
            }

            Map<String, String> meta = new HashMap<>();
            meta.put("foo", "bar");
            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(CONSUMER_NAME)
                .metadata(meta)
                .build();
            PushSubscribeOptions pso = PushSubscribeOptions.builder()
                .stream(STREAM)
                .name(CONSUMER_NAME)
                .configuration(cc).build();

            JetStream js = jsm.jetStream();
            JetStreamSubscription sub = js.subscribe(null, pso);
            ConsumerInfo ci = sub.getConsumerInfo();
            System.out.println("Meta " + ci.getConsumerConfiguration().getMetadata());
        }
    }
}
