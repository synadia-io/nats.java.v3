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
import io.synadia.client.support.DebugListener;

import java.io.IOException;

public class ZFaber0 {
    // C:\Users\batman\.local\share\nats\nsc\keys\creds\synadia\remote_control\default.creds

    public static void main(String[] args) throws IOException {
        connect("54.172.150.40");
        connect("54.147.0.217");
        connect("44.212.16.171");
    }

    private static void connect(String port) {
        DebugListener l = new DebugListener();
        Options options = new Options.Builder()
            .server("nats://" + port + ":4222")
            .connectionListener(l)
            .errorListener(l)
            .build();


        try (Connection nc = Nats.connect(options)) {
            System.out.println("\n" + nc.getServerInfo());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
