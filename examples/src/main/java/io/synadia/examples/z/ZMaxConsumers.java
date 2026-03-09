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

import io.synadia.client.Connection;
import io.synadia.client.JetStreamApiException;
import io.synadia.client.JetStreamManagement;
import io.synadia.client.Nats;
import io.synadia.client.api.*;
import io.synadia.client.support.Debug;

import java.io.IOException;

public class ZMaxConsumers {
    public static final String STREAM = "temp_stream";
    public static final String SUBJECT = "temp_stream";
    public static final String CONSUMER = "temp_consumer";

    public static void main(String[] args) {
        try {
            try (Connection nc = Nats.connect()) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                createOrReplaceStream(jsm, STREAM, SUBJECT);
                ConsumerConfiguration cc = ConsumerConfiguration.builder()
                    .name(CONSUMER)
                    .maxAckPending(1000)
                    .build();
                ConsumerInfo ci = jsm.addOrUpdateConsumer(STREAM, cc);
                Debug.info("ConsumerInfo", ci.getJv().toJson());

                cc = ConsumerConfiguration.builder()
                    .name(CONSUMER)
                    .maxAckPending(1001)
                    .build();
                ci = jsm.addOrUpdateConsumer(STREAM, cc);
                Debug.info("ConsumerInfo", ci.getJv().toJson());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createOrReplaceStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        Debug.info("createStream");
        deleteStream(jsm, stream);

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(stream)
//                .storageType(StorageType.Memory)
                .subjects(subject)
                .maxConsumers(1)
                .retentionPolicy(RetentionPolicy.Interest)
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
