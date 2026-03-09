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
import io.synadia.client.support.Debug;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class ZLoadSeed {
    static final String STREAM = "strm";
    static final String SUBJECT = "sub";
    static final int CONSUME_COUNT = 100;
    static final long INACTIVE_THRESHOLD = 30000;
    static final long LATCH_WAIT = 5000;
    static final TimeUnit LATCH_UNIT = TimeUnit.MILLISECONDS;
    static final long REPORT_INCREMENT = 10_000;

    static long totalCount = 0;
    static long totalElapsed = 0;
    static long reportAt = REPORT_INCREMENT;

    public static void main(String[] args) throws IOException, InterruptedException {
        cleanConsumers();


        InfoReader r1 = new InfoReader(4222);
        Thread tr1 = new Thread(r1);
        tr1.start();
        InfoReader r2 = new InfoReader(5222);
        Thread tr2 = new Thread(r2);
        tr2.start();
        InfoReader r3 = new InfoReader(6222);
        Thread tr3 = new Thread(r3);
        tr3.start();

        //noinspection InfiniteLoopStatement
        while (true) {
            Thread t4 = new Thread(new Consoomer(4222));
            t4.start();
            Thread t5 = new Thread(new Consoomer(5222));
            t5.start();
            Thread t6 = new Thread(new Consoomer(6222));
            t6.start();
            t4.join();
            t5.join();
            t6.join();
        }
    }

    static class InfoReader implements Runnable {
        final String name;
        final int port;
        final ReentrantLock lock = new ReentrantLock();

        public InfoReader(int port) {
            name = "rdr-" + port + "-" + nextReaderId();
            this.port = port;
        }

        @Override
        public void run() {
            try (Connection nc = Nats.connect(getOptions(port))) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                while (!Thread.interrupted()) {
                    try {
                        List<String> cons = jsm.getConsumerNames(STREAM);
                        if (!cons.isEmpty()) {
                            long start = System.currentTimeMillis();
                            for (String con : cons) {
                                try {
                                    jsm.getConsumerInfo(STREAM, con);
                                }
                                catch (Exception e) {
                                    if (e instanceof JetStreamApiException) {
                                        if (e.getMessage().contains("10014")) {
                                            continue;
                                        }
                                    }
                                    Debug.info("INFO EX GCI", name, e);
                                }
                            }
                            long elapsed = System.currentTimeMillis() - start;
                            lock.lock();
                            try {
                                totalCount += cons.size();
                                totalElapsed += elapsed;
                                if (totalCount > reportAt) {
                                    reportAt += REPORT_INCREMENT;
                                    float teMin = (float) totalElapsed / 60000;
                                    float per = totalCount / teMin;
                                    if (teMin > 0) {
                                        Debug.info("INFO", name, cons.size(), "C: " + totalCount, "EL: " + totalElapsed + "ms " + teMin + " min", "PER: " + per);
                                    }
                                }
                            }
                            finally {
                                lock.unlock();
                            }
                        }
                    }
                    catch (Exception e) {
                        Debug.info("INFO EX", name, e);
                    }
                    try {
                        //noinspection BusyWait
                        Thread.sleep(10);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    static class Consoomer implements Runnable {
        final String name;
        final int port;

        public Consoomer(int port) {
            name = "con-" + port + "-" + nextConsumerId();
            this.port = port;
        }

        @Override
        public void run() {
            try (Connection nc = Nats.connect(getOptions(port))) {
                StreamContext sc = nc.getStreamContext(STREAM);
                ConsumerContext cc = sc.createOrUpdateConsumer(
                    ConsumerConfiguration.builder()
                        .name(name)
                        .filterSubject(SUBJECT)
                        .inactiveThreshold(INACTIVE_THRESHOLD)
                        .build());

                CountDownLatch latch = new CountDownLatch(CONSUME_COUNT);
                MessageHandler handler = m -> {
                    m.ack();
                    latch.countDown();
                };


                try (MessageConsumer con = cc.consume(handler)) {
                    latch.await(LATCH_WAIT, LATCH_UNIT);
                }
            }
            catch (Exception e) {
                Debug.info("CON EX", name, e);
            }
        }
    }

    private static void cleanConsumers() {
        try (Connection nc = Nats.connect(getOptions(4222))) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            List<String> cons = jsm.getConsumerNames(STREAM);
            if (!cons.isEmpty()) {
                for (String con : cons) {
                    try {
                        jsm.deleteConsumer(STREAM, con);
                    }
                    catch (Exception ignore) {}
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    private static Options getOptions(int port) {
        return new Options.Builder()
            .server("nats://localhost:" + port)
            .build();
    }

    private static String format(AtomicLong count) {
        return String.format("%,d", count.get());
    }

    private static final AtomicInteger READER_ID = new AtomicInteger();
    private static String nextReaderId() {
        return Integer.toString(READER_ID.incrementAndGet());
    }

    private static final AtomicInteger CONSUMER_ID = new AtomicInteger();
    private static String nextConsumerId() {
        return Integer.toString(CONSUMER_ID.incrementAndGet());
    }
}
