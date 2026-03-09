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
import io.synadia.client.api.OrderedConsumerConfiguration;
import io.synadia.client.support.Debug;

public class ZOrdered {
    public static void main(String[] args) {
        try (Connection nc = Nats.connect("nats://localhost:4222")) {
            Z0Utils.createOrReplaceStream(nc, "stream", "sub");
            JetStream js = nc.jetStream();
            for (int i = 1; i <= 10; i++) {
                js.publish("sub", ("" + i).getBytes());
            }

            JetStreamSubscription sub = js.subscribe("sub", PushSubscribeOptions.builder().ordered(true).name("foo").build());
            Debug.info("SUB", sub.getConsumerName(), sub.getConsumerInfo());

            StreamContext sctx = nc.getStreamContext("stream");
            OrderedConsumerContext ctx = sctx.createOrderedConsumer(new OrderedConsumerConfiguration().consumerNamePrefix("pre"));
            Debug.info("ctx", ctx.getConsumerName());
            FetchConsumer fc = ctx.fetchMessages(1);
            fc.nextMessage();
            Debug.info("ctx", ctx.getConsumerName());
            nc.forceReconnect();
            fc.nextMessage();
            Debug.info("ctx", ctx.getConsumerName());

//            Message m = sub.nextMessage(1000);
//            while (m != null) {
//                m = sub.nextMessage(1000);
//            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
