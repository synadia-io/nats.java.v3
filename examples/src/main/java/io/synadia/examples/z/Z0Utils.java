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
import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.impl.NatsJetStreamMetaData;

import java.io.IOException;
import java.util.List;

public class Z0Utils {
    public static StreamInfo createOrReplaceStream(Connection nc, String stream, String subject) throws IOException, JetStreamApiException {
        return createOrReplaceStream(nc.jetStreamManagement(), stream, subject);
    }

    public static StreamInfo createOrReplaceStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        try {
            jsm.deleteStream(stream);
        }
        catch (Exception ignore) {}

        return createStream(jsm, stream, subject);
    }

    public static StreamInfo cleanAndCreate(Connection nc, String stream, String subject) throws IOException, JetStreamApiException {
        return cleanAndCreate(nc.jetStreamManagement(), stream, subject);
    }

    public static StreamInfo cleanAndCreate(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        cleanupJs(jsm);
        return createStream(jsm, stream, subject);
    }

    public static StreamInfo createStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        StreamConfiguration sc = StreamConfiguration.builder()
            .name(stream)
            .storageType(StorageType.Memory)
            .subjects(subject)
            .build();
        return jsm.addStream(sc);
    }

    public static void cleanupJs(Connection c) throws IOException, JetStreamApiException {
        cleanupJs(c.jetStreamManagement());
    }

    public static void cleanupJs(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        List<String> streams = jsm.getStreamNames();
        for (String s : streams)
        {
            try {
                jsm.deleteStream(s);
            }
            catch (Exception ignore) {}
        }
    }

    public static String stringify(Message msg) {
        NatsJetStreamMetaData meta = msg.metaData();
        return "StreamSeq: " + meta.streamSequence() + " | "
            + "ConSeq: " + meta.consumerSequence() + " | "
            + "Delivered: " + meta.deliveredCount() + " | "
            + "Pending: " + meta.pendingCount();
    }

    public static String stringify(ConsumerInfo ci) {
        return "Waiting: " + ci.getNumWaiting() + " | "
            + "Delivered: " + ci.getDelivered() + " | "
            + "Redelivered: " + ci.getRedelivered() + " | "
            + "Pending: " + ci.getNumAckPending();
    }
}
