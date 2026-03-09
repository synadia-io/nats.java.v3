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
import io.synadia.client.impl.ErrorListenerConsoleImpl;
import io.synadia.client.support.Debug;

import java.io.IOException;

// https://github.com/nats-io/nats.java/issues/1272

public class Z1272Debug {
    final static String SERVER = Options.DEFAULT_URL;

    final static String STREAM_NAME = "stream1272";
    final static String SUBJECT = "subject1272";
    final static String CONSUMER = "consumer1272";
    final static StorageType STORAGE_TYPE = StorageType.Memory;

    final static int MAX_RECONNECTS = Integer.MAX_VALUE - 1;
    final static int PUB_SEED = 10;
    final static long PUB_FREQUENCY = 1000 * 60 * 60; // 60 minutes
    final static long PUB_RETRY_WAIT = 5000;          // if a pub fails, maybe a disconnect happened, don't die, try again.
    final static long ADMIN_FREQUENCY = 1000 * 60 * 10; // 10 minute

    public static void main(String[] args) {
        Connection adminConnection = getAdminConnection();

        // setup stream and consumer
        setupStreamAndConsumer(adminConnection);

        // Admin will periodically check the state of the consumer
        Thread tAdmin = new Thread(() -> admin(adminConnection));
        tAdmin.start();

        // Start publishing on a separate thread to simulate an independent publishing app
        Thread tPub = new Thread(() -> publish(adminConnection));
        tPub.start();

        // Consume...until program is killed
        Options options = Options.builder()
            .server(SERVER)
            .maxReconnects(MAX_RECONNECTS)
            .connectionListener((conn, type) -> Debug.info("MAIN", "Connection event: %s", type))
            .errorListener(new ErrorListenerConsoleImpl())
            .build();
        try (Connection nc = Nats.connect(options)) {
            StreamContext streamContext = nc.getStreamContext(STREAM_NAME);

            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(CONSUMER)
                .maxAckPending(1L)
                .build();

            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(cc);

            MessageHandler handler = msg -> {
                Debug.info("HANDLER", msg);
                msg.ack();
            };

            try (MessageConsumer messageConsumer = consumerContext.consume(handler)) {
                Thread.sleep(1_000_000_000); // just sleep here forever, will have to manually stop this program
            }
            catch (Exception e) {
                Debug.info("MAIN EX 1", e);
            }
        } catch (Exception e) {
            Debug.info("MAIN EX 0", e);
        }
        System.exit(-1);
    }

    private static Connection getAdminConnection() {
        Connection nc = null;
        try {
            nc = Nats.connect(Options.builder().server(SERVER)
                    .readListener(new ReadListener() {
                        @Override
                        public void protocol(String op, String text) {
                            Debug.info("RL PRO", op, text);
                        }

                        @Override
                        public void message(String op, Message message) {
                            String body = new String(message.getData());
                            if (body.contains("\"type\":\"io.nats.jetstream.api.v1.consumer_info_response\""))
                            {
                                return;
                            }
                            Debug.info("RL MSG", op, message);
                        }
                    })
                .build());
        }
        catch (IOException | InterruptedException e) {
            Debug.info("INIT EX", e);
            System.exit(-1);
        }
        return nc;
    }

    private static void setupStreamAndConsumer(Connection adminConnection) {
        try {
            JetStreamManagement jsm = adminConnection.jetStreamManagement();

            try { jsm.deleteStream(STREAM_NAME); } catch (JetStreamApiException ignore) {}
            StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(STREAM_NAME)
                .subjects(SUBJECT)
                .storageType(STORAGE_TYPE)
                .build();
            jsm.addStream(streamConfig);
            Debug.info("SETUP", "Stream created %s", STREAM_NAME);

            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable(CONSUMER)
                .filterSubject(SUBJECT)
                .build();

            ConsumerInfo consumerInfo = jsm.createConsumer(STREAM_NAME, cc);
            Debug.info("SETUP", "Consumer created '%s' %s", consumerInfo.getName(), consumerInfo);
        }
        catch (Exception e) {
            Debug.info("SETUP EX", e);
            System.exit(-1);
        }
    }

    private static void publish(Connection adminConnection) {
        long num = 0;
        try {
            JetStream js = adminConnection.jetStream();
            for (int x = 0; x < PUB_SEED; x++) {
                num++;
                try {
                    js.publish(SUBJECT, ("SEED " + num).getBytes());
                }
                catch (JetStreamApiException e) {
                    Debug.info("PUBLISHER EX", "FAILED TO SEED, QUITTING.", e);
                    System.exit(-1);
                }
            }

            num = 0;
            long nextSleep = PUB_FREQUENCY;
            while (true) {
                //noinspection BusyWait
                Thread.sleep(nextSleep);
                num++;
                try {
                    js.publish(SUBJECT, ("TRICKLE " + num).getBytes());
                    nextSleep = PUB_FREQUENCY;
                }
                catch (JetStreamApiException e) {
                    Debug.info("PUBLISHER", "Non-Fatal, Try Again", e);
                    nextSleep = PUB_RETRY_WAIT;
                }
            }
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void admin(Connection adminConnection) {
        try {
            JetStreamManagement jsm = adminConnection.jetStreamManagement();
            while (true) {
                //noinspection BusyWait
                Thread.sleep(ADMIN_FREQUENCY);
                try {
                    ConsumerInfo ci = jsm.getConsumerInfo(STREAM_NAME, CONSUMER);
                    Debug.info("ADMIN", ci);
                }
                catch (JetStreamApiException e) {
                    Debug.info("ADMIN EX", e);
                }
            }
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}