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
import io.synadia.client.api.RetentionPolicy;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import io.synadia.client.impl.ErrorListenerLoggerImpl;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class ZIssue602 {
    public static void main(String[] args) throws Exception {
        Options connectionOptions = new Options.Builder()
            .server("nats://localhost:4222")
            .errorListener(new ErrorListenerLoggerImpl())
            .noNoResponders()
            .build();
        Connection connection = Nats.connect(connectionOptions);
        createStreamIfNotExists(connection, "test");
        System.out.println("Server version: " + connection.getServerInfo().getVersion());

        ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder().build();
        PullSubscribeOptions subscriptionOptions = PullSubscribeOptions.builder()
            .configuration(consumerConfiguration)
            .durable("test")
            .stream("test")
            .build();
        JetStreamSubscription subscription = connection.jetStream().subscribe("test", subscriptionOptions);

        CountDownLatch latch = new CountDownLatch(1);
        subscription.pull(10);
        Thread t = new Thread(() -> {
            try {
                System.out.println(System.currentTimeMillis() + " M1 ");
                Message m = subscription.nextMessage(Duration.ofSeconds(10));
                System.out.println(System.currentTimeMillis() + " M2 " + m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.start();

//        latch.await();
        // give worker thread a chance to enter nextMessage() and block.
        Thread.sleep(500);
        System.out.println(System.currentTimeMillis() + " Z1 " + subscription.hashCode());
        subscription.unsubscribe();
        Thread.sleep(500);

        System.out.println(System.currentTimeMillis() + " Z2");
        subscription.drain(Duration.ofSeconds(1));
        Thread.sleep(500);

        System.out.println(System.currentTimeMillis() + " Z3");
        connection.drain(Duration.ofSeconds(1)).get();
        t.join();
    }


    private static void createStreamIfNotExists(Connection connection, String stream) throws Exception {
        JetStreamManagement jetStreamManagement = connection.jetStreamManagement();

        if (!streamExists(jetStreamManagement, stream)) {
            createNewStream(jetStreamManagement, stream);
        }
    }

    private static boolean streamExists(JetStreamManagement jetStreamManagement, String streamName) throws Exception {
        try {
            return jetStreamManagement.getStreamInfo(streamName) != null;
        } catch (JetStreamApiException ex) {
            if (ex.getErrorCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private static void createNewStream(JetStreamManagement jetStreamManagement, String stream) throws Exception {
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
            .name(stream)
            .storageType(StorageType.File)
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .build();
        jetStreamManagement.addStream(streamConfiguration);
    }
}
