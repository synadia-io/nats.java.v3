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

public class ZConnectionThrowingException {
    public static void main(String[] args) {
        ConnectionListener cl = (conn, type) -> debug("CL status change "+ type);

        ErrorListener el = new ErrorListener() {
            @Override
            public void errorOccurred(Connection conn, String error) {
                debug("EL errorOccurred " + error);
            }

            @Override
            public void exceptionOccurred(Connection conn, Exception exp) {
                debug("EL exceptionOccurred " + exp);
            }
        };

        Options options = Options.builder()
            .connectionListener(cl)
            .errorListener(el)
            .maxReconnects(0)
            .server("nats://localhost:4222,nats://localhost:4223,nats://localhost:4224")
            .build();

        try (Connection nc = Nats.connect(options)) {
            long wait = 600_000;
            while (wait > 0) {
                if (nc.getStatus() == Connection.Status.CONNECTED) {
                    debug("Connected...");
                }
                else {
                    debug("Not connected..." + nc.getStatus());
                }
                Thread.sleep(1000);
                wait -= 1000;
            }
        }
        catch (Exception e) {
            debug(e);
        }
    }

    static void debug(String debug) {
        System.out.println("[" + Thread.currentThread().getName() + "@" + time() + "] " + debug);
    }

    static void debug(Exception e) {
        System.err.println("[" + Thread.currentThread().getName() + "@" + time() + "] " + e);
        e.printStackTrace();
    }

    static String time() {
        String t = "" + System.currentTimeMillis();
        return t.substring(t.length() - 9);
    }
}
