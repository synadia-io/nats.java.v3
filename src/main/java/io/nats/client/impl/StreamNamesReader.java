package io.nats.client.impl;

import static io.nats.client.support.ApiConstants.STREAMS;
import static io.nats.client.support.ApiConstants.SUBJECT;

class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
