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

import static io.synadia.examples.z.Z0Utils.cleanAndCreate;

public class ZSimplePubSub {

    public static final String STREAM = "stream";
    public static final String SUBJECT = "subject";
    public static final String CONSUMER = "consumer";

    public static void main(String[] args) {
        try {
            Options options = new Options.Builder()
                .build();

            try (Connection nc = Nats.connect(options)) {
                cleanAndCreate(nc, STREAM, SUBJECT);

                Dispatcher d = nc.createDispatcher();
                d.subscribe(SUBJECT, m -> {
                    System.out.println("REC " + new String(m.getData()));
                });

                Thread p = new Thread(() -> {
                    for (int x = 0; x < 10000; x++) {
                        nc.publish(SUBJECT, (x + "").getBytes());
                        try {
                            Thread.sleep(200);
                        }
                        catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                p.start();

                p.join();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
