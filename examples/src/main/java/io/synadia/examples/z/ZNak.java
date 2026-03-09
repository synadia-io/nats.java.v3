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
import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.support.Debug;

import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.examples.z.Z0Utils.cleanAndCreate;

public class ZNak {

    public static final String STREAM = "streamNak";
    public static final String SUBJECT = "subjectNak";

    public static void main(String[] args) {
        try {
            Options options = new Options.Builder()
                .build();
            try (Connection nc = Nats.connect(options)) {
                cleanAndCreate(nc, STREAM, SUBJECT);
                JetStream js = nc.jetStream();
                js.publish(SUBJECT, null);

                ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
                    .deliverPolicy(DeliverPolicy.All)
                    .ackPolicy(AckPolicy.Explicit)
                    .maxDeliver(3)
                    .headersOnly(false)
                    .build();

                PushSubscribeOptions pso = PushSubscribeOptions.builder()
                    .configuration(consumerConfiguration)
                    .build();

                AtomicInteger count = new AtomicInteger();
                Dispatcher d = nc.createDispatcher();
                JetStreamSubscription sub = js.subscribe(SUBJECT, d, m -> {
                    try {
                        System.out.println("Receive # " + count.incrementAndGet());
                        m.nak();
                    }
                    catch (Exception e) {
                        Debug.info("handler ex", e);
                    }
                }, false, pso);

                System.out.println(sub.getConsumerInfo());
                Thread.sleep(5000);
                System.out.println(sub.getConsumerInfo());
            }
        }
        catch (Exception e) {
            Debug.info("main ex", e);
        }
    }
}
