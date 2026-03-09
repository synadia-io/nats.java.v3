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

import io.synadia.client.AuthHandler;
import io.synadia.client.Connection;
import io.synadia.client.Nats;
import io.synadia.client.Options;

import java.io.IOException;

public class ZAuthReConnect {

    public static void main(String[] args) throws IOException {
        AuthHandler ah = Nats.credentials("C:\\nats\\temp\\nats-client-test\\NatsClientTest\\nsc\\nkeys\\creds\\tank\\SYS\\sys.creds");

        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .authHandler(ah)
//            .errorListener(new ErrorListenerLoggerImpl())
            .connectionListener((conn, type) -> System.out.println(type))
            .build();

        try (Connection nc = Nats.connect(options)) {
            while (true) {
                Thread.sleep(1000);
                nc.forceReconnect();
            }
//            Thread.sleep(100000000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
