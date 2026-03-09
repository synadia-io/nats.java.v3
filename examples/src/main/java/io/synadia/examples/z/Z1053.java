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
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;

import static java.lang.String.format;

public class Z1053 {
    static int NUM_OF_PARTITIONS = 1;
    static int NUM_OF_TABLES = 1;
    static int MESSAGE_PER_TABLE = 1;

    public static void main(String[] args) {
        try (Connection nc = Nats.connect(Options.DEFAULT_URL)) {
            setupStreamAndPublishSomeMessages(nc);
            consume(nc);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupStreamAndPublishSomeMessages(Connection nc) throws IOException, JetStreamApiException {
        JetStreamManagement jsm = nc.jetStreamManagement();

        JetStream js = nc.jetStream();
        for (int i = 0; i < NUM_OF_PARTITIONS; i++) {
            String partition = "P" + i;
            String database = "db_bill";

            for (int t = 0; t < NUM_OF_TABLES; t++) {
                String table = "table_" + (char)('a' + t);
                String subject = format("Kame.%s.%s.%s", partition, database, table);
                try { jsm.deleteStream(partition); } catch (Exception ignore) {}
                jsm.addStream(StreamConfiguration.builder()
                    .name(partition)
                    .subjects(subject)
                    .build());
                for (int m = 0; m < MESSAGE_PER_TABLE; m++) {
                    String data = format("%s record %d", table, m);
                    js.publish(subject, data.getBytes());
                }
            }
        }
    }

    private static void consume(Connection connection) throws JetStreamApiException, IOException, InterruptedException {

        MessageHandler natsListener = m -> {
          System.out.println(m.getSubject() + " " + new String(m.getData()));
          m.ack();
        };

        for (int i = 0; i < NUM_OF_PARTITIONS; i++) {
            String partition = "P" + i;
            String database = "db_bill";
            String table = "*";

            String subject = format("Kame.%s.%s.%s", partition, database, table);
            String durable = "bill_durable";

            System.out.format("nats subject:%s, durable:%s, streamName:%s\n", subject, durable, partition);
            MessageConsumer mc = connection.getStreamContext(partition)
                .createOrUpdateConsumer(ConsumerConfiguration.builder()
                    .filterSubjects(subject).durable(durable)
                    .build())
                .consume(natsListener);


//            String shopDatabase = "db_bo_shop";
//            String shopSubject = format("Kame.%s.%s.%s", partition, shopDatabase, table);
//            String shopDurable = "bill_durable_shop";
//            System.out.println("nats subject: {}, durable:{}, streamName:{}", shopSubject, shopDurable, partition);
//            connection.getStreamContext(partition)
//                .createOrUpdateConsumer(ConsumerConfiguration.builder()
//                    .filterSubjects(shopSubject).durable(shopDurable)
//                    .build())
//                .consume(natsListener);
        }

        Thread.sleep(10000);
    }
}