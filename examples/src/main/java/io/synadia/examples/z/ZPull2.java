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
import io.synadia.client.impl.NatsMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.examples.ExampleUtils.sleep;
import static io.synadia.examples.jetstream.NatsJsUtils.makeData;

public class ZPull2 {
    public static final String STREAM = "stream";
    public static final String SUBJECT = "subject";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStream js = nc.jetStream();

//            createCleanMemStream(nc.jetStreamManagement(), STREAM, SUBJECT);

            publish(js, SUBJECT, "A", 5);

            long thresh = 5000;
            JetStreamSubscription sub = js.subscribe(SUBJECT,
                ConsumerConfiguration.builder()
                    .ackPolicy(AckPolicy.None)
                    .inactiveThreshold(thresh)
                    .buildPullSubscribeOptions()
            );

            int id = 0;
            pull(++id, sub, pro(10, 1000));

            sleep(1000);
            publish(js, SUBJECT, "B", 5);

            pull(++id, sub, pro(10, 1000));

            pull(++id, sub, nw(10));
            pull(++id, sub, nw(10));

            pull(++id, sub, pro(10, 1000));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void pull(int id, JetStreamSubscription sub, PullRequestOptions pro) throws InterruptedException {
        System.out.println("\n[" + id + "] " + pro.toJson());
        sub.pull(pro);
        readMessagesAck(sub, true);
    }

    private static PullRequestOptions pro(int batchSize, long expires) {
        return PullRequestOptions.builder(batchSize)
            .idleHeartbeat(100)
            .expiresIn(expires)
            .build();
    }

    private static PullRequestOptions nw(int batchSize) {
        return PullRequestOptions.builder(batchSize)
            .noWait()
            .build();
    }

    public static List<Message> readMessagesAck(JetStreamSubscription sub, boolean verbose) throws InterruptedException {
        long nextMessageTimeout = 200;
        List<Message> messages = new ArrayList<>();
        Message msg = sub.nextMessage(nextMessageTimeout);
        while (msg != null) {
            messages.add(msg);
            msg.ack();
//            if (verbose) System.out.println(" r --> " + msg.getSubject() + " " + metaString(msg.metaData()) + " " + new String(msg.getData()));
            msg = sub.nextMessage(nextMessageTimeout);
        }
        return messages;
    }

    public static void publish(JetStream js, String subject, String prefix, int count) throws IOException, JetStreamApiException {
        publish(js, subject, prefix, count, -1);
    }

    public static void publish(JetStream js, String subject, String prefix, int count, int msgSize) throws IOException, JetStreamApiException {
        System.out.println("\nPublish (" + prefix + ")  to " + subject + " for " + count + " x " + msgSize);
        for (int x = 1; x <= count; x++) {
            byte[] data = makeData(prefix, msgSize, false, x);
            Message msg =
                NatsMessage.builder()
                    .subject(subject)
                    .data(data)
                    .build();
            js.publish(msg);
        }
    }
}
