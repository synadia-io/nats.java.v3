package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for list readers whose pages contain plain strings rather than objects, for instance
 * stream names or consumer names. Nulls in the array are skipped.
 */
public abstract class StringListReader extends AbstractListReader {

    List<String> strings;

    protected StringListReader(String objectName, String filterFieldName) {
        super(objectName, filterFieldName);
        strings = new ArrayList<>();
    }

    @Override
    public void processItems(List<LazyJsonValue> items) {
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
