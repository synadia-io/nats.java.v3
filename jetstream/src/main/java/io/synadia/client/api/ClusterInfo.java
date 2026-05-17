package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Information about the cluster a stream or consumer is part of.
 */
@NullMarked
public class ClusterInfo extends LazyApiObject {
    private @Nullable List<Replica> _replicas;

    @Nullable
    static ClusterInfo optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new ClusterInfo(v);
    }

    ClusterInfo(LazyJsonValue v) {
        super(v);
    }

    /**
     * The cluster name. Technically can be null
     * @return the cluster or null
     */
    @Nullable
    public String getName() {
        return readString(ljv, NAME);
    }

    /**
     * In clustered environments the name of the Raft group managing the asset
     * @return the raft group name or null
     */
    @Nullable
    public String getRaftGroup() {
        return readString(ljv, RAFT_GROUP);
    }

    /**
     * The server name of the RAFT leader
     * @return the leader or null
     */
    @Nullable
    public String getLeader() {
        return readString(ljv, LEADER);
    }

    /**
     * The time that it was elected as leader, absent when not the leader
     * @return the time or null
     */
    @Nullable
    public ZonedDateTime getLeaderSince() {
        return readDate(ljv, LEADER_SINCE);
    }

    /**
     * Indicates if the traffic_account is the system account.
     * @return true if the traffic_account is the system account
     */
    public boolean isSystemAccount() {
        return readBoolean(ljv, SYSTEM_ACCOUNT, false);
    }

    /**
     * The account where the replication traffic goes over.
     * @return the traffic account or null
     */
    @Nullable
    public String getTrafficAccount() {
        return readString(ljv, TRAFFIC_ACCOUNT);
    }

    /**
     * The members of the RAFT cluster.
     * @return the replicas, empty list if none
     */
    public List<Replica> getReplicas() {
        if (_replicas == null) {
            _replicas = Replica.listOf(readValue(ljv, REPLICAS));
        }
        return _replicas;
    }

    @Override
    public String toString() {
        return "ClusterInfo " + ljv.toJson();
    }
}
