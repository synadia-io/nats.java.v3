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
import io.synadia.client.api.KeyValueConfiguration;
import io.synadia.client.api.KeyValueEntry;
import io.synadia.client.api.KeyValueWatcher;
import io.synadia.client.api.StorageType;
import io.synadia.client.impl.NatsKeyValueWatchSubscription;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"BusyWait", "InfiniteLoopStatement"})
public class ZResilientWatch {

    private static final String[] SERVERS = new String[]{"nats://localhost:4222"};
    private static final String BUCKET_NAME = "bucket";
    private static final int NUM_KEYS = 10;
    private static final long PUT_DELAY = 250;
    private static final long REPORT_FREQUENCY = 3000;

    public static void main(String[] args) throws IOException {

        Options options = new Options.Builder()
            .servers(SERVERS)
            .connectionListener(new MinCl())
            .errorListener(new MinEl())
            .build();

        try (Connection nc = Nats.connect(options)) {
            createKv(nc);

            KeyValue kv = nc.keyValue(BUCKET_NAME);

            AtomicInteger puts = new AtomicInteger();
            AtomicInteger watched = new AtomicInteger();
            AtomicReference<KeyValueEntry> lastWatched = new AtomicReference<>();
            AtomicLong nextReport = new AtomicLong();

            KeyValueWatcher watcher = new KeyValueWatcher() {
                @Override
                public void watch(KeyValueEntry kve) {
                    watched.incrementAndGet();
                    lastWatched.set(kve);
                }

                @Override
                public void endOfData() {}
            };
            NatsKeyValueWatchSubscription watch = kv.watchAll(watcher);

            AtomicInteger[] nextValues = new AtomicInteger[NUM_KEYS];
            for (int i = 0; i < NUM_KEYS; i++) {
                nextValues[i] = new AtomicInteger();
            }
            while (true) {
                KeyValueEntry kve = lastWatched.get();
                if (kve != null && nextReport.get() < System.currentTimeMillis()) {
                    System.out.println(System.currentTimeMillis()
                        + " | Puts/Watched: " + puts.get() + "/" + watched.get()
                        + " | Last Watched:" + kve.getKey() + "=" + kve.getValueAsString()
                    );
                    nextReport.set(System.currentTimeMillis() + REPORT_FREQUENCY);
                }

                try {
                    int ix = ThreadLocalRandom.current().nextInt(NUM_KEYS);
                    String key = "k" + ix;
                    String value = "v" + nextValues[ix].getAndIncrement();
                    kv.put(key, value);
                    puts.incrementAndGet();
                }
                catch (Exception e) {
                    System.out.println(System.currentTimeMillis() + " | " + e.getMessage());
                    nextReport.set(0);
                }
                Thread.sleep(PUT_DELAY);
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createKv(Connection nc) throws IOException, JetStreamApiException {
        try {
            nc.keyValueManagement().delete(BUCKET_NAME);
        }
        catch (Exception ignore) {}

        KeyValueManagement kvm = nc.keyValueManagement();
        kvm.create(KeyValueConfiguration.builder()
            .name(BUCKET_NAME)
            .storageType(StorageType.File)
            .build());
    }

    static class MinCl implements ConnectionListener {
        @Override
        public void connectionEvent(Connection conn, Events type) {
            System.out.println(System.currentTimeMillis() + " | " + type.name());
        }
    }

    static class MinEl implements ErrorListener {
        @Override
        public void errorOccurred(final Connection conn, final String error) {
            System.out.println(supplyMessage(System.currentTimeMillis() + " | errorOccurred", conn, null, null, "Error: ", error));
        }

        @Override
        public void exceptionOccurred(final Connection conn, final Exception exp) {
            System.out.println(supplyMessage(System.currentTimeMillis() + " | exceptionOccurred", conn, null, null, "Exception: ", exp));
        }

        @Override
        public void heartbeatAlarm(final Connection conn, final JetStreamSubscription sub,
                                   final long lastStreamSequence, final long lastConsumerSequence) {
            System.out.println(supplyMessage(System.currentTimeMillis() + " | heartbeatAlarm", conn, null, sub, "lastStreamSequence: ", lastStreamSequence, "lastConsumerSequence: ", lastConsumerSequence));
        }
    }
}

