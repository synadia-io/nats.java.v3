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
import io.synadia.client.api.RetentionPolicy;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("CallToPrintStackTrace")
public class ZNvidia2 {

    public static final String STREAM = "stream1058";
    public static final String SUBJECT = "sub1058";
    public static final String CONSUMER = "con1058";

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();
            List<StreamInfo> list = jsm.getStreams();
            for (StreamInfo si : list) {
                System.out.println(si.getJv());
            }
            deleteStream(jsm);

            long now = System.nanoTime();
            try {
                Debug.info("Pub", js.publish(SUBJECT, null));
            }
            catch (Exception e) {
                Debug.info("Pub Ex", e);
            }
            long elapsed = System.nanoTime() - now;
            Debug.info("Pub Elapsed", elapsed);


            createStream(jsm);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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