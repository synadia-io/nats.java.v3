package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.synadia.client.utils.ApiConstants.DELIVER_SUBJECT;
import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;

/**
 * Consumer information for durable sourcing. Dictates that a durable consumer with a
 * specific name is used for sourcing. Returned from the server.
 */
@NullMarked
public class ConsumerSource extends LazyApiObject {

    @Nullable
    static ConsumerSource optionalInstance(@Nullable LazyJsonValue vConsumerSource) {
        return vConsumerSource == null ? null : new ConsumerSource(vConsumerSource);
    }

    ConsumerSource(LazyJsonValue v) {
        super(v);
    }

    /**
     * The durable consumer name used for sourcing. A required field.
     * @return the consumer name
     */
    public String getName() {
        return readStringOrEmpty(ljv, NAME);
    }

    /**
     * The subject to deliver messages to. A required field.
     * @return the deliver subject
     */
    public String getDeliverSubject() {
        return readStringOrEmpty(ljv, DELIVER_SUBJECT);
    }

    @Override
    public String toString() {
        return "ConsumerSource " + ljv.toJson();
    }
}
