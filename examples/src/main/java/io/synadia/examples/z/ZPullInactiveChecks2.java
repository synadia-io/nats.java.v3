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
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

import static io.synadia.client.support.JsonUtils.printFormatted;

public class ZPullInactiveChecks2 {

    static String STREAM = "DurStream";
    static String SUBJECT = "DurSubject";
    static String PULL_NAME = "DurPull";
    static String PUSH_NAME = "DurPush";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            jsm.purgeStream(STREAM);
//            createStream(jsm);
            printFormatted(jsm.getStreamInfo(STREAM).getStreamState());

            for (int x = 1; x <= 9; x++) {
                js.publish(SUBJECT, ("data" + x).getBytes());
            }

            printFormatted(jsm.getStreamInfo(STREAM).getStreamState());

            // PULL
            JetStreamSubscription sub = nc.jetStream().subscribe(SUBJECT,
                ConsumerConfiguration.builder()
                    .durable(PULL_NAME)
                    .ackWait(30_000)
                    .buildPullSubscribeOptions()
            );

            ConsumerInfo ci = jsm.getConsumerInfo(STREAM, PULL_NAME);
            System.out.println("\nPull After Sub    | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());
            for (int x = 1; x <= 9; x++) {
                sub.pull(1);
                Message m = sub.nextMessage(1000);
                ci = jsm.getConsumerInfo(STREAM, PULL_NAME);
                System.out.println("Pull " + x + " Before Ack | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());
                m.ack();
            }
            ci = jsm.getConsumerInfo(STREAM, PULL_NAME);
            System.out.println("Pull End          | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());

            // PUSH
            sub = nc.jetStream().subscribe(SUBJECT,
                ConsumerConfiguration.builder()
                    .durable(PUSH_NAME)
                    .deliverSubject("DurDeliver")
                    .ackWait(30_000)
                    .buildPushSubscribeOptions()
            );

            ci = jsm.getConsumerInfo(STREAM, PUSH_NAME);
            System.out.println("\nPush After Sub    | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());
            for (int x = 1; x <= 9; x++) {
                Message m = sub.nextMessage(1000);
                ci = jsm.getConsumerInfo(STREAM, PUSH_NAME);
                System.out.println("Push " + x + " Before Ack | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());
                m.ack();
            }
            ci = jsm.getConsumerInfo(STREAM, PUSH_NAME);
            System.out.println("Push End          | Pending: " + ci.getNumPending() + " | AckPending: " + ci.getNumAckPending());

            printFormatted(jsm.getStreamInfo(STREAM).getStreamState());
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
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
