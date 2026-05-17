package io.synadia.client.kv;

import io.synadia.client.api.DiscardPolicy;
import io.synadia.client.api.MirrorCreator;
import io.synadia.client.api.SourceCreator;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.impl.JetStreamConstants.SERVER_DEFAULT_DUPLICATE_WINDOW_MS;
import static io.synadia.client.kv.KeyValueUtils.*;
import static io.synadia.client.testutils.JsValidator.validateBucketName;

public class KeyValueManagement {
    private final NatsConnection nc;
    private final KeyValueOptions kvo;
    private final JetStreamManagement jsm;

    public KeyValueManagement(@NonNull NatsConnection connection) throws IOException {
        this(connection, null);
    }

    public KeyValueManagement(@NonNull NatsConnection connection, @Nullable KeyValueOptions kvo) throws IOException {
        this.nc = connection;
        this.kvo = kvo;
        this.jsm = new JetStreamManagement(connection, kvo == null ? null : kvo.getJetStreamOptions());
    }

    /**
     * Gets a context for working with a Key Value bucket
     * @param bucketName the bucket name
     * @return a KeyValue instance.
     * @throws IOException various IO exception such as timeout or interruption
     */
    public KeyValue keyValue(String bucketName) throws IOException {
        validateBucketName(bucketName, true);
        return new KeyValue(bucketName, nc, kvo);
    }

    /**
     * Create a key value store.
     * @param creator the key value configuration creator
     * @return the key value Status
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueStatus create(KeyValueConfigurationCreator creator) throws IOException, JetStreamApiException {
        return new KeyValueStatus(jsm.addStream(setupStreamCreator(creator)));
    }

    /**
     * Update a key value store configuration. Storage type cannot change.
     * @param creator the key value configuration
     * @return the Key Value Status
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueStatus update(KeyValueConfigurationCreator creator) throws IOException, JetStreamApiException {
        return new KeyValueStatus(jsm.updateStream(setupStreamCreator(creator)));
    }

    private static @NonNull StreamCreator setupStreamCreator(KeyValueConfigurationCreator creator) {
        StreamCreator sc = creator.getStreamCreatorCopy()
            .discardPolicy(DiscardPolicy.New)
            .allowRollup(true)
            .allowDirect(true)
            .denyDelete(true);

        MirrorCreator mc = sc.getMirrorCreator();
        if (mc != null) {
            // if they want a mirror...
            // - the stream must have mirror connect and
            // - the stream name must be a proper bucket name
            sc.mirrorDirect(true);
            String mName = mc.getStreamName();
            if (!hasPrefix(mName)) {
                mc = new MirrorCreator(toStreamName(mName), mc);
            }
            sc.mirrorCreator(mc);
        }
        else {
            List<SourceCreator> sourceCreators = creator.getSourceCreators();
            if (!sourceCreators.isEmpty()) {
                for (int i = 0; i < sourceCreators.size(); i++) {
                    SourceCreator c = sourceCreators.get(i);
                    String sName = c.getStreamName();
                    if (!hasPrefix(sName)) {
                        sourceCreators.set(i, new SourceCreator(toStreamName(sName), c));
                    }
                }
                sc.sourceCreators(sourceCreators);
            }
            else {
                sc.subjects(toStreamSubject(creator.getBucketName()));
            }
        }

        if (creator.getLimitMarkerTtl() != null) {
            sc.subjectDeleteMarkerTtl(creator.getLimitMarkerTtl()).allowMessageTtl();
        }

        long ttlMs = creator.getTtl().toMillis();
        long dupeMs = SERVER_DEFAULT_DUPLICATE_WINDOW_MS;
        if (ttlMs > 0 && ttlMs < SERVER_DEFAULT_DUPLICATE_WINDOW_MS) {
            dupeMs = ttlMs;
        }
        sc.duplicateWindow(dupeMs);

        return sc;
    }

    /**
     * Get the list of bucket names.
     * @return list of bucket names
     * @throws IOException covers various communication issues with the NATS
     *          server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<String> getBucketNames() throws IOException, JetStreamApiException {
        List<String> buckets = new ArrayList<>();
        List<String> names = jsm.getStreamNames();
        for (String name : names) {
            if (name.startsWith(KV_STREAM_PREFIX)) {
                buckets.add(extractBucketName(name));
            }
        }
        return buckets;
    }

    /**
     * Gets the status for an existing bucket.
     * @param bucketName the bucket name to use
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return the bucket status object
     */
    public KeyValueStatus getStatus(String bucketName) throws IOException, JetStreamApiException {
        validateBucketName(bucketName, true);
        return new KeyValueStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * Get the statuses for all buckets
     * @return list of statuses
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<KeyValueStatus> getStatuses() throws IOException, JetStreamApiException {
        List<String> bucketNames = getBucketNames();
        List<KeyValueStatus> statuses = new ArrayList<>();
        for (String name : bucketNames) {
            statuses.add(new KeyValueStatus(jsm.getStreamInfo(toStreamName(name))));
        }
        return statuses;
    }


    /**
     * Deletes an existing bucket. Will throw a JetStreamApiException if the delete fails.
     * @param bucketName the stream name to use.
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void delete(String bucketName) throws IOException, JetStreamApiException {
        validateBucketName(bucketName, true);
        jsm.deleteStream(toStreamName(bucketName));
    }
}
