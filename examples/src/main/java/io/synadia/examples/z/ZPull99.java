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
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.examples.jetstream.NatsJsUtils.createCleanMemStream;

public class ZPull99 {
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

            createCleanMemStream(nc, "stream", "subject");

            jsm.addOrUpdateConsumer("stream", ConsumerConfiguration.builder().durable("durGt").filterSubject(">").build());
            jsm.addOrUpdateConsumer("stream", ConsumerConfiguration.builder().durable("durA").filterSubject("subject.A").build());

            Dispatcher d = nc.createDispatcher();

            AtomicInteger countGt = new AtomicInteger();
            PullSubscribeOptions psoGt = PullSubscribeOptions.bind("stream", "durGt");
            JetStreamSubscription subGt = js.subscribe(null, d,
                msg -> {
                    countGt.incrementAndGet();
                    System.out.println("durGt -> " + msg.getSubject() + " " + new String(msg.getData()));
                },
                psoGt);
            subGt.pullExpiresIn(10, 1000);

            AtomicInteger countA = new AtomicInteger();
            PullSubscribeOptions psoA = PullSubscribeOptions.bind("stream", "durA");
            JetStreamSubscription subA = js.subscribe(null, d,
                msg -> {
                    countA.incrementAndGet();
                    System.out.println("durA -> " + msg.getSubject() + " " + new String(msg.getData()));
                },
                psoA);
            subA.pullExpiresIn(10, 1000);

            for (int x = 0; x < 10; x++) {
                if (x % 2 == 0) {
                    js.publish("subject.A", ("" + x).getBytes());
                }
                else {
                    js.publish("subject.B", ("" + x).getBytes());
                }
            }

            Thread.sleep(2000); // just give time for the message to come across
            System.out.println("subGt got " + countGt.get() + " messages.");
            System.out.println("subA got " + countA.get() + " messages.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
