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
import io.synadia.client.JetStreamManagement;
import io.synadia.client.Nats;
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StreamConfiguration;

public class ZDeleteMessage {

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            jsm.addStream(StreamConfiguration.builder().name("foo").subjects("bar").build());
            PublishAck pa1 = jsm.jetStream().publish("bar", "z1".getBytes());
            PublishAck pa2 = jsm.jetStream().publish("bar", "z2".getBytes());
            PublishAck pa3 = jsm.jetStream().publish("bar", "z3".getBytes());
            PublishAck pa4 = jsm.jetStream().publish("bar", "z4".getBytes());
            PublishAck pa5 = jsm.jetStream().publish("bar", "z5".getBytes());

            jsm.deleteMessage("foo", pa5.getSeqno());
            jsm.deleteMessage("foo", pa4.getSeqno());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
