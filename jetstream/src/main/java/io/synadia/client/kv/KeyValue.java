package io.synadia.client.kv;

import io.nats.json.DateTimeUtils;
import io.synadia.client.api.*;
import io.synadia.client.impl.*;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static io.synadia.client.impl.JetStreamConstants.JS_SEQUENCE_TEMPORARILY_UNKNOWN;
import static io.synadia.client.impl.JetStreamConstants.JS_WRONG_LAST_SEQUENCE;
import static io.synadia.client.kv.KeyValueUtils.*;
import static io.synadia.client.utils.JsValidator.*;
import static io.synadia.client.utils.NatsConstants.DOT;
import static io.synadia.client.utils.NatsConstants.GREATER_THAN;

/**
 * Read and write access to a single Key Value bucket. A bucket is backed by a JetStream stream, and each key is
 * a subject within it, so a key's history is the stream's message history for that subject.
 * <p>Obtain an instance from the connection rather than constructing one. Keys may not contain wildcards except
 * where a method explicitly allows them, such as the {@code watch} and {@code keys} filters.
 */
public class KeyValue extends AbstractBucketFeature {

    private final String streamSubject;
    private final String readPrefix;
    private final String writePrefix;

    KeyValue(String bucketName, NatsConnection connection) throws JetStreamException, InterruptedException {
        this(bucketName, connection, null);
    }

    KeyValue(String bucketName, NatsConnection connection, KeyValueOptions kvo) throws JetStreamException, InterruptedException {
        super(bucketName, connection, kvo);
        StreamInfo si = this.jsm.getStreamInfo(streamName);

        streamSubject = toStreamSubject(bucketName);
        String readTemp = toKeyPrefix(bucketName);

        String writeTemp;
        Mirror m = si.getConfiguration().getMirror();
        if (m != null) {
            String bName = trimPrefix(m.getStreamName());
            String mExtApi = m.getExternal() == null ? null : m.getExternal().getApi();
            if (mExtApi == null) {
                writeTemp = toKeyPrefix(bName);
            }
            else {
                readTemp = toKeyPrefix(bName);
                writeTemp = mExtApi + DOT + toKeyPrefix(bName);
            }
        }
        else if (kvo == null || kvo.getJetStreamOptions().isDefaultPrefix()) {
            writeTemp = readTemp;
        }
        else {
            writeTemp = kvo.getJetStreamOptions().getPrefix() + readTemp;
        }

        readPrefix = readTemp;
        writePrefix = writeTemp;
    }

    @Override
    protected String toStreamName(String bucketName) {
        return KeyValueUtils.toStreamName(bucketName);
    }

    String readSubject(String key) {
        return readPrefix + key;
    }

    String writeSubject(String key) {
        return writePrefix + key;
    }

    /**
     * Get the name of the bucket.
     * @return the name
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * Get the entry for a key
     * when the key exists and is live (not deleted and not purged)
     * @param key the key
     * @return the KvEntry object or null if not found.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueEntry get(String key) throws JetStreamException, InterruptedException {
        return existingOnly(_get(validateNonWildcardKvKeyRequired(key)));
    }

    /**
     * Get the specific revision of an entry for a key
     * when the key exists and is live (not deleted and not purged)
     * @param key the key
     * @param revision the revision
     * @return the KvEntry object or null if not found.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueEntry get(String key, long revision) throws JetStreamException, InterruptedException {
        return existingOnly(_get(validateNonWildcardKvKeyRequired(key), revision));
    }

    KeyValueEntry existingOnly(KeyValueEntry kve) {
        return kve == null || kve.getOperation() != KeyValueOperation.PUT ? null : kve;
    }

    KeyValueEntry _get(String key) throws JetStreamException, InterruptedException {
        MessageInfo mi = _getLast(readSubject(key));
        return mi == null ? null : new KeyValueEntry(mi);
    }

    KeyValueEntry _get(String key, long revision) throws JetStreamException, InterruptedException {
        MessageInfo mi = _getBySeq(revision);
        if (mi != null) {
            KeyValueEntry kve = new KeyValueEntry(mi);
            if (key.equals(kve.getKey())) {
                return kve;
            }
        }
        return null;
    }

    /**
     * Put a byte[] as the value for a key
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, byte[] value) throws JetStreamException, InterruptedException {
        return _write(key, value, null, null).getSequenceNumber();
    }

    /**
     * Put a byte[] as the value for a key
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, String value) throws JetStreamException, InterruptedException {
        return _write(key, value.getBytes(StandardCharsets.UTF_8), null, null).getSequenceNumber();
    }

    /**
     * Put a byte[] as the value for a key
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, Number value) throws JetStreamException, InterruptedException {
        return _write(key, value.toString().getBytes(StandardCharsets.ISO_8859_1), null, null).getSequenceNumber();
    }

    /**
     * Put as the value for a key iff the key does not exist (there is no history)
     * or is deleted (history shows the key is deleted)
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long create(String key, byte[] value) throws JetStreamException, InterruptedException {
        return create(key, value, null);
    }

    /**
     * Put as the value for a key iff the key does not exist (there is no history) or is deleted, applying a
     * per-message TTL.
     * @param key the key
     * @param value the bytes of the value
     * @param messageTtl the TTL for this specific message, or null for the bucket default
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long create(String key, byte[] value, MessageTtl messageTtl) throws JetStreamException, InterruptedException {
        validateNonWildcardKvKeyRequired(key);
        try {
            return _update(key, value, 0, messageTtl);
        }
        catch (JetStreamApiException e) {
            int code = e.getApiErrorCode();
            if (code == JS_WRONG_LAST_SEQUENCE || code == JS_SEQUENCE_TEMPORARILY_UNKNOWN) {
                // must check if the last message for this subject is a delete or purge
                // if it was, it's okay to "create" it, as long as someone doesn't create in the meantime
                // which is why I use the revision, which must be greater than zero b/c I just tried zero
                KeyValueEntry kve = _get(key);
                if (kve != null && kve.getOperation() != KeyValueOperation.PUT) {
                    long revision = kve.getRevision();
                    if (revision > 0) {
                        return _update(key, value, revision, messageTtl);
                    }
                }
            }
            throw e;
        }
    }

    /**
     * Put as the value for a key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param value the bytes of the value
     * @param expectedRevision the expected last revision
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long update(String key, byte[] value, long expectedRevision) throws JetStreamException, InterruptedException {
        validateNonWildcardKvKeyRequired(key);
        return _update(key, value, expectedRevision, null);
    }

    private long _update(String key, byte[] value, long expectedRevision, MessageTtl messageTtl) throws JetStreamException, InterruptedException {
        return _write(key, value, null, getPublishOptions(expectedRevision, messageTtl)).getSequenceNumber();
    }

    /**
     * Put as the value for a key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param value the bytes of the value
     * @param expectedRevision the expected last revision
     * @return the revision number for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long update(String key, String value, long expectedRevision) throws JetStreamException, InterruptedException {
        return update(key, value.getBytes(StandardCharsets.UTF_8), expectedRevision);
    }

    /**
     * Soft deletes the key by placing a delete marker.
     * @param key the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void delete(String key) throws JetStreamException, InterruptedException {
        _write(key, null, getDeleteHeaders(), null);
    }

    /**
     * Soft deletes the key by placing a delete marker iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void delete(String key, long expectedRevision) throws JetStreamException, InterruptedException {
        _write(key, null, getDeleteHeaders(), getPublishOptions(expectedRevision, null));
    }

    /**
     * Purge all values/history from the specific key
     * @param key the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void purge(String key) throws JetStreamException, InterruptedException {
        _write(key, null, getPurgeHeaders(), null);
    }

    /**
     * Purge all values/history from the specific key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void purge(String key, long expectedRevision) throws JetStreamException, InterruptedException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(expectedRevision, null));
    }

    /**
     * Purge all values/history from the specific key
     * @param key the key
     * @param messageTtl the individual ttl for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void purge(String key, MessageTtl messageTtl) throws JetStreamException, InterruptedException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(-1, messageTtl));
    }

    /**
     * Purge all values/history from the specific key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @param messageTtl the individual ttl for the key
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public void purge(String key, long expectedRevision, MessageTtl messageTtl) throws JetStreamException, InterruptedException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(expectedRevision, messageTtl));
    }

    private PublishAck _write(String key, byte[] data, Headers h, PublishOptions popts) throws JetStreamException, InterruptedException {
        validateNonWildcardKvKeyRequired(key);
        return js.publish(NatsMessage.builder().subject(writeSubject(key)).data(data).headers(h).build(), popts);
    }

    /**
     * Watch a key for updates, delivering them to the watcher until the returned subscription is closed.
     * @param key the key, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watch(String key, KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        validateKvKeyWildcardAllowedRequired(key);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, Collections.singletonList(key), watcher, -1, watchOptions);
    }

    /**
     * Watch a key for updates starting from a specific revision.
     * @param key the key, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param fromRevision the revision to start from, or -1 to start from the latest
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watch(String key, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        validateKvKeyWildcardAllowedRequired(key);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, Collections.singletonList(key), watcher, fromRevision, watchOptions);
    }

    /**
     * Watch several keys for updates.
     * @param keys the keys, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watch(List<String> keys, KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        validateKvKeysWildcardAllowedRequired(keys);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, keys, watcher, -1, watchOptions);
    }

    /**
     * Watch several keys for updates starting from a specific revision.
     * @param keys the keys, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param fromRevision the revision to start from, or -1 to start from the latest
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watch(List<String> keys, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        validateKvKeysWildcardAllowedRequired(keys);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, keys, watcher, fromRevision, watchOptions);
    }

    /**
     * Watch every key in the bucket for updates.
     * @param watcher the watcher to receive updates
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watchAll(KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        return new KeyValueWatchSubscription(this, Collections.singletonList(GREATER_THAN), watcher, -1, watchOptions);
    }

    /**
     * Watch every key in the bucket for updates starting from a specific revision.
     * @param watcher the watcher to receive updates
     * @param fromRevision the revision to start from, or -1 to start from the latest
     * @param watchOptions the watch options to apply
     * @return the subscription, which must be closed to stop watching
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription watchAll(KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        return new KeyValueWatchSubscription(this, Collections.singletonList(GREATER_THAN), watcher, fromRevision, watchOptions);
    }

    /**
     * Get a list of the keys in a bucket.
     * @return List of keys
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if the thread is interrupted
     */
    public List<String> keys() throws JetStreamException, InterruptedException {
        return _keys(Collections.singletonList(readSubject(GREATER_THAN)));
    }

    /**
     * Get a list of the keys in a bucket matching a filter.
     * @param filter the key filter, which may contain wildcards
     * @return List of keys
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> keys(String filter) throws JetStreamException, InterruptedException {
        return _keys(Collections.singletonList(readSubject(filter)));
    }

    /**
     * Get a list of the keys in a bucket matching any of several filters.
     * @param filters the key filters, which may contain wildcards
     * @return List of keys
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> keys(List<String> filters) throws JetStreamException, InterruptedException {
        List<String> readSubjectFilters = new ArrayList<>(filters.size());
        for (String f : filters) {
            readSubjectFilters.add(readSubject(f));
        }
        return _keys(readSubjectFilters);
    }

    private List<String> _keys(List<String> readSubjectFilters) throws JetStreamException, InterruptedException {
        List<String> list = new ArrayList<>();
        visitSubject(readSubjectFilters, DeliverPolicy.LastPerSubject, true, false, m -> {
            KeyValueOperation op = getOperation(m.getHeaders());
            if (op == KeyValueOperation.PUT) {
                list.add(new BucketAndKey(m).key);
            }
        });
        return list;
    }

    /**
     * Get a list of keys in the bucket through a LinkedBlockingQueue.
     * A KeyResult with isDone being true or an exception signifies there are no more keys
     * @return the LinkedBlockingQueue from which to poll
     */
    public LinkedBlockingQueue<KeyResult> consumeKeys() {
        return _consumeKeys(Collections.singletonList(readSubject(GREATER_THAN)));
    }

    /**
     * Get a list of keys in the bucket through a LinkedBlockingQueue filtered by a
     * subject-like string, for instance "key" or "key.foo.*" or "key.&gt;"
     * A KeyResult with isDone being true or an exception signifies there are no more keys
     * @param filter the subject like key filter
     * @return the LinkedBlockingQueue from which to poll
     */
    public LinkedBlockingQueue<KeyResult> consumeKeys(String filter) {
        return _consumeKeys(Collections.singletonList(readSubject(filter)));
    }

    /**
     * Get a list of keys in the bucket through a LinkedBlockingQueue filtered by
     * subject-like strings, for instance "aaa.*", "bbb.*;"
     * A KeyResult with isDone being true or an exception signifies there are no more keys
     * @param filters the subject like key filters
     * @return the LinkedBlockingQueue from which to poll
     */
    public LinkedBlockingQueue<KeyResult> consumeKeys(List<String> filters) {
        List<String> readSubjectFilters = new ArrayList<>(filters.size());
        for (String f : filters) {
            readSubjectFilters.add(readSubject(f));
        }
        return _consumeKeys(readSubjectFilters);
    }

    private LinkedBlockingQueue<KeyResult> _consumeKeys(List<String> readSubjectFilters) {
        LinkedBlockingQueue<KeyResult> q = new LinkedBlockingQueue<>();
        nc.getOptions().getExecutor().submit( () -> {
            try {
                visitSubject(readSubjectFilters, DeliverPolicy.LastPerSubject, true, false, m -> {
                    KeyValueOperation op = getOperation(m.getHeaders());
                    if (op == KeyValueOperation.PUT) {
                        q.offer(new KeyResult(new BucketAndKey(m).key));
                    }
                });
                q.offer(new KeyResult());
            }
            catch (JetStreamException e) {
                q.offer(new KeyResult(e));
            }
            catch (InterruptedException e) {
                q.offer(new KeyResult(e));
                Thread.currentThread().interrupt();
            }
        });

        return q;
    }

    /**
     * Get the history (list of KeyValueEntry) for a key
     * @param key the key
     * @return List of KvEntry
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if the thread is interrupted
     */
    public List<KeyValueEntry> history(String key) throws JetStreamException, InterruptedException {
        validateNonWildcardKvKeyRequired(key);
        List<KeyValueEntry> list = new ArrayList<>();
        visitSubject(readSubject(key), DeliverPolicy.All, false, true, m -> list.add(new KeyValueEntry(m)));
        return list;
    }

    /**
     * Remove history from all keys that currently are deleted or purged
     * with using a default KeyValuePurgeOptions
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if the thread is interrupted
     */
    public void purgeDeletes() throws JetStreamException, InterruptedException {
        purgeDeletes(null);
    }

    /**
     * Remove history from all keys that currently are deleted or purged, considering options.
     * @param options the purge options
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if the thread is interrupted
     */
    public void purgeDeletes(KeyValuePurgeOptions options) throws JetStreamException, InterruptedException {
        long dmThresh = options == null
            ? KeyValuePurgeOptions.DEFAULT_THRESHOLD_MILLIS
            : options.getDeleteMarkersThresholdMillis();

        ZonedDateTime limit;
        if (dmThresh < 0) {
            limit = DateTimeUtils.fromNow(600000); // long enough in the future to clear all
        }
        else if (dmThresh == 0) {
            limit = DateTimeUtils.fromNow(KeyValuePurgeOptions.DEFAULT_THRESHOLD_MILLIS);
        }
        else {
            limit = DateTimeUtils.fromNow(-dmThresh);
        }

        List<String> keep0List = new ArrayList<>();
        List<String> keep1List = new ArrayList<>();
        visitSubject(streamSubject, DeliverPolicy.LastPerSubject, true, false, m -> {
            KeyValueEntry kve = new KeyValueEntry(m);
            if (kve.getOperation() != KeyValueOperation.PUT) {
                if (kve.getCreated().isAfter(limit)) {
                    keep1List.add(new BucketAndKey(m).key);
                }
                else {
                    keep0List.add(new BucketAndKey(m).key);
                }
            }
        });

        for (String key : keep0List) {
            jsm.purgeStream(streamName, PurgeOptions.subject(readSubject(key)));
        }

        for (String key : keep1List) {
            PurgeOptions po = PurgeOptions.builder()
                .subject(readSubject(key))
                .keep(1)
                .build();
            jsm.purgeStream(streamName, po);
        }
    }

    /**
     * Get the KeyValueStatus object
     * @return the status object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if the thread is interrupted
     */
    public KeyValueStatus getStatus() throws JetStreamException, InterruptedException {
        return new KeyValueStatus(jsm.getStreamInfo(streamName));
    }
}
