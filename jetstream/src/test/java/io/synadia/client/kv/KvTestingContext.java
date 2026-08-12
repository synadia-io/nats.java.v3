package io.synadia.client.kv;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.StorageType;
import io.synadia.client.impl.JetStream;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;

import java.util.HashSet;
import java.util.Set;

public class KvTestingContext implements AutoCloseable {
    public final NatsConnection nc;
    public final JetStreamManagement jsm;
    public final JetStream js;
    public final KeyValueManagement kvm;

    private final Set<String> kvBuckets;

    public KvTestingContext(NatsConnection nc) throws JetStreamException {
        this.nc = nc;
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();
        kvm = new KeyValueManagement(nc);

        kvBuckets = new HashSet<>();
    }

    // ----------------------------------------------------------------------------------------------------
    // KeyValue
    // ----------------------------------------------------------------------------------------------------
    public KeyValueConfigurationCreator kvCreator(String bucketName) {
        return new KeyValueConfigurationCreator(bucketName)
            .storageType(StorageType.Memory);
    }

    public KeyValueStatus kvCreate(String bucketName) throws JetStreamException, InterruptedException {
        return kvCreate(kvCreator(bucketName));
    }

    public KeyValueStatus kvCreate(KeyValueConfigurationCreator creator) throws JetStreamException, InterruptedException {
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
