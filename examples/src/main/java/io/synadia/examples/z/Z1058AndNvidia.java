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
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("CallToPrintStackTrace")
public class Z1058AndNvidia {

    public static final String STREAM = "stream1058";
    public static final String SUBJECT = "sub1058";
    public static final String CONSUMER = "con1058";

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            List<StreamInfo> list = jsm.getStreams();
            for (StreamInfo si : list) {
                System.out.println(si.getJv());
            }
//            createStream(jsm);
//            createConsumer(jsm);
//
//            for (int x = 1; x <= 10; x++) {
//                jsm.jetStream().publish(SUBJECT, ("A-" + x).getBytes());
//            }
//
//            ConsumerContext cc = nc.getConsumerContext(STREAM, CONSUMER);
//            fetchLoop(cc);
//
//            deleteStream(jsm);
//            printConsumer(jsm);
//
//            createStream(jsm);
//            printConsumer(jsm);
//
//            fetchLoop(cc);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void fetchLoop(ConsumerContext cc) throws IOException, JetStreamApiException, InterruptedException, JetStreamStatusCheckedException {
        Debug.info("fetchLoop 1");
        FetchConsumer fc = cc.fetch(FetchConsumeOptions.builder().expiresIn(1000).maxMessages(2).build());
        Debug.info("fetchLoop 2");
        Message m = fc.nextMessage();
        Debug.info("fetchLoop 3");
        while (m != null) {
            printMessage(m);
            m = fc.nextMessage();
        }
    }

    private static void printMessage(Message message) {
        Debug.info("Message", message.metaData());
    }

    private static void createConsumer(JetStreamManagement jsm) {
        try {
            printConsumer(jsm.addOrUpdateConsumer(STREAM, createConsumerConfiguration()));
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static ConsumerConfiguration createConsumerConfiguration() {
        return ConsumerConfiguration.builder()
            .durable(CONSUMER)
            .filterSubject(SUBJECT)
            .build();
    }

    private static void printConsumer(JetStreamManagement jsm) {
        try {
            printConsumer(jsm.getConsumerInfo(STREAM, CONSUMER));
        }
        catch (Exception e) {
            Debug.info("printConsumer", e.getMessage());
        }
    }

    private static void printConsumer(ConsumerInfo ci) {
        Debug.info("Consumer", ci.getJv());
    }

    public static void createStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        Debug.info("createStream");
        deleteStream(jsm);

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .build();
            StreamInfo si = jsm.addStream(sc);
            Debug.info("Created stream: " + si.getJv());
        }
        catch (Exception e) {
            Debug.info("Failed creating stream: '' " + e);
            System.exit(-1);
        }
    }


    private static void deleteStream(JetStreamManagement jsm) {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}
    }
}