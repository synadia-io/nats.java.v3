package io.nats.client.impl;

import static io.nats.client.support.ApiConstants.CONSUMERS;
import static io.nats.client.support.ApiConstants.SUBJECT;

class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
