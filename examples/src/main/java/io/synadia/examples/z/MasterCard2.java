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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

public class MasterCard2 {
    static String stream = "mc-stream";
    static String subject = "mc-subject";
    static int pubCount = 10;
    static int subCount = 20;

    public static void main(String[] args) throws IOException {
        Options options = new Options.Builder().server("nats://localhost:4222").build();

        try (Connection nc = Nats.connect(options)) {
            setupStreamAndData(nc);

            JetStream js = nc.jetStream();
            JetStreamSubscription sub = js.subscribe(subject, PullSubscribeOptions.builder().build());

//            PullRequestOptions pro = PullRequestOptions.builder(subCount).idleHeartbeat(1000).expiresIn(10000).build();
            PullRequestOptions pro = PullRequestOptions.builder(subCount).noWait().build();
            System.out.println(pro.toJson());
            sub.pull(pro);

            int red = 0;
            boolean flag = true;
            while (red < subCount) {
                System.out.println("about to next");
                Message m = sub.nextMessage(1000);
                if (m == null) {
                    if (flag) {
                        System.out.println("pull2");
                        sub.pull(pro);
                        flag = false;
                    }
                    else {
                        break;
                    }
                }
                else {
                    red++;
                    System.out.println(red + ". " + m);
                    m.ack();
                }
            }
        }
        catch (Exception e) {
//            e.printStackTrace();
            System.out.println("!! " + e.getMessage());
        }
    }

    public static void setupStreamAndData(Connection nc) throws IOException, JetStreamApiException {
        try {
            StreamConfiguration sc = StreamConfiguration.builder()
                .name(stream)
                .storageType(StorageType.Memory)
                .subjects(subject)
                .build();
            nc.jetStreamManagement().addStream(sc);
            nc.jetStreamManagement().purgeStream(stream);
            System.out.println("Created stream: '" + stream + "'");

            JetStream js = nc.jetStream();
            System.out.print("Publishing...");
            for (int x = 0; x < pubCount; x++) {
                if ((x+1) % 3000 == 0) {
                    System.out.print(".");
                }
                js.publish(subject, ("mc-data-" + x).getBytes());
            }
            System.out.println("complete.");
        }
        catch (Exception e) {
            System.out.println("Failed creating stream: '" + stream + "' " + e);
        }
    }
}
