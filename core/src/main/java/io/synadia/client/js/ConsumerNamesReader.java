package io.synadia.client.js;

import static io.synadia.client.support.ApiConstants.CONSUMERS;
import static io.synadia.client.support.ApiConstants.SUBJECT;

public class ConsumerNamesReader extends StringListReader {
    ConsumerNamesReader() {
        super(CONSUMERS, SUBJECT);
    }
}
