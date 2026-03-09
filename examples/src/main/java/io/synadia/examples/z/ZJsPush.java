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


public class ZJsPush {

    public static void main(String[] args) throws IOException {
        String[] split = ".foo.bar".split("\\.");
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .build();

        try (Connection nc = Nats.connect(options)) {
            String subject = "with space";

            createCleanMemStream(nc, "stream", "subject.>");

            JetStream js = nc.jetStream();

            nc.jetStreamManagement().addOrUpdateConsumer("stream",
                ConsumerConfiguration.builder()
                    .deliverSubject("del")
                    .filterSubject("subject." + subject).build());

            JetStreamSubscription sub = js.subscribe("sub", PushSubscribeOptions.builder().name("dur").build());

        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
