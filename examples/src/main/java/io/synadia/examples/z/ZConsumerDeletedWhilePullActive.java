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

import java.io.IOException;

import static io.synadia.examples.z.ZHbPull.createTestStream;

public class ZConsumerDeletedWhilePullActive {
    public static final String STREAM = "hb-stream";
    public static final String SUBJECT = "hb-subject";
    public static final String CALLBACK_CONSUMER = "CALLBACK_CONSUMER";
    public static final String SYNC_CONSUMER = "SYNC_CONSUMER";

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server(Options.DEFAULT_URL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStream js = nc.jetStream();
            JetStreamManagement jsm = nc.jetStreamManagement();

            // Create the stream.
            createTestStream(jsm);

            // Setup pull subscriptions
            JetStreamSubscription syncSub = js.subscribe(SUBJECT,
                PullSubscribeOptions.builder()
                    .durable(SYNC_CONSUMER).build());

            Dispatcher d = nc.createDispatcher();
            JetStreamSubscription callbackSub = js.subscribe(SUBJECT, d, Message::ack,
                PullSubscribeOptions.builder().durable(CALLBACK_CONSUMER).build());

            // Pull with long expiration.
            // No messages have been published to the subject, so it will just wait.
            PullRequestOptions pro = PullRequestOptions.builder(1)
                .expiresIn(3000)
                .idleHeartbeat(300)
                .build();

            syncSub.pull(pro);
            callbackSub.pull(pro);

            // Simulate someone else deleting the consumer in the middle of the pull.
            jsm.deleteConsumer(STREAM, syncSub.getConsumerName());
            jsm.deleteConsumer(STREAM, callbackSub.getConsumerName());

            // Both Sync and Callback subscriptions get messages sent to the error listener.
            // Sync subscriptions throw exceptions on errors.
            try {
                syncSub.nextMessage(1000);
            }
            catch (JetStreamStatusException e) {
                System.err.println("Sync Exception: " + e);
            }

            // Make sure the error listener has time to get the errors
            // before closing the connection.
            Thread.sleep(1000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
