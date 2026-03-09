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

import java.util.Date;

public class ZTimeTest {
    public static void main(String[] args) throws InterruptedException {
        System.out.println(new Date());
        System.out.println(System.nanoTime());
        Thread.sleep(5000);
        System.out.println(new Date());
        System.out.println(System.nanoTime());
        if (true) return;

        long maxWaitMillis = 1000;
        long start = 1_650_000_000_000L; // simulate a time
        for (long elapsed = 800; elapsed < 1200; elapsed += 300) {
            long normalNow = start + elapsed;
            long backwardsNow = start - (1000 * 60 * 60) + elapsed; // back 1 hour
            long forwardsNow = start + (1000 * 60 * 60) + elapsed; // forward 1 hour
            System.out.println("-------------------------------------------------------------");
            System.out.println("elapsed = " + elapsed);
            System.out.println("normalNow - start    = " + (normalNow - start));
            System.out.println("backwardsNow - start = " + (backwardsNow - start));
            System.out.println("forwardsNow - start  = " + (forwardsNow - start));
            System.out.println("norm old = " + (maxWaitMillis - (normalNow - start)));
            System.out.println("norm new = " + (maxWaitMillis - Math.max(0, normalNow - start)));
            System.out.println("norm abs = " + (maxWaitMillis - Math.abs(normalNow - start)));
            System.out.println("back old = " + (maxWaitMillis - (backwardsNow - start)));
            System.out.println("back new = " + (maxWaitMillis - Math.max(0, backwardsNow - start)));
            System.out.println("back abs = " + (maxWaitMillis - Math.abs(backwardsNow - start)));
            System.out.println("fwd old  = " + (maxWaitMillis - (forwardsNow - start)));
            System.out.println("fwd new  = " + (maxWaitMillis - Math.max(0, forwardsNow - start)));
            System.out.println("fwd abs  = " + (maxWaitMillis - Math.abs(forwardsNow - start)));
        }
    }
}