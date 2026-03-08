package io.nats.client.other;

import io.nats.client.*;
import io.nats.compatibility.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("CallToPrintStackTrace")
public class ClientCompatibilityMain {
    public static ExecutorService EXEC_SERVICE = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {

        String url = null;
        if (args != null && args.length == 1) {
            url = args[0];
        }
        if (url == null) {
            url = System.getenv("NATS_URL");
            if (url == null) {
                url = "nats://localhost:4222";
            }
        }

        Log.info("Url: " + url);
        Options options = new Options.Builder()
            .server(url)
            .errorListener(new ErrorListener() {})
            .build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();
            d.subscribe("tests.>", m-> {
                try {
                    TestMessage testMessage = new TestMessage(m);
                    if (testMessage.suite == Suite.DONE) {
                        System.exit(0);
                    }

                    if (testMessage.kind == Kind.RESULT) {
                        String p = testMessage.payload == null ? "" : new String(testMessage.payload);
                        if (testMessage.something.equals("pass")) {
                            Log.info("PASS", testMessage.subject, p);
                        }
                        else {
                            Log.error("FAIL", testMessage.subject, p);
                        }
                        return;
                    }

                    EXEC_SERVICE.submit(() -> {
                        try {
                            //noinspection SwitchStatementWithTooFewBranches
                            switch (testMessage.suite) {
                                case OBJECT_STORE:
                                    new ObjectStoreCommand(nc, testMessage).execute();
                                    break;
                                default:
                                    Log.error("UNSUPPORTED SUITE: " + testMessage.suite);
                                    break;
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            System.exit(-2);
                        }
                    });
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            });

            Log.info("Ready");
            Thread.sleep(600000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
