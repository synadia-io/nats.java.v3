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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DispatcherExample {


    public static final String SUBJECT = "example-subject";

    static class ExampleConnectionListener implements ConnectionListener {
        public CountDownLatch reconnectLatch = new CountDownLatch(1);

        @Override
        public void connectionEvent(Connection conn, Events type) {}

        @Override
        public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
            System.out.println("Connection Callback: " + type.getEvent());
            if (type == Events.RECONNECTED) {
                reconnectLatch.countDown();
            }
        }
    }

    static class ExampleReaderListener implements ReadListener {
        @Override
        public void protocol(String op, String text) {
            if (op.equals("INFO")) {
                System.out.println("Reader Callback: INFO Received From Server");
            }
        }

        @Override
        public void message(String op, Message message) {
            String text = message.getSubject() + " / " + new String(message.getData());
            System.out.println("Reader Callback: Message Received From Server: " + text);
        }
    }

    public static void main(String[] args) {
        CountDownLatch messageLatch1 = new CountDownLatch(3);
        ExampleConnectionListener cl = new ExampleConnectionListener();
        Options options = Options.builder()
            .server("nats://localhost:4222")
            .connectionListener(cl)
            .readListener(new ExampleReaderListener())
            .errorListener(new ErrorListener(){})
            .build();

        try (Connection nc = Nats.connect(options)) {
            System.out.println("Starting Handler Test");
            Dispatcher d = nc.createDispatcher(m -> {
                String data = new String(m.getData());
                System.out.println("Handler Given Message: " + data);
                if (cl.reconnectLatch.getCount() == 1) {
                    cl.reconnectLatch.await(10, TimeUnit.SECONDS);
                    System.out.println("Publish Message 3");
                    nc.publish(SUBJECT, "m3".getBytes());
                }
                messageLatch1.countDown();
                System.out.println("Handler Exit: " + data);
            });
            d.subscribe(SUBJECT);

            System.out.println("Publish Messages 1 & 2");
            nc.publish(SUBJECT, "m1".getBytes());
            nc.publish(SUBJECT, "m2".getBytes());
            messageLatch1.await(30, TimeUnit.SECONDS);
            d.unsubscribe(SUBJECT);

            // reset
            cl.reconnectLatch = new CountDownLatch(1);
            CountDownLatch messageLatch2 = new CountDownLatch(3);

            System.out.println("Starting Polling Test");
            Subscription sub = nc.subscribe(SUBJECT);

            System.out.println("Publish Messages 1 & 2");
            nc.publish(SUBJECT, "m1".getBytes());
            nc.publish(SUBJECT, "m2".getBytes());

            Message m = sub.nextMessage(1000);
            String data = new String(m.getData());
            System.out.println("Polled Message: " + data);
            cl.reconnectLatch.await(10, TimeUnit.SECONDS);

            System.out.println("Publish Message 3");
            nc.publish(SUBJECT, "m3".getBytes());

            m = sub.nextMessage(5000);
            data = new String(m.getData());
            System.out.println("Polled Message: " + data);

            m = sub.nextMessage(5000);
            data = new String(m.getData());
            System.out.println("Polled Message: " + data);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
