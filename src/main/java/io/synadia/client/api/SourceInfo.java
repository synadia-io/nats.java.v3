package io.synadia.client.api;

import io.synadia.client.support.JsonValue;
import io.synadia.client.support.JsonValueUtils;

import java.util.List;

/**
 * Information about a stream being sourced
 */
public class SourceInfo extends SourceInfoBase {

    static List<SourceInfo> optionalListOf(JsonValue vSourceInfos) {
        return JsonValueUtils.optionalListOf(vSourceInfos, SourceInfo::new);
    }

    SourceInfo(JsonValue vSourceInfo) {
        super(vSourceInfo);
    }

    @Override
    public String toString() {
        return "SourceInfo " + jv;
    }
}
