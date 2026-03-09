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

public class ScratchDocsForCode {
    public static void main(String[] args) {

        try
        {
            Connection c = Nats.connectReconnectOnConnect();
            System.out.println("Initial 2: " + c.getStatus());
            while (c.getStatus() != Connection.Status.CONNECTED)
            {
                Thread.sleep(2000);
                System.out.println("State: " + c.getStatus());
            }
            c.close();
        }
        catch (Exception e)
        {
            System.out.println("Exception " + e);
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
