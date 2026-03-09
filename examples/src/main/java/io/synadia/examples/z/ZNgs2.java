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
import io.synadia.client.api.*;
import io.synadia.client.impl.ErrorListenerLoggerImpl;

import java.time.Duration;
import java.util.Date;
import java.util.List;

public class ZNgs2 {

    public static void main(String[] args) {
        Options options = new Options.Builder()
//            .server("tls://connect.ngs.global")
            .server("tls://us-central1.gcp.cloud.ngs.global")
            .authHandler(Nats.credentials("C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\Go big or go home\\default.creds"))
//            .server(Options.DEFAULT_URL)
            .errorListener(new ErrorListenerLoggerImpl())
            .build();

        try (Connection nc = Nats.connect(options)) {
            ServerInfo si = nc.getServerInfo();
            for (String u : si.getConnectURLs()) {
                System.out.println(u + " -> " + options.createURIForServer(u));
            }
            if (true) return;
            JetStreamOptions jso = JetStreamOptions.builder().requestTimeout(Duration.ofSeconds(20)).build();
            KeyValueOptions kvo = KeyValueOptions.builder().jetStreamOptions(jso).build();

            JetStreamManagement jsm = nc.jetStreamManagement(jso);
            JetStream js = nc.jetStream(jso);

            List<String> streams = jsm.getStreamNames();
            for (String stream : streams) {
                System.out.println(jsm.getStreamInfo(stream));
            }

            KeyValueManagement kvm = nc.keyValueManagement(kvo);
            KeyValueConfiguration kvc = KeyValueConfiguration.builder()
                .name("qqq")
                .storageType(StorageType.File)
                .maxBucketSize(10 * 1024 * 1024)
                .build();
            kvm.create(kvc);

            List<String> buckets = kvm.getBucketNames();
            for (String b : buckets) {
                System.out.println(kvm.getStatus(b));
            }

            KeyValue kv = nc.keyValue("qqq", kvo);
            kv.put("abc", new Date().toString().getBytes());
            kv.put("def", new Date().toString().getBytes());

            System.out.println(kv.get("abc"));
            System.out.println(kv.get("def"));

            kv = nc.keyValue("qqq", kvo);
            System.out.println("Getting Keys");
            List<String> keys = kv.keys();
            for (String k : keys) {
                System.out.println("key: " + k);
            }

            List<KeyValueEntry> ents = kv.history("abc");
            for (KeyValueEntry k : ents) {
                System.out.println(k);
            }

            PushSubscribeOptions pso = PushSubscribeOptions.builder()
                .stream("KV_clients")
//                .ordered(ordered)
                .configuration(
                    ConsumerConfiguration.builder()
                        .ackPolicy(AckPolicy.None)
                        .deliverPolicy(DeliverPolicy.All)
//                        .headersOnly(true)
                        .build())
                .build();
//            JetStreamSubscription sub = js.subscribe("$KV.clients.>", pso);
//            JetStreamSubscription sub = js.subscribe(">", pso);
            JetStreamSubscription sub = js.subscribe("$KV.clients.MwYas2meUPv9PrWMIBPTzx", pso);
            System.out.println("CI: " + sub.getConsumerInfo());

            Message m = sub.nextMessage(jso.getRequestTimeout());
            while (m != null) {
                System.out.println(m);
                m = sub.nextMessage(jso.getRequestTimeout());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
