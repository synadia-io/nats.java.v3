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
import io.synadia.client.KeyValueManagement;
import io.synadia.client.Nats;
import io.synadia.client.api.KeyValueConfiguration;
import io.synadia.client.api.StorageType;

import java.io.IOException;

public class Z1134 {

    public static void main(String[] args) throws IOException {
        try (Connection nc = Nats.connect()) {
            KeyValueManagement kvm = nc.keyValueManagement();

            KeyValueConfiguration kvc = KeyValueConfiguration.builder()
                .name("bucket")
                .description("description")
                .maxHistoryPerKey(5)
                .storageType(StorageType.File)
                .maximumValueSize(999)
                // 1073741824
                // 2147483647
                // 9999999999
                .build();

            kvm.create(kvc);

            System.out.println(nc.jetStreamManagement().getStreamInfo("KV_bucket").getConfig().getMaximumMessageSize());
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }
}