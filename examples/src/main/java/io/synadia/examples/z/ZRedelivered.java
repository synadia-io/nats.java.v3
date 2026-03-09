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
import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.ReplayPolicy;
import io.synadia.examples.jetstream.NatsJsUtils;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;

public class ZRedelivered {
    public static void main(String[] args) {
        String streamName = "sname";
        String consumerName = "cname";
        String subject = "sub";
        int subCount = 16;

        Options options = Options.builder().errorListener(new ErrorListener() {}).build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();
            NatsJsUtils.createOrReplaceStream(jsm, streamName, subject);

            js.publish(subject, "message1".getBytes());
            js.publish(subject, "message22".getBytes());
            js.publish(subject, "message333".getBytes());

            ConsumerConfiguration consumer1Configuration = ConsumerConfiguration.builder()
                .durable(consumerName)
                .deliverPolicy(DeliverPolicy.All)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(Duration.ofSeconds(15))
                .replayPolicy(ReplayPolicy.Instant)
                .maxDeliver(-1)
                .headersOnly(false)
                .build();

            jsm.addOrUpdateConsumer(streamName, consumer1Configuration);

            PullSubscribeOptions pullOpts = PullSubscribeOptions.bind(streamName, consumerName);
            Subscriber[] subs = new Subscriber[subCount];
            Thread[] threads = new Thread[subCount];
            for (int x = 0; x < subCount; x++)
            {
                subs[x] = new FetchSubscriber(x, js.subscribe(null, pullOpts));
//                subs[x] = new PullSubscriber(x, js.subscribe(null, pullOpts));
                threads[x] = new Thread(subs[x]);
                threads[x].start();
            }

            threads[0].join(); // only need to wait for one since they are just set to run forever

        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    static abstract class Subscriber implements Runnable {
        int id;
        JetStreamSubscription sub;

        public Subscriber(int id, JetStreamSubscription sub) {
            this.id = id;
            this.sub = sub;
        }

        public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

        void print(Message msg)
        {
            String data = new String(msg.getData());
            long nd = msg.metaData().deliveredCount();
            long seq = msg.metaData().streamSequence();
            long pend = msg.metaData().pendingCount();

            System.out.println(
                "[" + id + "]  <" + FORMATTER.format(new Date()) + "> " + data
                    + " Seq = " + seq
                    + ", Delivered = " + nd
                    + ", Pending = " + pend
            );
        }
    }

    static class FetchSubscriber extends Subscriber {
        public FetchSubscriber(int id, JetStreamSubscription sub) {
            super(id, sub);
        }

        @Override
        public void run() {
            //noinspection InfiniteLoopStatement
            while (true)
            {
                List<Message> messages = sub.fetch(1, 20);
                if (!messages.isEmpty())
                {
                    print(messages.get(0));
                }
            }
        }
    }

    static class PullSubscriber extends Subscriber {
        public PullSubscriber(int id, JetStreamSubscription sub) {
            super(id, sub);
        }

        @Override
        public void run() {
            //noinspection InfiniteLoopStatement
            while (true)
            {
                sub.pull(PullRequestOptions.builder(1).expiresIn(20).build());
                try {
                    Message m = sub.nextMessage(30);
                    if (m != null) {
                        print(m);
                    }
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
