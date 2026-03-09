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
import io.synadia.client.api.ServerInfo;
import io.synadia.client.support.Debug;
import io.synadia.client.support.DebugListener;

public class ZLeakForceReconnect {

    public static final String STREAM = "stream";
    public static final String SUBJECT = "subject";
    public static final String CONSUMER = "consumer";

    public static void main(String[] args) {
        try {
            DebugListener d = new DebugListener();
            Options options = Options.builder()
//                .server("tls://connect.ngs.global")
//                .authHandler(Nats.credentials("C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\Go big or go home\\default.creds"))
                .connectionListener(d)
                .errorListener(d)
//                .executor(executor)
                .build();
            Connection nc = Nats.connect(options);
            while (true) {
                try {
                    ServerInfo si = nc.getServerInfo();
                    Debug.info("CONNECTED TO", si.getServerName() + " " + si.getClientId());
//                nc.forceReconnect(ForceReconnectOptions.FORCE_CLOSE_INSTANCE);
                    Thread.sleep(2500);
                }
                catch (Exception e) {}
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
