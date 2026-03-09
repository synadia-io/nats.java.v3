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
import io.synadia.client.support.Debug;
import io.synadia.client.support.DebugConnectionListener;
import io.synadia.client.support.DebugErrorListener;

public class Z0 {
    public static void main(String[] args) {
        Options options = Options.builder()
            .server("nats://192.168.50.122:4222")
            .connectionListener(new DebugConnectionListener())
            .errorListener(new DebugErrorListener(false))
            .maxReconnects(0)
//            .token("tknx")
            .build();

        try (Connection nc = Nats.connect(options)) {
            Debug.info("CONNECTED");
        }
        catch (Exception e) {
            Debug.info("ZEX", e);
//            Debug.stackTrace("ZEX", e);
        }
    }
}
