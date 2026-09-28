package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.PullOrderedConsumerCreator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A consume reads the whole stream whatever its batch size. Batch size 1 is the edge: the re-pull
 * threshold is the batch size less the re-pull size, and with a batch of 1 that arithmetic used to
 * land on 0, which the pending count can never go below, so the consumer pulled once and stopped.
 */
public class ConsumeBatchSizeTests extends JetStreamTestBase {

    private static final int COUNT = 5;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 10})
    public void testConsumeReadsEveryMessageAtAnyBatchSize(int batchSize) throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 1, COUNT);

            AtomicInteger received = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(COUNT);
            MessageHandler handler = m -> {
                received.incrementAndGet();
                latch.countDown();
            };

            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            OrderedConsumerContext occ = streamContext.createOrderedConsumer(
                new PullOrderedConsumerCreator().filterSubjects(ctx.subject()));

            try (MessageConsumer mc = occ.consume(ConsumeOptions.builder().batchSize(batchSize).build(), handler)) {
                //noinspection ResultOfMethodCallIgnored
                latch.await(5000, TimeUnit.MILLISECONDS);
            }

            assertEquals(COUNT, received.get(), "batch size " + batchSize);
        });
    }
}
