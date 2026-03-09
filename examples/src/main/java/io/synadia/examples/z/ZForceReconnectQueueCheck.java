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
import io.synadia.client.support.DebugListener;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("CallToPrintStackTrace")
public class ZForceReconnectQueueCheck {
    public static void main(String[] args) throws IOException, InterruptedException {
        int pubCount = 100000;
        int subscribeTime = 5000;
        int port = 4222;
        String subject = "SBJ";

        ZForceReconnectQueueCheckDataPort.writeCheck = "PUB " + subject;
        ZForceReconnectQueueCheckDataPort.delay = 50;

        _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, false, 0);
        _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, false, 1000);
        _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, true, 0);
        _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, true, 1000);
    }

    private static void _testForceReconnectQueueCheck(String subject, int pubCount, int subscribeTime, int port, boolean forceClose, int flushWait) throws InterruptedException {
        ReconnectQueueCheckSubscriber subscriber = new ReconnectQueueCheckSubscriber(pubCount, subject);
        Thread tsub = new Thread(subscriber);
        tsub.start();

        Options options = reconnectQueueChecketOptions(port, ZForceReconnectQueueCheckDataPort.class.getCanonicalName());
        try (Connection nc = Nats.connect(options)) {
            for (int x = 1; x <= pubCount; x++) {
                nc.publish(subject, (x + "").getBytes());
            }

            ForceReconnectOptions.Builder b = ForceReconnectOptions.builder();
            if (flushWait > 0) {
                b.flush(flushWait);
            }
            if (forceClose) {
                b.forceClose();
            }
            nc.forceReconnect(b.build());

            long maxTime = subscribeTime;
            while (!subscriber.subscriberDone.get() && maxTime > 0) {
                //noinspection BusyWait
                Thread.sleep(100);
                maxTime -= 100;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        subscriber.subscriberDone.set(false);
        tsub.join();

        System.out.println("Settings: forceClose=" + forceClose + ", flushWait=" + flushWait);
        System.out.println("Subscriber: completed=" + subscriber.completed + ", lastNotSkipped=" + subscriber.lastNotSkipped + ", firstAfterSkip=" + subscriber.firstAfterSkip);
    }

    static class ReconnectQueueCheckSubscriber implements Runnable {
        final AtomicBoolean subscriberDone;
        final int pubCount;
        final String subject;
        boolean completed;
        int lastNotSkipped;
        int firstAfterSkip;

        public ReconnectQueueCheckSubscriber(int pubCount, String subject) {
            this.subscriberDone = new AtomicBoolean(false);
            this.pubCount = pubCount;
            this.subject = subject;
            lastNotSkipped = 0;
            firstAfterSkip = -1;
            completed = false;
        }

        @Override
        public void run() {
            Options options = reconnectQueueChecketOptions(4222);
            try (Connection nc = Nats.connect(options)) {
                Subscription sub = nc.subscribe(subject);
                while (!subscriberDone.get()) {
                    Message m = sub.nextMessage(1000);
                    if (m != null) {
                        String next = "" + (lastNotSkipped + 1);
                        String md = new String(m.getData());
                        if (md.equals(next)) {
                            if (++lastNotSkipped >= pubCount) {
                                completed = true;
                                subscriberDone.set(true);
                            }
                        }
                        else {
                            firstAfterSkip = Integer.parseInt(md);
                            subscriberDone.set(true);
                        }
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static Options reconnectQueueChecketOptions(int port) {
        return reconnectQueueChecketOptions(port, null);
    }

    static Options reconnectQueueChecketOptions(int port, String dataPortClassName) {
        DebugListener listener = new DebugListener();
        return Options.builder()
            .server("nats://localhost:" + port)
//            .errorListener(listener)
//            .connectionListener(listener)
            .dataPortType(dataPortClassName)
            .build();
    }
}
