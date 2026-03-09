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
import java.time.Duration;

public class ZFetchTests3 {
    private static final String URL = "nats://localhost:4222";
    private static final String STREAM_NAME = "pwstrm";
    private static final String SUBJECT = "pwsubject";
    private static final String CONSUMER = "pwcon";
    private static final int BATCH_SIZE = 10;

    public static void main(String[] args) {
        try (Connection conn = Nats.connect(URL)) {
            JetStreamManagement jsm = conn.jetStreamManagement();
            JetStream js = conn.jetStream();

            resetStream(jsm);
            for (int i = 0; i < 50; i++) {
                js.publish(SUBJECT, ("data-" + i).getBytes());
            }

            StreamContext sctx = js.getStreamContext(STREAM_NAME);
            ConsumerContext cc = sctx.createOrUpdateConsumer(ConsumerConfiguration.builder().name(CONSUMER).build());
            FetchConsumeOptions fco = FetchConsumeOptions.builder()
                .maxMessages(BATCH_SIZE)
                .expiresIn(10000)
                .build();
            consume(cc, fco);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static boolean KEEP_CONSUMING = true;
    private static void consume(ConsumerContext cc, FetchConsumeOptions fco) {

        log("Fetch Consume Options: " + fco.toJson());
        int total = 0;
        while (KEEP_CONSUMING) {
            try {
                ConsumerInfo info = cc.getConsumerInfo();
                log("Pending Messages: " + info.getNumPending());
                long start = System.currentTimeMillis();
                FetchConsumer fetchConsumer = cc.fetch(fco);
                Message message = fetchConsumer.nextMessage();
                if (message == null) {
                    long elapsed = System.currentTimeMillis() - start;
                    log("Timeout. No messages received. Elapsed: " + elapsed + "ms");
                }
                else {
                    int round = 0;
                    while (message != null) {
                        round++;
                        total++;
                        message.ack();
                        message = fetchConsumer.nextMessage();
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    log("Round: " + round + ", Total: " + total + ", Elapsed: " + elapsed);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void resetStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM_NAME); } catch (Exception ignore) {}

        // Create the stream
        StreamConfiguration streamConfig =
            StreamConfiguration.builder()
                .name(STREAM_NAME)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .maxAge(Duration.ofDays(3))
                // .noAck(false) // default is already false
                .build();
        jsm.addStream(streamConfig);
    }

    private static void log(String s) {
        System.out.println("" + System.currentTimeMillis() + " [" + Thread.currentThread().getName() + "] " + s);
    }
}