package io.synadia.client.impl;

import static io.synadia.client.utils.NatsConstants.EMPTY_BODY;

class MarkerMessage extends NatsMessage {
    // Poison pill is a graphic, but common term for an item that breaks loops or stop something.
    // In this class the poison pill is used to break out of timed waits on the blocking queue.
    // A simple == is used to resolve if any message is exactly the static pill object in question
    static final MarkerMessage POISON_PILL = new MarkerMessage("_poison");

    static final MarkerMessage END_RECONNECT = new MarkerMessage("_end");

    public MarkerMessage(String subject) {
        super(subject, null, EMPTY_BODY);
    }
}
