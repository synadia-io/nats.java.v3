package io.synadia.client.api;

import io.nats.json.JsonValue;

import java.util.List;

import static io.nats.json.JsonValueUtils.listOfOrNull;

/**
 * Information about a stream being sourced
 */
public class SourceInfo extends SourceInfoBase {

    static List<SourceInfo> optionalListOf(JsonValue vSourceInfos) {
        return listOfOrNull(vSourceInfos, SourceInfo::new);
    }

    SourceInfo(JsonValue vSourceInfo) {
        super(vSourceInfo);
    }

    @Override
    public String toString() {
        return "SourceInfo " + jv;
    }
}
