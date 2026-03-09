package io.synadia.client.impl;

import static io.synadia.client.support.ApiConstants.CONSUMERS;
import static io.synadia.client.support.ApiConstants.SUBJECT;

class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
