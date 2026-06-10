package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.nats.json.LazyJsonValueUtils.readValue;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.*;

/**
 * Information about a stream being sourced
 */
@NullMarked
abstract class StreamSourceInfo extends LazyApiObject {

    protected StreamSourceInfo(LazyJsonValue v) {
        super(v);
    }

    /**
     * The name of the Stream being replicated
     * @return the name
     */
    public String getName() {
        return readStringOrEmpty(ljv, NAME);
    }

    /**
     * The subject filter to apply to the messages
     * @return the subject filter
     */
    @Nullable
    public String getFilterSubject() {
        return readString(ljv, FILTER_SUBJECT);
    }

    /**
     * How many uncommitted operations this peer is behind the leader.
     * <p>The server value is an unsigned 64-bit number.
     * @return the lag
     */
    public long getLag() {
        return readUnsignedLongOrZero(ljv, LAG);
    }

    /**
     * How many uncommitted operations this peer is behind the leader, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getLag()}.
     * @return the lag, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getLagAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, LAG);
    }

    /**
     * Time since this peer was last seen
     * @return the time, or {@link Duration#ZERO} if absent
     */
    public Duration getActive() {
        return readDurationOrZero(ljv, ACTIVE);
    }

    /**
     * Configuration referencing a stream source in another account or JetStream domain
     * @return the external
     */
    @Nullable
    public External getExternal() {
        return External.optionalInstance(readValue(ljv, EXTERNAL));
    }

    /**
     * The list of subject transforms, if any
     * @return the list of subject transforms
     */
    public List<SubjectTransform> getSubjectTransforms() {
        return SubjectTransform.listOf(readValue(ljv, SUBJECT_TRANSFORMS));
    }

    /**
     * The last error
     * @return the error
     */
    @Nullable
    public Error getError() {
        return Error.optionalInstance(readValue(ljv, ERROR));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + ljv.toJson();
    }
}
