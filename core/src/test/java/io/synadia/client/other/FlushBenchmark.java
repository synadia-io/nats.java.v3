package io.synadia.client.other;

import io.synadia.client.Nats;
import io.synadia.client.Options;
import io.synadia.client.impl.NatsConnection;

import java.text.NumberFormat;

public class FlushBenchmark {
    public static void main(String args[]) throws InterruptedException {
        int flushes = 100_000;

        System.out.println("###");
        System.out.printf("### Running benchmark with %s flushes.\n",
                                NumberFormat.getInstance().format(flushes));
        System.out.println("###");

        try {
            Options options = new Options.Builder().turnOnAdvancedStats().build();
            NatsConnection nc = Nats.connect(options);

            long start = System.nanoTime();
            for (int i=0; i<flushes; i++){
                nc.flush(null);
            }
            long end = System.nanoTime();

            nc.close();

            System.out.printf("### Total time to perform %s flushes was %s ms, %f ns/op\n",
                    NumberFormat.getInstance().format(flushes),
                    NumberFormat.getInstance().format((end - start) / 1_000_000L),
                    ((double) (end - start)) / ((double) (flushes)));
            System.out.printf("### This is equivalent to %s flushes/sec.\n",
                    NumberFormat.getInstance().format(1_000_000_000L * flushes / (end - start)));

            System.out.println("###");
            System.out.println("### Flush connection stats ####");
            System.out.println();
            System.out.print(nc.getStatistics().toString());
        } catch (Exception ex) {
            System.out.println("Exception running benchmark.");
            ex.printStackTrace();
        }
    }
}
