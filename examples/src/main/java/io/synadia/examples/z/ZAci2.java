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
import io.synadia.client.support.DebugErrorListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ZAci2 {

    private static final String SEP = "-------------------------------------------------------";

    public static void main(String[] args) {
        String server = "localhost:4222";
        ErrorListener noopEl = new DebugErrorListener(); // new ErrorListener() {};

        Options minOptions = new Options.Builder().server(server).errorListener(noopEl)
            .build();

        Options noRespondersOptions = new Options.Builder().server(server).errorListener(noopEl)
            .reportNoResponders()   // TimeoutException for timeouts
            .build();

        Options timeoutOptions = new Options.Builder().server(server).errorListener(noopEl)
            .useTimeoutException()   // TimeoutException for timeouts
            .build();

        try (Connection ncMin = Nats.connect(minOptions);
             Connection ncNoResponders = Nats.connect(noRespondersOptions);
             Connection ncTimeout = Nats.connect(timeoutOptions);
        ) {
            testNoResponders("Minimal", ncMin);
            testNoResponders("No Responders", ncNoResponders);
            testNoResponders("Timeout", ncTimeout);
            testTimeoutWithFuture("Minimal", ncMin);
            testTimeoutWithFuture("Timeout", ncTimeout);
            testTimeout("Minimal", ncMin);
            testTimeout("Timeout", ncTimeout);
        }
        catch (Exception e) {
            System.out.println("Failure: " + e.getMessage());
        }
    }

    private static void testNoResponders(String label, Connection nc) {
        System.out.println("\n" + SEP + "\nTest No Responders with '" + label + "' Options\n" + SEP);
        requestWithFuture(nc, unique());
    }

    private static void testTimeoutWithFuture(String label, Connection nc) {
        System.out.println("\n" + SEP + "\nTest Timeout W/Future with '" + label + "' Options\n" + SEP);
        String subject = unique();
        Dispatcher d = nc.createDispatcher(m -> {
            System.out.println("Replier received subject '" + m.getSubject());
            // NOT RESPONDING SO THERE IS A TIMEOUT
        });
        d.subscribe(subject);
        request(nc, subject);
        d.unsubscribe(subject);
    }

    private static void testTimeout(String label, Connection nc) {
        System.out.println("\n" + SEP + "\nTest Timeout with '" + label + "' Options\n" + SEP);
        String subject = unique();
        Dispatcher d = nc.createDispatcher(m -> {
            System.out.println("Replier received subject '" + m.getSubject());
            // NOT RESPONDING SO THERE IS A TIMEOUT
        });
        d.subscribe(subject);
        requestWithFuture(nc, subject);
        d.unsubscribe(subject);
    }

    private static void requestWithFuture(Connection nc, String subject) {
        String data = unique();
        System.out.println("Making request on subject '" + subject + "'");
        CompletableFuture<Message> future = nc.request(subject, data.getBytes(StandardCharsets.UTF_8));
        try {
            Message m = future.get(500, TimeUnit.MILLISECONDS);
            if (m == null) {
                System.out.println("No response received.");
            }
            else {
                System.out.println("Received: " + new String(m.getData(), StandardCharsets.UTF_8));
            }
        }
        catch (Exception e) {
            System.out.println("Exception: " + stringify(e));
        }
    }

    private static void request(Connection nc, String subject) {
        String data = unique();
        System.out.println("Making request on subject '" + subject + "'");
        try {
            Message m = nc.request(subject, data.getBytes(StandardCharsets.UTF_8), Duration.ofMillis(500));
            if (m == null) {
                System.out.println("No response received.");
            }
            else {
                System.out.println("Received: " + new String(m.getData(), StandardCharsets.UTF_8));
            }
        }
        catch (Exception e) {
            System.out.println("Exception: " + stringify(e));
        }
    }

    private static String stringify(Exception e) {
        if (e.getCause() == null) {
            return e.toString();
        }
        return "\n  " + e.toString().replaceFirst(": ", ":\n    ");
    }

    static AtomicInteger unique = new AtomicInteger(0xE0);
    private static String unique() {
        return "U" + Integer.toHexString(unique.incrementAndGet()).toUpperCase();
    }
}
