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

import java.io.IOException;

public class Z1 {
    public static void main(String[] args) throws IOException, InterruptedException {
        String subject = "blah" + (char)0 + (char)127 + "γλώσσαληνική";

        try (Connection nc = Nats.connect(Options.DEFAULT_URL)) {
            System.out.println(nc.getServerInfo());
//            Dispatcher d = nc.createDispatcher(m -> {
//                System.out.println(new String(m.getData()));
//            });
//            d.subscribe(subject);
//
//            nc.publish(subject, "DATA".getBytes());
//
//            Thread.sleep(100);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
