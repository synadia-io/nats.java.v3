package io.synadia.client;

import io.synadia.client.api.KeyValueConfiguration;
import io.synadia.client.api.KeyValueStatus;

import java.io.IOException;
import java.util.List;

/**
 * Key Value Store Management context for creation and access to key value buckets.
 */
public interface KeyValueManagement {

    /**
     * Create a key value store.
     * @param config the key value configuration
     * @return bucket info
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    KeyValueStatus create(KeyValueConfiguration config) throws IOException, JetStreamApiException;

    /**
     * Update a key value store configuration. Storage type cannot change.
     * @param config the key value configuration
     * @return bucket info
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    KeyValueStatus update(KeyValueConfiguration config) throws IOException, JetStreamApiException;

    /**
     * Get the list of bucket names.
     * @return list of bucket names
     * @throws IOException covers various communication issues with the NATS
     *          server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    List<String> getBucketNames() throws IOException, JetStreamApiException;

    /**
     * Gets the status for an existing bucket.
     * @param bucketName the bucket name to use
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return the bucket status object
     */
    KeyValueStatus getStatus(String bucketName) throws IOException, JetStreamApiException;

    /**
     * Get the statuses for all buckets
     * @return list of statuses
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    List<KeyValueStatus> getStatuses() throws IOException, JetStreamApiException;

    /**
     * Deletes an existing bucket. Will throw a JetStreamApiException if the delete fails.
     * @param bucketName the stream name to use.
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    void delete(String bucketName) throws IOException, JetStreamApiException;
}
