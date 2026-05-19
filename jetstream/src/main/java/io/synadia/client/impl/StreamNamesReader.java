package io.synadia.client.impl;

import static io.synadia.client.utils.ApiConstants.STREAMS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

public class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
