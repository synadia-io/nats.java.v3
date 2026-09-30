package io.synadia.client.kv;

import io.synadia.client.api.*;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.NatsConnection;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.impl.JetStreamConstants.SERVER_DEFAULT_DUPLICATE_WINDOW_MS;
import static io.synadia.client.kv.KeyValueUtils.*;
import static io.synadia.client.utils.JsValidator.validateBucketName;

/**
 * Create, inspect and delete Key Value buckets. For reading and writing the keys themselves, use
 * {@link KeyValue KeyValue}.
 */
public class KeyValueManagement {
    private final NatsConnection nc;
    private final KeyValueOptions kvo;
    private final JetStreamManagement jsm;

    /**
     * Construct management for the connection, with default options.
     * @param connection the connection
     */
    public KeyValueManagement(@NonNull NatsConnection connection) {
        this(connection, null);
    }

    /**
     * Construct management for the connection with the given options.
     * @param connection the connection
     * @param kvo the key value options, or null for defaults
     */
    public KeyValueManagement(@NonNull NatsConnection connection, @Nullable KeyValueOptions kvo) {
        this.nc = connection;
        this.kvo = kvo;
        this.jsm = new JetStreamManagement(connection, kvo == null ? null : kvo.getJetStreamOptions());
    }

    /**
     * Gets a context for working with a Key Value bucket
     * @param bucketName the bucket name
     * @return a KeyValue instance.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValue keyValue(String bucketName) throws JetStreamException, InterruptedException {
        validateBucketName(bucketName, true);
        return new KeyValue(bucketName, nc, kvo);
    }

    /**
     * Create a key value store.
     * @param creator the key value configuration creator
     * @return the key value Status
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueStatus create(KeyValueCreator creator) throws JetStreamException, InterruptedException {
        return new KeyValueStatus(jsm.addStream(buildStreamCreator(creator)));
    }

    /**
     * Update a key value store configuration. Storage type cannot change.
     * @param creator the key value configuration
     * @return the Key Value Status
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueStatus update(KeyValueCreator creator) throws JetStreamException, InterruptedException {
        return new KeyValueStatus(jsm.updateStream(buildStreamCreator(creator)));
    }

    // package scope so can be tested
    static @NonNull StreamCreator buildStreamCreator(KeyValueCreator creator) {
        // these are done here, and not in the KeyValueCreator constructor on purpose
        // because this ensures this is the last time the value is set
        StreamCreator sc = creator.getStreamCreatorCopy()
            .discardPolicy(DiscardPolicy.New)
            .allowRollup(true)
            .allowDirect(true)
            .denyDelete(true);

        // The KeyValueCreator just takes raw bucket names, but mirrors and sources
        // need to point at the actual stream name, which is what this section does
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

        // this code is to manage the duplicate window
        // it's done here. In v2 it was done in the builder like all this other stuff
        Duration ttlMsDur = creator.getTtl();
        long ttlMs = ttlMsDur == null ? 0 : ttlMsDur.toMillis();
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> getBucketNames() throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return the bucket status object
     */
    public KeyValueStatus getStatus(String bucketName) throws JetStreamException, InterruptedException {
        validateBucketName(bucketName, true);
        return new KeyValueStatus(jsm.getStreamInfo(toStreamName(bucketName)));
    }

    /**
     * Get the statuses for all buckets
     * @return list of statuses
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<KeyValueStatus> getStatuses() throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void delete(String bucketName) throws JetStreamException, InterruptedException {
        validateBucketName(bucketName, true);
        jsm.deleteStream(toStreamName(bucketName));
    }
}
