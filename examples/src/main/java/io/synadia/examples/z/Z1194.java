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
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.support.DebugErrorListener;

public class Z1194 {
    public static void main(String[] args) throws Exception {
        Options options = Options.builder()
            .errorListener(new DebugErrorListener())
            .build();
        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            String streamName = "EVENTS";
            jsm.addStream(StreamConfiguration.builder()
                .name(streamName)
                .subjects("event.>")
                .build());

            StreamContext streamContext = nc.getStreamContext(streamName);

            // Setting maxBatch=1, so we shouldn't allow fetching more messages at once.
            ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder().maxBatch(1).build();
            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);

            int count = 0;

            // Fetching a batch of 100 messages is not allowed, so we rightfully don't get any messages and wait for timeout.
            // But we don't get informed about the status message.
            FetchConsumeOptions fco = FetchConsumeOptions.builder()
                .maxMessages(100)
                .expiresIn(1000)
                .raiseStatusWarnings()
                .build();
            try (FetchConsumer fetchConsumer = consumerContext.fetch(fco)) {
                Message msg;
                while ((msg = fetchConsumer.nextMessage()) != null) {
                    msg.ack();
                    count++;
                }
            }

            System.out.printf("Received %d messages.", count);
        }
    }}
