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
import io.synadia.client.impl.ErrorListenerConsoleImpl;
import io.synadia.client.impl.NatsJetStreamMetaData;

import java.time.Duration;

public class ZInterrupt {
    static String STREAM = "interrupt-stream";
    static String SUBJECT = "interrupt-subject";
    static String CONSUMER = "interrupt-consumer";

    public static void main(String[] args) {

        Options opts = Options.builder()
            .errorListener(new ErrorListenerConsoleImpl())
            .connectionListener((c, t) -> info(t.toString()))
            .build();

        try (Connection conn = Nats.connect(opts)) {

            JetStreamManagement jsm = conn.jetStreamManagement();
            JetStream js = jsm.jetStream();

            // set up a stream clearing any old version
            // use file since we are killing the server
            try { jsm.deleteStream(STREAM); } catch (Exception ignore) {}
            jsm.addStream(StreamConfiguration.builder().name(STREAM).subjects(SUBJECT).storageType(StorageType.File).build());

            jsm.addOrUpdateConsumer(STREAM, ConsumerConfiguration.builder().durable(CONSUMER).build());

            Thread p = new Thread(() -> publish(js));
            p.start();

            Thread c = new Thread(() -> consumeNext(conn));
            c.start();

            Thread.sleep(1000000); // just let it run
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public static void consumeNext(Connection conn) {
        ConsumerContext ctx;
        try {
            ctx = conn.getConsumerContext(STREAM, CONSUMER);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        long lastSeq = 0;
        while (true) {
            try {
                if (lastSeq == 0) {
                    info("Waiting for first message...");
                }
                else {
                    info("Last Message Sequence {}. Waiting for next message...", lastSeq);
                }

                Message message = ctx.next(1000);
                if (message == null) {
                    info("No message received");
                }
                else {
                    NatsJetStreamMetaData md = message.metaData();
                    lastSeq = md.streamSequence();
                    message.ackSync(Duration.ofSeconds(1));
                    info("Consumed message: seqNo={}", lastSeq);
                }
            }
            catch (Exception e) {
                error("Error consuming message", e);
                try {
                    //noinspection BusyWait
                    Thread.sleep(5000L);
                }
                catch (InterruptedException ex) {
                    error("Error sleeping", ex);
                }
            }
        }
    }

    public static void publish(JetStream js) {
        int num = 0;
        while (true) {
            try {
                js.publish(SUBJECT, ("" + (++num)).getBytes());
                // publish a few really quick then slow down
                if (num > 10) {
                    //noinspection BusyWait
                    Thread.sleep(200);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            catch (Exception e) {
                error("Exception while publish {}", e);
                try {
                    //noinspection BusyWait
                    Thread.sleep(1000);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void info(String format, Object... objects) {
        System.out.printf(format.replace("{}", "%s") + "\n", objects);
    }

    private static void error(String format, Object... objects) {
        System.err.printf(format.replace("{}", "%s") + "\n", objects);
    }
}
