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
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ZLeakConsume {
    public static final String STREAM = "strm";
    public static final String SUBJECT = "sub";
    public static final int REPORT_FREQ = 50_000;
    public static final int STOP_CONSUMER_AT = 5_000_000;
    public static final int BUSY_WAIT = 1000;
    public static final int ENSURE_CONSUMED_WAIT = 1000;

    public static void main(String[] args) throws IOException, InterruptedException {
        while (true) {
            Thread t4 = new Thread(() -> consume(4222));
            t4.start();
            Thread t5 = new Thread(() -> consume(5222));
            t5.start();
            Thread t6 = new Thread(() -> consume(6222));
            t6.start();

            t4.join();
            t5.join();
            t6.join();

            Thread.sleep(10_000);
        }
    }

    private static void consume(int port) {
        Options options = new Options.Builder()
            .server("nats://localhost:" + port)
            .build();

        try (Connection nc = Nats.connect(options)) {
            String name = "con-" + port + "-" + nextConsumerId();
            StreamContext sc = nc.getStreamContext(STREAM);
            ConsumerContext cc = sc.createOrUpdateConsumer(
                ConsumerConfiguration.builder()
                    .name(name)
                    .filterSubject(SUBJECT)
                    .build());

            AtomicLong count = new AtomicLong();
            MessageHandler handler = m -> {
                if (count.incrementAndGet() % REPORT_FREQ == 0) {
                    Debug.info("Consume", name, format(count));
                }
                m.ack();
            };

            try (MessageConsumer con = cc.consume(handler)) {
                while (!con.isStopped()) {
                    sleep(BUSY_WAIT);
                    if (count.get() >= STOP_CONSUMER_AT) {
                        con.stop();
                    }
                }

                ConsumerInfo ci = con.getConsumerInfo();
                while (ci != null && ci.getNumPending() > 0) {
                    sleep(BUSY_WAIT);
                    ci = con.getConsumerInfo();
                }
            }

            sleep(ENSURE_CONSUMED_WAIT);
            Debug.info("Consume Done", name, format(count));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String format(AtomicLong count) {
        return String.format("%,d", count.get());
    }

    private static final AtomicInteger CONSUMER_ID = new AtomicInteger();
    private static String nextConsumerId() {
        return Integer.toString(CONSUMER_ID.incrementAndGet());
    }

    private static void sleep(long sleep) {
        try {
            Thread.sleep(sleep);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
