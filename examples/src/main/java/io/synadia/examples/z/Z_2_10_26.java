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
import io.synadia.client.support.Debug;
import io.synadia.client.support.DebugListener;

import java.io.IOException;

import static io.synadia.examples.jetstream.NatsJsUtils.createCleanMemStream;
import static io.synadia.examples.jetstream.NatsJsUtils.publish;

public class Z_2_10_26 {
    public static final String STREAM = "stream";
    public static final String SUBJECT = "subject";
    public static final String CONSUMER = "dur";

    public static void main(String[] args) throws IOException {
        DebugListener l = new DebugListener();
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(l)
            .readListener(l)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

//            safeDeleteStream(jsm, STREAM);
//            Debug.info("GM IN");
//            MessageInfo mi = jsm.getMessage(STREAM, 1);
//            Debug.info("GM OUT", mi);

            createCleanMemStream(nc, STREAM, SUBJECT);
            publish(js, SUBJECT, 10, 1);

            jsm.addOrUpdateConsumer(STREAM, ConsumerConfiguration.builder()
                .durable(CONSUMER)
                .filterSubject(SUBJECT)
                .build());

            PullSubscribeOptions so = PullSubscribeOptions.fastBind(STREAM, CONSUMER);
            JetStreamSubscription sub = js.subscribe(null, so);
            jsm.deleteConsumer(STREAM, CONSUMER);
            sub.pull(10);
            Message m = sub.nextMessage(1000);
            Debug.info("Message received", m);

            ConsumerContext cc = nc.getConsumerContext(STREAM, CONSUMER);
            jsm.deleteConsumer(STREAM, CONSUMER);

//            Debug.info("IN");
//            Message m = cc.next(1000);
//            Debug.info("OUT");

//            Debug.info("IN");
//            FetchConsumer fc = cc.fetch(FetchConsumeOptions.builder().maxMessages(1).build());
//            Message m = fc.nextMessage();
//            Debug.info("OUT");

            Debug.info("IN");
            IterableConsumer ic = cc.iterate();
            m = ic.nextMessage(1000);
            Debug.info("OUT", m);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
