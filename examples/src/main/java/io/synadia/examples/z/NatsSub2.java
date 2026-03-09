// Copyright 2015-2018 The NATS Authors
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

import java.time.Duration;

public class NatsSub2 {

    static final String usageString =
            "\nUsage: java -cp <classpath> io.nats.examples.NatsSub [-s server] <subject> <msgCount>\n"
            + "\nUse tls:// or opentls:// to require tls, via the Default SSLContext\n"
            + "\nSet the environment variable NATS_NKEY to use challenge response authentication by setting a file containing your private key.\n"
            + "\nSet the environment variable NATS_CREDS to use JWT/NKey authentication by setting a file containing your user creds.\n"
            + "\nUse the URL for user/pass/token authentication.\n";

    public static void main(String[] args) {
        String[] servers = new String[]{"server1", "server2"};
        String subject = "eventbroker.showcase.corenats.benchmark";
        int messageCount = 5000;

        Options options = Options.builder()
            .servers(servers)
            .build();

        try (Connection nc = Nats.connect(options)) {
            System.out.println("Connected to server: " + nc.getServerInfo());

            Subscription sub = nc.subscribe(subject);
            nc.flush(Duration.ofSeconds(5));
            System.out.println("Subscription started to: " + sub.getSubject());

            for (int i = 1; i <= messageCount; i++) {
                System.out.printf("\nWaiting for message #[%d]...", (i+1));
                Message msg = sub.nextMessage(Duration.ofSeconds(10));
                if (msg == null) {
                    System.out.println("did not receive message in time, exiting.");
                    break;
                }
                else {
                    System.out.printf("received for subject: %s\n", msg.getSubject());
                }
            }
        }
        catch (Exception exp) {
            System.err.println(exp);
        }
    }
}