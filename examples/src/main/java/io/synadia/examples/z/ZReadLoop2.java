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
import io.synadia.client.support.Debug;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("CallToPrintStackTrace")
public class ZReadLoop2 {

    static long TIMEOUT_MS = 2000;

    public static void main(String[] args) {
        Options options = new Options.Builder().server("nats://localhost:4222")
//            .useDispatcherWithExecutor()
            .useTimeoutException().build();
        try (Connection nc = Nats.connect()) {
            Dispatcher d = nc.createDispatcher(m -> {
                Debug.msg("HANDLER", m);
                Thread.sleep(TIMEOUT_MS - 200);
                nc.publish(m.getReplyTo(), ("RT" + m.getSubject()).getBytes());
            });
            d.subscribe("subject.*");

            AtomicInteger t = new AtomicInteger();
            while (true) {
                try (final Connection connection = Nats.connect(options)) {
                    System.out.println("\n\n\n");

                    int i = t.incrementAndGet();
                    String id1 = i + "A";
                    String id2 = i + "B";
                    Thread t1 = new Thread(null, () -> runFor503(connection, id1), "Thread-" + id1);
                    Thread t2 = new Thread(null, () -> runFor503(connection, id2), "Thread-" + id2);

                    t1.start();
                    t2.start();

                    t1.join();
                    t2.join();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runFor503(Connection connection, String id) {
        try {
            // Handler for `subject1` does not exist, and we should
            // immediately get 503 (CancellationException with null message).
            connection.requestWithTimeout("subject." + id, new byte[]{}, Duration.ofMillis(TIMEOUT_MS)).get();
        }
        catch (Exception e) {
            Debug.info("runEx", e);
            if (e.getCause() instanceof TimeoutException) {
                System.exit(-1);
            }
        }
    }
}