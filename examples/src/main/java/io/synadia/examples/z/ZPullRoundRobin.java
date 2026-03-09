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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.examples.ExampleArgs;
import io.synadia.examples.ExampleUtils;
import io.synadia.examples.jetstream.NatsJsUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.examples.jetstream.NatsJsUtils.publish;

public class ZPullRoundRobin {
    static ExampleArgs exArgs;

    public static void main(String[] args) {
        exArgs = ExampleArgs.builder("Pull Vs", args, "")
            .defaultStream("rr-stream")
            .defaultSubject("rr-subject")
            .defaultDurable("rr-durable")
            .defaultMsgCount(100000)
            .defaultMsgSize(5000)
            .build();

        try (Connection nc = Nats.connect(ExampleUtils.createExampleOptions(exArgs.server))) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

//            setupStreamAndPublish(jsm, js);
            setupConsumer(jsm);

            AtomicInteger sharedCount = new AtomicInteger();
            List<Thread> threads = new ArrayList<>();
            for (int x = 1; x <= 5; x++) {
                threads.add(new Thread(new Puller(x, js, 100, sharedCount)));
            }
            for (Thread t : threads) {
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Entry {
        int owner;
        long msgId;

        public Entry(int owner, long msgId) {
            this.owner = owner;
            this.msgId = msgId;
        }
    }

    static final Object lock = new Object();
    static final List<Entry> Entries = new ArrayList<>();

    static void record(Entry e, AtomicInteger sharedCount) {
        synchronized (lock) {
            Entries.add(e);
            System.out.printf("[%d] %3d | %5d\n", e.owner, e.msgId, sharedCount.incrementAndGet());
        }
    }

    static class Puller implements Runnable {
        int id;
        JetStream js;
        int batchSize;
        AtomicInteger sharedCount;

        public Puller(int id, JetStream js, int batchSize, AtomicInteger sharedCount) {
            this.id = id;
            this.js = js;
            this.batchSize = batchSize;
            this.sharedCount = sharedCount;
        }

        @Override
        public void run() {
            JetStreamSubscription sub;
            try {
                sub = js.subscribe(null, PullSubscribeOptions.bind(exArgs.stream, exArgs.durable));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }

            sub.pull(batchSize);
            sub.pull(batchSize);
            sub.pull(batchSize);
            int round = 0;
            int red = 0;
            while (sharedCount.get() < exArgs.msgCount) {
                try {
                    Message m = sub.nextMessage(1000);
                    if (m != null) {
                        red++;
                        round++;
                        record(new Entry(id, NatsJsUtils.extractId(m)), sharedCount);
                        m.ack();
                        if (round == batchSize) {
                            round = 0;
                            sub.pull(batchSize);
                        }
                        Thread.sleep(1);
                    }
                }
                catch (InterruptedException e) {
                    System.out.println("READ EX " + e);
                }
            }
        }
    }

    private static void setupConsumer(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try {
            jsm.deleteConsumer(exArgs.stream, exArgs.durable);
        }
        catch (JetStreamApiException ignore) {}
        jsm.addOrUpdateConsumer(exArgs.stream,
            ConsumerConfiguration.builder().durable(exArgs.durable).ackWait(2500).build());
    }

    private static void setupStreamAndPublish(JetStreamManagement jsm, JetStream js) throws IOException, JetStreamApiException {
        System.out.println("Setting up stream and publishing...");
        createStream(jsm, exArgs.stream, exArgs.subject);
        publish(js, exArgs.subject, "vs", exArgs.msgCount, exArgs.msgSize, false);
    }

    private static void createStream(JetStreamManagement jsm, String streamName, String... subjects) throws IOException, JetStreamApiException {
        try {
            jsm.deleteStream(streamName);
        }
        catch (JetStreamApiException ignore) {
            // don't care
        }

        jsm.addStream(StreamConfiguration.builder()
            .name(streamName)
            .storageType(StorageType.File)
            .replicas(3)
            .subjects(subjects)
            .build());

    }
}
