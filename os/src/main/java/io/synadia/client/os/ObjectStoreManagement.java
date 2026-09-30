package io.synadia.client.os;

import io.synadia.client.api.DiscardPolicy;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.os.ObjectStoreUtil.*;
import static io.synadia.client.utils.JsValidator.validateBucketName;

/**
 * Create, inspect and delete Object Store buckets. For reading and writing the objects themselves, use
 * {@link ObjectStore ObjectStore}.
 */
public class ObjectStoreManagement {
    private final NatsConnection nc;
    private final ObjectStoreOptions oso;
    private final JetStreamManagement jsm;

    /**
     * Construct management for the connection, with default options.
     * @param connection the connection
     */
    public ObjectStoreManagement(@NonNull NatsConnection connection) {
        this(connection, null);
    }

    /**
     * Construct management for the connection with the given options.
     * @param connection the connection
     * @param oso the object store options, or null for defaults
     */
    public ObjectStoreManagement(@NonNull NatsConnection connection, @Nullable ObjectStoreOptions oso) {
        this.nc = connection;
        this.oso = oso;
        this.jsm = new JetStreamManagement(connection, oso == null ? null : oso.getJetStreamOptions());
    }

    /**
     * Gets a context for working with an Object Store.
     * @param bucketName the bucket name
     * @return an ObjectStore instance.
     */
    public ObjectStore objectStore(String bucketName) {
        validateBucketName(bucketName, true);
        return new ObjectStore(bucketName, nc, oso);
    }

    /**
     * Create an object store.
     * @param creator the object store configuration creator
     * @return the object store status
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public ObjectStoreStatus create(ObjectStoreCreator creator) throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> getBucketNames() throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ObjectStoreStatus getStatus(String bucketName) throws JetStreamException, InterruptedException {
        validateBucketName(bucketName, true);
        return new ObjectStoreStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * Gets the status for all object store buckets.
     * @return list of statuses
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<ObjectStoreStatus> getStatuses() throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void delete(String bucketName) throws JetStreamException, InterruptedException {
        validateBucketName(bucketName, true);
        jsm.deleteStream(toStreamName(bucketName));
    }
}
