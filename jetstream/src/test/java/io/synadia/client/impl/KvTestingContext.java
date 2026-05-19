package io.synadia.client.impl;

import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.kv.KeyValueConfigurationCreator;
import io.synadia.client.kv.KeyValueManagement;
import io.synadia.client.kv.KeyValueStatus;
import io.synadia.client.utils.TestBase;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KvTestingContext implements AutoCloseable {
    public final JetStreamManagement jsm;
    public final JetStream js;
    public final KeyValueManagement kvm;

    private final String subjectBase;
    private final Map<Object, String> subjects;
    private final String consumerNameBase;
    private final Map<Object, String> consumerNames;
    public String stream;
    public StreamInfo si;

    private final Set<String> kvBuckets;

    public KvTestingContext(NatsConnection nc) throws JetStreamApiException, IOException {
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();
        kvm = new KeyValueManagement(nc);

        stream = TestBase.random();
        subjectBase = TestBase.random();
        subjects = new HashMap<>();
        consumerNameBase = TestBase.random();
        consumerNames = new HashMap<>();

        kvBuckets = new HashSet<>();
    }

    // ----------------------------------------------------------------------------------------------------
    // KeyValue
    // ----------------------------------------------------------------------------------------------------
    public KeyValueConfigurationCreator kvCreator(String bucketName) {
        return new KeyValueConfigurationCreator(bucketName)
            .storageType(StorageType.Memory);
    }

    public KeyValueStatus kvCreate(String bucketName) throws JetStreamApiException, IOException {
        return kvCreate(kvCreator(bucketName));
    }

    public KeyValueStatus kvCreate(KeyValueConfigurationCreator creator) throws JetStreamApiException, IOException {
        kvBuckets.add(creator.getBucketName());
        return kvm.create(creator);
    }

    @Override
    public void close() throws Exception {
        for (String bucket : kvBuckets) {
            try { kvm.delete(bucket); } catch (Exception ignore) {}
        }
    }
}
