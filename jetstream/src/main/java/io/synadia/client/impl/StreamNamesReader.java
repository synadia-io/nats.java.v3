package io.synadia.client.impl;

import static io.synadia.client.testutils.ApiConstants.STREAMS;
import static io.synadia.client.testutils.ApiConstants.SUBJECT;

public class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
