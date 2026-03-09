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
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

public class ZScratch {

    static final String STREAM = "ScratchStream";
    static final String SUBJECT = "ScratchSubject";
    static final int MESSAGE_COUNT = 10;

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

            createStream(jsm); Thread.sleep(1000);
            for (int x = 1; x <= MESSAGE_COUNT; x++) {
                js.publish(SUBJECT, ("" + x).getBytes());
            }

            JetStreamSubscription sub = js.subscribe(SUBJECT);
            ConsumerInfo ci = sub.getConsumerInfo();
            System.out.println(ci.getName() + " " + ci.getDelivered());

            for (int x = 1; x <= MESSAGE_COUNT; x++) {
                Message m = sub.nextMessage(1000);
                m.ack();
            }

            ci = sub.getConsumerInfo();
            System.out.println(ci.getName() + " " + ci.getDelivered());

            System.out.println("SLEEP");
            Thread.sleep(5000);

            ci = sub.getConsumerInfo();
            System.out.println(ci.getName() + " " + ci.getDelivered());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createStream(JetStreamManagement jsm) {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .build();
            jsm.addStream(sc);
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + STREAM + "' " + e);
        }
    }
}
