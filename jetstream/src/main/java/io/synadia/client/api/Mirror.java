package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mirror Information. Maintains a 1:1 mirror of another stream with name matching this property.
 * When a mirror is configured subjects and sources must be empty.
 */
@NullMarked
public class Mirror extends StreamSource {

    @Nullable
    static Mirror optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new Mirror(v);
    }

    Mirror(LazyJsonValue v) {
        super(v);
    }
}
