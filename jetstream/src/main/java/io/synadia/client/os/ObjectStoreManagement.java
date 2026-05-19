package io.synadia.client.os;

import io.synadia.client.api.DiscardPolicy;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.os.ObjectStoreUtil.*;
import static io.synadia.client.utils.JsValidator.validateBucketName;

public class ObjectStoreManagement {
    private final NatsConnection nc;
    private final ObjectStoreOptions oso;
    private final JetStreamManagement jsm;

    public ObjectStoreManagement(@NonNull NatsConnection connection) throws IOException {
        this(connection, null);
    }

    public ObjectStoreManagement(@NonNull NatsConnection connection, @Nullable ObjectStoreOptions oso) throws IOException {
        this.nc = connection;
        this.oso = oso;
        this.jsm = new JetStreamManagement(connection, oso == null ? null : oso.getJetStreamOptions());
    }

    /**
     * Gets a context for working with an Object Store.
     * @param bucketName the bucket name
     * @return an ObjectStore instance.
     * @throws IOException various IO exception such as timeout or interruption
     */
    public ObjectStore objectStore(String bucketName) throws IOException {
        validateBucketName(bucketName, true);
        return new ObjectStore(bucketName, nc, oso);
    }

    /**
     * Create an object store.
     * @param creator the object store configuration creator
     * @return the object store status
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public ObjectStoreStatus create(ObjectStoreConfigurationCreator creator) throws IOException, JetStreamApiException {
        StreamCreator sc = creator.getStreamCreatorCopy()
            .subjects(toMetaStreamSubject(creator.getBucketName()), toChunkStreamSubject(creator.getBucketName()))
            .allowRollup(true)
            .allowDirect(true)
            .discardPolicy(DiscardPolicy.New);
        return new ObjectStoreStatus(jsm.addStream(sc));
    }

    /**
     * Get the list of object stores bucket names
     * @return list of object stores bucket names
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
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
     * Gets the status for an existing object store bucket.
     * @param bucketName the object store bucket name to get info for
     * @return the bucket status object
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectStoreStatus getStatus(String bucketName) throws IOException, JetStreamApiException {
        validateBucketName(bucketName, true);
        return new ObjectStoreStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * Gets the status for all object store buckets.
     * @return list of statuses
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<ObjectStoreStatus> getStatuses() throws IOException, JetStreamApiException {
        List<String> bucketNames = getBucketNames();
        List<ObjectStoreStatus> statuses = new ArrayList<>();
        for (String name : bucketNames) {
            statuses.add(new ObjectStoreStatus(jsm.getStreamInfo(toStreamName(name))));
        }
        return statuses;
    }

    /**
     * Deletes an existing object store. Will throw a JetStreamApiException if the delete fails.
     * @param bucketName the object store bucket name to delete
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void delete(String bucketName) throws IOException, JetStreamApiException {
        validateBucketName(bucketName, true);
        jsm.deleteStream(toStreamName(bucketName));
    }
}
