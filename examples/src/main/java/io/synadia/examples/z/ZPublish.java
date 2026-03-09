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
import io.synadia.client.support.DebugStatsCollector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class ZPublish {
    public static final String STREAM = "pubstream";
    public static final String SUBJECT = "pubsub";

    public static void main(String[] args) {
        DebugStatsCollector stats = new DebugStatsCollector();
        ConnectionListener cl = new ConnectionListener() {
            @Override
            public void connectionEvent(Connection conn, Events type) {
            }

            @Override
            public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
                Debug.info("CL", "%s@%s", Integer.toHexString(conn.hashCode()).toUpperCase(), time, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails, stats.toShortString());
            }
        };
        try {
            Options options = new Options.Builder()
                .connectionListener(cl)
                .ignoreDiscoveredServers()
                .errorListener(new ErrorListener() {})
                .statisticsCollector(stats)
                .build();

            try (Connection nc = Nats.connectReconnectOnConnect(options)) {
                createStream(nc.jetStreamManagement(), STREAM, SUBJECT);
                JetStream js = nc.jetStream();
                int x = 0;
                while (x < 1000) {
                    try {
                        js.publish(SUBJECT, ("" + ++x).getBytes(StandardCharsets.ISO_8859_1));
                        if (x % 1000 == 0) {
                            Debug.info("PUB", x, stats);
                        }
                        else {
                            Thread.sleep(1);
                        }
                    }
                    catch (Exception e) {
                        Debug.info("PUB ex", e.getMessage(), stats);
                        Thread.sleep(100);
                    }
                }

                StreamInfo si = nc.jetStreamManagement().getStreamInfo(STREAM);
                Debug.info("SI", si);

                nc.jetStreamManagement().purgeStream(STREAM);
                si = nc.jetStreamManagement().getStreamInfo(STREAM);
                Debug.info("SI", si);
            }
        }
        catch (Exception e) {
            Debug.info("ZZZ ex", stats);
            e.printStackTrace();
        }
    }

    private static void pub(JetStream js, PublishOptions po) {
        try {
            js.publish(SUBJECT, null, null, po);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    private static void puba(JetStream js, PublishOptions po) {
        CompletableFuture<PublishAck> f = null;
        try {
            f = js.publishAsync(SUBJECT, null, null, po);
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
        deleteStream(jsm, stream);

        Debug.info("createStream");
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(stream)
                .storageType(StorageType.File)
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
        Debug.info("deleteStream");
        try {
            jsm.deleteStream(stream);
        }
        catch (Exception ignore) {}
    }

}
