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
import io.synadia.client.Dispatcher;
import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.support.Debug;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ZLeakPubSub {

    public static final String SUBJECT = "subject";
    public static final int SUB_REPORT_FREQ = 5000;
    public static final int PUB_ACT_FREQ = 25_000;
    public static final int PUB_REST_TIME = 500;

    public static void main(String[] args) {
        try {
//            DebugListener d = new DebugListener();
            Options options = Options.builder()
//                .connectionListener(d)
//                .errorListener(d)
//                .executor(executor)
                .build();

            byte[] data = new byte[8 * 1024];
            ThreadLocalRandom.current().nextBytes(data);

            AtomicLong pubCount = new AtomicLong();
            AtomicLong subCount = new AtomicLong();
            try (Connection nc = Nats.connect(options)) {
                Dispatcher d = nc.createDispatcher();
                d.subscribe(SUBJECT, m -> {
                    if (subCount.incrementAndGet() % SUB_REPORT_FREQ == 0) {
                        Debug.info("Received", subCount.get());
                    }
                });

                Thread pubt = new Thread(() -> {
                    while (true) {
                        byte[] pubbytes = (pubCount.incrementAndGet() + " ").getBytes(StandardCharsets.US_ASCII);
                        System.arraycopy(pubbytes, 0, data, 0, pubbytes.length);
                        nc.publish(SUBJECT, data);
                        if (pubCount.get() % PUB_ACT_FREQ == 0) {
                            Debug.info("Publish", pubCount.get());
                            sleep(PUB_REST_TIME);
                        }
                    }
                });
                pubt.start();
                pubt.join();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sleep(long time) {
        try { Thread.sleep(time); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }
}
