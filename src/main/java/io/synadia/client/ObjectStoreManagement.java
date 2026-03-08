package io.synadia.client;

import io.synadia.client.api.ObjectStoreConfiguration;
import io.synadia.client.api.ObjectStoreStatus;

import java.io.IOException;
import java.util.List;

/**
 * Object Store Management context for creation and access to object stores.
 */
public interface ObjectStoreManagement {

    /**
     * Create an object store.
     * @param config the object store configuration
     * @return bucket info
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    ObjectStoreStatus create(ObjectStoreConfiguration config) throws IOException, JetStreamApiException;

    /**
     * Get the list of object stores bucket names
     * @return list of object stores bucket names
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    List<String> getBucketNames() throws IOException, JetStreamApiException;

    /**
     * Gets the status for an existing object store bucket.
     * @param bucketName the object store bucket name to get info for
     * @return the bucket status object
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    ObjectStoreStatus getStatus(String bucketName) throws IOException, JetStreamApiException;

    /**
     * Gets the status for all object store buckets.
     * @return list of statuses
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    List<ObjectStoreStatus> getStatuses() throws IOException, JetStreamApiException;

    /**
     * Deletes an existing object store. Will throw a JetStreamApiException if the delete fails.
     * @param bucketName the object store bucket name to delete
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    void delete(String bucketName) throws IOException, JetStreamApiException;
}
