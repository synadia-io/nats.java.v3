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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ZAci {

    private static final String SEP = "-------------------------------------------------------";

    public static void main(String[] args) {
        String server = "localhost:4222";
        ErrorListener noopEl = new ErrorListener() {};

        Options minOptions = new Options.Builder().server(server).errorListener(noopEl)
            .build();

        Options noRespondersOptions = new Options.Builder().server(server).errorListener(noopEl)
            .reportNoResponders()
            .build();

        try (Connection ncMin = Nats.connect(minOptions);
             Connection ncNoResponders = Nats.connect(noRespondersOptions);
        ) {
            testWithFuture("Minimal", ncMin, false);
            testWithFuture("No Responders", ncNoResponders, true);
            testWithoutFuture("Minimal", ncMin);
            testWithoutFuture("No Responders", ncNoResponders);
        }
        catch (Exception e) {
            System.out.println("Failure: " + e.getMessage());
        }
    }

    private static void testWithFuture(String label, Connection nc, boolean expectNoResponders) {
        System.out.println("\n" + SEP + "\nTest With Future, Connect with '" + label + "' Options\n" + SEP);
        requestWithFuture(nc, expectNoResponders);
    }

    private static void testWithoutFuture(String label, Connection nc) {
        System.out.println("\n" + SEP + "\nTest W/O Future, Connect with '" + label + "' Options\n" + SEP);
        requestWithoutFuture(nc);
    }

    private static void requestWithFuture(Connection nc, boolean expectNoResponders) {
        String subject = subject();
        byte[] data = data();
        System.out.println("Making request on subject '" + subject + "'");
        CompletableFuture<Message> future = nc.request(subject, data);
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
            if (expectNoResponders) {
                System.out.println("Should be No Responders: " + stringify(e));
            }
            else {
                System.out.println("Should Be CancellationException: " + stringify(e));
            }
        }
    }

    private static void requestWithoutFuture(Connection nc) {
        String subject = subject();
        byte[] data = data();
        System.out.println("Making request on subject '" + subject + "'");
        try {
            Message m = nc.request(subject, data, Duration.ofMillis(500));
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
            return "\n  " + e;
        }
        return "\n  " + e.toString().replaceFirst(": ", ":\n    ");
    }


    static AtomicInteger S = new AtomicInteger(0xA0);
    private static String subject() {
        return "S-" + Integer.toHexString(S.incrementAndGet()).toUpperCase();
    }

    static AtomicInteger D = new AtomicInteger(0xD0);
    private static byte[] data() {
        return ("D-" + Integer.toHexString(D.incrementAndGet()).toUpperCase()).getBytes(StandardCharsets.UTF_8);
    }
}
