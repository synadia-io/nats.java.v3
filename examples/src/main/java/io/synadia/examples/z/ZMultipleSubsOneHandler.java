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

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.examples.ExampleUtils.EXAMPLE_CONNECTION_LISTENER;
import static io.synadia.examples.ExampleUtils.EXAMPLE_ERROR_LISTENER;

public class ZMultipleSubsOneHandler {

    public static void main(String[] args) throws IOException {

        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(EXAMPLE_ERROR_LISTENER)
            .connectionListener(EXAMPLE_CONNECTION_LISTENER)
            .build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher((msg) -> {
                System.out.println(msg.getSubject() + " " + new String(msg.getData()));
            });
            d.subscribe("foo");
            d.subscribe("bar");

            AtomicInteger foo = new AtomicInteger(1_000_000);
            AtomicInteger bar = new AtomicInteger(1_000_000);
            Thread f = new Thread(() -> {
                while (true) {
                    nc.publish("foo", new String(Integer.toString(foo.incrementAndGet())).getBytes());
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(10));
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            Thread b = new Thread(() -> {
                while (true) {
                    nc.publish("bar", new String(Integer.toString(bar.incrementAndGet())).getBytes());
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(10));
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            f.start();
            b.start();

            f.join();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }
}
