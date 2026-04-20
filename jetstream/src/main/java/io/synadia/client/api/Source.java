package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static io.synadia.client.impl.JetStreamApiUtils.mapToList;

/**
 * Source Information
 */
@NullMarked
public class Source extends StreamSource {
    static List<Source> listOf(LazyJsonValue v) {
        return mapToList(v, Source::new);
    }

    Source(LazyJsonValue v) {
        super("Source", v);
    }
}
