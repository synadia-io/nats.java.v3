package io.synadia.client.impl;

import io.synadia.client.utils.ByteArrayBuilder;

// ----------------------------------------------------------------------------------------------------
// Protocol message is a special version of a InternalPublishableMessage extends NatsMessage
// ----------------------------------------------------------------------------------------------------
/**
 * A message that carries only a protocol control line, such as PING or UNSUB, and never
 * any payload or headers. Written to the outgoing queue alongside regular messages so that
 * protocol operations keep their ordering with respect to publishes.
 */
public class ProtocolMessage extends InternalPublishableMessage {
    final boolean filterOnStop;

    ProtocolMessage(ByteArrayBuilder babProtocol, boolean filterOnStop) {
        super();
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
