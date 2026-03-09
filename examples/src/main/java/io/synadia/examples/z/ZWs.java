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
import io.synadia.client.impl.NatsMessage;
import io.synadia.examples.jetstream.NatsJsUtils;

import java.io.IOException;

public class ZWs {

    static final String STREAM = "WsStream";
    static final String SUBJECT = "WsSubject";
    static final int MESSAGE_COUNT = 1000;

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("ws://localhost:8084")
            .errorListener(new ErrorListener() {
            })
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            NatsJsUtils.createStream(jsm, STREAM, SUBJECT);
            Thread.sleep(1000);
            for (int x = 1; x <= MESSAGE_COUNT; x++) {
                Message m = NatsMessage.builder()
                    .subject(SUBJECT)
                    .data("" + x)
                    .build();
                js.publish(m);
            }
            Thread.sleep(5000);

            JetStreamSubscription sub = js.subscribe(SUBJECT);
            for (int x = 1; x <= MESSAGE_COUNT; x++) {
                Message m = sub.nextMessage(1000);
                System.out.println(x + " " + m);
                m.ack();
            }
            System.out.println(nc.getServerInfo());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}