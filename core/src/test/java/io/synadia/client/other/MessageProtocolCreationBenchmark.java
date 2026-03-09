package io.synadia.client.other;

/*
import io.synadia.client.impl.NatsMessage;
import io.synadia.client.impl.ProtocolMessage;

import java.text.NumberFormat;

import static io.synadia.client.support.NatsConstants.EMPTY_BODY;
*/

// This class is kept only for history. It can only run if it is moved to the
// io.nats.client.impl package since ProtocolMessage is package scoped
public class MessageProtocolCreationBenchmark {
/*
    public static void main(String[] args) throws InterruptedException {
        int warmup = 1_000_000;
        int msgCount = 50_000_000;

        System.out.printf("### Running benchmarks with %s messages.\n", NumberFormat.getInstance().format(msgCount));

        for (int j = 0; j < warmup; j++) {
            new NatsMessage("subject", "replyTo", EMPTY_BODY);
        }

        long start = System.nanoTime();
        for (int j = 0; j < msgCount; j++) {
            new NatsMessage("subject", "replyTo", EMPTY_BODY);
        }
        long end = System.nanoTime();

        System.out.printf("\n### Total time to create %s non-utf8 messages for sending was %s ms\n\t%f ns/op\n\t%s op/sec\n",
                NumberFormat.getInstance().format(msgCount),
                NumberFormat.getInstance().format((end - start) / 1_000_000L),
                ((double) (end - start)) / ((double) (msgCount)),
                NumberFormat.getInstance().format(((double)(1_000_000_000L * msgCount))/((double) (end - start))));

        start = System.nanoTime();
        for (int j = 0; j < msgCount; j++) {
            new NatsMessage("subject", "replyTo", EMPTY_BODY);
        }
        end = System.nanoTime();

        System.out.printf("\n### Total time to create %s utf8 messages for sending was %s ms\n\t%f ns/op\n\t%s op/sec\n",
                NumberFormat.getInstance().format(msgCount),
                NumberFormat.getInstance().format((end - start) / 1_000_000L),
                ((double) (end - start)) / ((double) (msgCount)),
                NumberFormat.getInstance().format(((double)(1_000_000_000L * msgCount))/((double) (end - start))));
        
        start = System.nanoTime();
        for (int j = 0; j < msgCount; j++) {
            new ProtocolMessage(EMPTY_BODY, true);
        }
        end = System.nanoTime();

        System.out.printf("\n### Total time to create %s a protocol message was %s ms\n\t%f ns/op\n\t%s op/sec\n",
                NumberFormat.getInstance().format(msgCount),
                NumberFormat.getInstance().format((end - start) / 1_000_000L),
                ((double) (end - start)) / ((double) (msgCount)),
                NumberFormat.getInstance().format(((double)(1_000_000_000L * msgCount))/((double) (end - start))));
    }
*/
}
