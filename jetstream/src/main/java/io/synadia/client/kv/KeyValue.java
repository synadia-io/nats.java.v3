package io.synadia.client.kv;

import io.nats.json.DateTimeUtils;
import io.synadia.client.MessageTtl;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.Mirror;
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.impl.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static io.synadia.client.impl.JetStreamConstants.JS_SEQUENCE_TEMPORARILY_UNKNOWN;
import static io.synadia.client.impl.JetStreamConstants.JS_WRONG_LAST_SEQUENCE;
import static io.synadia.client.kv.KeyValueUtils.*;
import static io.synadia.client.testutils.JsValidator.*;
import static io.synadia.client.testutils.NatsConstants.DOT;
import static io.synadia.client.testutils.NatsConstants.GREATER_THAN;

public class KeyValue extends AbstractBucketFeature {

    private final String streamSubject;
    private final String readPrefix;
    private final String writePrefix;

    KeyValue(String bucketName, NatsConnection connection, KeyValueOptions kvo) throws IOException {
        super(bucketName, connection, kvo);
        StreamInfo si;
        try {
             si = this.jsm.getStreamInfo(streamName);
        } catch (JetStreamApiException e) {
            // can't throw directly, that would be a breaking change
            throw new IOException(e);
        }

        streamSubject = toStreamSubject(bucketName);
        String readTemp = toKeyPrefix(bucketName);

        String writeTemp;
        Mirror m = si.getConfiguration().getMirror();
        if (m != null) {
            String bName = trimPrefix(m.getName());
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
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueEntry get(String key) throws IOException, JetStreamApiException {
        return existingOnly(_get(validateNonWildcardKvKeyRequired(key)));
    }

    /**
     * Get the specific revision of an entry for a key
     * when the key exists and is live (not deleted and not purged)
     * @param key the key
     * @param revision the revision
     * @return the KvEntry object or null if not found.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public KeyValueEntry get(String key, long revision) throws IOException, JetStreamApiException {
        return existingOnly(_get(validateNonWildcardKvKeyRequired(key), revision));
    }

    KeyValueEntry existingOnly(KeyValueEntry kve) {
        return kve == null || kve.getOperation() != KeyValueOperation.PUT ? null : kve;
    }

    KeyValueEntry _get(String key) throws IOException, JetStreamApiException {
        MessageInfo mi = _getLast(readSubject(key));
        return mi == null ? null : new KeyValueEntry(mi);
    }

    KeyValueEntry _get(String key, long revision) throws IOException, JetStreamApiException {
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
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, byte[] value) throws IOException, JetStreamApiException {
        return _write(key, value, null, null).getSequenceNumber();
    }

    /**
     * Put a byte[] as the value for a key
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, String value) throws IOException, JetStreamApiException {
        return _write(key, value.getBytes(StandardCharsets.UTF_8), null, null).getSequenceNumber();
    }

    /**
     * Put a byte[] as the value for a key
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long put(String key, Number value) throws IOException, JetStreamApiException {
        return _write(key, value.toString().getBytes(StandardCharsets.ISO_8859_1), null, null).getSequenceNumber();
    }

    /**
     * Put as the value for a key iff the key does not exist (there is no history)
     * or is deleted (history shows the key is deleted)
     * @param key the key
     * @param value the bytes of the value
     * @return the revision number for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long create(String key, byte[] value) throws IOException, JetStreamApiException {
        return create(key, value, null);
    }

    public long create(String key, byte[] value, MessageTtl messageTtl) throws IOException, JetStreamApiException {
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
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long update(String key, byte[] value, long expectedRevision) throws IOException, JetStreamApiException {
        validateNonWildcardKvKeyRequired(key);
        return _update(key, value, expectedRevision, null);
    }

    private long _update(String key, byte[] value, long expectedRevision, MessageTtl messageTtl) throws IOException, JetStreamApiException {
        return _write(key, value, null, getPublishOptions(expectedRevision, messageTtl)).getSequenceNumber();
    }

    /**
     * Put as the value for a key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param value the bytes of the value
     * @param expectedRevision the expected last revision
     * @return the revision number for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public long update(String key, String value, long expectedRevision) throws IOException, JetStreamApiException {
        return update(key, value.getBytes(StandardCharsets.UTF_8), expectedRevision);
    }

    /**
     * Soft deletes the key by placing a delete marker.
     * @param key the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void delete(String key) throws IOException, JetStreamApiException {
        _write(key, null, getDeleteHeaders(), null);
    }

    /**
     * Soft deletes the key by placing a delete marker iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void delete(String key, long expectedRevision) throws IOException, JetStreamApiException {
        _write(key, null, getDeleteHeaders(), getPublishOptions(expectedRevision, null));
    }

    /**
     * Purge all values/history from the specific key
     * @param key the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void purge(String key) throws IOException, JetStreamApiException {
        _write(key, null, getPurgeHeaders(), null);
    }

    /**
     * Purge all values/history from the specific key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void purge(String key, long expectedRevision) throws IOException, JetStreamApiException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(expectedRevision, null));
    }

    /**
     * Purge all values/history from the specific key
     * @param key the key
     * @param messageTtl the individual ttl for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void purge(String key, MessageTtl messageTtl) throws IOException, JetStreamApiException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(-1, messageTtl));
    }

    /**
     * Purge all values/history from the specific key iff the key exists and its last revision matches the expected
     * @param key the key
     * @param expectedRevision the expected last revision
     * @param messageTtl the individual ttl for the key
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public void purge(String key, long expectedRevision, MessageTtl messageTtl) throws IOException, JetStreamApiException {
        _write(key, null, getPurgeHeaders(), getPublishOptions(expectedRevision, messageTtl));
    }

    private PublishAck _write(String key, byte[] data, Headers h, PublishOptions popts) throws IOException, JetStreamApiException {
        validateNonWildcardKvKeyRequired(key);
        return js.publish(NatsMessage.builder().subject(writeSubject(key)).data(data).headers(h).build(), popts);
    }

    public KeyValueWatchSubscription watch(String key, KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        validateKvKeyWildcardAllowedRequired(key);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, Collections.singletonList(key), watcher, -1, watchOptions);
    }

    public KeyValueWatchSubscription watch(String key, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        validateKvKeyWildcardAllowedRequired(key);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, Collections.singletonList(key), watcher, fromRevision, watchOptions);
    }

    public KeyValueWatchSubscription watch(List<String> keys, KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        validateKvKeysWildcardAllowedRequired(keys);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, keys, watcher, -1, watchOptions);
    }

    public KeyValueWatchSubscription watch(List<String> keys, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        validateKvKeysWildcardAllowedRequired(keys);
        validateNotNull(watcher, "Watcher is required");
        return new KeyValueWatchSubscription(this, keys, watcher, fromRevision, watchOptions);
    }

    public KeyValueWatchSubscription watchAll(KeyValueWatcher watcher, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        return new KeyValueWatchSubscription(this, Collections.singletonList(GREATER_THAN), watcher, -1, watchOptions);
    }

    public KeyValueWatchSubscription watchAll(KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        return new KeyValueWatchSubscription(this, Collections.singletonList(GREATER_THAN), watcher, fromRevision, watchOptions);
    }

    /**
     * Get a list of the keys in a bucket.
     * @return List of keys
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public List<String> keys() throws IOException, JetStreamApiException, InterruptedException {
        return _keys(Collections.singletonList(readSubject(GREATER_THAN)));
    }

    public List<String> keys(String filter) throws IOException, JetStreamApiException, InterruptedException {
        return _keys(Collections.singletonList(readSubject(filter)));
    }

    public List<String> keys(List<String> filters) throws IOException, JetStreamApiException, InterruptedException {
        List<String> readSubjectFilters = new ArrayList<>(filters.size());
        for (String f : filters) {
            readSubjectFilters.add(readSubject(f));
        }
        return _keys(readSubjectFilters);
    }

    private List<String> _keys(List<String> readSubjectFilters) throws IOException, JetStreamApiException, InterruptedException {
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
            catch (IOException | JetStreamApiException e) {
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
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public List<KeyValueEntry> history(String key) throws IOException, JetStreamApiException, InterruptedException {
        validateNonWildcardKvKeyRequired(key);
        List<KeyValueEntry> list = new ArrayList<>();
        visitSubject(readSubject(key), DeliverPolicy.All, false, true, m -> list.add(new KeyValueEntry(m)));
        return list;
    }

    /**
     * Remove history from all keys that currently are deleted or purged
     * with using a default KeyValuePurgeOptions
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public void purgeDeletes() throws IOException, JetStreamApiException, InterruptedException {
        purgeDeletes(null);
    }

    /**
     * Remove history from all keys that currently are deleted or purged, considering options.
     * @param options the purge options
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public void purgeDeletes(KeyValuePurgeOptions options) throws IOException, JetStreamApiException, InterruptedException {
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
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public KeyValueStatus getStatus() throws IOException, JetStreamApiException, InterruptedException {
        return new KeyValueStatus(jsm.getStreamInfo(streamName));
    }
}
