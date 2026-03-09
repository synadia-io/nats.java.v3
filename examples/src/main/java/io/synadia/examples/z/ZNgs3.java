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

import io.synadia.client.*;

import java.io.IOException;

import static io.synadia.client.support.JsonUtils.printFormatted;

public class ZNgs3 {

    public static void main(String[] args) throws IOException {
        AuthHandler ah = Nats.credentials("<location-of-creds>\\default.creds");
        Options options = new Options.Builder()
            .server("tls://us-central1.gcp.cloud.ngs.global")
            .authHandler(ah)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            printFormatted(jsm.getAccountStatistics());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
