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
import io.synadia.client.JetStreamManagement;
import io.synadia.client.Nats;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.support.Debug;

public class ZMain {

    public static void main(String[] args) throws InterruptedException {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            try { jsm.deleteStream("stream"); } catch (Exception ignore) {}

            try {
                jsm.addStream(StreamConfiguration.builder()
                    .name("stream")
                    .storageType(StorageType.Memory)
                    .subjects("subject")
//                    .allowDirect(true)
                    .build());
            }
            catch (Exception ignore) {}

            jsm.jetStream().publish("subject", "data".getBytes());

            Debug.info("!!!", jsm.getMessage("stream", 1));
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
