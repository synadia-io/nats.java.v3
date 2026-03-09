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

import java.io.IOException;

import static io.synadia.examples.jetstream.NatsJsUtils.createCleanMemStream;
import static io.synadia.examples.jetstream.NatsJsUtils.publish;

public class ZSub {
    public static final String STREAM = "stream";
    public static final String SUBJECT = "subject";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
//            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            createCleanMemStream(nc, "stream", "subject", "subject2");
            publish(js, "subject", 200, 1);

            jsm.addOrUpdateConsumer("stream",
                ConsumerConfiguration.builder().durable("dur1").deliverGroup("del")
                    .filterSubjects("subject", "subject")
                    .build());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
