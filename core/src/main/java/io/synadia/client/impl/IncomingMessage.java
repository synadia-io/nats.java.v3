package io.synadia.client.impl;

import io.synadia.client.utils.ByteArrayBuilder;

/**
 * A message read off the wire. Unlike a message built for publishing, it never has to produce a
 * protocol line, so the protocol accessors are unsupported and no size calculation is done.
 */
public class IncomingMessage extends NatsMessage {
    protected IncomingMessage(byte[] data) {
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
