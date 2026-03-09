// Copyright 2015-2018 The NATS Authors
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
import io.synadia.client.api.MessageInfo;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.impl.ErrorListenerConsoleImpl;
import io.synadia.client.support.Debug;

public class ZScratch5 {
    static final String STREAM = "stream";
    static final String SUBJECT = "subject";

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

            try {
                StreamConfiguration sc = StreamConfiguration.builder()
                    .name(STREAM)
                    .storageType(StorageType.Memory)
                    .subjects(SUBJECT)
                    .allowDirect(false)
                    .build();
                jsm.addStream(sc);
                System.out.println("Created Stream!");

                for (int x = 0; x < 10; x++) {
                    js.publish(SUBJECT, null);
                }

                MessageInfo mi = jsm.getMessage(STREAM, 1);
                Debug.info("GET 1", mi);

                mi = jsm.getMessage(STREAM, 2);
                Debug.info("GET 2", mi);

                mi = jsm.getLastMessage(STREAM, SUBJECT);
                Debug.info("LAST", mi);

                mi = jsm.getFirstMessage(STREAM, SUBJECT);
                Debug.info("FIRST", mi);

                mi = jsm.getNextMessage(STREAM, 1, SUBJECT);
                Debug.info("NEXT 1", mi);

                mi = jsm.getNextMessage(STREAM, 6, SUBJECT);
                Debug.info("NEXT 6", mi);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
