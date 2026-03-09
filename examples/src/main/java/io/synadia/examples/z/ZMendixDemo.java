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
import io.synadia.client.api.StreamInfo;
import io.synadia.client.api.StreamInfoOptions;
import io.synadia.client.api.Subject;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class ZMendixDemo {

    public static final String SYMBOL_SUBJECT = "symbol.ETHBTC";

    public static void mainy(String[] args) throws IOException {
        // 1. Connect to server
        // 2. Create a JetStream Management context
        // 3. Get the stream info which has a list of subjects
        // 4. Print the subjects.
        try (Connection nc = Nats.connect("nats://localhost:4222")) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            StreamInfo si = jsm.getStreamInfo("ticker", StreamInfoOptions.builder().allSubjects().build());
            List<Subject> subjects = si.getStreamState().getSubjects();
            for (Subject subject : subjects) {
                System.out.println(subject.getName());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        // 1. Connect to server
        // 2. Create a JetStream context
        // 3. Create a subscription to the "subject" for the symbol
        // 4. Get the messages with the subscription and print them out.
        try (Connection nc = Nats.connect("nats://localhost:4222")) {
            JetStream js = nc.jetStream();
            JetStreamSubscription sub = js.subscribe("symbol.ETHBTC",
                PullSubscribeOptions.builder().build());
            Iterator<Message> iter = sub.iterate(10000, 1000);
            int count = 0;
            while (iter.hasNext()) {
                Message m = iter.next();
                ++count;
                String data = new String(m.getData());
                System.out.println(data);
            }
            System.out.println("Retrieved " + count + " messages.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
