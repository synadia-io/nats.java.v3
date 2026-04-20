package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.CLUSTER;
import static io.synadia.client.testutils.ApiConstants.TAGS;
import static io.synadia.client.testutils.Validator.nullOrEmpty;

/**
 * PlacementCreator is used to create placement directives for use in a StreamCreator.
 */
@NullMarked
public class PlacementCreator implements JsonSerializable {
    private @Nullable String cluster;
    private @Nullable List<String> tags;

    /**
     * Construct an empty PlacementCreator
     */
    public PlacementCreator() {}

    /**
     * Construct a PlacementCreator with cluster and tags
     * @param cluster the cluster name
     * @param tags the list of tags, may be null
     */
    public PlacementCreator(@Nullable String cluster, @Nullable List<String> tags) {
        this.cluster = cluster == null || cluster.isEmpty() ? null : cluster;
        this.tags = tags == null || tags.isEmpty() ? null : tags;
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
    public PlacementCreator tags(String... tags) {
        return tags(Arrays.asList(tags));
    }

    /**
     * Set the tags
     * @param tags the list of tags
     * @return this instance for chaining
     */
    public PlacementCreator tags(@Nullable List<String> tags) {
        if (nullOrEmpty(tags)) {
            this.tags = null;
        }
        else {
            this.tags = new ArrayList<>();
            for (String tag : tags) {
                if (!nullOrEmpty(tag)) {
                    this.tags.add(tag);
                }
            }
            if (this.tags.isEmpty()) {
                this.tags = null;
            }
        }
        return this;
    }

    /**
     * Whether the Placement has either a cluster or tags
     * @return true if the Placement has data
     */
    public boolean hasData() {
        return cluster != null || tags != null;
    }

    /**
     * The desired cluster name to place the stream.
     * @return The cluster name
     */
    @Nullable
    public String getCluster() {
        return cluster;
    }

    /**
     * Tags required on servers hosting this stream
     * @return the list of tags
     */
    @Nullable
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
