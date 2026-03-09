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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ZHowFast {

    private static final int FILL1 = 10_000_000;
    private static final int FILL2 = 1000;
    private static final int DELAY = 10;
    public final boolean filterOut;

    public ZHowFast(int i) {
        this.filterOut = i % DELAY == 0;
    }

    public static void main(String[] args) throws InterruptedException {
        newWay();
        oldWay();
    }

    private static void newWay() throws InterruptedException {
        AtomicInteger out1 = new AtomicInteger();
        AtomicInteger tot1 = new AtomicInteger();
        LinkedBlockingQueue<ZHowFast> q = new LinkedBlockingQueue<>();
        fill(q, out1, tot1, FILL1, 0, new AtomicBoolean(true));
        AtomicBoolean keepGoing = new AtomicBoolean(true);
        AtomicInteger out2 = new AtomicInteger();
        AtomicInteger tot2 = new AtomicInteger();
        Thread t = new Thread(() -> fill(q, out2, tot2, FILL2, DELAY, keepGoing));
        t.start();

        long start = System.nanoTime();
        List<ZHowFast> list = new ArrayList<>();
        q.drainTo(list);
        for (ZHowFast z : list) {
            if (!z.filterOut) {
                q.offer(z);
            }
        }
        long elapsed = System.nanoTime() - start;

        keepGoing.set(false);
        System.out.println(elapsed + "ns | " + (elapsed / 1_000_000) + "ms");
        System.out.println("1] " + list.size() + "{" + tot1.get() + "} - " + out1.get());
        System.out.println("2] {" + tot2.get() + "} - " + out2.get());
        System.out.println(q.size());
        t.join();
    }

    private static void oldWay() {
        AtomicInteger out1 = new AtomicInteger();
        AtomicInteger tot1 = new AtomicInteger();
        LinkedBlockingQueue<ZHowFast> q = new LinkedBlockingQueue<>();
        fill(q, out1, tot1, FILL1, 0, new AtomicBoolean(true));
        AtomicBoolean keepGoing = new AtomicBoolean(true);
        AtomicInteger out2 = new AtomicInteger();
        AtomicInteger tot2 = new AtomicInteger();
        Thread t = new Thread(() -> fill(q, out2, tot2, FILL2, DELAY, keepGoing));
        t.start();

        long start = System.nanoTime();
        List<ZHowFast> list = new ArrayList<>();
        ZHowFast cursor = q.poll();
        while (cursor != null) {
            if (!cursor.filterOut) {
                list.add(cursor);
            }
            cursor = q.poll();
        }
        q.addAll(list);
        long elapsed = System.nanoTime() - start;

        keepGoing.set(false);
        System.out.println("\n" + elapsed + "ns | " + (elapsed / 1_000_000) + "ms");
        System.out.println("1] " + list.size() + "{" + tot1.get() + "} - " + out1.get());
        System.out.println("2] {" + tot2.get() + "} - " + out2.get());
        System.out.println(q.size());
    }

    private static void fill(LinkedBlockingQueue<ZHowFast> q,
                             AtomicInteger out,
                             AtomicInteger tot,
                             int count, long delay, AtomicBoolean keepGoing) {
        for (int i = 0; i < count && keepGoing.get(); i++) {
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            ZHowFast z = new ZHowFast(i);
            tot.incrementAndGet();
            if (z.filterOut) {
                out.incrementAndGet();
            }
            q.offer(z);
        }
    }
}
