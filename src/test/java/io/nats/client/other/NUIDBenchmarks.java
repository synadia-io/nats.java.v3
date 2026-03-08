package io.nats.client.other;

import io.nats.client.NUID;

import java.text.NumberFormat;


public class NUIDBenchmarks {

    public static void main(String args[]) {
        benchmarkGlobalNUIDSpeed();
        System.out.println();
        benchmarkNUIDSpeed();
    }

    public static void benchmarkNUIDSpeed() {
        long count = 10_000_000;
        NUID nuid = new NUID();

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            nuid.next();
        }
        long elapsedNsec = System.nanoTime() - start;
        System.out.printf("Average generation time for %s NUIDs was %f ns\n",
                NumberFormat.getNumberInstance().format(count), (double) elapsedNsec / count);

    }

    public static void benchmarkGlobalNUIDSpeed() {
        long count = 10_000_000;

        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            NUID.nextGlobal();
        }
        long elapsedNsec = System.nanoTime() - start;
        System.out.printf("Average generation time for %s global NUIDs was %f ns\n",
                NumberFormat.getNumberInstance().format(count), (double) elapsedNsec / count);
    }
}
