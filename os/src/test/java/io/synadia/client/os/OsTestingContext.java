package io.synadia.client.os;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.StorageType;
import io.synadia.client.impl.JetStream;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class OsTestingContext implements AutoCloseable {
    public final NatsConnection nc;
    public final JetStreamManagement jsm;
    public final JetStream js;
    public final ObjectStoreManagement osm;

    private final Set<String> osBuckets;

    public OsTestingContext(NatsConnection nc) {
        this.nc = nc;
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();
        osm = new ObjectStoreManagement(nc);
        osBuckets = new HashSet<>();
    }

    public ObjectStoreCreator osCreator(String bucketName) {
        return new ObjectStoreCreator(bucketName)
            .storageType(StorageType.Memory);
    }

    public ObjectStore create(String bucketName) throws JetStreamException, InterruptedException {
        return create(osCreator(bucketName));
    }

    public ObjectStoreStatus createReturnStatus(String bucketName) throws JetStreamException, InterruptedException {
        return createReturnStatus(osCreator(bucketName));
    }

    public ObjectStore create(String bucketName, Function<ObjectStoreCreator, ObjectStoreCreator> fun) throws JetStreamException, InterruptedException {
        return create(fun.apply(osCreator(bucketName)));
    }

    public ObjectStoreStatus createReturnStatus(String bucketName, Function<ObjectStoreCreator, ObjectStoreCreator> fun) throws JetStreamException, InterruptedException {
        return createReturnStatus(fun.apply(osCreator(bucketName)));
    }

    public ObjectStore create(ObjectStoreCreator creator) throws JetStreamException, InterruptedException {
        osBuckets.add(creator.getBucketName());
        return osm.create(creator);
    }

    public ObjectStoreStatus createReturnStatus(ObjectStoreCreator creator) throws JetStreamException, InterruptedException {
        osBuckets.add(creator.getBucketName());
        return osm.createReturnStatus(creator);
    }

    @Override
    public void close() throws Exception {
        for (String bucket : osBuckets) {
            try { osm.delete(bucket); } catch (Exception ignore) {}
        }
    }
}
