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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class Z1420 {
    public static void main(String[] args) {
        Options options = Options.builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection connection = Nats.connect(options)) {
            AtomicInteger published = new AtomicInteger(0);
            AtomicInteger handled = new AtomicInteger(0);

            Dispatcher d = connection.createDispatcher();
            Subscription subscription = d.subscribe(
                "subject",
                m -> {
                    System.out.println("[" + System.currentTimeMillis() + "] Handler: " + new String(m.getData()));
                    handled.incrementAndGet();
                }
            );

            Thread t = new Thread(() -> {
                boolean done = false;
                int x = 0;
                while (!done) {
                    try {
                        connection.publish("subject", (++x + "").getBytes());
                        published.incrementAndGet();
                        Thread.sleep(10);
                    }
                    catch (IllegalStateException ise) {
                        System.out.println("[" + System.currentTimeMillis() + "] Publish: " + ise.getMessage());
                        done = true;
                    }
                    catch (InterruptedException e) {
                        System.out.println("[" + System.currentTimeMillis() + "] " + e.getMessage());
                        done = true;
                        Thread.currentThread().interrupt();
                    }
                }
            });
            t.start();

            Thread.sleep(500);

            System.out.println("[" + System.currentTimeMillis() + "] About to drain dispatcher...");
            CompletableFuture<Boolean> dDrained = subscription.drain(Duration.ofSeconds(10));
            System.out.println("[" + System.currentTimeMillis() + "] Dispatcher drained: " + dDrained.get());

            System.out.println("[" + System.currentTimeMillis() + "] Published Total: " + published.get());
            System.out.println("[" + System.currentTimeMillis() + "] Handled Total: " + handled.get());
        }
        catch (Exception e) {
            System.out.println("[" + System.currentTimeMillis() + "] Exception: " + e);
        }
    }

    private static void javaVersion(Connection connection) throws InterruptedException, ExecutionException, TimeoutException {
        Dispatcher d = connection.createDispatcher();
        Subscription subscription = d.subscribe(
            "subject",
            m -> System.out.println("[" + System.currentTimeMillis() + "] Handler: " + new String(m.getData()))
        );

        connection.publish("subject", "1".getBytes());
        connection.publish("subject", "2".getBytes());
        connection.publish("subject", "3".getBytes());
        connection.publish("subject", "4".getBytes());
        connection.publish("subject", "5".getBytes());

        Thread.sleep(50);

        System.out.println("[" + System.currentTimeMillis() + "] Going to drain dispatcher...");
        CompletableFuture<Boolean> dDrained = subscription.drain(Duration.ofSeconds(5));
        System.out.println("[" + System.currentTimeMillis() + "] Dispatcher drained: " + dDrained.get());

        System.out.println("[" + System.currentTimeMillis() + "] Going to drain connection...");
        CompletableFuture<Boolean> cDrained = connection.drain(Duration.ofSeconds(5));
        System.out.println("[" + System.currentTimeMillis() + "] Connection drained: " + cDrained.get());
    }
}
