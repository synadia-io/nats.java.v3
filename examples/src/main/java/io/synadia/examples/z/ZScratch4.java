// Copyright 2015-2018 The NATS Authors
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

public class ZScratch4 {
    public static void main(String[] args) throws InterruptedException {
        test(100);
        test(-100);
        test(-1000);
        test(Long.MAX_VALUE);
        test(Long.MAX_VALUE - 100);
        test(Long.MAX_VALUE + 100);

        testCurrentTimeMillis();
    }

    private static void test(long start) {
        long end = start + 500;
        long elapsed = end - start;
        System.out.println(start + " | " + end + " | " + elapsed);
    }

    public static void testCurrentTimeMillis() throws InterruptedException {
        System.out.println("????? No way to refresh TimeZone in running java app? Stop, change Timezone, start");

        System.out.println(System.currentTimeMillis());

        System.out.println("Change TZ, you have 25sec");
        Thread.sleep(25_000);

        System.out.println(System.currentTimeMillis());
    }
}

