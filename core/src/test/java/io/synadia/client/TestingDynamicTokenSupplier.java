package io.synadia.client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class TestingDynamicTokenSupplier implements Supplier<char[]> {
    AtomicInteger counter = new AtomicInteger(0);

    @Override
    public char[] get() {
        return ("dynamic-token-" + counter.incrementAndGet()).toCharArray();
    }
}
