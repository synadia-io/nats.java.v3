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
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.impl.ErrorListenerLoggerImpl;
import io.synadia.client.impl.MemoryAuthHandler;
import io.synadia.client.impl.NatsImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import static io.synadia.client.support.JsonUtils.printFormatted;

public class ZNgs1 {

    public static void main(String[] args) throws IOException {
        String credsFile = "C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\remote_control\\default.creds";
        byte[] data = Files.readAllBytes(Paths.get(credsFile));

        AuthHandler ah = new MemoryAuthHandler(data);
        ah = NatsImpl.credentials(credsFile);
        Options options = new Options.Builder()
            .server("tls://connect.ngs.global")
//            .server("tls://gcp.cloud.ngs.global")
            .authHandler(ah)
// [104.198.133.116:4222, 34.134.28.149:4222, 34.69.207.58:4222]
//            .server("tls://connect.ngs.global")
//            .authHandler(Nats.credentials("C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\arondight_1\\scott.creds"))

//            .server(Options.DEFAULT_URL)

            .errorListener(new ErrorListenerLoggerImpl())
            .connectionListener((conn, type) -> System.out.println(type))
            .build();

        try (Connection nc = Nats.connect(options)) {
            printFormatted(nc.getServerInfo());
            if (true) {
                return;
            }
            long total = 0;
            for (int x = 0; x < 1000; x++) {
                long n = nc.RTT().toNanos();
                total += n;
                System.out.println(x + " " + n + " " + total);
            }
            if (true) return;

            JetStreamOptions jso = JetStreamOptions.builder().requestTimeout(Duration.ofSeconds(20)).build();
            KeyValueOptions kvo = KeyValueOptions.builder().jetStreamOptions(jso).build();

            JetStreamManagement jsm = nc.jetStreamManagement(jso);

            JetStream js = nc.jetStream(jso);
            JetStreamSubscription sub;
            Message m;

//            List<StreamInfo> streams = jsm.getStreams();
//            for (StreamInfo stream : streams) {
//                System.out.println(stream);
//            }

//            System.out.println(jsm.getAccountStatistics());

            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                .durable("bar9")
//                .deliverSubject("batdel1")
                .build();
            ConsumerInfo ci = jsm.addOrUpdateConsumer("bat", cc);
            System.out.println(ci);
            ci = jsm.addOrUpdateConsumer("foo", cc);
            System.out.println(ci);
            if (true) return;;
//
//            PushSubscribeOptions pso = PushSubscribeOptions.bind("strm1", "sub1dur1");
//            sub = js.subscribe("sub1", pso);
//            System.out.println(sub.getConsumerInfo());
//            m = sub.nextMessage(jso.getRequestTimeout());
//            while (m != null) {
//                System.out.println(m);
//                m = sub.nextMessage(jso.getRequestTimeout());
//            }
//            sub.unsubscribe();
//            jsm.deleteConsumer("strm1", "sub1dur1");


            // consume ephemeral 1
            PushSubscribeOptions psox = ConsumerConfiguration.builder()
//                .ackWait(Duration.ofSeconds(5))
                .buildPushSubscribeOptions();
            sub = js.subscribe("sub1", psox);
            System.out.println(sub.getConsumerInfo());
//            Thread.sleep(5000);

            m = sub.nextMessage(jso.getRequestTimeout());
            while (m != null) {
                m.ack();
                System.out.println( m + " " + m.metaData());
                m = sub.nextMessage(jso.getRequestTimeout());
                System.out.println(sub.getConsumerInfo());
            }
            sub.unsubscribe();
            if (true) return;

            // consume durable 1
            PushSubscribeOptions pso = ConsumerConfiguration.builder()
                .durable("sub1dur1")
                .buildPushSubscribeOptions();
            sub = js.subscribe("sub1", pso);
            System.out.println(sub.getConsumerInfo());
            m = sub.nextMessage(jso.getRequestTimeout());
            while (m != null) {
                System.out.println(m);
                m = sub.nextMessage(jso.getRequestTimeout());
            }
            sub.unsubscribe();
            jsm.deleteConsumer("strm1", "sub1dur1");

//            KeyValue kv = nc.keyValue("remotes", kvo);
//            System.out.println("Getting Keys");
//            List<String> keys = kv.keys();
//            for (String k : keys) {
//                System.out.println("key: " + k);
//                List<KeyValueEntry> entries = kv.history(k);
//                for (KeyValueEntry kve : entries) {
//                    System.out.println("  " + kve);
//                }
//            }
//
//            PushSubscribeOptions pso = PushSubscribeOptions.builder()
//                .stream("KV_remotes")
//                .configuration(
//                    ConsumerConfiguration.builder()
//                        .ackPolicy(AckPolicy.None)
//                        .deliverPolicy(DeliverPolicy.All)
//                        .build())
//                .build();
//
//            sub = js.subscribe("$KV.remotes.>", pso);
//
//            System.out.println("CI $KV.remotes.>: " + sub.getConsumerInfo());
//
//            m = sub.nextMessage(jso.getRequestTimeout());
//            while (m != null) {
//                System.out.println(m);
//                m = sub.nextMessage(jso.getRequestTimeout());
//            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
