package io.synadia.client.api;

import io.nats.json.JsonValue;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.JsonValueUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Information about the cluster a stream is part of.
 */
public class ClusterInfo {

    private final String name;
    private final String raftGroup;
    private final String leader;
    private final ZonedDateTime leaderSince;
    private final boolean systemAccount;
    private final String trafficAccount;
    private final List<Replica> replicas;

    static ClusterInfo optionalInstance(JsonValue v) {
        return v == null ? null : new ClusterInfo(v);
    }

    ClusterInfo(JsonValue v) {
        name = readString(v, NAME);
        raftGroup = readString(v, RAFT_GROUP);
        leader = readString(v, LEADER);
        leaderSince = readDate(v, LEADER_SINCE);
        systemAccount = readBoolean(v, SYSTEM_ACCOUNT, false);
        trafficAccount = readString(v, TRAFFIC_ACCOUNT);
        replicas = Replica.optionalListOf(readValue(v, REPLICAS));
    }

    /**
     * The cluster name. Technically can be null
     * @return the cluster or null
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * In clustered environments the name of the Raft group managing the asset
     * @return the raft group name or null
     */
    @Nullable
    public String getRaftGroup() {
        return raftGroup;
    }

    /**
     * The server name of the RAFT leader
     * @return the leader or null
     */
    @Nullable
    public String getLeader() {
        return leader;
    }

    /**
     * The time that it was elected as leader, absent when not the leader
     * @return the time or null
     */
    @Nullable
    public ZonedDateTime getLeaderSince() {
        return leaderSince;
    }

    /**
     * Indicates if the traffic_account is the system account. When true, replication traffic goes over the system account.
     * @return true if the traffic_account is the system account
     */
    public boolean isSystemAccount() {
        return systemAccount;
    }

    /**
     * The account where the replication traffic goes over.
     * @return the traffic account or null
     */
    @Nullable
    public String getTrafficAccount() {
        return trafficAccount;
    }

    /**
     * The members of the RAFT cluster. May be null if there are no replicas.
     * @return the replicas or null
     */
    @Nullable
    public List<Replica> getReplicas() {
        return replicas;
    }

    @Override
    public String toString() {
        return "ClusterInfo{" +
            "name='" + name + '\'' +
            ", raftGroup='" + raftGroup + '\'' +
            ", leader='" + leader + '\'' +
            ", leaderSince=" + leaderSince +
            ", systemAccount=" + systemAccount +
            ", trafficAccount=" + trafficAccount +
            ", replicas=" + replicas +
            '}';
    }
}
