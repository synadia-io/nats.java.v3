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
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ZFetchTests {
    private static final String URL = "nats://localhost:4222";
    private static final String STREAM_NAME = "TestStream";
    private static final String SUBJECT = "test";
    private static final String DURABLE = "testDurable";
    private static final long DELAY = 10; // seconds
    private static final int PUB_COUNT = 200;

    public static void main(String[] args) {
        try (Connection conn = Nats.connect(URL)) {
            JetStreamManagement jsm = conn.jetStreamManagement();;
            JetStream js = conn.jetStream();

            Dispatcher d = conn.createDispatcher();

            // make sure the stream is gone before starting. delete exceptions when stream does not exist
            log("Testing with JetStream publishes...");
            resetStream(jsm);
            JetStreamSubscription watch = watch(js, d, 1);
            execute(jsm, js, ZFetchTests::jsPublishAfterDelay);
            d.unsubscribe(watch);

            log("Testing with core publishes...");
            resetStream(jsm);
            watch = watch(js, d, 2);
            execute(jsm, js, ZFetchTests::corePublishAfterDelay);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JetStreamSubscription watch(JetStream js, Dispatcher d, int id) throws IOException, JetStreamApiException {
        AtomicInteger watchCounter = new AtomicInteger();
        return js.subscribe(SUBJECT, d, msg -> {
            if (watchCounter.incrementAndGet() % 50 == 0) {
                log("Watch " + id + ": " + msg.toString());
            }
        }, true);
    }

    private static void execute(JetStreamManagement jsm, JetStream js, Runnable runnable) throws IOException, JetStreamApiException {
        // Create the consumer
        ConsumerConfiguration cc =
            ConsumerConfiguration.builder()
                .durable(DURABLE)
                .ackWait(300)
                .maxAckPending(2000)
                .build();
        jsm.addOrUpdateConsumer(STREAM_NAME, cc);

        // make the pull subscription
        PullSubscribeOptions so = PullSubscribeOptions.builder().durable(DURABLE).build();
        JetStreamSubscription sub = js.subscribe(SUBJECT, so);

        // start the publisher
        Thread publisher = new Thread(runnable);
        publisher.start();

        // Start fetch before messages are published
        log("Before-1: Starting Fetch...");
        List<Message> messages = sub.fetch(10, Duration.ofSeconds(DELAY * 3));
        for (Message m : messages) {
            log("BF Msg: " + m.toString());
            m.ack();
        }
        log("Before-2: Fetched message: " + messages.size());

        // Start fetch after messages are published
        log("After-1: Starting Fetch...");
        List<Message> messages2 = sub.fetch(PUB_COUNT, Duration.ofSeconds(DELAY * 30));
        for (Message m : messages) {
            log("AFT Msg: " + m.toString());
            m.ack();
        }
        log("After-2: Fetched messages second try: " + messages2.size());
    }

    // This publisher uses JetStream to publish messages to a subject
    private static void jsPublishAfterDelay() {
        try (Connection conn = Nats.connect(URL)) {
            JetStream js = conn.jetStream();
            // Wait DELAY seconds so we publish in the middle of the first fetch
            log("Sleep before publish...");
            Thread.sleep(DELAY * 1000);
            log("About to publish...");
            for (int i = 0; i < PUB_COUNT; i++) {
                String str = "Test" + i;
                js.publish(SUBJECT, str.getBytes());
            }
            log("Done publishing");
        } catch (Exception e) {
            log("Failed loading messages into nats! " + e);
        }
    }

    // This publisher uses core publish to publish messages to a subject
    // only use core if you don't care about PublishAcks
    private static void corePublishAfterDelay() {
        try (Connection conn = Nats.connect(URL)) {
            // Wait DELAY seconds so we publish in the middle of the first fetch
            log("Sleep before publish...");
            Thread.sleep(DELAY);
            log("About to publish...");
            for (int i = 0; i < PUB_COUNT; i++) {
                String str = "Test" + i;
                conn.publish(SUBJECT, str.getBytes());
            }
            log("Done publishing");
        } catch (Exception e) {
            log("Failed loading messages into nats! " + e);
        }
    }

    private static void resetStream(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        try { jsm.deleteStream(STREAM_NAME); } catch (Exception ignore) {}

        // Create the stream
        StreamConfiguration streamConfig =
            StreamConfiguration.builder()
                .name(STREAM_NAME)
                .storageType(StorageType.File)
                .subjects(SUBJECT)
                .maxAge(Duration.ofDays(3))
                // .noAck(false) // default is already false
                .build();
        jsm.addStream(streamConfig);
    }

    private static void log(String s) {
        System.out.println("" + System.currentTimeMillis() + " [" + Thread.currentThread().getName() + "] " + s);
    }
}