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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.support.Debug;

import java.io.IOException;

public class ZSimpleFailover {

    static final String STREAM = "zsf-stream";
    static final String SUBJECT = "zsf-subject";
    static final String NAME = "zsf-name";
    static final int MESSAGE_COUNT = 10;

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .connectionListener((c, t) -> System.out.println(t))
            .errorListener(new ErrorListener() {})
            .maxReconnects(-1)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = jsm.jetStream();

            try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
            createStream(jsm);

            PullSubscribeOptions pso = PullSubscribeOptions.builder()
                .stream(STREAM)
//                .configuration(ConsumerConfiguration.builder()
//                    .filterSubject(SUBJECT)
//                    .idleHeartbeat(1000)
//                    .build())
                .build();

            JetStreamSubscription sub = js.subscribe(SUBJECT, pso);
            PullRequestOptions pro = PullRequestOptions.builder(1)
                .expiresIn(60000)
                .noWait()
                .idleHeartbeat(30000)
                .build();
            Debug.info("PRO", pro.toJson());
            sub.pull(pro);

            Thread.sleep(700_000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createStream(JetStreamManagement jsm) {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.Memory)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
