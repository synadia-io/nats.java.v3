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
import io.synadia.client.Nats;
import io.synadia.client.Options;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ZLoadCommon {
    public static final String STREAM = "strm";
    public static final String SUBJECT = "sub";

    public static void cleanConsumers() {
        try (Connection nc = Nats.connect(getOptions(4222))) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            List<String> cons = jsm.getConsumerNames(STREAM);
            if (!cons.isEmpty()) {
                for (String con : cons) {
                    try {
                        jsm.deleteConsumer(STREAM, con);
                    }
                    catch (Exception ignore) {}
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static Options getOptions(int port) {
        return new Options.Builder()
            .server("nats://localhost:" + port)
            .build();
    }

    private static String format(AtomicLong count) {
        return String.format("%,d", count.get());
    }

    private static final AtomicInteger READER_ID = new AtomicInteger();
    private static String nextReaderId() {
        return Integer.toString(READER_ID.incrementAndGet());
    }

    private static final AtomicInteger CONSUMER_ID = new AtomicInteger();
    private static String nextConsumerId() {
        return Integer.toString(CONSUMER_ID.incrementAndGet());
    }
}
