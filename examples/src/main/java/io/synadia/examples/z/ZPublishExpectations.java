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
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ZPublishExpectations {
    public static final String STREAM1 = "stream1";
    public static final String SUBJECT1 = "sub1";
    public static final String STREAM2 = "stream2";
    public static final String SUBJECT2 = "sub2";

    public static void main(String[] args) {
        try {
            Options options = new Options.Builder()
                .build();

            try (Connection nc = Nats.connectReconnectOnConnect(options)) {
                createStream(nc.jetStreamManagement(), STREAM1, SUBJECT1);
                createStream(nc.jetStreamManagement(), STREAM2, SUBJECT2);
                JetStream js = nc.jetStream();

                puba(js, PublishOptions.builder().expectedStream(STREAM2).build());
                puba(js, PublishOptions.builder().expectedLastSequence(9).build());
                puba(js, PublishOptions.builder().expectedLastMsgId("x").build());
                puba(js, PublishOptions.builder().expectedLastSubjectSequence(9).build());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void pub(JetStream js, PublishOptions po) {
        try {
            js.publish(SUBJECT1, null, null, po);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void puba(JetStream js, PublishOptions po) {
        CompletableFuture<PublishAck> f = null;
        try {
            f = js.publishAsync(SUBJECT1, null, null, po);
            while (!f.isDone()) {
                Thread.sleep(10);
            }
            System.out.println("isCompletedExceptionally: " + f.isCompletedExceptionally());
            f.get();
        }
        catch (Exception e) {
            System.out.println(e);
            try {
                f.get();
            }
            catch (Exception ee) {
                System.out.println(ee);
            }
        }
    }

    public static void createStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        Debug.info("createStream");
        deleteStream(jsm, stream);

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(stream)
                .storageType(StorageType.Memory)
                .subjects(subject)
                .build();
            StreamInfo si = jsm.addStream(sc);
            Debug.info("Created stream: " + si.getJv());
        }
        catch (Exception e) {
            Debug.info("Failed creating stream: '' " + e);
            System.exit(-1);
        }
    }


    private static void deleteStream(JetStreamManagement jsm, String stream) {
        try {
            jsm.deleteStream(stream);
        }
        catch (Exception ignore) {}
    }
}
