package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Information about an upstream stream source in a mirror
 */
@NullMarked
public class MirrorInfo extends StreamSourceInfo {

    @Nullable
    static MirrorInfo optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new MirrorInfo(v);
    }

    MirrorInfo(LazyJsonValue v) {
        super(v);
    }
}
