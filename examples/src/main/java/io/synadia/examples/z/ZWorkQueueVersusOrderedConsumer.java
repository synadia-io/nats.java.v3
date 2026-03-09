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
import io.synadia.client.OrderedConsumerContext;
import io.synadia.client.api.OrderedConsumerConfiguration;
import io.synadia.client.api.RetentionPolicy;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import static io.synadia.examples.jetstream.NatsJsUtils.safeDeleteStream;

@SuppressWarnings("CallToPrintStackTrace")
public class ZWorkQueueVersusOrderedConsumer {

    public static final String STREAM = "wqvoc";
    public static final String SUBJECT = "subwqvoc";

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();

            safeDeleteStream(jsm, STREAM);

            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.Memory)
                .subjects(SUBJECT)
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .build();
            jsm.addStream(sc);

            OrderedConsumerContext occ = nc.getStreamContext(STREAM).createOrderedConsumer(new OrderedConsumerConfiguration().filterSubjects(SUBJECT));
            occ.iterate();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}