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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.synadia.client.support.NatsConstants.NANOS_PER_MILLI;

public class ZUtf8Test implements Comparable<ZUtf8Test> {

    public static final String ASCII = "utf8test";
    public static final String UNICODE = "Ùnìcødé!";

    public static final int COUNT = 1_000_000;
    public static final int ENLARGE_SUBJECT_AT = COUNT / 25;
    public static final long BENCH_SLEEP = 2000;
    public static final long BETWEEN_SLEEP = 5000;

    public static final String SERVER = "localhost:4222";

    public static void main(String[] args) {
        try {
            List<ZUtf8Test> done = new ArrayList<>();
            List<ZUtf8Test> ready = new ArrayList<>();
            for (int x = 0; x < 10; x++) {
//                ready.add(new ZUtf8Test(true, ASCII));
                ready.add(new ZUtf8Test(false, ASCII));
//                ready.add(new ZUtf8Test(true, UNICODE));
//                ready.add(new ZUtf8Test(false, UNICODE));
            }
            int order = 0;
            while (!ready.isEmpty()) {
                int ix = ThreadLocalRandom.current().nextInt(ready.size());
                ZUtf8Test state = ready.remove(ix);
                if (++order > 1) {
                    //noinspection BusyWait
                    Thread.sleep(BETWEEN_SLEEP);
                }
                state.order = order;
                System.out.println(state);
                done.add(bench(state));
            }

            done.sort(null);
            System.out.println();
            System.out.println();
            String last = done.get(0).getSortable();
            for (ZUtf8Test state : done) {
                if (!last.equals(state.getSortable())) {
                    System.out.println();
                    last = state.getSortable();
                }
                System.out.println(state);
            }
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    boolean supportUtf8Subjects;
    String subject;
    long elapsedNanos;
    CountDownLatch latch;
    int order;

    public ZUtf8Test(boolean supportUtf8Subjects, String subject) {
        this.supportUtf8Subjects = supportUtf8Subjects;
        this.subject = subject;
        this.latch = new CountDownLatch(COUNT);
    }

    @Override
    public int compareTo(ZUtf8Test o) {
        return getSortable().compareTo(o.getSortable());
    }

    private String getSortable() {
        return (supportUtf8Subjects ? "supportUtf8Subjects" : "ascii") + (subject.equals(ASCII) ? "ascii  " : "unicode");
    }

    @Override
    public String toString() {
        return elapsedNanos == 0
            ? ((supportUtf8Subjects ? "supportUtf8Subjects" : "ascii") + " | " + (subject.equals(ASCII) ? "ascii" : "unicode"))
            : ((elapsedNanos / NANOS_PER_MILLI) + "ms"
            + " | " + (elapsedNanos / COUNT) + "ns"
            + " | " + (supportUtf8Subjects ? "supportUtf8Subjects" : "ascii")
            + " | " + (subject.equals(ASCII) ? "ascii" : "unicode")
            + " | " + (latch.getCount() == 0 ? "Complete" : "Incomplete")
            + " | # " + order)
            ;
    }

    private static ZUtf8Test bench(ZUtf8Test state) throws Exception {
        Options options = state.supportUtf8Subjects
            ? new Options.Builder().server(SERVER).supportUTF8Subjects().build()
            : new Options.Builder().server(SERVER).build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();
            d.subscribe(state.subject + ".>", m -> state.latch.countDown());

            long slept = 0;
            String subject = state.subject + "." + state.subject;
            long start = System.nanoTime();
            for (int x = 0; x < COUNT; x++) {
                if (x % ENLARGE_SUBJECT_AT == 0) {
                    subject += "." + state.subject;
                    System.out.println(x + " | " + subject.length() + " | " + state.latch.getCount());
                    Thread.sleep(BENCH_SLEEP);
                    slept += BENCH_SLEEP;
                }

                nc.publish(subject, null);
            }

            state.latch.await(10, TimeUnit.MINUTES);

            state.elapsedNanos = (System.nanoTime() - start) - (slept * NANOS_PER_MILLI);
            System.out.println(state);
            return state;
        }
    }
}
