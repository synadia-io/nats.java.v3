package io.synadia.client.api;

import io.nats.json.JsonValue;
import io.nats.json.JsonValueUtils;

import java.util.List;

/**
 * Information about a stream being sourced
 */
public class SourceInfo extends SourceInfoBase {

    static List<SourceInfo> optionalListOf(JsonValue vSourceInfos) {
        return JsonValueUtils.listOfOrNull(vSourceInfos, SourceInfo::new);
    }

    SourceInfo(JsonValue vSourceInfo) {
        super(vSourceInfo);
    }

    @Override
    public String toString() {
        return "SourceInfo " + jv;
    }
}
