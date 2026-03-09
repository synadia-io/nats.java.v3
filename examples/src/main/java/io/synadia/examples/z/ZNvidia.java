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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.support.NatsConstants.NANOS_PER_MILLI;

@SuppressWarnings({"CallToPrintStackTrace", "ExtractMethodRecommender"})
public class ZNvidia {

    public static final String STREAM = "streamN";
    public static final String SUBJECT = "subN";
    public static final String CONSUMER = "conN";
    public static final int PUB_THREADS = 200;
    public static final long PUB_DELAY = 10;
    public static final int PUB_STOP = 1_000_000;

    static LinkedBlockingQueue<Info> FUTURES = new LinkedBlockingQueue<>();
    static List<Info> INFOS = Collections.synchronizedList(new ArrayList<Info>());

    static final CountDownLatch latch = new CountDownLatch(PUB_THREADS);
    static final AtomicLong INFO_NUM = new AtomicLong();

    static class Info {
        public final String group;
        public final long num;
        public final long created;
        public CompletableFuture<PublishAck> future;
        public PublishAck pa;
        public Throwable throwable;
        public long paTime = -1;
        public long paElapsed = 0;

        public Info(int group) {
            this.group = pad4(group);
            num = INFO_NUM.incrementAndGet();
            created = System.nanoTime();
        }

        public Info(Message m) {
            String[] split = new String(m.getData()).split("\\Q.\\E");
            group = split[0];
            num = Long.parseLong(split[1]);
            created = Long.parseLong(split[2]);
        }

        public byte[] getData() {
            return (group + "." + num + "." + created).getBytes();
        }

        void record(PublishAck pa, Throwable t) {
            long now = System.nanoTime();
            if (paTime == -1) {
                paTime = now;
                this.pa = pa;
                throwable = t;
                paElapsed = paTime - created;
                String pas = pa == null ? "" : " | " + pa.getJv().toJson();
                String ts = throwable == null ? "" : " | " + throwable.getMessage();
                System.out.println("REC: " + group + "." + num + " | " + paElapsed + pas + ts);
            }
        }
    }

    static class Publisher implements Runnable {
        int publisherNumber;
        JetStream js;

        public Publisher(int publisherNumber, JetStream js) {
            this.publisherNumber = publisherNumber;
            this.js = js;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                Info i = new Info(publisherNumber);
                i.future = js.publishAsync(SUBJECT, i.getData());
                i.future.whenComplete(i::record);

                FUTURES.offer(i);
                INFOS.add(i);

                if (i.num > PUB_STOP) {
                    latch.countDown();
                    return;
                }

                sleep(PUB_DELAY);
            }
        }
    }

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            createStream(jsm);

            JetStream js = nc.jetStream();

            for (int x = 1; x <= PUB_THREADS; x++) {
                new Thread(new Publisher(x, js)).start();
            }

            Thread futureHandlerThread = new Thread(() -> {
                int nullCount = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    Info i = FUTURES.poll();
                    if (i == null) {
                        if (++nullCount > 100 && latch.getCount() == 0) {
                            return;
                        }
                        sleep(10);
                    }
                    else {
                        nullCount = 0;
                        if (i.future.isDone()) {
                            try {
                                i.record(i.future.get(), null);
                            }
                            catch (Throwable t) {
                                i.record(null, t);
                            }
                        }
                        else {
                            FUTURES.offer(i); // put it back
                            sleep(10);
                        }
                    }
                }
            });
            futureHandlerThread.start();
            futureHandlerThread.join();

            long total = 0;
            long successes = 0;
            long failures = 0;
            for (Info i : INFOS) {
                total += i.paElapsed;
                if (i.pa != null) {
                    successes++;
                }
                if (i.throwable != null) {
                    failures++;
                }
            }

            long average = total / INFOS.size();
            System.out.println();
            System.out.println("Successes:  " + successes);
            System.out.println("Failures:   " + failures);
            System.out.println("Average ns: " + average);
            System.out.println("Average ms: " + (double)average / NANOS_PER_MILLI);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        System.out.println("createStream");
        deleteStream(jsm);

        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(STREAM)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .build();
            StreamInfo si = jsm.addStream(sc);
            System.out.println("Created stream: " + si.getJv());
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '' " + e);
            System.exit(-1);
        }
    }


    private static void deleteStream(JetStreamManagement jsm) {
        try {
            jsm.deleteStream(STREAM);
        }
        catch (Exception ignore) {}
    }


    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String pad4(int i) {
        if (i < 10) { return "___" + i; }
        if (i < 100) { return "__" + i; }
        if (i < 1000) { return "_" + i; }
        return "" + i;
    }
}