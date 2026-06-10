package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Base class for API objects that wrap a {@link LazyJsonValue} as their sole data source.
 * Provides a value-based {@code equals} / {@code hashCode} (delegating to the underlying
 * JSON value) and a default {@link JsonSerializable#toJson()} that returns the wrapped
 * JSON's text representation.
 * <p>
 * Subclasses expose typed accessors on top of the wrapped {@code ljv} field; because
 * every accessor is derived from {@code ljv}, comparing {@code ljv} alone is exhaustive.
 * <p>
 * Optimistic required-field readers live as static helpers on {@link io.synadia.client.utils.ApiUtils}
 * ({@code readStringOrEmpty}, {@code readLongOrMinusOne}, {@code readIntegerOrMinusOne},
 * {@code readDurationOrZero}, {@code readDateOrDefault}); see {@code REQUIRED_FIELDS_POLICY.md}.
 */
@NullMarked
public abstract class LazyApiObject implements JsonSerializable {

    /**
     * The wrapped JSON value. Source of truth for all derived accessors.
     */
    protected final LazyJsonValue ljv;

    /**
     * Construct a LazyApiObject around the given JSON value.
     * @param ljv the wrapped JSON value
     */
    protected LazyApiObject(LazyJsonValue ljv) {
        this.ljv = ljv;
    }

    @Override
    public String toJson() {
        return ljv.toJson();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return ljv.equals(((LazyApiObject) o).ljv);
    }

    @Override
    public int hashCode() {
        return ljv.hashCode();
    }
}
