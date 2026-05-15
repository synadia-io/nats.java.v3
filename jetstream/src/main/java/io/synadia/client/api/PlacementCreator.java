package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.impl.JetStreamApiUtils.replaceAllStrings;
import static io.synadia.client.testutils.ApiConstants.CLUSTER;
import static io.synadia.client.testutils.ApiConstants.TAGS;

/**
 * PlacementCreator is used to create placement directives for use in a StreamCreator.
 */
@NullMarked
public class PlacementCreator implements JsonSerializable {
    private @Nullable String cluster;
    private final List<String> tags;

    /**
     * Construct an empty PlacementCreator
     */
    public PlacementCreator() {
        this.tags = new ArrayList<>();
    }

    /**
     * Construct a PlacementCreator with cluster and tags
     * @param cluster the cluster name
     * @param tags the list of tags, may be null
     */
    public PlacementCreator(@Nullable String cluster, @Nullable List<String> tags) {
        this.cluster = cluster == null || cluster.isEmpty() ? null : cluster;
        this.tags = new ArrayList<>();
        replaceAllStrings(this.tags, tags);
    }

    /**
     * Construct a PlacementCreator from a Placement (server response)
     * @param p the placement to copy from
     */
    PlacementCreator(Placement p) {
        this(p.getCluster(), p.getTags());
    }

    /**
     * Set the cluster string.
     * @param cluster the cluster
     * @return this instance for chaining
     */
    public PlacementCreator cluster(String cluster) {
        this.cluster = cluster;
        return this;
    }

    /**
     * Set the tags
     * @param tags the tags
     * @return this instance for chaining
     */
    public PlacementCreator tags(@Nullable String... tags) {
        replaceAllStrings(this.tags, tags);
        return this;
    }

    /**
     * Set the tags
     * @param tags the list of tags
     * @return this instance for chaining
     */
    public PlacementCreator tags(@Nullable List<String> tags) {
        replaceAllStrings(this.tags, tags);
        return this;
    }

    /**
     * Whether the Placement has either a cluster or tags
     * @return true if the Placement has data
     */
    public boolean hasData() {
        return cluster != null || !tags.isEmpty();
    }

    /**
     * The desired cluster name to place the stream.
     * @return The cluster name
     */
    public @Nullable String getCluster() {
        return cluster;
    }

    /**
     * Tags required on servers hosting this stream
     * @return the list of tags; never null, but may be empty
     */
    public List<String> getTags() {
        return tags;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, CLUSTER, cluster);
        addStrings(sb, TAGS, tags);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "PlacementCreator" + toJson();
    }
}
