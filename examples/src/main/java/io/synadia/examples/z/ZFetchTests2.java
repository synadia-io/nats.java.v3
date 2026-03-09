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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ZFetchTests2 {
    private static final String URL = "nats://localhost:4222";
    private static final String STREAM_NAME = "TestStream";
    private static final String SUBJECT = "test";
    private static final StorageType STORAGE_TYPE = StorageType.Memory;
    private static final long DELAY = 10; // seconds
    private static final int PULL_COUNT = 10;
    private static final int PUB_COUNT = 50;

    public static void main(String[] args) {
        try (Connection conn = Nats.connect(URL)) {
            JetStreamManagement jsm = conn.jetStreamManagement();;
            JetStream js = conn.jetStream();
            resetStream(jsm);

            // setup and start the pull
            final AtomicBoolean go = new AtomicBoolean(true);
            final AtomicInteger count = new AtomicInteger(PULL_COUNT);
            final PullSubscribeOptions so =
                ConsumerConfiguration.builder()
                    .durable("Dur" + System.currentTimeMillis())
                    .maxAckPending(2000)
                    .buildPullSubscribeOptions();
            final JetStreamSubscription sub = js.subscribe(SUBJECT, so);
            sub.pullExpiresIn(PULL_COUNT, Duration.ofSeconds(DELAY * 3));
            Thread pt = new Thread(() -> {
                while (go.get() && count.get() > 0) {
                    log("LOOP " + count.get());
                    try {
                        Message m = sub.nextMessage(500);
                        if (m != null) {
                            log("RCVD: " + m);
                            m.ack();
                            count.decrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            pt.start();

            Thread.sleep(DELAY * 1000);

            for (int i = 0; i < PUB_COUNT; i++) {
                String str = "Test" + i;
                log("PUB: " + js.publish(SUBJECT, str.getBytes()));
            }

            pt.join(60000);
            go.set(false);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resetStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM_NAME); } catch (Exception ignore) {}

        // Create the stream
        StreamConfiguration streamConfig =
            StreamConfiguration.builder()
                .name(STREAM_NAME)
                .storageType(STORAGE_TYPE)
                .subjects(SUBJECT)
                .maxAge(Duration.ofDays(3))
                .build();
        jsm.addStream(streamConfig);
    }

    private static void log(String s) {
        System.out.println("" + System.currentTimeMillis() + " [" + Thread.currentThread().getName() + "] " + s);
    }
}