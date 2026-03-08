package io.nats.client.impl;

import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValueManagement;
import io.nats.client.KeyValueOptions;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueStatus;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.Validator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.NatsKeyValueUtil.*;

public class NatsKeyValueManagement implements KeyValueManagement {
    private final NatsJetStreamManagement jsm;
    private final boolean serverOlderThan272;

    NatsKeyValueManagement(NatsConnection connection, KeyValueOptions kvo, NatsJetStreamManagement jsm) throws IOException {
        if (jsm == null) {
            this.jsm = new NatsJetStreamManagement(connection, kvo == null ? null : kvo.getJetStreamOptions());
        }
        else {
            this.jsm = jsm;
        }
        serverOlderThan272 = this.jsm.conn.getServerInfo().isOlderThanVersion("2.7.2");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyValueStatus create(KeyValueConfiguration config) throws IOException, JetStreamApiException {
        StreamConfiguration sc = config.getBackingConfig();

        // most validation / KVC setup is done in the KeyValueConfiguration Builder
        // but this is done here because the context has a connection which has the server info with a version
        if ( serverOlderThan272 ) {
            sc = StreamConfiguration.builder(sc).discardPolicy(null).build(); // null discard policy will use default
        }
        return new KeyValueStatus(jsm.addStream(sc));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyValueStatus update(KeyValueConfiguration config) throws IOException, JetStreamApiException {
        return new KeyValueStatus(jsm.updateStream(config.getBackingConfig()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getBucketNames() throws IOException, JetStreamApiException {
        List<String> buckets = new ArrayList<>();
        List<String> names = jsm.getStreamNames();
        for (String name : names) {
            if (name.startsWith(KV_STREAM_PREFIX)) {
                buckets.add(extractBucketName(name));
            }
        }
        return buckets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyValueStatus getBucketInfo(String bucketName) throws IOException, JetStreamApiException {
        return getStatus(bucketName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KeyValueStatus getStatus(String bucketName) throws IOException, JetStreamApiException {
        Validator.validateBucketName(bucketName, true);
        return new KeyValueStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<KeyValueStatus> getStatuses() throws IOException, JetStreamApiException {
        List<String> bucketNames = getBucketNames();
        List<KeyValueStatus> statuses = new ArrayList<>();
        for (String name : bucketNames) {
            statuses.add(new KeyValueStatus(jsm.getStreamInfo(toStreamName(name))));
        }
        return statuses;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String bucketName) throws IOException, JetStreamApiException {
        Validator.validateBucketName(bucketName, true);
        jsm.deleteStream(toStreamName(bucketName));
    }
}
