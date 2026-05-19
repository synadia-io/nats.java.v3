package io.synadia.client.impl;

import static io.synadia.client.utils.ApiConstants.CONSUMERS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

public class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
