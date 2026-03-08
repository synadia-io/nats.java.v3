package io.synadia.client.impl;

import static io.synadia.client.support.ApiConstants.STREAMS;
import static io.synadia.client.support.ApiConstants.SUBJECT;

class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
