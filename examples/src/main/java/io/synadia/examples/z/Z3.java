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

public class Z3 {
    // start the program
    // let it run for a few seconds, watch the output
    // kill the server
    // restart the server
    // watch the output
    public static void main(String[] args) throws Exception {
        Options options = new Options.Builder()
            .server(Options.DEFAULT_URL)
            .build();

        String stream = "stream1347";
        String subject = "subject1347";
        String conName = "con1347";
        try (Connection nc = Nats.connect(options)) {
            // delete the stream for a fresh start.
            // catch exception we don't care if it didn't exist
            try {
                nc.jetStreamManagement().deleteStream(stream);
            }
            catch (Exception ignore) {}

            // make the stream
            nc.jetStreamManagement()
                .addStream(
                    StreamConfiguration.builder()
                        .name(stream)
                        .subjects(subject)
                        .storageType(StorageType.File) // HAS TO BE FILE
                        .build());

            // consume options are not necessary for the example
            ConsumeOptions consumeOptions = ConsumeOptions.builder()
                .batchSize(15000)
                .expiresIn(1000)
                .build();

            StreamContext streamContext = nc.getStreamContext(stream);
            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(
                ConsumerConfiguration.builder()
                    .name(conName)
                    .deliverPolicy(DeliverPolicy.LastPerSubject)
                    .ackPolicy(AckPolicy.None)
                    .filterSubjects(">")
                    .build());

            MessageHandler handler = msg -> System.out.println("Received message: " + new String(msg.getData()));

            //noinspection resource this should really be done in a try-resource, but it matches the original code.
            MessageConsumer mcon = consumerContext.consume(consumeOptions, handler);

            JetStream js = nc.jetStream();
            int x = 0;

            //noinspection InfiniteLoopStatement just run until the user kills the program.
            while (true) {
                try {
                    String data = "Data" + (++x);
                    js.publish(subject, data.getBytes());
                    System.out.println("Published message: " + data);
                    //noinspection BusyWait
                    Thread.sleep(250);
                }
                catch (Exception e) {
                    // publish can fail during disconnect
                    // try again in a bit
                    //noinspection BusyWait
                    Thread.sleep(1000);
                }
            }
        }
    }
}