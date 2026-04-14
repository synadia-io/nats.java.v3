package io.synadia.client.js;

import static io.synadia.client.support.ApiConstants.STREAMS;
import static io.synadia.client.support.ApiConstants.SUBJECT;

public class StreamNamesReader extends StringListReader {
    StreamNamesReader() {
        super(STREAMS, SUBJECT);
    }
}
