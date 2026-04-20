package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.synadia.client.testutils.Validator;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * RepublishCreator is used to create a Republish configuration for use in a StreamCreator.
 */
@NullMarked
public class RepublishCreator implements JsonSerializable {
    private final String source;
    private final String destination;
    private final boolean headersOnly;

    /**
     * Construct a RepublishCreator
     * @param source the Published subject matching filter
     * @param destination the RePublish Subject template
     * @param headersOnly Whether to RePublish only headers (no body)
     */
    public RepublishCreator(String source, String destination, boolean headersOnly) {
        this.source = Validator.required(source, "Source");
        this.destination = Validator.required(destination, "Destination");
        this.headersOnly = headersOnly;
    }

    /**
     * Construct a RepublishCreator from a Republish (server response)
     * @param r the republish to copy from
     */
    RepublishCreator(Republish r) {
        this(r.getSource(), r.getDestination(), r.isHeadersOnly());
    }

    /**
     * Get source, the Published subject matching filter
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * Get destination, the RePublish Subject template
     * @return the destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * Get headersOnly, Whether to RePublish only headers (no body)
     * @return headersOnly
     */
    public boolean isHeadersOnly() {
        return headersOnly;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SRC, source);
        addField(sb, DEST, destination);
        addField(sb, HEADERS_ONLY, headersOnly);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "RepublishCreator " + toJson();
    }
}
