package io.nats.client.impl;

import io.nats.client.JetStreamApiException;
import io.nats.client.ObjectStoreManagement;
import io.nats.client.ObjectStoreOptions;
import io.nats.client.api.ObjectStoreConfiguration;
import io.nats.client.api.ObjectStoreStatus;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.support.Validator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.NatsObjectStoreUtil.*;

public class NatsObjectStoreManagement implements ObjectStoreManagement {
    private final NatsJetStreamManagement jsm;

    NatsObjectStoreManagement(NatsConnection connection, ObjectStoreOptions oso, NatsJetStreamManagement jsm) throws IOException {
        if (jsm == null) {
            this.jsm = new NatsJetStreamManagement(connection, oso == null ? null : oso.getJetStreamOptions());
        }
        else {
            this.jsm = jsm;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ObjectStoreStatus create(ObjectStoreConfiguration config) throws IOException, JetStreamApiException {
        StreamConfiguration sc = config.getBackingConfig();
        return new ObjectStoreStatus(jsm.addStream(sc));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getBucketNames() throws IOException, JetStreamApiException {
        List<String> buckets = new ArrayList<>();
        List<String> names = jsm.getStreamNames();
        for (String name : names) {
            if (name.startsWith(OBJ_STREAM_PREFIX)) {
                buckets.add(extractBucketName(name));
            }
        }
        return buckets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ObjectStoreStatus getStatus(String bucketName) throws IOException, JetStreamApiException {
        Validator.validateBucketName(bucketName, true);
        return new ObjectStoreStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ObjectStoreStatus> getStatuses() throws IOException, JetStreamApiException {
        List<String> bucketNames = getBucketNames();
        List<ObjectStoreStatus> statuses = new ArrayList<>();
        for (String name : bucketNames) {
            statuses.add(new ObjectStoreStatus(jsm.getStreamInfo(toStreamName(name))));
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
