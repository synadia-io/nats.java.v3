package io.synadia.client.impl;

import io.synadia.client.utils.ByteArrayBuilder;

// ----------------------------------------------------------------------------------------------------
// Protocol message is a special version of a NatsPublishableMessage extends NatsMessage
// ----------------------------------------------------------------------------------------------------
public class ProtocolMessage extends NatsPublishableMessage {
    final boolean filterOnStop;

    ProtocolMessage(ByteArrayBuilder babProtocol, boolean filterOnStop) {
        super(false);
        protocolBab = babProtocol;
        sizeInBytes = controlLineLength = protocolBab.length() + 2; // CRLF, protocol doesn't have data
        this.filterOnStop = filterOnStop;
    }

    ProtocolMessage(byte[] protocol, boolean filterOnStop) {
        this(new ByteArrayBuilder(protocol), filterOnStop);
    }

    ProtocolMessage(ProtocolMessage pm) {
        this(pm.protocolBab, pm.filterOnStop);
    }

    @Override
    boolean isProtocol() {
        return true;
    }

    @Override
    boolean isFilterOnStop() {
        return filterOnStop;
    }

    @Override
    int copyNotEmptyHeaders(int destPosition, byte[] dest) {
        return 0; // until a protocol messages gets headers, might as well shortcut this.
    }
}
