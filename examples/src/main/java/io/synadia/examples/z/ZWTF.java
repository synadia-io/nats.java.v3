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
import io.synadia.client.JetStreamManagement;
import io.synadia.client.NUID;
import io.synadia.client.Nats;
import io.synadia.client.api.*;

import java.util.List;

import static io.synadia.client.support.JsonUtils.printFormatted;

public class ZWTF {

    public static final String STREAM = "OrchestrationStream_08fa7d3dcd644745bace255d2c76fbc2";
    public static final String CON = "NatsMessagesExporter_TestExporter";

    public static void main(String[] args) {
        try (Connection nc = Nats.connectReconnectOnConnect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
//            printFormatted(jsm.getStreamInfo(STREAM));
//            printFormatted(jsm.getConsumerInfo(STREAM, CON));

//            printFormatted(jsm.getStreamInfo(STREAM).getStreamState());
            ConsumerInfo ci = jsm.createConsumer(STREAM, ConsumerConfiguration.builder()
                .name(NUID.nextGlobalSequence())
                .filterSubject(">")
                .build());
            printFormatted(ci);
            printFormatted(jsm.getStreamInfo(STREAM, StreamInfoOptions.builder().allSubjects().build()));
            StreamInfo si = jsm.getStreamInfo(STREAM, StreamInfoOptions.builder().allSubjects().build());
            List<Subject> subjects = si.getStreamState().getSubjects();
            long count = 0;
            for (Subject s : subjects) {
                System.out.println(s);
                count += s.getCount();
            }
            System.out.println(count);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
