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

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.impl.AckType.AckProgress;
import static io.synadia.examples.z.Z0Utils.createOrReplaceStream;

public class Z1042 {

    public static final String STREAM = "str1042";
    public static final String SUBJECT = "sub1042";

    public static void main(String[] args) {
        try {
            Options options = new Options.Builder().build();

            try (Connection nc = Nats.connectReconnectOnConnect(options)) {
                createOrReplaceStream(nc, STREAM, SUBJECT);

                JetStream js = nc.jetStream();

                js.publish(SUBJECT, null);

                JetStreamSubscription sub = js.subscribe(SUBJECT);
                Message m = sub.nextMessage(1000);
                System.out.println("MSG " + m);
                Message ackReply = nc.request(m.getReplyTo(), AckProgress.bytes, Duration.ofSeconds(1));
                if (ackReply == null) {
                    throw new TimeoutException("Ack response timed out.");
                }
                System.out.println("REP " + ackReply);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
