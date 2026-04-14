package io.synadia.client.js;

import io.nats.json.LazyJsonValue;

import java.util.ArrayList;
import java.util.List;

abstract class StringListReader extends AbstractListReader {

    List<String> strings;

    protected StringListReader(String objectName, String filterFieldName) {
        super(objectName, filterFieldName);
        strings = new ArrayList<>();
    }

    @Override
    void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            String s = v.getString();
            if (s != null) {
                strings.add(s);
            }
        }
    }

    List<String> getStrings() {
        return strings;
    }
}
