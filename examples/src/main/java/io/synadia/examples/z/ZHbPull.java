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

public class ZHbPull {
    public static final String STREAM = "hb-stream";
    public static final String SUBJECT = "hb-subject";
    public static final String CONSUMER = "hb-con";

    public static void main(String[] args) throws IOException {

        Options options = new Options.Builder()
            .server(Options.DEFAULT_URL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            createTestStream(nc.jetStreamManagement());
            JetStream js = nc.jetStream();

            PullSubscribeOptions pullSubOpts = ConsumerConfiguration.builder()
                .name(CONSUMER)
                .inactiveThreshold(1000)
                .buildPullSubscribeOptions();

            JetStreamSubscription sub = js.subscribe(SUBJECT, pullSubOpts);
            System.out.println("Pull 1");
            sub.pull(PullRequestOptions.builder(5).idleHeartbeat(300).expiresIn(1000).build());
            Thread.sleep(3000);
            System.out.println("Pull 2");
            sub.pull(PullRequestOptions.builder(5).idleHeartbeat(300).expiresIn(1000).build());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createTestStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}

        StreamConfiguration sc = StreamConfiguration.builder()
            .name(STREAM)
            .storageType(StorageType.Memory)
            .subjects(SUBJECT)
            .build();
        jsm.addStream(sc);
    }
}
