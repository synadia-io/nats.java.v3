package io.nats.client.impl;

import io.nats.client.support.ByteArrayBuilder;

public class IncomingMessage extends NatsMessage {
    IncomingMessage() {
        super((byte[])null);
    }

    IncomingMessage(byte[] data) {
        super(data);
    }

    @Override
    protected void calculate() {
        // intentionally does nothing
    }

    @Override
    ByteArrayBuilder getProtocolBab() {
        throw new IllegalStateException("getProtocolBab not supported for this type of message.");
    }

    @Override
    byte[] getProtocolBytes() {
        throw new IllegalStateException("getProtocolBytes not supported for this type of message.");
    }

    @Override
    int getControlLineLength() {
        throw new IllegalStateException("getControlLineLength not supported for this type of message.");
    }
}
