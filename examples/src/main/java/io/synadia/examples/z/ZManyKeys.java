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
import io.synadia.client.api.KeyResult;
import io.synadia.client.api.KeyValueConfiguration;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class ZManyKeys {
    public static void main(String[] args) {
        try (Connection nc = Nats.connectReconnectOnConnect()) {
            KeyValueManagement kvm = nc.keyValueManagement();
            kvm.create(KeyValueConfiguration.builder().name("bucket").build());
            KeyValue kv = nc.keyValue("bucket");
            for (int x = 0; x < 1_000_000; x++) {
                if (x % 10000 == 0) {
                    System.out.println("PUB " + x);
                }
                kv.put(new NUID().nextSequence(), "" + x);
            }

            long now = 0;
            kv.keys(); // prime the server

            now = System.nanoTime();
            List<String> list = kv.keys();
            for (String k : list) {}
            System.out.println(System.nanoTime() - now);

            now = System.nanoTime();
            LinkedBlockingQueue<KeyResult> q = kv.consumeKeys();
            KeyResult r = q.poll();
            while (r != null) {
                if (r.isDone()) {
                    r = null;
                }
                else {
                    r = q.poll();
                }
            }
            System.out.println(System.nanoTime() - now);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
