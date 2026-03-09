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

public class ZNSGW {
    static String[] TEST1C1 = new String[]{"--port", "4333", "--user", "hello", "--pass", "s3cret", "--pub", "q", "--request"};
    static String[] TEST1C2 = new String[]{"--port", "4333", "--user", "a", "--pass", "s3cret", "--pub", "q", "--request"};
    static String[] TEST2 = new String[]{"--port", "4333", "--user", "hello", "--pass", "s3cret", "--sub", "q"};
    static String[] TEST3 = new String[]{"--port", "4222", "--user", "a", "--pass", "s3cret", "--reconnect-wait", "500"};
    static String[] TEST4 = new String[]{"--port", "4333", "--user", "hello", "--pass", "s3cret", "--sub", "q"};

    public static void main(String[] args) throws Exception {
        _test(TEST3);
    }

    public static void _test(String[] args) throws Exception {
        int port = 4222;
        String name = null;
        String user = null;
        String pass = null;
        String pubSubject = null;
        String subSubject = null;
        int failAfter = 0;
        boolean useRequest = false;
        int reconnectWait = 0;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("-n") || args[i].equals("--name")) && i + 1 < args.length) {
                name = args[++i];
            } else if ((args[i].equals("-p") || args[i].equals("--port")) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--user") && i + 1 < args.length) {
                user = args[++i];
            } else if (args[i].equals("--pass") && i + 1 < args.length) {
                pass = args[++i];
            } else if (args[i].equals("--pub") && i + 1 < args.length) {
                pubSubject = args[++i];
            } else if (args[i].equals("--sub") && i + 1 < args.length) {
                subSubject = args[++i];
            } else if (args[i].equals("--fail-after") && i + 1 < args.length) {
                failAfter = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--request")) {
                useRequest = true;
            } else if (args[i].equals("--reconnect-wait") && i + 1 < args.length) {
                reconnectWait = Integer.parseInt(args[++i]);
            }
        }

        System.out.printf("options: port=%d user=%s pass=%s name=%s pub=%s sub=%s fail-after=%d request=%b%n",
            port, user, pass, name, pubSubject, subSubject, failAfter, useRequest);

        Options.Builder builder = new Options.Builder()
            .server("nats://localhost:" + port)
            .maxReconnects(-1)

            .errorListener(new ErrorListener() {
                @Override
                public void errorOccurred(Connection conn, String error) {
                    System.out.println("server error: " + error);
                }
                @Override
                public void exceptionOccurred(Connection conn, Exception exp) {
                    System.out.println("server error: " + exp.getMessage());
                }
                @Override
                public void slowConsumerDetected(Connection conn, Consumer consumer) {}
            })
            .connectionListener((conn, type) -> {
                switch (type) {
                    case CONNECTED:
                        System.out.println("connected to " + conn.getConnectedUrl());
                        break;
                    case DISCONNECTED:
                        System.out.println("disconnected");
                        break;
                    case RECONNECTED:
                        System.out.println("reconnected to " + conn.getConnectedUrl());
                        break;
                    case CLOSED:
                        String lastErr = conn.getLastError();
                        if (lastErr != null && !lastErr.isEmpty()) {
                            System.out.println("connection closed with error: " + lastErr);
                        } else {
                            System.out.println("connection closed");
                        }
                        break;
                    default:
                        System.out.println("event: " + type);
                }
            });
        if (user != null) {
            builder.userInfo(user, pass != null ? pass : "");
        }
        if (name != null) {
            builder.connectionName(name);
        }
        if (reconnectWait > 0) {
            builder.reconnectWait(Duration.ofMillis(reconnectWait));
        }
        Options options = builder.build();

        Connection tmp = null;
        while (tmp == null) {
            try {
                tmp = Nats.connectReconnectOnConnect(options);
            } catch (Exception e) {
                System.out.println("connect failed: " + e.getMessage() + ", retrying...");
                Thread.sleep(1000);
            }
        }
        final Connection nc = tmp;

        if (subSubject != null) {
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getReplyTo() != null) {
                    nc.publish(msg.getReplyTo(), "ok".getBytes());
                }
            });
            d.subscribe(subSubject);
            System.out.println("subscribed to " + subSubject);
        }

        if (pubSubject != null) {
            final String subject = pubSubject;
            final int fa = failAfter;
            final boolean req = useRequest;
            Thread pubThread = new Thread(() -> {
                int count = 0;
                while (!Thread.interrupted()) {
                    try {
                        count++;
                        byte[] payload = new byte[0];
                        if (fa > 0 && count % (fa + 1) == 0) {
                            payload = "xxxxxxxxxxxxxxxx".getBytes();
                        }
                        if (nc.getStatus() == Connection.Status.CONNECTED) {
                            if (req) {
                                try {
                                    Message msg = nc.request(subject, payload, Duration.ofSeconds(1));
                                    if (msg != null) {
                                        System.out.println("got response on " + subject);
                                    } else {
                                        System.out.println("request error: timeout");
                                    }
                                } catch (Exception e) {
                                    System.out.println("request error: " + e.getMessage());
                                }
                            } else {
                                nc.publish(subject, payload);
                                System.out.println("published to " + subject);
                            }
                        }
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            pubThread.setDaemon(true);
            pubThread.start();
        }

        System.out.println("waiting for events... (ctrl+c to quit)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                nc.close();
            } catch (InterruptedException e) {
                // ignore
            }
        }));

        Thread.currentThread().join();
    }
}
