package io.synadia.client.kv;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.StorageType;
import io.synadia.client.impl.JetStream;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.JetStreamTestingContext;
import io.synadia.client.impl.NatsConnection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class KvTestingContext implements AutoCloseable {
    public final NatsConnection nc;
    public final JetStreamManagement jsm;
    public final JetStream js;
    public final KeyValueManagement kvm;

    private final Set<String> kvBuckets;

    public KvTestingContext(JetStreamTestingContext ctx) {
        this.nc = ctx.nc;
        jsm = ctx.jsm;
        js = ctx.js;
        kvm = new KeyValueManagement(nc);
        kvBuckets = new HashSet<>();
    }

    public KvTestingContext(NatsConnection nc) {
        this.nc = nc;
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();
        kvm = new KeyValueManagement(nc);
        kvBuckets = new HashSet<>();
    }

    // ----------------------------------------------------------------------------------------------------
    // KeyValue
    // ----------------------------------------------------------------------------------------------------
    public KeyValueCreator kvCreator(String bucketName) {
        return new KeyValueCreator(bucketName)
            .storageType(StorageType.Memory);
    }

    public KeyValueStatus kvCreate(String bucketName) throws JetStreamException, InterruptedException {
        return kvCreate(kvCreator(bucketName));
    }

    public KeyValueStatus kvCreate(String bucketName, Function<KeyValueCreator, KeyValueCreator> fun) throws JetStreamException, InterruptedException {
        return kvCreate(fun.apply(kvCreator(bucketName)));
    }

    public KeyValueStatus kvCreate(KeyValueCreator creator) throws JetStreamException, InterruptedException {
        kvBuckets.add(creator.getBucketName());
        return kvm.create(creator);
    }

    public void addBucket(String bucketName) {
        kvBuckets.add(bucketName);
    }

    @Override
    public void close() throws Exception {
        for (String bucket : kvBuckets) {
            try { kvm.delete(bucket); } catch (Exception ignore) {}
        }
    }
}
