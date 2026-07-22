package io.synadia.client.os;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.impl.JetStream;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.utils.TestBase;

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

    public OsTestingContext(NatsConnection nc) throws JetStreamException {
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

    public ObjectStoreStatus osCreate(String bucketName) throws JetStreamException, InterruptedException {
        return osCreate(osCreator(bucketName));
    }

    public ObjectStoreStatus osCreate(ObjectStoreConfigurationCreator creator) throws JetStreamException, InterruptedException {
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
