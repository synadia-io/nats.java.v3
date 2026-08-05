package io.synadia.client.impl;

import static io.synadia.client.utils.ApiConstants.STREAMS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

/**
 * Accumulates the stream names from the pages of a stream names request,
 * optionally filtered on the server by subject.
 */
public class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
