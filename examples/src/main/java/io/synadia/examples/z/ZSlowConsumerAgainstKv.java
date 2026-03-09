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
import io.synadia.client.impl.NatsKeyValueWatchSubscription;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class ZSlowConsumerAgainstKv {

    private static final String BUCKET_NAME = "buck";
    private static final int KEYSTART = 655_360;

    static class El implements ErrorListener {
        public int slow = 0;
        public int fc = 0;

        @Override
        public void slowConsumerDetected(Connection conn, Consumer consumer) {
            System.out.println("\nSLOW " + (++slow));
        }

        @Override
        public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String subject, FlowControlSource source) {
            System.out.println("\nFC DIS " + (++fc) + " | bc:" + sub.getPendingByteCount() + " bl:" + sub.getPendingByteLimit() + " mc:" + sub.getPendingMessageCount() + " ml:" + sub.getPendingMessageLimit());
        }
    }
    public static void main(String[] args) throws IOException {
        El el = new El();

        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(el)
            .build();

        try (Connection nc = Nats.connect(options)) {
            createKv(nc);
            KeyValue kv = nc.keyValue(BUCKET_NAME);
            AtomicInteger puts = new AtomicInteger();
            AtomicInteger dels = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);

            KeyValueWatcher watcher = new KeyValueWatcher() {
                @Override
                public void watch(KeyValueEntry keyValueEntry) {
                    System.out.println(keyValueEntry.getKey() + " ");
                    if (keyValueEntry.getOperation() == KeyValueOperation.PUT) {
                        puts.incrementAndGet();
//                        System.out.print(".");
                    }
                    else {
                        dels.incrementAndGet();
//                        System.out.print("x");
                    }
                }

                @Override
                public void endOfData() {
                    latch.countDown();
                }
            };

            NatsKeyValueWatchSubscription watch = kv.watchAll(watcher);
            latch.await();
            watch.unsubscribe();
            System.out.println();
            System.out.println("P-" + puts.get());
            System.out.println("D-" + dels.get());
            System.out.println("SLOW-" + el.slow);
            System.out.println("FC-" + el.fc);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createKv(Connection nc) throws IOException, JetStreamApiException {
        try {
            nc.keyValue(BUCKET_NAME);
            return;
        }
        catch (IOException meansDoesNotExist) { /* fall through and create */ }

        KeyValueManagement kvm = nc.keyValueManagement();
        kvm.create(KeyValueConfiguration.builder().name(BUCKET_NAME).storageType(StorageType.Memory).build());
        KeyValue kv = nc.keyValue(BUCKET_NAME);
        byte[] value = new byte[320];
        int del = 0;
        for (int put = 0; put < 300_000; put++) {
            String key = "" + (put + 1); // Integer.toHexString(put + KEYSTART);
            if (put % 5000 == 0) System.out.println("Put " + put);
            kv.put(key, value);
            int op = ThreadLocalRandom.current().nextInt(7);
            if (op == 0) {
                kv.delete(key);
                if (++del % 5000 == 0) System.out.println("Del " + put + " (" + del + ")");
            }
        }
    }
}
