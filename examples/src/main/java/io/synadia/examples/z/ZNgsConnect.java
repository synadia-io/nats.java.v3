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
import io.synadia.client.impl.ErrorListenerLoggerImpl;
import io.synadia.client.impl.MemoryAuthHandler;
import io.synadia.client.impl.NatsImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.synadia.client.support.JsonUtils.printFormatted;

public class ZNgsConnect {

    public static void main(String[] args) throws IOException {
        String credsFile = "C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\remote_control\\default.creds";
        byte[] data = Files.readAllBytes(Paths.get(credsFile));

        AuthHandler ah = new MemoryAuthHandler(data);
        ah = NatsImpl.credentials(credsFile);
        Options options = new Options.Builder()
            .server("nats://connect.ngs.global")
            .credentialPath(credsFile)
//            .authHandler(ah)
//            .authHandler(Nats.credentials("C:\\Users\\batman\\.local\\share\\nats\\nsc\\keys\\creds\\synadia\\arondight_1\\scott.creds"))
            .errorListener(new ErrorListenerLoggerImpl())
            .connectionListener((conn, type) -> System.out.println(type))
            .build();

        try (Connection nc = Nats.connect(options)) {
            printFormatted(nc.getServerInfo());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
