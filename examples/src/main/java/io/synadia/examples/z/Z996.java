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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.api.StreamInfo;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Z996 {

    private final static String NATS_SERVER = "nats://localhost:4222";
    private final static String NATS_TOKEN = "KxX1FqaJ";

    private final static String STREAM_NAME = "NatsTimeout";
    private final static String STREAM_SUBJECT = "test";

    public static void main(String[] args) throws IOException, JetStreamApiException, InterruptedException {
        Z996 nt = new Z996();
        nt.createStream();
        int i = 0;
        while (true) {
            System.out.printf("Iteration: %d\n", i++);
            nt.parallelSubscribe();
            Thread.sleep(1000);
        }
    }

    private void parallelSubscribe() throws IOException, JetStreamApiException, InterruptedException {
        final Connection nastCon = getConnection();
        final JetStream jetStream = nastCon.jetStream();
        final int streams = 2;
        CountDownLatch cdl = new CountDownLatch(streams);

        for (int i = 0; i < streams; i++) {
            final int c = i;
            new Thread(() -> {
                try {
                    jetStream.subscribe(STREAM_SUBJECT);
                    cdl.countDown();
                    System.out.printf("Stream [%d] established.\n", c);
                } catch (Exception e) {
                    System.err.printf("Stream [%d] not established.\n", c);
                    e.printStackTrace();
                }
            }).start();
        }
        if (!cdl.await(3, TimeUnit.SECONDS)) {
            System.err.printf("Error on subscribe\n");
            System.exit(1);
        }

        nastCon.close();
    }

    private void createStream() throws IOException, JetStreamApiException, InterruptedException {
        final Connection nastCon = getConnection();
        JetStreamManagement jsm = nastCon.jetStreamManagement();
        List<StreamInfo> currentStreams = jsm.getStreams();

        Optional<StreamInfo> oldStream = currentStreams.stream()
            .filter(si -> si.getConfiguration().getName().equals(STREAM_NAME)).findFirst();

        if (oldStream.isPresent()) {
            System.out.printf("use existing stream\n");
        } else {
            StreamInfo info = jsm.addStream(StreamConfiguration.builder().name(STREAM_NAME)
                .storageType(StorageType.Memory).subjects(STREAM_SUBJECT).description("Test Stream").build());
            System.out.printf("stream created: %s\n", info);
        }

        nastCon.close();
    }

    private Connection getConnection() {

        io.synadia.client.Options.Builder builder = new Options.Builder().server(NATS_SERVER);
//            .token(NATS_TOKEN.toCharArray());
        try {
            return Nats.connect(builder.build());
        } catch (IllegalStateException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}