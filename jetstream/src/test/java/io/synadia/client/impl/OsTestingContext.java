package io.synadia.client.impl;

import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.os.ObjectStoreConfigurationCreator;
import io.synadia.client.os.ObjectStoreManagement;
import io.synadia.client.os.ObjectStoreStatus;
import io.synadia.client.testutils.TestBase;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OsTestingContext implements AutoCloseable {
    public final JetStreamManagement jsm;
    public final JetStream js;
    public final ObjectStoreManagement osm;

    private final String subjectBase;
    private final Map<Object, String> subjects;
    private final String consumerNameBase;
    private final Map<Object, String> consumerNames;
    public String stream;
    public StreamInfo si;

    private final Set<String> osBuckets;

    public OsTestingContext(NatsConnection nc) throws JetStreamApiException, IOException {
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();
        osm = new ObjectStoreManagement(nc);

        stream = TestBase.random();
        subjectBase = TestBase.random();
        subjects = new HashMap<>();
        consumerNameBase = TestBase.random();
        consumerNames = new HashMap<>();

        osBuckets = new HashSet<>();
    }

    public ObjectStoreConfigurationCreator osCreator(String bucketName) {
        return new ObjectStoreConfigurationCreator(bucketName)
            .storageType(StorageType.Memory);
    }

    public ObjectStoreStatus osCreate(String bucketName) throws JetStreamApiException, IOException {
        return osCreate(osCreator(bucketName));
    }

    public ObjectStoreStatus osCreate(ObjectStoreConfigurationCreator creator) throws JetStreamApiException, IOException {
        osBuckets.add(creator.getBucketName());
        return osm.create(creator);
    }

    @Override
    public void close() throws Exception {
        for (String bucket : osBuckets) {
            try { osm.delete(bucket); } catch (Exception ignore) {}
        }
    }
}
