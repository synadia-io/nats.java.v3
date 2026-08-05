package io.synadia.client.impl;

import static io.synadia.client.utils.ApiConstants.CONSUMERS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

/**
 * Reads the paged consumer names response from the JetStream API, optionally filtered by subject.
 */
public class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
