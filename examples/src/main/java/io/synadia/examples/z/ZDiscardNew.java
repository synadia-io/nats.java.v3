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
import io.synadia.client.api.*;
import io.synadia.client.impl.ErrorListenerConsoleImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ZDiscardNew {

    static final String STREAM = "stream";
    static final String SUBJECT = "subject";
    static final String CONSUMER = "con";

    public static void main(String[] args) {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListenerConsoleImpl())
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            try {
                jsm.deleteStream(STREAM);
            }
            catch (JetStreamApiException ignore) {}

            // --------------------------------------------------------------------------------
            // 1. Stream Configuration
            // --------------------------------------------------------------------------------
            // * Try commenting '.discardPolicy(DiscardPolicy.New)' out
            //   to see no publish exceptions and different sequence on the received message
            // --------------------------------------------------------------------------------
            // * Try using maxMessages instead of maxBytes
            // --------------------------------------------------------------------------------
            try {
                StreamConfiguration sc = StreamConfiguration.builder()
                    .name(STREAM)
                    .storageType(StorageType.Memory)
                    .subjects(SUBJECT)
                    .retentionPolicy(RetentionPolicy.WorkQueue)
                    .discardPolicy(DiscardPolicy.New)
                    .maxBytes(1000)
//                    .maxMessages(2)
                    .build();
                StreamInfo si = jsm.addStream(sc);
                System.out.println("Created Stream!");
            }
            catch (Exception e) {
                System.out.println("Failed creating stream: '" + STREAM + "' " + e);
            }

            byte[] data = new byte[330];

            // --------------------------------------------------------------------------------
            // 1. Synchronous Publish - use either this -OR- the "1. Asynchronous" block
            // --------------------------------------------------------------------------------
            for (int x = 1; x <= 5; x++) {
                try {
                    PublishAck pa = js.publish(SUBJECT, data);
                    System.out.println("Synchronous publish succeeded: " + pa.getSeqno());
                }
                catch (JetStreamApiException je) {
                    System.out.println("Synchronous publishing failed: " + je.getApiErrorCode() + " '" + je.getErrorDescription() + "'");
                    break;
                }
            }

            // --------------------------------------------------------------------------------
            // Get the messages. Since it's work queue, messages will be removed from stream
            // --------------------------------------------------------------------------------
            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .name(CONSUMER)
                .filterSubject(SUBJECT)
                .inactiveThreshold(10_000)
                .build();
            StreamContext sctx = nc.getStreamContext(STREAM);
            ConsumerContext cctx = sctx.createOrUpdateConsumer(cc);
            while (true) {
                Message m = cctx.next(1000);
                if (m == null) {
                    break;
                }
                m.ack();
                System.out.println("Received message sequence " + m.metaData().streamSequence());
            }

            // --------------------------------------------------------------------------------
            // 2. Asynchronous Publish - use either this -OR- the "1. Synchronous" block
            // --------------------------------------------------------------------------------
            for (int x = 1; x <= 5; x++) {
                try {
                    CompletableFuture<PublishAck> f = js.publishAsync(SUBJECT, data);
                    PublishAck pa = f.get(200, TimeUnit.MILLISECONDS);
                    System.out.println("Asynchronous publish succeeded: " + pa.getSeqno());
                }
                catch (ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    while (cause != null && !(cause instanceof JetStreamApiException)) {
                        cause = cause.getCause();
                    }
                    if (cause == null) {
                        System.out.println("Asynchronous publish failed: " + ee);
                    }
                    else {
                        JetStreamApiException je = (JetStreamApiException)cause;
                        System.out.println("Asynchronous publish failed: " + je.getApiErrorCode() + " '" + je.getErrorDescription() + "'");
                    }
                    break;
                }
            }

            // --------------------------------------------------------------------------------
            // Get the messages. Since it's work queue, messages will be removed from stream
            // --------------------------------------------------------------------------------
            while (true) {
                Message m = cctx.next(1000);
                if (m == null) {
                    break;
                }
                m.ack();
                System.out.println("Received message sequence " + m.metaData().streamSequence());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
