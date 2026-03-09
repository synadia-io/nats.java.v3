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

public class ZQueue {

    public static void main(String[] args) throws Exception {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .filterSubject("example-subject")
                .durable("example-durable")
                .deliverSubject(NUID.nextGlobal())
                .deliverGroup("queue-group")
                // ...
                .build();
            jsm.addOrUpdateConsumer("example-stream", cc);

            MessageHandler handler1 = m -> {
                System.out.println("1 " + m);
                m.ack();
            };
            MessageHandler handler2 = m -> {
                System.out.println("2 " + m);
                m.ack();
            };
            MessageHandler handler3 = m -> {
                System.out.println("3 " + m);
                m.ack();
            };
            Dispatcher d = nc.createDispatcher();

            JetStream js = nc.jetStream();
            PushSubscribeOptions pso = PushSubscribeOptions.bind("example-stream", "example-durable");
            js.subscribe(null, "queue-group", d, handler1, false, pso);
            js.subscribe(null, "queue-group", d, handler2, false, pso);
            js.subscribe(null, "queue-group", d, handler3, false, pso);
            Thread.sleep(5000);
        }
    }
}
