package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.synadia.client.utils.ApiUtils.mapToList;

/**
 * Information about a stream being sourced
 */
@NullMarked
public class SourceInfo extends StreamSourceInfo {

    static List<SourceInfo> listOf(@Nullable LazyJsonValue v) {
        return mapToList(v, SourceInfo::new);
    }

    SourceInfo(LazyJsonValue v) {
        super(v);
    }
}
