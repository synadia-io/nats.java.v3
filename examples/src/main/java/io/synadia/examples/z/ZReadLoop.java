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
import io.synadia.client.support.Debug;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("CallToPrintStackTrace")
public class ZReadLoop {

    public static void main(String[] args) {
        Options options = Options.builder()
            .reportNoResponders()
            .build();
        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher(System.out::println);
            d.subscribe("readloop.x");

            JetStreamManagement jsm = nc.jetStreamManagement();
            CompletableFuture<Message> f = nc.request("readloop.a", null);
            f.handle((m,e) -> {
                Debug.msg("handle", m, e);
                Thread t = new Thread(() -> {
                    try {
                        nc.request("readloop.x", null, Duration.ofSeconds(2));
                    }
                    catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                });
                t.start();
                try {
                    t.join(2000);
                }
                catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
//                try {
//                    jsm.getStreams();
//                    nc.request("readloop2", null);
//                }
//                catch (Exception ex) {
//                    Debug.msg("ex inside", m, e);
//                }
                return m;
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}