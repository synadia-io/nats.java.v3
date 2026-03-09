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
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.List;

import static io.synadia.examples.z.Z0Utils.cleanAndCreate;

public class ZRequestInHandler {

    public static final String STREAM = "streamRIH";
    public static final String SUBJECT = "subjectRIH";

    public static void main(String[] args) {
        try {
            Options options = new Options.Builder().build();
            try (Connection nc = Nats.connect(options)) {
                cleanAndCreate(nc, STREAM, SUBJECT);
                JetStreamManagement jsm = nc.jetStreamManagement();
                JetStream js = jsm.jetStream();
                js.publish(SUBJECT, null);

                Dispatcher d = nc.createDispatcher();
                js.subscribe(SUBJECT, d, m -> {
                    try {
                        Debug.info("handler 1");
                        m.ack();
                        Debug.info("handler 2");
                        try {
                            List<String> streams = jsm.getStreamNames();
                            Debug.info("handler 3", streams);
                        }
                        catch (IOException | JetStreamApiException e) {
                            Debug.info("handler 3x", e);
                        }
                    }
                    catch (Exception e) {
                        Debug.info("handler ex", e);
                    }
                }, false);
                Thread.sleep(2000);
            }
        }
        catch (Exception e) {
            Debug.info("main ex", e);
        }
    }
}
