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

import io.synadia.client.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Z1320 {

    private static final String SUBJECT = NUID.nextGlobalSequence(); // just a random name
    private static final AtomicBoolean CAN_PUBLISH = new AtomicBoolean(false);

    private static final ErrorListener EL = new ErrorListener() {
        @Override
        public void errorOccurred(Connection conn, String error) {
            debug("EL Error: " + error);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            debug("EL Exception", exp);
        }
    };

    private static final ConnectionListener CL = (conn, type) -> {
        debug("CL Event: " + type.name());
        if (type == ConnectionListener.Events.RESUBSCRIBED) {
            CAN_PUBLISH.set(true);
        }
        else if (type == ConnectionListener.Events.DISCONNECTED) {
            CAN_PUBLISH.set(false);
        }
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        Options options = Options.builder()
            .server("nats://localhost:4222")
            .connectionListener(CL)
            .errorListener(EL)
            .token("1234".toCharArray())
            .build();

        Connection nc = Nats.connect(options);
        Dispatcher d = nc.createDispatcher();

        d.subscribe(SUBJECT, m -> {
            debug("Received: " + new String(m.getData()));
        });

        CAN_PUBLISH.set(true);
        new Thread(publish(nc)).start();
    }

    @SuppressWarnings("BusyWait")
    private static Runnable publish(Connection nc) {
        return () -> {
            int round = 0;
            int pub = 0;
            int disc = 999;
            while (true) {
                try {
                    if (CAN_PUBLISH.get()) {
                        if (disc > 0) {
                            round++;
                            pub = 0;
                            disc = 0;
                        }
                        String data = round + "." + ++pub;
                        nc.publish(SUBJECT, data.getBytes());
//                        debug("Publish " + data);
                        Thread.sleep(500);
                    }
                    else {
                        debug("Pub-Disconnected " + round + "." + ++disc);
                        Thread.sleep(1000);
                    }
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    static void debug(String debug) {
        System.out.println("[" + Thread.currentThread().getName() + "@" + time() + "] " + debug);
    }

    @SuppressWarnings({"SameParameterValue", "CallToPrintStackTrace"})
    static void debug(String debug, Exception e) {
        System.err.println("[" + Thread.currentThread().getName() + "@" + time() + "] " + debug + " | " + e);
        e.printStackTrace();
    }

    static String time() {
        String t = "" + System.currentTimeMillis();
        return t.substring(t.length() - 9);
    }
}