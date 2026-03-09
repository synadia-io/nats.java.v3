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
import io.synadia.client.JetStreamManagement;
import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.impl.ErrorListenerConsoleImpl;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.examples.z.Z0Utils.createOrReplaceStream;

public class ZMaxMessagesInOutgoingQueue {

    public static void main(String[] args) {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .connectionListener((conn, type) -> System.out.println("CL: " + type))
            .errorListener(new ErrorListenerConsoleImpl())
            .maxMessagesInOutgoingQueue(20)
            .build();

        try (Connection nc = Nats.connect(options)) {
            String stream = "mmioq";
            String subject = "z";
            JetStreamManagement jsm = nc.jetStreamManagement();
            createOrReplaceStream(jsm, stream, subject);

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> publish(nc, subject));
                threads[i].start();
            }
            publish(nc, subject);

            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
            }

            Thread.sleep(1000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int TIX = 0;
    private static final AtomicBoolean KEEP_RUNNING = new AtomicBoolean(true);

    private static void publish(Connection nc, String subject) {
        int tix = ++TIX;
        try {
            for (int x = 0; KEEP_RUNNING.get() && x < 1_000_000; x++) {
                nc.publish(subject, null);
            }
        }
        catch (Exception e) {
            KEEP_RUNNING.set(false);
            System.out.println("Thread " + tix + " : " + e);
        }
    }
}
