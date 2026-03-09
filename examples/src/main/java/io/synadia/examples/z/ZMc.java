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
import io.synadia.client.support.DebugConnectionListener;
import io.synadia.client.support.DebugErrorListener;
import io.synadia.client.support.DebugStatsCollector;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings({"BusyWait", "CallToPrintStackTrace"})
public class ZMc {

    static String SERVER = "nats://52.23.254.8,nats://54.85.186.153,nats://18.205.160.230";
//    static String SERVER = Options.DEFAULT_URL;

    public static void main(String[] args) {
        AtomicLong consumed = new AtomicLong();
        AtomicLong published = new AtomicLong();

        StatisticsCollector sc = new DebugStatsCollector();
        Options options = Options.builder()
            .connectionTimeout(5000)
            .connectionListener(new DebugConnectionListener())
            .errorListener(new DebugErrorListener())
            .statisticsCollector(sc)
            .server(SERVER)
            .socketWriteTimeout(200)
            .noRandomize()
            .ignoreDiscoveredServers()
            .build();
        Debug.info("SERVERS", options.getServers());
        try (Connection nc = Nats.connect(options)) {
            Debug.info("SI", nc.getServerInfo().getClientIp(), nc.getServerInfo().getServerName());

            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(2500);
                        Debug.info("STATE", nc.getServerInfo().getServerName(),
                            "Queue: %s", nc.outgoingPendingMessageCount(),
                            "Write: %s", sc.getOutMsgs(),
                            "Published: %s", published.get(),
                            "Consumed: %s", consumed.get()
                        );
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            t.start();

            Dispatcher d = nc.createDispatcher();
            d.subscribe("foo", m -> {
                long c = consumed.incrementAndGet();
                if (c % 1000 == 0) {
                    Debug.info("CONSUME", new String(m.getData(), StandardCharsets.ISO_8859_1));
                }
            });

            while (true) {
                try {
                    long p = published.incrementAndGet();
                    nc.publish("foo", ("f->" + p).getBytes(StandardCharsets.ISO_8859_1));
                    if (p % 1000 == 0) {
                        Debug.info("PUBLISH", p);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException e) {
                    System.exit(-1);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
