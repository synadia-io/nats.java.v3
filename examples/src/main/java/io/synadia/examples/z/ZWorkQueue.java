// Copyright 2015-2018 The NATS Authors
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
import io.synadia.client.api.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

public class ZWorkQueue {
    private static final String NATS_SERVER = "localhost";
    private static final String NATS_USERNAME = "nats";
    private static final String NATS_PASSWORD = "nats";

    public static final String STREAM = "wqstream";
    public static final String MIRROR_STREAM = "wqmirror";
    public static final String SUBJECT = "wqsub";
    public static final String CONSUMER = "wqcon";

    public static final boolean FIRST_RUN = false;
    public static final boolean DO_NAK = false;

    public static void main(String[] args) {
        try {
            ZWorkQueueLogging.log("Application starting...");

            boolean errorMode = !Arrays.asList(args).contains("--no-err");
            ZWorkQueueLogging.log("Error mode = " + errorMode);

            createAndPopulateStream();

            for (int i = 1; ; i++) {
                ZWorkQueueLogging.log("===== Starting run " + i + " =====");
                execute(errorMode ? 4000 : Long.MAX_VALUE);
                ZWorkQueueLogging.log("===== Run " + i + " ended =====");
                System.out.println();
                Thread.sleep(2000);
            }
        } catch (Throwable e) {
            ZWorkQueueLogging.log("Application ended unexpectedly");
            e.printStackTrace();
        }
    }


    private static void execute(long sleepBeforeShutDown) throws Exception {
        try (Connection natsConnection = Nats.connect(
            Options.builder()
                .server("localhost")
                .userInfo("nats", "nats")
                .build()
        )) {
            ZWorkQueueConsumer consumer = new ZWorkQueueConsumer(
                natsConnection.jetStream(),
                STREAM,
                CONSUMER,
                SUBJECT
            );
            consumer.start();

            Thread.sleep(sleepBeforeShutDown);

            consumer.shutDown();
        }
    }

    private static void publishMessages(JetStream js) {
        // seed the stream with messages.
        int counter = 0;
        while (counter++ < 100) {
            try {
                js.publish(SUBJECT, ("data" + counter).getBytes());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void createAndPopulateStream() throws IOException, JetStreamApiException {
        try (Connection nc = connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
            try { jsm.deleteStream(MIRROR_STREAM); } catch (Exception ignore) {}

            // Create the mirror
            jsm.addStream(
                StreamConfiguration.builder()
                    .name(MIRROR_STREAM)
                    .subjects(SUBJECT)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .maxAge(Duration.ofDays(1))
                    .replicas(1)
                    .storageType(StorageType.File)
                    .discardPolicy(DiscardPolicy.Old)
                    .allowDirect(true)
                    .build()
            );

            // Create the main stream
            jsm.addStream(
                StreamConfiguration.builder()
                    .name(STREAM)
                    .retentionPolicy(RetentionPolicy.WorkQueue)
                    .replicas(1)
                    .storageType(StorageType.File)
                    .discardPolicy(DiscardPolicy.Old)
                    .allowDirect(true)
                    .mirrorDirect(true)
                    .mirror(
                        Mirror.builder()
                            .name(MIRROR_STREAM)
                            .build()
                    )
                    .build()
            );

            // Publish messages to the stream
            JetStream jetStream = jsm.jetStream();
            for (int i = 0; i < 100; i++) {
                jetStream.publish(SUBJECT, String.valueOf(i).getBytes());
            }
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    private static Connection connect() throws IOException, InterruptedException {
        Options.Builder builder = Options.builder().server(NATS_SERVER);
        if (NATS_USERNAME != null) {
            builder.userInfo(NATS_USERNAME, NATS_PASSWORD);
        }
        return Nats.connect(builder.build());
    }
}
