package io.synadia.client.impl;

import io.nats.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

abstract class StringListReader extends AbstractListReader {

    List<String> strings;

    StringListReader(String objectName, String filterFieldName) {
        super(objectName, filterFieldName);
        strings = new ArrayList<>();
    }

    @Override
    void processItems(List<JsonValue> items) {
        for (JsonValue v : items) {
            if (v.string != null) {
                strings.add(v.string);
            }
        }
    }

    List<String> getStrings() {
        return strings;
    }
}
