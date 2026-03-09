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
import io.synadia.client.impl.ErrorListenerConsoleImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ZNgs0 {
    // C:\Users\batman\.local\share\nats\nsc\keys\creds\synadia\remote_control\default.creds

    public static void main(String[] args) throws IOException {
        String pathToCreds = "C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\remote_control\\default.creds";
        byte[] credBytes = Files.readAllBytes(Paths.get(pathToCreds));

        Options options = new Options.Builder()
            .server("tls://connect.ngs.global")
            .connectionListener((b, e) -> System.out.println(e + " " + b))
            .errorListener(new ErrorListenerConsoleImpl())
//            .authHandler(Nats.credentials(pathToCreds))
            .authHandler(Nats.staticCredentials(credBytes))
            .build();

        try (Connection nc = Nats.connect(options)) {
            int tries = 10;
            while (tries-- > 0) {
                System.out.println("\n" + nc.getServerInfo());
                Thread.sleep(2000);
                nc.forceReconnect();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
