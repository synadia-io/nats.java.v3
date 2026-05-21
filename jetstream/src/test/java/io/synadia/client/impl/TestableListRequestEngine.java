package io.synadia.client.impl;

import io.synadia.client.Message;

public class TestableListRequestEngine extends ListRequestEngine {
    public int getTotal() {
        return total;
    }

    public int getLimit() {
        return limit;
    }

    public int getLastOffset() {
        return lastOffset;
    }

    public TestableListRequestEngine() {
    }

    public TestableListRequestEngine(Message msg) throws JetStreamApiException {
        super(msg);
    }

    @Override
    public boolean hasMore() {
        return super.hasMore();
    }

    @Override
    public byte[] internalNextJson() {
        return super.internalNextJson();
    }

    @Override
    public byte[] internalNextJson(String fieldName, String filter) {
        return super.internalNextJson(fieldName, filter);
    }

    @Override
    public int nextOffset() {
        return super.nextOffset();
    }
}
