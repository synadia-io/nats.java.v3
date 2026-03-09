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
import io.synadia.client.api.KeyValueStatus;
import io.synadia.client.support.JsonUtils;

import java.time.Duration;

public class ZKvTtl {

    public static void main(String[] args) throws InterruptedException {
        try (Connection nc = Nats.connect()) {
            KeyValueManagement keyValueManagement = nc.keyValueManagement();

            String bucketName = "bucket";

            // make sure the bucket does not exist before start
            try { keyValueManagement.delete(bucketName); } catch (Exception ignore) {}

            // create the new config with just the name and a 10 second ttl
            KeyValueConfiguration config = KeyValueConfiguration.builder()
                .name(bucketName)
//                .replicas(3)
//                .ttl(Duration.ofSeconds(10))
                .build();

            System.out.println("Stream Configuration used to create the bucket...");
            System.out.println(JsonUtils.getFormatted(config.getBackingConfig().toJson()));

            // execute the create call
            KeyValueStatus keyValueStatus = keyValueManagement.create(config);
            System.out.println("\nBacking Stream Info after create...");
            System.out.println(JsonUtils.getFormatted(keyValueStatus.getBackingStreamInfo().getConfiguration().toJson()));

            // check the cli. This code waits until you press enter at the console.
//            Scanner scanner = new Scanner(System.in);
//            System.out.print("Check the stream in the cli via 'nats s info KV_bucket'.\nThe press enter when ready. $");
//            scanner.nextLine();

            // create a new config based on the existing config as returned during the create call
            // the builder works in order that methods are called, in this case and in the issue
            // .ttl() is called after applying the existing configuration
            KeyValueConfiguration config2 = KeyValueConfiguration.builder(keyValueStatus.getConfiguration())
                .ttl(Duration.ofSeconds(20))
                .build();
            System.out.println("\nStream Configuration used to update the bucket...");
            System.out.println(JsonUtils.getFormatted(config2.getBackingConfig().toJson()));

            // execute the update call
            KeyValueStatus keyValueStatus2 = keyValueManagement.update(config2);
            System.out.println("\nBacking Stream Info after update...");
            System.out.println(JsonUtils.getFormatted(keyValueStatus2.getBackingStreamInfo().getConfiguration().toJson()));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
