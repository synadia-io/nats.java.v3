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

import java.time.Duration;

public class Scratch {
    public static void main(String[] args) {
//        Options options;
//        options = Options.builder().userInfo("alice", "alice").build();
//        options = Options.builder().userInfo("auth", "auth").build();
//        subscribe(options);

//        options = Options.builder().userInfo("pub", "pub").build();
//        publish(options);

        Duration d = Duration.ofSeconds(-1);
        System.out.println(d.toMillis());
    }

    private static void publish(Options options) {
        try (Connection nc = Nats.connect(options)) {
            for (int x = 0; x < 10_000_000; x++) {
                String msg = "data-" + x;
                nc.publish("alice-test", msg.getBytes());
                System.out.println(msg);
                Thread.sleep(1000);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void subscribe(Options options) {
        try (Connection nc = Nats.connect(options)) {
            Subscription sub = nc.subscribe("alice.test");
            while (true) {
                Message m = sub.nextMessage(1500);
                if (m == null) {
                    System.out.println("Message Timeout");
                }
                else
                {
                    System.out.println("Got Message: " + new String(m.getData()));
                }

            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
