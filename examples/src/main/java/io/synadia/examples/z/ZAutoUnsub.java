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
import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.Subscription;
import io.synadia.client.support.Debug;

public class ZAutoUnsub {

    public static final String SUBJECT = "scott-nvidia";

    public static void main(String[] args) {
        Options o = Options.builder()
            .connectionName("scott")
            .server("demo.nats.io")
            .build();
        try (Connection nc = Nats.connect(o)) {
            System.out.println(nc.getServerInfo());
            Subscription sub = nc.subscribe(SUBJECT);

            nc.publish(SUBJECT, null);
            nc.publish(SUBJECT, null);
            nc.publish(SUBJECT, null);
            nc.publish(SUBJECT, null);
//            nc.publish(SUBJECT, null);

            Debug.info("1. about to UNSUB 5");
            sub.unsubscribe(5);
            Thread.sleep(60_000);

            Debug.info("2. about to UNSUB");
            sub.unsubscribe();
//            Debug.info("3. about to UNSUB");
//            sub.unsubscribe();

//            Dispatcher d = nc.createDispatcher();
//            d.subscribe("bar");
//            d.unsubscribe("bar", 5);
//            d.unsubscribe("bar");
//            d.unsubscribe("bar");
            Thread.sleep(1000000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
