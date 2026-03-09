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

import io.synadia.client.NUID;
import io.synadia.client.support.JsonValue;

import java.util.ArrayList;
import java.util.List;

public class ZRunStat {
    public static void main(String[] args) throws InterruptedException {
//        Thread t = new Thread(() -> {
//            try {
//                System.out.println("!!!");
//                Thread.sleep(1000);
//            }
//            catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }, "TEE");
//        t.start();
//        t.interrupt();

        List<String> strings = new ArrayList<>();
        List<byte[]> bytes = new ArrayList<>();

        while (true) {
            RunStat runStat = new RunStat();
            JsonValue jv = new JsonValue(runStat.toJsonValueMap());
            String json = jv.toJson();
            RunStat test = new RunStat(jv);
            JsonValue jvTest = new JsonValue(test.toJsonValueMap());
            String jsonTest = jvTest.toJson();
            System.out.println((runStat.equals(test)) + "\n" + json + "\n" + jsonTest);
            System.out.println();

            for (int x = 0; x < 1000; x++) {
                for (int o = 0; o < 10; o++) {
                    strings.add(NUID.nextGlobal());
                }
                bytes.add(new byte[100]);
                Thread.sleep(1);
            }
        }

//        try (Connection nc = Nats.connectReconnectOnConnect()) {
//            KeyValueManagement kvm = nc.keyValueManagement();
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private static boolean isAlive(long id, long[] deadThreadIds) {
        for (long dead : deadThreadIds) {
            if (dead == id) {
                return false;
            }
        }
        return true;
    }
}
