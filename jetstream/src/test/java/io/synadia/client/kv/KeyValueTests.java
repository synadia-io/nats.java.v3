package io.synadia.client.kv;

import io.synadia.client.*;
import io.synadia.client.api.*;
import io.synadia.client.impl.*;
import io.synadia.client.utils.Debug;
import io.synadia.client.utils.VersionUtils;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.impl.JetStreamConstants.SERVER_DEFAULT_DUPLICATE_WINDOW_MS;
import static io.synadia.client.impl.JetStreamOptions.DEFAULT_JS_OPTIONS;
import static io.synadia.client.kv.KeyValuePurgeOptions.DEFAULT_THRESHOLD_MILLIS;
import static io.synadia.client.kv.KeyValuePurgeOptions.NO_THRESHOLD_MILLIS;
import static io.synadia.client.kv.KeyValueWatchOption.*;
import static io.synadia.client.utils.NatsConstants.DOT;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.TestBase.*;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class KeyValueTests {

    @Test
    public void testWorkflow() throws Exception {
        long now = ZonedDateTime.now().toEpochSecond();
        String byteKey = "key.byte" + random();
        String stringKey = "key.string" + random();
        String longKey = "key.long" + random();
        String notFoundKey = "notFound" + random();
        String byteValue1 = "Byte Value 1";
        String byteValue2 = "Byte Value 2";
        String stringValue1 = "String Value 1";
        String stringValue2 = "String Value 2";

        runInShared(VersionUtils::atLeast2_10, nc -> {
            new KeyValueManagement(nc); // coverage
            new KeyValueManagement(nc, KeyValueOptions.builder(DEFAULT_JS_OPTIONS).build()); // coverage

            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                Map<String, String> metadata = new HashMap<>();
                metadata.put(META_KEY, META_VALUE);

                // create the bucket
                String bucket = random();
                String desc = random();
                KeyValueStatus status = kvCtx.kvCreate(bucket,
                    cr -> cr.description(desc)
                        .maxHistoryPerKey(3)
                        .metadata(metadata));
                assertInitialStatus(status, bucket, desc);

                // get the kv context for the specific bucket
                KeyValue kv = kvm.keyValue(bucket);
                assertEquals(bucket, kv.getBucketName());
                status = kv.getStatus();
                assertInitialStatus(status, bucket, desc);

                // Put some keys. Each key is put in a subject in the bucket (stream)
                // The put returns the sequence number in the bucket (stream)
                assertEquals(1, kv.put(byteKey, byteValue1.getBytes()));
                assertEquals(2, kv.put(stringKey, stringValue1));
                assertEquals(3, kv.put(longKey, 1));

                // retrieve the values. all types are stored as bytes
                // so you can always get the bytes directly
                KeyValueEntry entry = kv.get(byteKey);
                assertNotNull(entry);
                assertNotNull(entry.getValue());
                assertEquals(byteValue1, new String(entry.getValue()));

                entry = kv.get(stringKey);
                assertNotNull(entry);
                assertNotNull(entry.getValue());
                assertEquals(stringValue1, new String(entry.getValue()));

                entry = kv.get(longKey);
                assertNotNull(entry);
                assertNotNull(entry.getValue());
                assertEquals("1", new String(entry.getValue()));

                // if you know the value is not binary and can safely be read
                // as a UTF-8 string, the getStringValue method is ok to use
                assertEquals(byteValue1, kv.get(byteKey).getValueAsString());
                assertEquals(stringValue1, kv.get(stringKey).getValueAsString());
                assertEquals("1", kv.get(longKey).getValueAsString());

                // if you know the value is a long, you can use
                // the getLongValue method
                // if it's not a number a NumberFormatException is thrown
                assertEquals(1, kv.get(longKey).getValueAsLong());
                assertThrows(NumberFormatException.class, () -> kv.get(stringKey).getValueAsLong());

                // going to manually track history for verification later
                List<Object> byteHistory = new ArrayList<>();
                List<Object> stringHistory = new ArrayList<>();
                List<Object> longHistory = new ArrayList<>();

                // entry gives detail about the latest entry of the key
                byteHistory.add(
                    assertEntry(bucket, byteKey, KeyValueOperation.PUT, 1, byteValue1, now, kv.get(byteKey)));

                stringHistory.add(
                    assertEntry(bucket, stringKey, KeyValueOperation.PUT, 2, stringValue1, now, kv.get(stringKey)));

                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 3, "1", now, kv.get(longKey)));

                // history gives detail about the key
                assertHistory(byteHistory, kv.history(byteKey));
                assertHistory(stringHistory, kv.history(stringKey));
                assertHistory(longHistory, kv.history(longKey));

                // let's check the bucket info
                status = kvm.getStatus(bucket);
                assertState(status, 3, 3);

                // delete a key. Its entry will still exist, but its value is null
                kv.delete(byteKey);
                assertNull(kv.get(byteKey));
                byteHistory.add(KeyValueOperation.DELETE);
                assertHistory(byteHistory, kv.history(byteKey));

                // hashCode coverage
                assertEquals(byteHistory.get(0).hashCode(), byteHistory.get(0).hashCode());
                assertNotEquals(byteHistory.get(0).hashCode(), byteHistory.get(1).hashCode());

                // let's check the bucket info
                status = kvm.getStatus(bucket);
                assertState(status, 4, 4);

                // if the key has been deleted no entry is returned
                assertNull(kv.get(byteKey));

                // if the key does not exist (no history) there is no entry
                assertNull(kv.get(notFoundKey));

                // Update values. You can even update a deleted key
                assertEquals(5, kv.put(byteKey, byteValue2.getBytes()));
                assertEquals(6, kv.put(stringKey, stringValue2));
                assertEquals(7, kv.put(longKey, 2));

                // values after updates
                entry = kv.get(byteKey);
                assertNotNull(entry);
                assertNotNull(entry.getValue());
                assertEquals(byteValue2, new String(entry.getValue()));
                assertEquals(stringValue2, kv.get(stringKey).getValueAsString());
                assertEquals(2, kv.get(longKey).getValueAsLong());

                // entry and history after update
                byteHistory.add(
                    assertEntry(bucket, byteKey, KeyValueOperation.PUT, 5, byteValue2, now, kv.get(byteKey)));
                assertHistory(byteHistory, kv.history(byteKey));

                stringHistory.add(
                    assertEntry(bucket, stringKey, KeyValueOperation.PUT, 6, stringValue2, now, kv.get(stringKey)));
                assertHistory(stringHistory, kv.history(stringKey));

                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 7, "2", now, kv.get(longKey)));
                assertHistory(longHistory, kv.history(longKey));

                // let's check the bucket info
                status = kvm.getStatus(bucket);
                assertState(status, 7, 7);

                // make sure it only keeps the correct amount of history
                assertEquals(8, kv.put(longKey, 3));
                assertEquals(3, kv.get(longKey).getValueAsLong());

                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 8, "3", now, kv.get(longKey)));
                assertHistory(longHistory, kv.history(longKey));

                status = kvm.getStatus(bucket);
                assertState(status, 8, 8);

                // this would be the 4th entry for the longKey
                // sp the total records will stay the same
                assertEquals(9, kv.put(longKey, 4));
                assertEquals(4, kv.get(longKey).getValueAsLong());

                // history only retains 3 records
                longHistory.remove(0);
                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 9, "4", now, kv.get(longKey)));
                assertHistory(longHistory, kv.history(longKey));

                // record count does not increase
                status = kvm.getStatus(bucket);
                assertState(status, 8, 9);

                assertKeys(kv.keys(), byteKey, stringKey, longKey);
                assertKeys(kv.keys("key.>"), byteKey, stringKey, longKey);
                assertKeys(kv.keys(byteKey), byteKey);
                assertKeys(kv.keys(Arrays.asList(longKey, stringKey)), longKey, stringKey);

                assertKeys(getKeysFromQueue(kv.consumeKeys()), byteKey, stringKey, longKey);
                assertKeys(getKeysFromQueue(kv.consumeKeys("key.>")), byteKey, stringKey, longKey);
                assertKeys(getKeysFromQueue(kv.consumeKeys(byteKey)), byteKey);
                assertKeys(getKeysFromQueue(kv.consumeKeys(Arrays.asList(longKey, stringKey))), longKey, stringKey);

                // purge
                kv.purge(longKey);
                longHistory.clear();
                assertNull(kv.get(longKey));
                longHistory.add(KeyValueOperation.PURGE);
                assertHistory(longHistory, kv.history(longKey));

                status = kvm.getStatus(bucket);
                assertState(status, 6, 10);

                // only 2 keys now
                assertKeys(kv.keys(), byteKey, stringKey);
                assertKeys(getKeysFromQueue(kv.consumeKeys()), byteKey, stringKey);

                kv.purge(byteKey);
                byteHistory.clear();
                assertNull(kv.get(byteKey));
                byteHistory.add(KeyValueOperation.PURGE);
                assertHistory(byteHistory, kv.history(byteKey));

                status = kvm.getStatus(bucket);
                assertState(status, 4, 11);

                // only 1 key now
                assertKeys(kv.keys(), stringKey);
                assertKeys(getKeysFromQueue(kv.consumeKeys()), stringKey);

                kv.purge(stringKey);
                stringHistory.clear();
                assertNull(kv.get(stringKey));
                stringHistory.add(KeyValueOperation.PURGE);
                assertHistory(stringHistory, kv.history(stringKey));

                status = kvm.getStatus(bucket);
                assertState(status, 3, 12);

                // no more keys left
                assertKeys(kv.keys());
                assertKeys(getKeysFromQueue(kv.consumeKeys()));

                // clear things
                KeyValuePurgeOptions kvpo = KeyValuePurgeOptions.builder().deleteMarkersNoThreshold().build();
                kv.purgeDeletes(kvpo);
                status = kvm.getStatus(bucket);
                assertState(status, 0, 12);

                longHistory.clear();
                assertHistory(longHistory, kv.history(longKey));

                stringHistory.clear();
                assertHistory(stringHistory, kv.history(stringKey));

                // put some more
                assertEquals(13, kv.put(longKey, 110));
                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 13, "110", now, kv.get(longKey)));

                assertEquals(14, kv.put(longKey, 111));
                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 14, "111", now, kv.get(longKey)));

                assertEquals(15, kv.put(longKey, 112));
                longHistory.add(
                    assertEntry(bucket, longKey, KeyValueOperation.PUT, 15, "112", now, kv.get(longKey)));

                assertEquals(16, kv.put(stringKey, stringValue1));
                stringHistory.add(
                    assertEntry(bucket, stringKey, KeyValueOperation.PUT, 16, stringValue1, now, kv.get(stringKey)));

                assertEquals(17, kv.put(stringKey, stringValue2));
                stringHistory.add(
                    assertEntry(bucket, stringKey, KeyValueOperation.PUT, 17, stringValue2, now, kv.get(stringKey)));

                assertHistory(longHistory, kv.history(longKey));
                assertHistory(stringHistory, kv.history(stringKey));

                status = kvm.getStatus(bucket);
                assertState(status, 5, 17);

                // delete the bucket
                kvm.delete(bucket);

                assertThrows(JetStreamApiException.class, () -> kvm.delete(bucket));
                assertThrows(JetStreamApiException.class, () -> kvm.getStatus(bucket));

                assertEquals(0, kvm.getBucketNames().size());
            }
        });
    }

    private static void assertState(KeyValueStatus status, int entryCount, int lastSeq) {
        assertEquals(entryCount, status.getEntryCount());
        assertEquals(lastSeq, status.getBackingStreamInfo().getStreamState().getLastSequence());
        assertEquals(status.getByteCount(), status.getBackingStreamInfo().getStreamState().getByteCount());
    }

    private void assertInitialStatus(KeyValueStatus status, String bucket, String desc) {
        KeyValueConfiguration kvc = status.getConfiguration();
        assertEquals(bucket, status.getBucketName());
        assertEquals(bucket, kvc.getBucketName());
        assertEquals(desc, status.getDescription());
        assertEquals(desc, kvc.getDescription());
        assertEquals(KeyValueUtils.toStreamName(bucket), kvc.getBackingConfig().getName());
        assertEquals(3, status.getMaxHistoryPerKey());
        assertEquals(3, kvc.getMaxHistoryPerKey());
        assertEquals(-1, status.getMaxBucketSize());
        assertEquals(-1, kvc.getMaxBucketSize());
        assertEquals(-1, status.getMaxValueSize());
        assertEquals(-1, kvc.getMaxValueSize());
        assertEquals(Duration.ZERO, status.getTtl());
        assertEquals(Duration.ZERO, kvc.getTtl());
        assertEquals(StorageType.Memory, status.getStorageType());
        assertEquals(StorageType.Memory, kvc.getStorageType());
        assertNull(status.getPlacement());
        assertNull(status.getRepublish());
        assertEquals(1, status.getReplicas());
        assertEquals(1, kvc.getReplicas());
        assertEquals(0, status.getEntryCount());
        assertEquals("JetStream", status.getBackingStore());
        assertNotNull(status.getConfiguration()); // coverage
        assertNotNull(status.getConfiguration().toString()); // coverage
        assertNotNull(status.toString()); // coverage
        assertTrue(status.toString().contains(bucket));
        assertTrue(status.toString().contains(desc));

        assertMetaData(status.getMetadata());
    }

    @Test
    public void testGetRevision() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                String bucket = random();
                kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(2));

                String key = random();
                KeyValue kv = kvm.keyValue(bucket);
                long seq1 = kv.put(key, 1);
                long seq2 = kv.put(key, 2);
                long seq3 = kv.put(key, 3);

                KeyValueEntry kve = kv.get(key);
                assertNotNull(kve);
                assertEquals(3, kve.getValueAsLong());

                kve = kv.get(key, seq3);
                assertNotNull(kve);
                assertEquals(3, kve.getValueAsLong());

                kve = kv.get(key, seq2);
                assertNotNull(kve);
                assertEquals(2, kve.getValueAsLong());

                kve = kv.get(key, seq1);
                assertNull(kve);

                kve = kv.get("notkey", seq3);
                assertNull(kve);
            }
        });
    }

    @Test
    public void testKeys() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                String bucket = random();
                kvCtx.kvCreate(bucket);

                KeyValue kv = kvm.keyValue(bucket);
                for (int x = 1; x <= 10; x++) {
                    kv.put("k" + x, x);
                }

                List<String> keys = kv.keys();
                assertEquals(10, keys.size());
                for (int x = 1; x <= 10; x++) {
                    assertTrue(keys.contains("k" + x));
                }

                keys = getKeysFromQueue(kv.consumeKeys());
                assertEquals(10, keys.size());
                for (int x = 1; x <= 10; x++) {
                    assertTrue(keys.contains("k" + x));
                }

                kv.delete("k1");
                kv.delete("k3");
                kv.delete("k5");
                kv.purge("k7");
                kv.purge("k9");

                keys = kv.keys();
                assertEquals(5, keys.size());
                keys = getKeysFromQueue(kv.consumeKeys());
                assertEquals(5, keys.size());

                for (int x = 2; x <= 10; x += 2) {
                    assertTrue(keys.contains("k" + x));
                }

                String keyWithDot = "part1.part2.part3";
                kv.put(keyWithDot, "key has dot");
                KeyValueEntry kve = kv.get(keyWithDot);
                assertEquals(keyWithDot, kve.getKey());

                for (int x = 1; x <= 500; x++) {
                    kv.put("x" + x, x);
                }

                keys = kv.keys();
                assertEquals(506, keys.size()); // 506 because there are left over keys from other part of test
                for (int x = 1; x <= 500; x++) {
                    assertTrue(keys.contains("x" + x));
                }

                keys = getKeysFromQueue(kv.consumeKeys());
                assertEquals(506, keys.size());
                for (int x = 1; x <= 500; x++) {
                    assertTrue(keys.contains("x" + x));
                }
            }
        });
    }

    private static List<String> getKeysFromQueue(LinkedBlockingQueue<KeyResult> q) {
        List<String> keys = new ArrayList<>();
        try {
            boolean notDone = true;
            do {
                KeyResult r = q.poll(100, TimeUnit.SECONDS);
                if (r != null) {
                    if (r.isDone()) {
                        notDone = false;
                    }
                    else {
                        keys.add(r.getKey());
                    }
                }
            }
            while (notDone);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return keys;
    }

    @Test
    public void testMaxHistoryPerKey() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                String bucket1 = random();
                String bucket2 = random();

                // default maxHistoryPerKey is 1
                kvCtx.kvCreate(bucket1);
                KeyValue kv = kvm.keyValue(bucket1);
                String key = random();
                kv.put(key, 1);
                kv.put(key, 2);

                List<KeyValueEntry> history = kv.history(key);
                assertEquals(1, history.size());
                assertEquals(2, history.get(0).getValueAsLong());

                kvCtx.kvCreate(bucket2, cr -> cr.maxHistoryPerKey(2));

                key = random();
                kv = kvm.keyValue(bucket2);
                kv.put(key, 1);
                kv.put(key, 2);
                kv.put(key, 3);

                history = kv.history(key);
                assertEquals(2, history.size());
                assertEquals(2, history.get(0).getValueAsLong());
                assertEquals(3, history.get(1).getValueAsLong());
            }
        });
    }

    @Test
    public void testCreateUpdate() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                String bucket = random();

                // doesn't exist yet
                assertThrows(JetStreamApiException.class, () -> kvm.getStatus(bucket));

                KeyValueStatus kvs = kvCtx.kvCreate(bucket);

                assertEquals(bucket, kvs.getBucketName());
                assertNull(kvs.getDescription());
                assertEquals(1, kvs.getMaxHistoryPerKey());
                assertEquals(-1, kvs.getMaxBucketSize());
                assertEquals(-1, kvs.getMaxValueSize());
                assertEquals(Duration.ZERO, kvs.getTtl());
                assertEquals(StorageType.Memory, kvs.getStorageType());
                assertEquals(1, kvs.getReplicas());
                assertEquals(0, kvs.getEntryCount());
                assertEquals("JetStream", kvs.getBackingStore());

                String key = random();
                KeyValue kv = kvm.keyValue(bucket);
                kv.put(key, 1);
                kv.put(key, 2);

                List<KeyValueEntry> history = kv.history(key);
                assertEquals(1, history.size());
                assertEquals(2, history.get(0).getValueAsLong());

                boolean compression = VersionUtils.atLeast2_10();
                String desc = random();
                KeyValueCreator kvc = new KeyValueCreator(bucket)
                    .storageType(StorageType.Memory)
                    .description(desc)
                    .maxHistoryPerKey(3)
                    .maxBucketSize(10_000)
                    .maximumValueSize(100)
                    .ttl(Duration.ofHours(1))
                    .compression(compression);

                kvs = kvm.update(kvc);

                assertEquals(bucket, kvs.getBucketName());
                assertEquals(desc, kvs.getDescription());
                assertEquals(3, kvs.getMaxHistoryPerKey());
                assertEquals(10_000, kvs.getMaxBucketSize());
                assertEquals(100, kvs.getMaxValueSize());
                assertEquals(Duration.ofHours(1), kvs.getTtl());
                assertEquals(StorageType.Memory, kvs.getStorageType());
                assertEquals(1, kvs.getReplicas());
                assertEquals(1, kvs.getEntryCount());
                assertEquals("JetStream", kvs.getBackingStore());
                assertEquals(compression, kvs.isCompressed());

                history = kv.history(key);
                assertEquals(1, history.size());
                assertEquals(2, history.get(0).getValueAsLong());

                KeyValueCreator kvcStor = kvc.storageType(StorageType.File);
                assertThrows(JetStreamApiException.class, () -> kvm.update(kvcStor));
            }
        });
    }

    @Test
    public void testHistoryDeletePurge() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                // create bucket
                String bucket = random();
                kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(64));

                KeyValue kv = kvm.keyValue(bucket);
                String key = random();
                kv.put(key, "a");
                kv.put(key, "b");
                kv.put(key, "c");
                List<KeyValueEntry> list = kv.history(key);
                assertEquals(3, list.size());

                kv.delete(key);
                list = kv.history(key);
                assertEquals(4, list.size());

                kv.purge(key);
                list = kv.history(key);
                assertEquals(1, list.size());
            }
        });
    }

    @Test
    public void testAtomicDeleteAtomicPurge() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                // create bucket
                String bucket = random();
                kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(64));

                KeyValue kv = kvm.keyValue(bucket);
                String key = random();
                kv.put(key, "a");
                kv.put(key, "b");
                kv.put(key, "c");
                assertEquals(3, kv.get(key).getRevision());

                // Delete wrong revision rejected
                assertThrows(JetStreamApiException.class, () -> kv.delete(key, 1));

                // Correct revision writes tombstone and bumps revision
                kv.delete(key, 3);

                assertHistory(Arrays.asList(
                        kv.get(key, 1L),
                        kv.get(key, 2L),
                        kv.get(key, 3L),
                        KeyValueOperation.DELETE),
                    kv.history(key));

                // Wrong revision rejected again
                assertThrows(JetStreamApiException.class, () -> kv.delete(key, 3));

                // Delete is idempotent: two consecutive tombstones
                kv.delete(key, 4);

                assertHistory(Arrays.asList(
                        kv.get(key, 1L),
                        kv.get(key, 2L),
                        kv.get(key, 3L),
                        KeyValueOperation.DELETE,
                        KeyValueOperation.DELETE),
                    kv.history(key));

                // Purge wrong revision rejected
                assertThrows(JetStreamApiException.class, () -> kv.purge(key, 1));

                // Correct revision writes roll-up purge tombstone
                kv.purge(key, 5);

                assertHistory(Collections.singletonList(KeyValueOperation.PURGE), kv.history(key));
            }
        });
    }

    @Test
    public void testPurgeDeletes() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                // create bucket
                String bucket = random();
                kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(64));

                KeyValue kv = kvm.keyValue(bucket);
                String keyA = random();
                String keyB = random();
                String keyC = random();
                String keyD = random();
                kv.put(keyA, "a");
                kv.delete(keyA);
                kv.put(keyB, "b");
                kv.put(keyC, "c");
                kv.put(keyD, "d");
                kv.purge(keyD);

                assertPurgeDeleteEntries(kvCtx.js, bucket, new String[]{"a", null, "b", "c", null});

                // default purge deletes uses the default threshold
                // so no markers will be deleted
                kv.purgeDeletes();
                assertPurgeDeleteEntries(kvCtx.js, bucket, new String[]{null, "b", "c", null});

                // deleteMarkersThreshold of 0 the default threshold
                // so no markers will be deleted
                kv.purgeDeletes(KeyValuePurgeOptions.builder().deleteMarkersThreshold(0).build());
                assertPurgeDeleteEntries(kvCtx.js, bucket, new String[]{"b", "c"});

                // no threshold causes all to be removed
                kv.purgeDeletes(KeyValuePurgeOptions.builder().deleteMarkersNoThreshold().build());
                assertPurgeDeleteEntries(kvCtx.js, bucket, new String[]{"b", "c"});
            }
        });
    }

    private void assertPurgeDeleteEntries(JetStream js, String bucket, String[] expected) throws JetStreamException, InterruptedException {
        JetStreamPushSubscription sub = js.pushSubscribe(KeyValueUtils.toStreamSubject(bucket));

        for (String s : expected) {
            Message m = sub.nextMessage(1000);
            assertNotNull(m);
            KeyValueEntry kve = new KeyValueEntry(m);
            if (s == null) {
                assertNotEquals(KeyValueOperation.PUT, kve.getOperation());
                assertEquals(0, kve.getDataLen());
            }
            else {
                assertEquals(KeyValueOperation.PUT, kve.getOperation());
                assertEquals(s, kve.getValueAsString());
            }
        }

        sub.unsubscribe();
    }

    @Test
    public void testCreateAndUpdate() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                // create bucket
                String bucket = random();
                kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(64));

                KeyValue kv = kvm.keyValue(bucket);

                String key = random();
                // 1. allowed to create something that does not exist
                long rev1 = kv.create(key, "a".getBytes());

                // 2. allowed to update with proper revision
                kv.update(key, "ab".getBytes(), rev1);

                // 3. not allowed to update with wrong revision
                assertThrows(JetStreamApiException.class, () -> kv.update(key, "zzz".getBytes(), rev1));

                // 4. not allowed to create a key that exists
                assertThrows(JetStreamApiException.class, () -> kv.create(key, "zzz".getBytes()));

                // 5. not allowed to update a key that does not exist
                assertThrows(JetStreamApiException.class, () -> kv.update(key, "zzz".getBytes(), 1));

                // 6. allowed to create a key that is deleted
                kv.delete(key);
                kv.create(key, "abc".getBytes());

                // 7. allowed to update a key that is deleted, as long as you have its revision
                kv.delete(key);
                nc.flush(1000);

                sleep(200); // a little pause to make sure things get flushed
                List<KeyValueEntry> hist = kv.history(key);
                kv.update(key, "abcd".getBytes(), hist.get(hist.size() - 1).getRevision());

                // 8. allowed to create a key that is purged
                kv.purge(key);
                kv.create(key, "abcde".getBytes());

                // 9. allowed to update a key that is deleted, as long as you have its revision
                kv.purge(key);

                sleep(200); // a little pause to make sure things get flushed
                hist = kv.history(key);
                kv.update(key, "abcdef".getBytes(), hist.get(hist.size() - 1).getRevision());
            }
        });
    }

    private void assertKeys(List<String> apiKeys, String... manualKeys) {
        assertEquals(manualKeys.length, apiKeys.size());
        for (String k : manualKeys) {
            assertTrue(apiKeys.contains(k));
        }
    }

    private void assertHistory(List<Object> manualHistory, List<KeyValueEntry> apiHistory) {
        assertEquals(apiHistory.size(), manualHistory.size());
        for (int x = 0; x < apiHistory.size(); x++) {
            Object o = manualHistory.get(x);
            if (o instanceof KeyValueOperation) {
                assertEquals(o, apiHistory.get(x).getOperation());
            }
            else {
                assertKvEquals((KeyValueEntry)o, apiHistory.get(x));
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private KeyValueEntry assertEntry(String bucket, String key, KeyValueOperation op, long seq, String value, long now, KeyValueEntry entry) {
        assertEquals(bucket, entry.getBucket());
        assertEquals(key, entry.getKey());
        assertEquals(op, entry.getOperation());
        assertEquals(seq, entry.getRevision());
        assertEquals(0, entry.getDelta());
        if (op == KeyValueOperation.PUT) {
            assertNotNull(entry.getValue());
            assertEquals(value, new String(entry.getValue()));
        }
        else {
            assertNull(entry.getValue());
        }
        assertTrue(now <= entry.getCreated().toEpochSecond());

        // coverage
        assertNotNull(entry.toString());
        return entry;
    }

    private void assertKvEquals(KeyValueEntry kv1, KeyValueEntry kv2) {
        assertEquals(kv1.getOperation(), kv2.getOperation());
        assertEquals(kv1.getRevision(), kv2.getRevision());
        assertEquals(kv1.getBucket(), kv2.getBucket());
        assertEquals(kv1.getKey(), kv2.getKey());
        assertArrayEquals(kv1.getValue(), kv2.getValue());
        long es1 = kv1.getCreated().toEpochSecond();
        long es2 = kv2.getCreated().toEpochSecond();
        assertEquals(es1, es2);
    }

    @Test
    public void testManageGetBucketNamesStatuses() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;

                String bucket1 = random();
                kvCtx.kvCreate(bucket1);

                String bucket2 = random();
                kvCtx.kvCreate(bucket2);

                List<KeyValueStatus> statuses = kvm.getStatuses();
                List<String> buckets = new ArrayList<>();
                for (KeyValueStatus s : statuses) {
                    buckets.add(s.getBucketName());
                }
                assertTrue(buckets.contains(bucket1));
                assertTrue(buckets.contains(bucket2));

                buckets = kvm.getBucketNames();
                assertTrue(buckets.contains(bucket1));
                assertTrue(buckets.contains(bucket2));
            }
        });
    }

    static class TestKeyValueWatcher implements KeyValueWatcher {
        public String name;
        public List<KeyValueEntry> entries = new ArrayList<>();
        public KeyValueWatchOption[] watchOptions;
        public boolean beforeWatcher;
        public boolean metaOnly;
        public int endOfDataReceived;
        public boolean endBeforeEntries;

        public TestKeyValueWatcher(String name, boolean beforeWatcher, KeyValueWatchOption... watchOptions) {
            this.name = name;
            this.beforeWatcher = beforeWatcher;
            this.watchOptions = watchOptions;
            for (KeyValueWatchOption wo : watchOptions) {
                if (wo == META_ONLY) {
                    metaOnly = true;
                    break;
                }
            }
        }

        @Override
        public String getConsumerNamePrefix() {
            return metaOnly ? null : name + "-";
        }

        @Override
        public String toString() {
            return "TestKeyValueWatcher{" +
                "name='" + name + '\'' +
                ", beforeWatcher=" + beforeWatcher +
                ", metaOnly=" + metaOnly +
                ", watchOptions=" + Arrays.toString(watchOptions) +
                '}';
        }

        @Override
        public void watch(@NonNull KeyValueEntry kve) {
//            }
            entries.add(kve);
        }

        @Override
        public void endOfData() {
            if (++endOfDataReceived == 1 && entries.isEmpty()) {
                endBeforeEntries = true;
            }
        }
    }

    static String TEST_WATCH_KEY_NULL = "key.nl";
    static String TEST_WATCH_KEY_1 = "key.1";
    static String TEST_WATCH_KEY_2 = "key.2";

    interface TestWatchSubSupplier {
        KeyValueWatchSubscription get(KeyValue kv) throws Exception;
    }

    @Test
    public void testWatch() throws Exception {
        Object[] key1AllExpecteds = new Object[]{
            "a", "aa", KeyValueOperation.DELETE, "aaa", KeyValueOperation.DELETE, KeyValueOperation.PURGE
        };

        Object[] key1FromRevisionExpecteds = new Object[]{
            "aa", KeyValueOperation.DELETE, "aaa"
        };

        Object[] noExpecteds = new Object[0];
        Object[] purgeOnlyExpecteds = new Object[]{KeyValueOperation.PURGE};

        Object[] key2AllExpecteds = new Object[]{
            "z", "zz", KeyValueOperation.DELETE, "zzz"
        };

        Object[] key2AfterExpecteds = new Object[]{"zzz"};

        Object[] allExpecteds = new Object[]{
            "a", "aa", "z", "zz",
            KeyValueOperation.DELETE, KeyValueOperation.DELETE,
            "aaa", "zzz",
            KeyValueOperation.DELETE, KeyValueOperation.PURGE,
            null
        };

        Object[] allPutsExpecteds = new Object[]{
            "a", "aa", "z", "zz", "aaa", "zzz", null
        };

        Object[] allFromRevisionExpecteds = new Object[]{
            "aa", "z", "zz",
            KeyValueOperation.DELETE, KeyValueOperation.DELETE,
            "aaa", "zzz",
        };

        TestKeyValueWatcher key1FullWatcher = new TestKeyValueWatcher("key1FullWatcher", true);
        TestKeyValueWatcher key1MetaWatcher = new TestKeyValueWatcher("key1MetaWatcher", true, META_ONLY);
        TestKeyValueWatcher key1StartNewWatcher = new TestKeyValueWatcher("key1StartNewWatcher", true, META_ONLY, UPDATES_ONLY);
        TestKeyValueWatcher key1StartAllWatcher = new TestKeyValueWatcher("key1StartAllWatcher", true, META_ONLY);
        TestKeyValueWatcher key2FullWatcher = new TestKeyValueWatcher("key2FullWatcher", true);
        TestKeyValueWatcher key2MetaWatcher = new TestKeyValueWatcher("key2MetaWatcher", true, META_ONLY);
        TestKeyValueWatcher allAllFullWatcher = new TestKeyValueWatcher("allAllFullWatcher", true);
        TestKeyValueWatcher allAllMetaWatcher = new TestKeyValueWatcher("allAllMetaWatcher", true, META_ONLY);
        TestKeyValueWatcher allIgDelFullWatcher = new TestKeyValueWatcher("allIgDelFullWatcher", true, IGNORE_DELETE);
        TestKeyValueWatcher allIgDelMetaWatcher = new TestKeyValueWatcher("allIgDelMetaWatcher", true, META_ONLY, IGNORE_DELETE);
        TestKeyValueWatcher starFullWatcher = new TestKeyValueWatcher("starFullWatcher", true);
        TestKeyValueWatcher starMetaWatcher = new TestKeyValueWatcher("starMetaWatcher", true, META_ONLY);
        TestKeyValueWatcher gtFullWatcher = new TestKeyValueWatcher("gtFullWatcher", true);
        TestKeyValueWatcher gtMetaWatcher = new TestKeyValueWatcher("gtMetaWatcher", true, META_ONLY);
        TestKeyValueWatcher multipleFullWatcher = new TestKeyValueWatcher("multipleFullWatcher", true);
        TestKeyValueWatcher multipleMetaWatcher = new TestKeyValueWatcher("multipleMetaWatcher", true, META_ONLY);
        TestKeyValueWatcher key1AfterWatcher = new TestKeyValueWatcher("key1AfterWatcher", false, META_ONLY);
        TestKeyValueWatcher key1AfterIgDelWatcher = new TestKeyValueWatcher("key1AfterIgDelWatcher", false, META_ONLY, IGNORE_DELETE);
        TestKeyValueWatcher key1AfterStartNewWatcher = new TestKeyValueWatcher("key1AfterStartNewWatcher", false, META_ONLY, UPDATES_ONLY);
        TestKeyValueWatcher key1AfterStartFirstWatcher = new TestKeyValueWatcher("key1AfterStartFirstWatcher", false, META_ONLY, INCLUDE_HISTORY);
        TestKeyValueWatcher key2AfterWatcher = new TestKeyValueWatcher("key2AfterWatcher", false, META_ONLY);
        TestKeyValueWatcher key2AfterStartNewWatcher = new TestKeyValueWatcher("key2AfterStartNewWatcher", false, META_ONLY, UPDATES_ONLY);
        TestKeyValueWatcher key2AfterStartFirstWatcher = new TestKeyValueWatcher("key2AfterStartFirstWatcher", false, META_ONLY, INCLUDE_HISTORY);
        TestKeyValueWatcher key1FromRevisionAfterWatcher = new TestKeyValueWatcher("key1FromRevisionAfterWatcher", false);
        TestKeyValueWatcher allFromRevisionAfterWatcher = new TestKeyValueWatcher("allFromRevisionAfterWatcher", false);
        TestKeyValueWatcher key1Key2FromRevisionAfterWatcher = new TestKeyValueWatcher("key1Key2FromRevisionAfterWatcher", false);

        List<String> allKeys = Arrays.asList(TEST_WATCH_KEY_1, TEST_WATCH_KEY_2, TEST_WATCH_KEY_NULL);

        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                _testWatch(kvCtx, key1FullWatcher, key1AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1FullWatcher, key1FullWatcher.watchOptions));
                _testWatch(kvCtx, key1MetaWatcher, key1AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1MetaWatcher, key1MetaWatcher.watchOptions));
                _testWatch(kvCtx, key1StartNewWatcher, key1AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1StartNewWatcher, key1StartNewWatcher.watchOptions));
                _testWatch(kvCtx, key1StartAllWatcher, key1AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1StartAllWatcher, key1StartAllWatcher.watchOptions));
                _testWatch(kvCtx, key2FullWatcher, key2AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_2, key2FullWatcher, key2FullWatcher.watchOptions));
                _testWatch(kvCtx, key2MetaWatcher, key2AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_2, key2MetaWatcher, key2MetaWatcher.watchOptions));
                _testWatch(kvCtx, allAllFullWatcher, allExpecteds, -1, kv -> kv.watchAll(allAllFullWatcher, allAllFullWatcher.watchOptions));
                _testWatch(kvCtx, allAllMetaWatcher, allExpecteds, -1, kv -> kv.watchAll(allAllMetaWatcher, allAllMetaWatcher.watchOptions));
                _testWatch(kvCtx, allIgDelFullWatcher, allPutsExpecteds, -1, kv -> kv.watchAll(allIgDelFullWatcher, allIgDelFullWatcher.watchOptions));
                _testWatch(kvCtx, allIgDelMetaWatcher, allPutsExpecteds, -1, kv -> kv.watchAll(allIgDelMetaWatcher, allIgDelMetaWatcher.watchOptions));
                _testWatch(kvCtx, starFullWatcher, allExpecteds, -1, kv -> kv.watch("key.*", starFullWatcher, starFullWatcher.watchOptions));
                _testWatch(kvCtx, starMetaWatcher, allExpecteds, -1, kv -> kv.watch("key.*", starMetaWatcher, starMetaWatcher.watchOptions));
                _testWatch(kvCtx, gtFullWatcher, allExpecteds, -1, kv -> kv.watch("key.>", gtFullWatcher, gtFullWatcher.watchOptions));
                _testWatch(kvCtx, gtMetaWatcher, allExpecteds, -1, kv -> kv.watch("key.>", gtMetaWatcher, gtMetaWatcher.watchOptions));
                _testWatch(kvCtx, key1AfterWatcher, purgeOnlyExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1AfterWatcher, key1AfterWatcher.watchOptions));
                _testWatch(kvCtx, key1AfterIgDelWatcher, noExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1AfterIgDelWatcher, key1AfterIgDelWatcher.watchOptions));
                _testWatch(kvCtx, key1AfterStartNewWatcher, noExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1AfterStartNewWatcher, key1AfterStartNewWatcher.watchOptions));
                _testWatch(kvCtx, key1AfterStartFirstWatcher, purgeOnlyExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_1, key1AfterStartFirstWatcher, key1AfterStartFirstWatcher.watchOptions));
                _testWatch(kvCtx, key2AfterWatcher, key2AfterExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_2, key2AfterWatcher, key2AfterWatcher.watchOptions));
                _testWatch(kvCtx, key2AfterStartNewWatcher, noExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_2, key2AfterStartNewWatcher, key2AfterStartNewWatcher.watchOptions));
                _testWatch(kvCtx, key2AfterStartFirstWatcher, key2AllExpecteds, -1, kv -> kv.watch(TEST_WATCH_KEY_2, key2AfterStartFirstWatcher, key2AfterStartFirstWatcher.watchOptions));
                _testWatch(kvCtx, key1FromRevisionAfterWatcher, key1FromRevisionExpecteds, 2, kv -> kv.watch(TEST_WATCH_KEY_1, key1FromRevisionAfterWatcher, 2, key1FromRevisionAfterWatcher.watchOptions));
                _testWatch(kvCtx, allFromRevisionAfterWatcher, allFromRevisionExpecteds, 2, kv -> kv.watchAll(allFromRevisionAfterWatcher, 2, allFromRevisionAfterWatcher.watchOptions));
                List<String> keys = Arrays.asList(TEST_WATCH_KEY_1, TEST_WATCH_KEY_2);
                _testWatch(kvCtx, key1Key2FromRevisionAfterWatcher, allFromRevisionExpecteds, 2, kv -> kv.watch(keys, key1Key2FromRevisionAfterWatcher, 2, key1Key2FromRevisionAfterWatcher.watchOptions));

                if (VersionUtils.atLeast2_10()) {
                    _testWatch(kvCtx, multipleFullWatcher, allExpecteds, -1, kv -> kv.watch(allKeys, multipleFullWatcher, multipleFullWatcher.watchOptions));
                    _testWatch(kvCtx, multipleMetaWatcher, allExpecteds, -1, kv -> kv.watch(allKeys, multipleMetaWatcher, multipleMetaWatcher.watchOptions));
                }
            }
        });
    }

    private void _testWatch(KvTestingContext kvCtx, TestKeyValueWatcher watcher, Object[] expectedKves, long fromRevision, TestWatchSubSupplier supplier) throws Exception {
        KeyValueManagement kvm = kvCtx.kvm;

        String bucket = random() + watcher.name;
        kvCtx.kvCreate(bucket, cr -> cr.maxHistoryPerKey(10));

        KeyValue kv = kvm.keyValue(bucket);

        KeyValueWatchSubscription sub = null;

        if (watcher.beforeWatcher) {
            sub = supplier.get(kv);
        }

        if (fromRevision == -1) {
            kv.put(TEST_WATCH_KEY_1, "a");
            kv.put(TEST_WATCH_KEY_1, "aa");
            kv.put(TEST_WATCH_KEY_2, "z");
            kv.put(TEST_WATCH_KEY_2, "zz");
            kv.delete(TEST_WATCH_KEY_1);
            kv.delete(TEST_WATCH_KEY_2);
            kv.put(TEST_WATCH_KEY_1, "aaa");
            kv.put(TEST_WATCH_KEY_2, "zzz");
            kv.delete(TEST_WATCH_KEY_1);
            kv.purge(TEST_WATCH_KEY_1);
            kv.put(TEST_WATCH_KEY_NULL, (byte[]) null);
        }
        else {
            kv.put(TEST_WATCH_KEY_1, "a");
            kv.put(TEST_WATCH_KEY_1, "aa");
            kv.put(TEST_WATCH_KEY_2, "z");
            kv.put(TEST_WATCH_KEY_2, "zz");
            kv.delete(TEST_WATCH_KEY_1);
            kv.delete(TEST_WATCH_KEY_2);
            kv.put(TEST_WATCH_KEY_1, "aaa");
            kv.put(TEST_WATCH_KEY_2, "zzz");
        }

        if (!watcher.beforeWatcher) {
            sub = supplier.get(kv);
        }

        // only testing this consumer name prefix on not meta only tests
        // this way there is coverage on working with and without a prefix
        if (!watcher.metaOnly) {
            List<String> names = kvCtx.jsm.getConsumerNames("KV_" + bucket);
            assertEquals(1, names.size());
            assertNotNull(watcher.getConsumerNamePrefix());
            assertTrue(names.get(0).startsWith(watcher.getConsumerNamePrefix()));
        }

        sleep(1500); // give time for the watches to get messages

        validateWatcher(expectedKves, watcher);

        //noinspection DataFlowIssue
        sub.close();
    }

    private void validateWatcher(Object[] expectedKves, TestKeyValueWatcher watcher) {
        assertEquals(expectedKves.length, watcher.entries.size());
        assertEquals(1, watcher.endOfDataReceived);

        if (expectedKves.length > 0) {
            assertEquals(watcher.beforeWatcher, watcher.endBeforeEntries);
        }

        int aix = 0;
        ZonedDateTime lastCreated = ZonedDateTime.of(2000, 4, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        long lastRevision = -1;

        for (KeyValueEntry kve : watcher.entries) {
            assertTrue(kve.getCreated().isAfter(lastCreated) || kve.getCreated().isEqual(lastCreated));
            lastCreated = kve.getCreated();

            assertTrue(lastRevision < kve.getRevision());
            lastRevision = kve.getRevision();

            Object expected = expectedKves[aix++];
            if (expected == null) {
                assertSame(KeyValueOperation.PUT, kve.getOperation());
                assertTrue(kve.getValue() == null || kve.getValue().length == 0);
                assertEquals(0, kve.getDataLen());
            }
            else if (expected instanceof String) {
                assertSame(KeyValueOperation.PUT, kve.getOperation());
                String s = (String) expected;
                if (watcher.metaOnly) {
                    assertTrue(kve.getValue() == null || kve.getValue().length == 0);
                    assertEquals(s.length(), kve.getDataLen());
                }
                else {
                    assertNotNull(kve.getValue());
                    assertEquals(s.length(), kve.getDataLen());
                    assertEquals(s, kve.getValueAsString());
                }
            }
            else {
                assertTrue(kve.getValue() == null || kve.getValue().length == 0);
                assertEquals(0, kve.getDataLen());
                assertSame(expected, kve.getOperation());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void
    testWithAccount(boolean simplified) throws Exception {
        // The two delivery mechanisms need different account exports: the simplified consume is
        // answered through the api service export, which has to allow a stream response, while the
        // push consumer is delivered through the inbox stream export. See ACCOUNT_PUSH_PULL_EXPORTS.md
        KeyValueWatchOption[] watchOptions = simplified
            ? new KeyValueWatchOption[0]
            : new KeyValueWatchOption[]{KeyValueWatchOption.PUSH_CONSUME};

        runInConfiguredServer(simplified ? "kv_account_simplified.conf" : "kv_account_push.conf", ts -> {
            char[] upA = new char[]{'a'};
            char[] upI = new char[]{'i'};
            Options acctA = optionsBuilder(ts).userInfo(upA, upA).build();
            Options acctI = optionsBuilder(ts).userInfo(upI, upI).inboxPrefix("ForI").build();

            try (NatsConnection connUserA = Nats.connect(acctA);
                 NatsConnection connUserI = Nats.connect(acctI)) {
                // some prep
                KeyValueOptions jsOpt_UserI_BucketA_WithPrefix =
                    KeyValueOptions.builder().jsPrefix("FromA").build();

                assertNotNull(jsOpt_UserI_BucketA_WithPrefix.getJetStreamOptions());

                KeyValueOptions jsOpt_UserI_BucketI_WithPrefix =
                    KeyValueOptions.builder().jsPrefix("FromA").build();

                assertNotNull(jsOpt_UserI_BucketI_WithPrefix.getJetStreamOptions());

                KeyValueManagement kvmUserA = new KeyValueManagement(connUserA);
                KeyValueManagement kvmUserIBcktA = new KeyValueManagement(connUserI, jsOpt_UserI_BucketA_WithPrefix);
                KeyValueManagement kvmUserIBcktI = new KeyValueManagement(connUserI, jsOpt_UserI_BucketI_WithPrefix);

                String bucketA = "BK-A"; // random();
                KeyValueCreator kvcA = new KeyValueCreator(bucketA)
                    .storageType(StorageType.Memory).maxHistoryPerKey(64);

                String bucketI = "BK-I"; // random();
                KeyValueCreator kvcI = new KeyValueCreator(bucketI)
                    .storageType(StorageType.Memory).maxHistoryPerKey(64);

                // testing KVM API
                assertEquals(bucketA, kvmUserA.create(kvcA).getBucketName());
                assertEquals(bucketI, kvmUserIBcktI.create(kvcI).getBucketName());

                assertKvAccountBucketNames(kvmUserA.getBucketNames(), bucketA, bucketI);
                assertKvAccountBucketNames(kvmUserIBcktA.getBucketNames(), bucketA, bucketI);
                assertKvAccountBucketNames(kvmUserIBcktI.getBucketNames(), bucketA, bucketI);

                assertEquals(bucketA, kvmUserA.getStatus(bucketA).getBucketName());
                assertEquals(bucketA, kvmUserIBcktA.getStatus(bucketA).getBucketName());
                assertEquals(bucketA, kvmUserIBcktI.getStatus(bucketA).getBucketName());
                assertEquals(bucketI, kvmUserA.getStatus(bucketI).getBucketName());
                assertEquals(bucketI, kvmUserIBcktA.getStatus(bucketI).getBucketName());
                assertEquals(bucketI, kvmUserIBcktI.getStatus(bucketI).getBucketName());

                // some more prep
                KeyValue kv_connA_bucketA = kvmUserA.keyValue(bucketA);
                KeyValue kv_connA_bucketI = kvmUserA.keyValue(bucketI);
                KeyValue kv_connI_bucketA = kvmUserIBcktA.keyValue(bucketA);
                KeyValue kv_connI_bucketI = kvmUserIBcktI.keyValue(bucketI);

                // check the names
                assertEquals(bucketA, kv_connA_bucketA.getBucketName());
                assertEquals(bucketA, kv_connI_bucketA.getBucketName());
                assertEquals(bucketI, kv_connA_bucketI.getBucketName());
                assertEquals(bucketI, kv_connI_bucketI.getBucketName());

                TestKeyValueWatcher watcher_connA_BucketA = new TestKeyValueWatcher("watcher_connA_BucketA", true);
                TestKeyValueWatcher watcher_connA_BucketI = new TestKeyValueWatcher("watcher_connA_BucketI", true);
                TestKeyValueWatcher watcher_connI_BucketA = new TestKeyValueWatcher("watcher_connI_BucketA", true);
                TestKeyValueWatcher watcher_connI_BucketI = new TestKeyValueWatcher("watcher_connI_BucketI", true);

                //noinspection resource
                kv_connA_bucketA.watchAll(watcher_connA_BucketA, watchOptions);
                //noinspection resource
                kv_connA_bucketI.watchAll(watcher_connA_BucketI, watchOptions);
                //noinspection resource
                kv_connI_bucketA.watchAll(watcher_connI_BucketA, watchOptions);
                //noinspection resource
                kv_connI_bucketI.watchAll(watcher_connI_BucketI, watchOptions);

                String keyA1 = random();
                String keyA2 = random();
                String keyI1 = random();
                String keyI2 = random();

                // bucket a from user a: AA, check AA, IA
                assertKveAccount(kv_connA_bucketA, keyA1, kv_connA_bucketA, kv_connI_bucketA);

                // bucket a from user i: IA, check AA, IA
                assertKveAccount(kv_connI_bucketA, keyA2, kv_connA_bucketA, kv_connI_bucketA);

                // bucket i from user a: AI, check AI, II
                assertKveAccount(kv_connA_bucketI, keyI1, kv_connA_bucketI, kv_connI_bucketI);

                // bucket i from user i: II, check AI, II
                assertKveAccount(kv_connI_bucketI, keyI2, kv_connA_bucketI, kv_connI_bucketI);

                // check keys from each kv
                assertKvAccountKeys(kv_connA_bucketA.keys(), keyA1, keyA2);
                assertKvAccountKeys(kv_connI_bucketA.keys(), keyA1, keyA2);
                assertKvAccountKeys(kv_connA_bucketI.keys(), keyI1, keyI2);
                assertKvAccountKeys(kv_connI_bucketI.keys(), keyI1, keyI2);

                Object[] expecteds = new Object[]{
                    data(0), data(1), KeyValueOperation.DELETE, KeyValueOperation.PURGE, data(2),
                    data(0), data(1), KeyValueOperation.DELETE, KeyValueOperation.PURGE, data(2)
                };

                validateWatcher(expecteds, watcher_connA_BucketA);
                validateWatcher(expecteds, watcher_connA_BucketI);
                validateWatcher(expecteds, watcher_connI_BucketA);
                validateWatcher(expecteds, watcher_connI_BucketI);
            }
        });
    }

    private void assertKvAccountBucketNames(List<String> bnames, String bucketA, String bucketI) {
        assertEquals(2, bnames.size());
        assertTrue(bnames.contains(bucketA));
        assertTrue(bnames.contains(bucketI));
    }

    private void assertKvAccountKeys(List<String> keys, String key1, String key2) {
        assertEquals(2, keys.size());
        assertTrue(keys.contains(key1));
        assertTrue(keys.contains(key2));
    }

    private void assertKveAccount(KeyValue kvWorker, String key, KeyValue kvUserA, KeyValue kvUserI) throws IOException, JetStreamException, InterruptedException {
        kvWorker.create(key, dataBytes(0));
        assertKveAccountGet(kvUserA, kvUserI, key, data(0));

        kvWorker.put(key, dataBytes(1));
        assertKveAccountGet(kvUserA, kvUserI, key, data(1));

        kvWorker.delete(key);
        KeyValueEntry kveUserA = kvUserA.get(key);
        KeyValueEntry kveUserI = kvUserI.get(key);
        assertNull(kveUserA);
        assertNull(kveUserI);

        assertKveAccountHistory(kvUserA.history(key), data(0), data(1), KeyValueOperation.DELETE);
        assertKveAccountHistory(kvUserI.history(key), data(0), data(1), KeyValueOperation.DELETE);

        kvWorker.purge(key);
        assertKveAccountHistory(kvUserA.history(key), KeyValueOperation.PURGE);
        assertKveAccountHistory(kvUserI.history(key), KeyValueOperation.PURGE);

        // leave data for keys checking
        kvWorker.put(key, dataBytes(2));
        assertKveAccountGet(kvUserA, kvUserI, key, data(2));
    }

    private void assertKveAccountHistory(List<KeyValueEntry> history, Object... expecteds) {
        assertEquals(expecteds.length, history.size());
        for (int x = 0; x < expecteds.length; x++) {
            if (expecteds[x] instanceof String) {
                assertEquals(expecteds[x], history.get(x).getValueAsString());
            }
            else {
                assertEquals(expecteds[x], history.get(x).getOperation());
            }
        }
    }

    private void assertKveAccountGet(KeyValue kvUserA, KeyValue kvUserI, String key, String data) throws IOException, JetStreamException, InterruptedException {
        KeyValueEntry kveUserA = kvUserA.get(key);
        KeyValueEntry kveUserI = kvUserI.get(key);
        assertNotNull(kveUserA);
        assertNotNull(kveUserI);
        assertEquals(kveUserA, kveUserI);
        assertEquals(data, kveUserA.getValueAsString());
        assertEquals(KeyValueOperation.PUT, kveUserA.getOperation());
    }

    @SuppressWarnings({"SimplifiableAssertion", "ConstantConditions"})
    @Test
    public void testCoverBucketAndKey() {
        String bucket = random();
        String key = random();
        KeyValueUtils.BucketAndKey bak1 = new KeyValueUtils.BucketAndKey(DOT + bucket + DOT + key);
        KeyValueUtils.BucketAndKey bak2 = new KeyValueUtils.BucketAndKey(DOT + bucket + DOT + key);
        KeyValueUtils.BucketAndKey bak3 = new KeyValueUtils.BucketAndKey(DOT + random() + DOT + key);
        KeyValueUtils.BucketAndKey bak4 = new KeyValueUtils.BucketAndKey(DOT + bucket + DOT + random());

        assertEquals(bucket, bak1.bucket);
        assertEquals(key, bak1.key);
        //noinspection EqualsWithItself
        assertEquals(bak1, bak1);
        assertEquals(bak1, bak2);
        assertEquals(bak2, bak1);
        assertNotEquals(bak1, bak3);
        assertNotEquals(bak1, bak4);
        assertNotEquals(bak3, bak1);
        assertNotEquals(bak4, bak1);

        assertFalse(bak4.equals(null));
        assertFalse(bak4.equals(new Object()));
    }

    @Test
    public void testCoverPrefix() {
        assertTrue(KeyValueUtils.hasPrefix("KV_has"));
        assertFalse(KeyValueUtils.hasPrefix("doesn't"));
        assertEquals("has", KeyValueUtils.trimPrefix("KV_has"));
        assertEquals("doesn't", KeyValueUtils.trimPrefix("doesn't"));

    }

    @Test
    public void testKeyValueEntryEqualsImpl() throws Exception {
        runInShared(nc -> {
            KvTestingContext kvCtx = new KvTestingContext(nc);
            KeyValueManagement kvm = kvCtx.kvm;

            // create bucket 1
            String bucket1 = random();
            kvCtx.kvCreate(bucket1);

            // create bucket 2
            String bucket2 = random();
            kvCtx.kvCreate(bucket2);

            KeyValue kv1 = kvm.keyValue(bucket1);
            KeyValue kv2 = kvm.keyValue(bucket2);
            String key1 = random();
            String key2 = random();
            String key3 = random();
            kv1.put(key1, "ONE");
            kv1.put(key2, "TWO");
            kv2.put(key1, "ONE");

            KeyValueEntry kve1_1 = kv1.get(key1);
            KeyValueEntry kve1_2 = kv1.get(key2);
            KeyValueEntry kve2_1 = kv2.get(key1);

            //noinspection EqualsWithItself
            assertEquals(kve1_1, kve1_1);
            assertEquals(kve1_1, kv1.get(key1));
            assertNotEquals(kve1_1, kve1_2);
            assertNotEquals(kve1_1, kve2_1);

            kv1.put(key1, "ONE-PRIME");
            assertNotEquals(kve1_1, kv1.get(key1));

            kv1.put(key3, (byte[]) null);
            KeyValueEntry kve9 = kv1.get(key3);
            assertNull(kve9.getValue());
            assertNull(kve9.getValueAsString());
            assertNull(kve9.getValueAsLong());

            kv1.put(key3, new byte[0]);
            kve9 = kv1.get(key3);
            assertNull(kve9.getValue());
            assertNull(kve9.getValueAsString());
            assertNull(kve9.getValueAsLong());

            // coverage
            //noinspection MisorderedAssertEqualsArguments
            assertNotEquals(kve1_1, null);
            //noinspection MisorderedAssertEqualsArguments
            assertNotEquals(kve1_1, new Object());
        });
    }

    @Test
    public void testKeyValueOptionsBuilderCoverage() {
        assertKvoBuilderCoverage(KeyValueOptions.builder().build());
        assertKvoBuilderCoverage(KeyValueOptions.builder().jetStreamOptions(DEFAULT_JS_OPTIONS).build());
        assertKvoBuilderCoverage(KeyValueOptions.builder((KeyValueOptions) null).build());
        assertKvoBuilderCoverage(KeyValueOptions.builder(KeyValueOptions.builder().build()).build());
        assertKvoBuilderCoverage(KeyValueOptions.builder(DEFAULT_JS_OPTIONS).build());

        KeyValueOptions kvo = KeyValueOptions.builder().jsPrefix("prefix").build();
        assertEquals("prefix.", kvo.getJetStreamOptions().getPrefix());
        assertFalse(kvo.getJetStreamOptions().isDefaultPrefix());

        kvo = KeyValueOptions.builder().jsDomain("domain").build();
        assertEquals("$JS.domain.API.", kvo.getJetStreamOptions().getPrefix());
        assertFalse(kvo.getJetStreamOptions().isDefaultPrefix());

        kvo = KeyValueOptions.builder().jsRequestTimeout(10000).build();
        assertEquals(10000, kvo.getJetStreamOptions().getRequestTimeout());
    }

    private void assertKvoBuilderCoverage(KeyValueOptions kvo) {
        JetStreamOptions jso = kvo.getJetStreamOptions();
        assertEquals(DEFAULT_JS_OPTIONS.getRequestTimeout(), jso.getRequestTimeout());
        assertEquals(DEFAULT_JS_OPTIONS.getPrefix(), jso.getPrefix());
        assertEquals(DEFAULT_JS_OPTIONS.isDefaultPrefix(), jso.isDefaultPrefix());
    }

    @Test
    public void testKeyValuePurgeOptionsBuilderCoverage() {
        assertEquals(DEFAULT_THRESHOLD_MILLIS,
            KeyValuePurgeOptions.builder().build()
                .getDeleteMarkersThresholdMillis());

        assertEquals(1,
            KeyValuePurgeOptions.builder().deleteMarkersThreshold(1).build()
                .getDeleteMarkersThresholdMillis());

        assertEquals(1000,
            KeyValuePurgeOptions.builder().deleteMarkersThreshold(1000).build()
                .getDeleteMarkersThresholdMillis());

        assertEquals(NO_THRESHOLD_MILLIS,
            KeyValuePurgeOptions.builder().deleteMarkersThreshold(0).build()
                .getDeleteMarkersThresholdMillis());

        assertEquals(KeyValuePurgeOptions.NO_THRESHOLD_MILLIS,
            KeyValuePurgeOptions.builder().deleteMarkersThreshold(-1).build()
                .getDeleteMarkersThresholdMillis());
    }

    @Test
    public void testCreateDiscardPolicy() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                // create bucket
                String bucket1 = random();
                KeyValueStatus status = kvCtx.kvCreate(bucket1);

                DiscardPolicy dp = status.getConfiguration().getBackingConfig().getDiscardPolicy();
                if (nc.getServerInfo().isSameOrNewerThanVersion("2.7.2")) {
                    assertEquals(DiscardPolicy.New, dp);
                }
                else {
                    assertTrue(dp == DiscardPolicy.New || dp == DiscardPolicy.Old);
                }
            }
        });
    }

    @Test
    public void testEntryCoercion() throws Exception {
        runInShared(nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                // create bucket
                String bucket = random();
                KeyValueStatus s = kvCtx.kvCreate(bucket);

                KeyValue kv = kvCtx.kvm.keyValue(bucket);
                kv.put("a", "a");
                KeyValueEntry kve = kv.get("a");
                assertNotNull(kve);
                assertNotNull(kve.getValue());
                try {
                    kve.getValueAsLong();
                    fail();
                }
                catch (NumberFormatException nfe) {
                    // correct!
                }
                catch (Exception e) {
                    fail(e);
                }

                kv.delete("a");
                List<KeyValueEntry> list = kv.history("a");
                assertNull(list.get(0).getValueAsString());
                assertNull(list.get(0).getValueAsLong());
            }
        });
    }

    @Test
    public void testKeyResultConstruction() {
        KeyResult r = new KeyResult();
        assertNull(r.getKey());
        assertNull(r.getException());
        assertFalse(r.isKey());
        assertFalse(r.isException());
        assertTrue(r.isDone());

        r = new KeyResult("key");
        assertEquals("key", r.getKey());
        assertNull(r.getException());
        assertTrue(r.isKey());
        assertFalse(r.isException());
        assertFalse(r.isDone());

        Exception e = new Exception();
        r = new KeyResult(e);
        assertNull(r.getKey());
        assertNotNull(r.getException());
        assertFalse(r.isKey());
        assertTrue(r.isException());
        assertTrue(r.isDone());
    }

    // in v2, conversion used to happen in the builder build method
    // but v3 doesn't have that anymore, instead it all happens
    // in the setupStreamCreator method
    @Test
    public void testMirrorSourceBuilderPrefixConversion() {
        String bucket = random();
        String name = random();
        String kvStreamName = "KV_" + name;
        KeyValueCreator kvc = new KeyValueCreator(bucket)
            .mirror(new MirrorCreator(name));
        StreamCreator sc = KeyValueManagement.buildStreamCreator(kvc);
        MirrorCreator mc = sc.getMirrorCreator();
        assertNotNull(mc);
        assertEquals(kvStreamName, mc.getStreamName());

        // the case where the user put the stream name
        // instead of the bucket as the mirror name
        kvc = new KeyValueCreator(bucket)
            .mirror(new MirrorCreator(kvStreamName));
        sc = KeyValueManagement.buildStreamCreator(kvc);
        mc = sc.getMirrorCreator();
        assertNotNull(mc);
        assertEquals(kvStreamName, mc.getStreamName());

        kvc = new KeyValueCreator(bucket)
            .mirror(new MirrorCreator(kvStreamName));
        sc = KeyValueManagement.buildStreamCreator(kvc);
        mc = sc.getMirrorCreator();
        assertNotNull(mc);
        assertEquals(kvStreamName, mc.getStreamName());

        SourceCreator s1 = new SourceCreator("s1");
        SourceCreator s2 = new SourceCreator("s2");
        SourceCreator s3 = new SourceCreator("s3");
        SourceCreator s4 = new SourceCreator("s4");
        SourceCreator s5 = new SourceCreator("KV_s5"); // stream name instead of bucket name
        SourceCreator s6 = new SourceCreator("KV_s6"); // stream name instead of bucket name

        kvc = new KeyValueCreator(bucket)
            .sourceCreators(s5, s6)
            .sourceCreators(Arrays.asList(s3, s4))
            .sourceCreators(s1, s2, s6);

        sc = KeyValueManagement.buildStreamCreator(kvc);
        List<SourceCreator> sources = sc.getSourceCreators();
        assertNotNull(sources);
        assertEquals(3, sources.size());
        List<String> names = new ArrayList<>();
        for (SourceCreator source : sources) {
            names.add(source.getStreamName());
        }
        assertTrue(names.contains("KV_s1"));
        assertTrue(names.contains("KV_s2"));
        assertTrue(names.contains("KV_s6"));
    }

    @Test
    public void testKeyValueMirrorCrossDomains() throws Exception {
        runInJsHubLeaf((hubNc, leafNc) -> {
            try (KvTestingContext hubCtx = new KvTestingContext(hubNc);
                 KvTestingContext leafCtx = new KvTestingContext(leafNc))
            {
                KeyValueManagement hubKvm = hubCtx.kvm;
                KeyValueManagement leafKvm = leafCtx.kvm;

                // Create main KV on HUB
                String hubBucket = random();
                hubCtx.kvCreate(hubBucket);

                KeyValue hubKv = hubKvm.keyValue(hubBucket);
                hubKv.put("key1", "aaa0");
                hubKv.put("key2", "bb0");
                hubKv.put("key3", "c0");
                hubKv.delete("key3");

                String leafBucket = random();
                String leafStream = "KV_" + leafBucket;
                leafCtx.kvCreate(leafBucket,
                    cr -> cr.mirror(new MirrorCreator(hubBucket).domain(HUB_DOMAIN)));

                sleep(200); // make sure things get a chance to propagate
                StreamInfo si = leafCtx.jsm.getStreamInfo(leafStream);
                assertTrue(si.getConfiguration().getMirrorDirect());
                assertEquals(3, si.getStreamState().getMessageCount());

                KeyValue leafKv = leafKvm.keyValue(leafBucket);
                _testMirror(hubKv, leafKv, 1);

                // through leafnode connection but to origin KV,
                // you must specify the hub domain in the jetstream options
                // and the hub bucket name since you are in the hub's domain
                KeyValueManagement hubViaLeafKvm = new KeyValueManagement(leafNc,
                    KeyValueOptions.builder().jsDomain(HUB_DOMAIN).build());
                KeyValue hubViaLeafKv = hubViaLeafKvm.keyValue(hubBucket);
                _testMirror(hubKv, hubViaLeafKv, 2);

                // just cleanup
                hubKvm.delete(hubBucket);
                leafKvm.delete(leafBucket);
            }
        });
    }

        private void _testMirror(KeyValue okv, KeyValue mkv, int num) throws Exception {
            mkv.put("key1", "aaa" + num);
            mkv.put("key3", "c" + num);

            sleep(200); // make sure things get a chance to propagate
            KeyValueEntry kve = mkv.get("key3");
            assertEquals("c" + num, kve.getValueAsString());

            mkv.delete("key3");
            sleep(200); // make sure things get a chance to propagate
            assertNull(mkv.get("key3"));

            kve = mkv.get("key1");
            assertEquals("aaa" + num, kve.getValueAsString());

            // Make sure we can create a watcher on the mirror KV.
            TestKeyValueWatcher mWatcher = new TestKeyValueWatcher("mirrorWatcher" + num, false);
            //noinspection unused
            try (KeyValueWatchSubscription mWatchSub = mkv.watchAll(mWatcher)) {
                sleep(200); // give the messages time to propagate
            }
            validateWatcher(new Object[]{"bb0", "aaa" + num, KeyValueOperation.DELETE}, mWatcher);

            // Does the origin data match?
            if (okv != null) {
                TestKeyValueWatcher oWatcher = new TestKeyValueWatcher("originWatcher" + num, false);
                //noinspection unused
                try (KeyValueWatchSubscription oWatchSub = okv.watchAll(oWatcher)) {
                    sleep(200); // give the messages time to propagate
                }
                validateWatcher(new Object[]{"bb0", "aaa" + num, KeyValueOperation.DELETE}, oWatcher);
            }
        }

        @Test
        public void testKeyValueTransform() throws Exception {
            runInShared(VersionUtils::atLeast2_10_3, nc -> {

                try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                    KeyValueManagement kvm = kvCtx.kvm;

                    String kvName1 = random();
                    String kvName2 = kvName1 + "-mir";
                    String mirrorSegment = "MirrorMe";
                    String dontMirrorSegment = "DontMirrorMe";
                    String generic = "foo";

                    kvCtx.kvCreate(kvName1);

                    SubjectTransformCreator transform = new SubjectTransformCreator(
                        "$KV." + kvName1 + "." + mirrorSegment + ".*",
                        "$KV." + kvName2 + "." + mirrorSegment + ".{{wildcard(1)}}"
                    );

                    MirrorCreator mirr = new MirrorCreator(kvName1)
                        .subjectTransformCreators(transform);

                    kvCtx.kvCreate(kvName2, cr -> cr.mirror(mirr));

                    KeyValue kv1 = kvm.keyValue(kvName1);

                    String key1 = mirrorSegment + "." + generic;
                    String key2 = dontMirrorSegment + "." + generic;
                    kv1.put(key1, mirrorSegment.getBytes());
                    kv1.put(key2, dontMirrorSegment.getBytes());

                    Thread.sleep(1000); // transforming takes some amount of time, otherwise the kv2.getKeys() fails

                    List<String> keys = kv1.keys();
                    assertTrue(keys.contains(key1));
                    assertTrue(keys.contains(key2));
                    // TODO COME BACK ONCE SERVER IS FIXED
//            assertNotNull(kv1.get(key1));
//            assertNotNull(kv1.get(key2));

                    KeyValue kv2 = kvm.keyValue(kvName2);
                    keys = kv2.keys();
                    assertTrue(keys.contains(key1));
                    assertFalse(keys.contains(key2));
                    // TODO COME BACK ONCE SERVER IS FIXED
//            assertNotNull(kv2.get(key1));
//            assertNull(kv2.get(key2));
                }
            });
        }

        @Test
        public void testTtlAndDuplicateWindowRoundTrip() throws Exception {
            runInShared(VersionUtils::atLeast2_10, nc -> {
                try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                    KeyValueManagement kvm = kvCtx.kvm;
                    String bucket = random();
                    KeyValueCreator kvCreator = kvCtx.kvCreator(bucket);
                    KeyValueStatus status = kvCtx.kvCreate(kvCreator);

                    StreamConfiguration sc = status.getBackingStreamInfo().getConfiguration();
                    assertEquals(0, sc.getMaxAge().toMillis());
                    assertNotNull(sc.getDuplicateWindow());
                    assertEquals(SERVER_DEFAULT_DUPLICATE_WINDOW_MS, sc.getDuplicateWindow().toMillis());

                    kvCreator.ttl(Duration.ofSeconds(10));
                    status = kvm.update(kvCreator);
                    sc = status.getBackingStreamInfo().getConfiguration();
                    assertEquals(10_000, sc.getMaxAge().toMillis());
                    assertNotNull(sc.getDuplicateWindow());
                    assertEquals(10_000, sc.getDuplicateWindow().toMillis());

                    bucket = random();
                    kvCreator = kvCtx.kvCreator(bucket).ttl(Duration.ofMinutes(30));
                    status = kvCtx.kvCreate(kvCreator);

                    sc = status.getBackingStreamInfo().getConfiguration();
                    assertEquals(30, sc.getMaxAge().toMinutes());
                    assertNotNull(sc.getDuplicateWindow());
                    assertEquals(SERVER_DEFAULT_DUPLICATE_WINDOW_MS, sc.getDuplicateWindow().toMillis());
                }
            });
        }

    @Test
    public void testConsumeKeys() throws Exception {
        int count = 10000;
        runInShared(VersionUtils::atLeast2_10, nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                String bucket = random();
                kvCtx.kvCreate(bucket);

                // put a bunch of keys so consume takes some time.
                KeyValue kv = kvm.keyValue(bucket);
                for (int x = 0; x < count; x++) {
                    kv.put("key" + x, "" + x);
                }

                long start = System.currentTimeMillis();
                LinkedBlockingQueue<KeyResult> list = kv.consumeKeys();
                long elapsed = System.currentTimeMillis() - start;
                assertTrue(elapsed < 10); // should return very quickly, even on a slow machine
                int consumed = 0;
                while (consumed <= count) { // there is always a terminator message at the end
                    KeyResult kr = list.poll(1, TimeUnit.SECONDS);
                    if (kr != null) {
                        if (++consumed == 1) {
                            long elapsed2 = System.currentTimeMillis() - start;
                            assertTrue(elapsed < elapsed2); // the first message comes in well after the function call returns
                        }
                    }
                }
                assertEquals(count + 1, consumed);
            }
        });
    }

    @Test
    public void testLimitMarkerCoverage() throws Exception {
        runInShared(VersionUtils::atLeast2_12, nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                String bucket = random();
                KeyValueStatus status = kvCtx.kvCreate(bucket, cr -> cr.limitMarkerTtl(1000));
                assertNotNull(status.getLimitMarkerTtl());
                assertEquals(1000, status.getLimitMarkerTtl().toMillis());

                String key = random();
                KeyValue kv = kvm.keyValue(bucket);
                kv.create(key, dataBytes(), MessageTtl.seconds(1));

                KeyValueEntry kve = kv.get(key);
                assertNotNull(kve);

                sleep(2000); // a good amount of time to make sure a CI server works

                kve = kv.get(key);
                assertNull(kve);

                // coverage of duration api vs ms api
                status = kvCtx.kvCreate(random(), cr -> cr.limitMarkerTtl(Duration.ofSeconds(2)));
                assertNotNull(status.getLimitMarkerTtl());
                assertEquals(2000, status.getLimitMarkerTtl().toMillis());

                // coverage of invalid ttl
                assertThrows(IllegalArgumentException.class,
                    () -> new KeyValueCreator(random()).limitMarkerTtl(999));

                assertThrows(IllegalArgumentException.class,
                    () -> new KeyValueCreator(random()).limitMarkerTtl(Duration.ofMillis(999)));

                // coverage of resetting limit marker
                KeyValueCreator creator = new KeyValueCreator(random());
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());

                creator.limitMarkerTtl(Duration.ofSeconds(10));
                assertEquals(Duration.ofSeconds(10), creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
                creator.limitMarkerTtl(null);
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());

                creator.limitMarkerTtl(Duration.ofSeconds(10));
                assertEquals(Duration.ofSeconds(10), creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
                creator.limitMarkerTtl(Duration.ofSeconds(0));
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());

                creator.limitMarkerTtl(Duration.ofSeconds(10));
                assertEquals(Duration.ofSeconds(10), creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
                creator.limitMarkerTtl(Duration.ofSeconds(-1));
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());

                creator.limitMarkerTtl(Duration.ofSeconds(10));
                assertEquals(Duration.ofSeconds(10), creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
                creator.limitMarkerTtl(0);
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());

                creator.limitMarkerTtl(Duration.ofSeconds(10));
                assertEquals(Duration.ofSeconds(10), creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
                creator.limitMarkerTtl(-1);
                assertNull(creator.getStreamCreatorCopy().getSubjectDeleteMarkerTtl());
            }
        });
    }

    @Test
    public void testLimitMarkerBehavior() throws Exception {
        runInShared(VersionUtils::atLeast2_12, nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                String bucket = random();
                String key1 = random();
                String key2 = random();
                String key3 = random();

                kvCtx.kvCreate(bucket, cr -> cr.limitMarkerTtl(Duration.ofSeconds(5)));

                KeyValue kv = kvm.keyValue(bucket);

                AtomicInteger wPuts = new AtomicInteger();
                AtomicInteger wDels = new AtomicInteger();
                AtomicInteger wPurges = new AtomicInteger();
                AtomicInteger wEod = new AtomicInteger();

                KeyValueWatcher watcher = new KeyValueWatcher() {
                    @Override
                    public void watch(KeyValueEntry keyValueEntry) {
                        if (keyValueEntry.getOperation() == KeyValueOperation.PUT) {
                            wPuts.incrementAndGet();
                        }
                        else if (keyValueEntry.getOperation() == KeyValueOperation.DELETE) {
                            wDels.incrementAndGet();
                        }
                        else if (keyValueEntry.getOperation() == KeyValueOperation.PURGE) {
                            wPurges.incrementAndGet();
                        }
                    }

                    @Override
                    public void endOfData() {
                        wEod.incrementAndGet();
                    }
                };

                //noinspection resource
                KeyValueWatchSubscription watch = kv.watchAll(watcher);

                AtomicInteger rMessages = new AtomicInteger();
                AtomicInteger rPurges = new AtomicInteger();
                AtomicInteger rMaxAges = new AtomicInteger();
                AtomicInteger rTtl2 = new AtomicInteger();
                AtomicInteger rTtl5 = new AtomicInteger();

                MessageHandler rawHandler = msg -> {
                    rMessages.incrementAndGet();
                    if (msg.hasHeaders()) {
                        String h = msg.getHeaders().getFirst("KV-Operation");
                        if (h != null && h.equals("PURGE")) {
                            rPurges.incrementAndGet();
                        }
                        h = msg.getHeaders().getFirst("Nats-Marker-Reason");
                        if (h != null && h.equals("MaxAge")) {
                            rMaxAges.incrementAndGet();
                        }
                        h = msg.getHeaders().getFirst("Nats-TTL");
                        if (h != null) {
                            if (h.equals("2s")) {
                                rTtl2.incrementAndGet();
                            }
                            else {
                                rTtl5.incrementAndGet();
                            }
                        }
                    }
                };

                PushConsumerCreator creator = new PushConsumerCreator().filterSubject(">");
                //noinspection unused
                JetStreamPushSubscription sub = kvCtx.js.pushSubscribe("KV_" + bucket, creator, rawHandler);

                kv.create(key1, dataBytes(), MessageTtl.seconds(2));
                kv.create(key2, dataBytes());
                kv.create(key3, dataBytes());

                assertNotNull(kv.get(key1));
                assertNotNull(kv.get(key2));
                assertNotNull(kv.get(key3));

                kv.purge(key2, MessageTtl.seconds(2));
                kv.purge(key3);

                // This section will have to be modified if there are changes
                // to how purge markers are handled (double purge on ttl purge, fix for no purge of non-ttl purge)
                sleep(8000); // longer than the message ttl plus the limit marker since double purge plus some extra

                assertNull(kv.get(key1));
                assertNull(kv.get(key2));
                assertNull(kv.get(key3));

                // create and put
                assertEquals(3, wPuts.get());
                assertEquals(3, wPurges.get()); // the 2 message ttl purge markers, and the manual purge.
                assertEquals(0, wDels.get());
                assertEquals(1, wEod.get());

                assertEquals(6, rMessages.get());
                assertEquals(2, rPurges.get());
                assertEquals(1, rMaxAges.get());
                assertEquals(2, rTtl2.get());
                assertEquals(1, rTtl5.get());
            }
        });
    }

    @Test
    public void testJustLimitMarkerCreatePurge() throws Exception {
        runInShared(VersionUtils::atLeast2_12, nc -> {
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                KeyValueManagement kvm = kvCtx.kvm;
                String bucket = random();
                String rawStream = "KV_" + bucket;
                String key = random();

                kvCtx.kvCreate(bucket, cr -> cr.limitMarkerTtl(Duration.ofSeconds(1)));

                KeyValue kv = kvm.keyValue(bucket);

                CountDownLatch errorLatch = new CountDownLatch(1);
                AtomicReference<String> error = new AtomicReference<>("");
                AtomicInteger messages = new AtomicInteger();
                List<String> ops = Collections.synchronizedList(new ArrayList<>());

                MessageHandler rawHandler = msg -> {
                    int mcount = messages.incrementAndGet();
                    String op = "null";
                    if (msg.hasHeaders()) {
                        op = msg.getHeaders().getFirst("KV-Operation");
                        if (op == null) {
                            op = msg.getHeaders().getFirst("Nats-Marker-Reason");
                            if (op == null) {
                                op = "PUT";
                            }
                        }
                    }
                    ops.add(op);
                    if (mcount == 1 || mcount == 3) {
                        if (!op.equals("PUT")) {
                            error.set("Invalid message, expected PUT (" + mcount + ") " + stringify(msg));
                            errorLatch.countDown();
                        }
                    }
                    else if (mcount == 2) {
                        if (!op.equals("MaxAge")) {
                            error.set("Invalid message, expected MaxAge (" + mcount + ") " + stringify(msg));
                            errorLatch.countDown();
                        }
                    }
                    else if (mcount == 4) {
                        if (!op.equals("PURGE")) {
                            error.set("Invalid message, expected PURGE (" + mcount + ") " + stringify(msg));
                            errorLatch.countDown();
                        }
                    }
                };

                PushConsumerCreator creator = new PushConsumerCreator().filterSubject(">");
                //noinspection unused
                JetStreamPushSubscription sub = kvCtx.js.pushSubscribe(rawStream, creator, rawHandler);

                long createdTimeMark = System.currentTimeMillis();
                kv.create(key, dataBytes(), MessageTtl.seconds(1));
                StreamInfo si = kvCtx.jsm.getStreamInfo(rawStream);
                assertEquals(1, si.getStreamState().getMessageCount());

                long purgedTimeMark = waitForPurge(kvCtx, rawStream);

                assertEquals(1, errorLatch.getCount(), error.get());
                assertEquals(2, messages.get());
                assertTrue(purgedTimeMark - createdTimeMark >= 1000); // ttl is 1 second, should take at least this long
                assertEquals("PUT", ops.get(0));
                assertEquals("MaxAge", ops.get(1));

                kv.create(key, dataBytes());
                si = kvCtx.jsm.getStreamInfo(rawStream);
                assertEquals(1, si.getStreamState().getMessageCount());

                kv.purge(key, MessageTtl.seconds(1));
                si = kvCtx.jsm.getStreamInfo(rawStream);
                assertEquals(1, si.getStreamState().getMessageCount());

                purgedTimeMark = waitForPurge(kvCtx, rawStream);
                assertEquals(1, errorLatch.getCount(), error.get());
                assertEquals(4, messages.get());
                assertTrue(purgedTimeMark - createdTimeMark >= 1000); // ttl is 1 second, should take at least this long
                assertEquals("PUT", ops.get(2));
                assertEquals("PURGE", ops.get(3));
            }
        });
    }

        private static long waitForPurge(KvTestingContext ctx, String rawStream) throws JetStreamException, InterruptedException {
            for (int tries = 0; tries < 20; tries++) {
                sleep(500); // it takes a bit of time for the purge to happen, depends on the server load
                StreamInfo si = ctx.jsm.getStreamInfo(rawStream);
                if (si.getStreamState().getMessageCount() == 0) {
                    return System.currentTimeMillis();
                }
            }
            return -1;
        }

        @Test
        public void testJustTtlForDeletePurge() throws Exception {
            runInShared(VersionUtils::atLeast2_12, nc -> {
                try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                    KeyValueManagement kvm = kvCtx.kvm;

                    String bucket = random();
                    String rawStream = "KV_" + bucket;
                    String key = random();

                    kvCtx.kvCreate(bucket, cr -> cr.ttl(Duration.ofSeconds(1)));

                    KeyValue kv = kvm.keyValue(bucket);

                    CountDownLatch errorLatch = new CountDownLatch(1);
                    AtomicReference<String> error = new AtomicReference<>("");
                    AtomicInteger messages = new AtomicInteger();
                    List<String> ops = Collections.synchronizedList(new ArrayList<>());

                    MessageHandler rawHandler = msg -> {
                        int mcount = messages.incrementAndGet();
                        String op = "null";
                        if (msg.hasHeaders()) {
                            op = msg.getHeaders().getFirst("KV-Operation");
                            if (op == null) {
                                op = "PUT";
                            }
                        }
                        ops.add(op);
                        if (mcount == 1 || mcount == 3) {
                            if (!op.equals("PUT")) {
                                error.set("Invalid message, expected PUT (" + mcount + ") " + stringify(msg));
                                errorLatch.countDown();
                            }
                        }
                        else if (mcount == 2) {
                            if (!op.equals("DEL")) {
                                error.set("Invalid message, expected DEL (" + mcount + ") " + stringify(msg));
                                errorLatch.countDown();
                            }
                        }
                        else if (mcount == 4) {
                            if (!op.equals("PURGE")) {
                                error.set("Invalid message, expected PURGE (" + mcount + ") " + stringify(msg));
                                errorLatch.countDown();
                            }
                        }
                    };

                    PushConsumerCreator pushConsumerCreator = new PushConsumerCreator();
                    //noinspection unused
                    JetStreamPushSubscription sub = kvCtx.js.pushSubscribe(rawStream, pushConsumerCreator, rawHandler);

                    kv.create(key, dataBytes());
                    StreamInfo si = kvCtx.jsm.getStreamInfo(rawStream);
                    assertEquals(1, si.getStreamState().getMessageCount());

                    kv.delete(key);
                    long createdTimeMark = System.currentTimeMillis();
                    si = kvCtx.jsm.getStreamInfo(rawStream);
                    assertEquals(1, si.getStreamState().getMessageCount());

                    long purgedTimeMark = waitForPurge(kvCtx, rawStream);
                    assertEquals(1, errorLatch.getCount(), error.get());
                    assertEquals(2, messages.get());
                    assertTrue(purgedTimeMark - createdTimeMark >= 1000);
                    assertEquals("PUT", ops.get(0));
                    assertEquals("DEL", ops.get(1));

                    kv.create(key, dataBytes());
                    createdTimeMark = System.currentTimeMillis();
                    kv.purge(key);

                    purgedTimeMark = waitForPurge(kvCtx, rawStream);
                    assertEquals(1, errorLatch.getCount(), error.get());
                    assertEquals(4, messages.get());
                    assertTrue(purgedTimeMark - createdTimeMark >= 1000);
                    assertEquals("PUT", ops.get(2));
                    assertEquals("PURGE", ops.get(3));
                }
            });
        }

        public static String stringify(Message msg) {
            return msg.metaData().streamSequence()
                + "/" + msg.metaData().consumerSequence()
                + "|" + msg.getSubject()
                + "|";
        }

        @Test
        public void testKeyValueOperation() {
            assertEquals(KeyValueOperation.PUT, KeyValueOperation.instance("PUT"));
            assertEquals(KeyValueOperation.DELETE, KeyValueOperation.instance("DEL"));
            assertEquals(KeyValueOperation.PURGE, KeyValueOperation.instance("PURGE"));
            assertNull(KeyValueOperation.instance("not-found"));
            assertEquals(KeyValueOperation.PUT, KeyValueOperation.getOrDefault("PUT", KeyValueOperation.PUT));
            assertEquals(KeyValueOperation.PUT, KeyValueOperation.getOrDefault("not-found", KeyValueOperation.PUT));
            assertEquals(KeyValueOperation.DELETE, KeyValueOperation.instanceByMarkerReason("Remove"));
            assertEquals(KeyValueOperation.PURGE, KeyValueOperation.instanceByMarkerReason("MaxAge"));
            assertEquals(KeyValueOperation.PURGE, KeyValueOperation.instanceByMarkerReason("Purge"));
            assertNull(KeyValueOperation.instanceByMarkerReason("not-found"));
        }
    }
