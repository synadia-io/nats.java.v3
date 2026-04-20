package io.synadia.client.impl;

import static io.synadia.client.testutils.ApiConstants.CONSUMERS;
import static io.synadia.client.testutils.ApiConstants.SUBJECT;

public class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
