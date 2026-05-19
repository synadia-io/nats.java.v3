package io.synadia.client.os;

import io.nats.json.DateTimeUtils;
import io.synadia.client.Message;
import io.synadia.client.NUID;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.PushOrderedConsumerCreator;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.impl.*;
import io.synadia.client.utils.Digester;

import java.io.*;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.synadia.client.os.ObjectStoreUtil.*;
import static io.synadia.client.utils.JetStreamClientError.*;
import static io.synadia.client.utils.NatsConstants.GREATER_THAN;
import static io.synadia.client.utils.Validator.validateNotNull;

public class ObjectStore extends AbstractBucketFeature {

    private final String rawChunkPrefix;
    private final String rawMetaPrefix;

    ObjectStore(String bucketName, NatsConnection connection, ObjectStoreOptions oso) throws IOException {
        super(bucketName, connection, oso);
        rawChunkPrefix = toChunkPrefix(bucketName);
        rawMetaPrefix = toMetaPrefix(bucketName);
    }

    private ObjectStore(String bucketName, ObjectStore existing) throws IOException {
        super(bucketName, existing);
        rawChunkPrefix = toChunkPrefix(bucketName);
        rawMetaPrefix = toMetaPrefix(bucketName);
    }

    @Override
    protected String toStreamName(String bucketName) {
        return ObjectStoreUtil.toStreamName(bucketName);
    }

    String rawChunkSubject(String nuid) {
        return rawChunkPrefix + nuid;
    }

    String rawMetaSubject(String name) {
        return rawMetaPrefix + encodeForSubject(name);
    }

    String rawAllMetaSubject() {
        return rawMetaPrefix + GREATER_THAN;
    }

    /**
     * Get the name of the object store's bucket.
     * @return the name
     */
    public String getBucketName() {
        return bucketName;
    }

    private ObjectInfo publishMeta(ObjectInfo info) throws IOException, JetStreamApiException {
        js.publish(NatsMessage.builder()
            .subject(rawMetaSubject(info.getObjectName()))
            .headers(getMetaHeaders())
            .data(info.serialize())
            .build()
        );
        return ObjectInfo.builder(info).modified(DateTimeUtils.gmtNow()).build();
    }

    /**
     * Place the contents of the input stream into a new object.
     * @param meta the metadata for the object
     * @param inputStream the source input stream
     * @return the ObjectInfo for the saved object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws NoSuchAlgorithmException if the Digest Algorithm is not known. Currently, the only supported algorithm is SHA-256
     */
    public ObjectInfo put(ObjectMeta meta, InputStream inputStream) throws IOException, JetStreamApiException, NoSuchAlgorithmException {
        validateNotNull(meta, "ObjectMeta");
        validateNotNull(meta.getObjectName(), "ObjectMeta name");
        validateNotNull(inputStream, "InputStream");
        validateNotNull(meta.getObjectMetaOptions(), "Meta Options");
        if (meta.getObjectMetaOptions().getLink() != null) {
            throw OsLinkNotAllowOnPut.instance();
        }

        ObjectInfo newInfo;
        ObjectInfo oldInfo = getInfo(meta.getObjectName());

        String nuid = NUID.nextGlobal();
        String chunkSubject = rawChunkSubject(nuid);

        int chunkSize = meta.getObjectMetaOptions().getChunkSize();
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }

        try {
            Digester digester = new Digester();
            long totalSize = 0; // track total bytes read to make sure
            int chunks = 0;

            // working with chunkSize number of bytes each time.
            byte[] buffer = new byte[chunkSize];
            int red = inputStream.read(buffer);
            while (red != -1) { // keep reading while not receiving the end of file mark (-1)
                // copy if red is less than buffer length
                byte[] payload = red == buffer.length ? buffer : Arrays.copyOfRange(buffer, 0, red);

                // digest the actual bytes
                digester.update(payload);

                // publish the payload
                js.publish(chunkSubject, payload);

                // track total chunks and bytes
                chunks++;
                totalSize += red;

                red = inputStream.read(buffer);
            }

            newInfo = publishMeta(ObjectInfo.builder(bucketName, meta)
                .size(totalSize)
                .chunks(chunks)
                .nuid(nuid)
                .chunkSize(chunkSize)
                .digest(digester.getDigestEntry())
                .build());
        }
        catch (IOException | JetStreamApiException | NoSuchAlgorithmException e) {
            try {
                jsm.purgeStream(streamName, PurgeOptions.subject(rawChunkSubject(nuid)));
            }
            catch (Exception ignore) {}
            throw e;
        }
        finally {
            try { inputStream.close(); } catch (IOException ignore) {}
        }

        if (oldInfo != null) {
            try {
                jsm.purgeStream(streamName, PurgeOptions.builder().subject(rawChunkSubject(oldInfo.getNuid())).build());
            }
            catch (IOException | JetStreamApiException ignore) {}
        }

        return newInfo;
    }

    /**
     * Place the contents of the input stream into a new object.
     * @param objectName the name of the object
     * @param inputStream the source input stream
     * @return the ObjectInfo for the saved object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws NoSuchAlgorithmException if the Digest Algorithm is not known. Currently, the only supported algorithm is SHA-256
     */
    public ObjectInfo put(String objectName, InputStream inputStream) throws IOException, JetStreamApiException, NoSuchAlgorithmException {
        return put(ObjectMeta.objectName(objectName), inputStream);
    }

    /**
     * Place the bytes into a new object.
     * @param objectName the name of the object
     * @param input the bytes to store
     * @return the ObjectInfo for the saved object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws NoSuchAlgorithmException if the Digest Algorithm is not known. Currently, the only supported algorithm is SHA-256
     */
    public ObjectInfo put(String objectName, byte[] input) throws IOException, JetStreamApiException, NoSuchAlgorithmException {
        return put(ObjectMeta.objectName(objectName), new ByteArrayInputStream(input));
    }

    /**
     * Place the contents of the file into a new object using the file name as the object name.
     * @param file the file to read
     * @return the ObjectInfo for the saved object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws NoSuchAlgorithmException if the Digest Algorithm is not known. Currently, the only supported algorithm is SHA-256
     */
    public ObjectInfo put(File file) throws IOException, JetStreamApiException, NoSuchAlgorithmException {
        return put(ObjectMeta.objectName(file.getName()), Files.newInputStream(file.toPath()));
    }

    /**
     * Get an object by name from the store, reading it into the output stream, if the object exists.
     * @param objectName The name of the object
     * @param out the destination stream.
     * @return the ObjectInfo for the object name or throw an exception if it does not exist or is deleted.
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     * @throws NoSuchAlgorithmException if the Digest Algorithm is not known. Currently, the only supported algorithm is SHA-256
     */
    public ObjectInfo get(String objectName, OutputStream out) throws IOException, JetStreamApiException, InterruptedException, NoSuchAlgorithmException {
        ObjectInfo oi = getInfo(objectName, false);
        if (oi == null) {
            throw OsObjectNotFound.instance();
        }

        if (oi.isLink()) {
            ObjectLink link = oi.getLink();
            if (link == null || link.isBucketLink()) {
                throw OsGetLinkToBucket.instance();
            }

            // is the link in the same bucket
            if (link.getBucket().equals(bucketName)) {
                return get(link.getObjectName(), out);
            }

            // different bucket
            // get the store for the linked bucket, then get the linked object
            return new ObjectStore(link.getBucket(), this).get(link.getObjectName(), out);
        }

        Digester digester = new Digester();
        long totalBytes = 0;
        long totalChunks = 0;
        long expectedChunks = oi.getChunks();

        // if there is one chunk, just go get the message directly and we're done.
        if (oi.getChunks() == 1) {
            MessageInfo mi = jsm.getLastMessage(streamName, rawChunkSubject(oi.getNuid()));
            byte[] data = mi.getData();

            // track the byte count and chunks
            // update the digest
            // write the bytes to the output file
            totalBytes = data == null ? 0 : data.length;
            totalChunks = 1;
            digester.update(data);
            if (totalBytes > 0) {
                out.write(data);
            }
        }
        else {
            PushOrderedConsumerCreator creator = new PushOrderedConsumerCreator(streamName)
                .filterSubject(rawChunkSubject(oi.getNuid()));

            JetStreamPushSubscription sub = js.pushSubscribe(creator);

            Message m = sub.nextMessage(jsm.getTimeout());
            while (m != null) {
                // track the byte count and chunks
                long pending = m.metaData().pendingCount();
                if (expectedChunks != pending + (++totalChunks)) {
                    throw OsGetChunksMismatch.instance(); // short circuit, we already know there are not enough chunks.
                }

                byte[] data = m.getData();
                totalBytes += data.length;

                // update the digest
                digester.update(data);

                // write the bytes to the output file
                out.write(data);

                // read until the subject is complete
                if (pending == 0) {
                    break;
                }
                m = sub.nextMessage(jsm.getTimeout());
            }

            try {
                sub.unsubscribe();
            }
            catch (RuntimeException ignore) {}
        }

        if (totalChunks != oi.getChunks()) { throw OsGetChunksMismatch.instance(); }
        if (totalBytes != oi.getSize()) { throw OsGetSizeMismatch.instance(); }
        String digest = oi.getDigest();
        if (digest == null || !digester.matches(digest)) { throw OsGetDigestMismatch.instance(); }

        out.flush(); // moved after validation, no need if invalid

        return oi;
    }

    /**
     * Get the info for an object if the object exists / is not deleted.
     * @param objectName The name of the object
     * @return the ObjectInfo for the object name or throw an exception if it does not exist.
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo getInfo(String objectName) throws IOException, JetStreamApiException {
        return getInfo(objectName, false);
    }

    /**
     * Get the info for an object if the object exists, optionally including deleted.
     * @param objectName The name of the object
     * @param includingDeleted whether to return info for deleted objects
     * @return the ObjectInfo for the object name or throw an exception if it does not exist.
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo getInfo(String objectName, boolean includingDeleted) throws IOException, JetStreamApiException {
        MessageInfo mi = _getLast(rawMetaSubject(objectName));
        if (mi == null) {
            return null;
        }
        ObjectInfo info = new ObjectInfo(mi);
        return includingDeleted || !info.isDeleted() ? info : null;
    }

    /**
     * Update the metadata of name, description or headers. All other changes are ignored.
     * @param objectName The name of the object
     * @param meta the metadata with the new or unchanged name, description and headers.
     * @return the ObjectInfo after update
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo updateMeta(String objectName, ObjectMeta meta) throws IOException, JetStreamApiException {
        validateNotNull(objectName, "object name");
        validateNotNull(meta, "ObjectMeta");
        validateNotNull(meta.getObjectName(), "ObjectMeta name");

        ObjectInfo currentInfo = getInfo(objectName, true);
        if (currentInfo == null) {
            throw OsObjectNotFound.instance();
        }
        if (currentInfo.isDeleted()) {
            throw OsObjectIsDeleted.instance();
        }

        boolean nameChange = !objectName.equals(meta.getObjectName());
        if (nameChange) {
            if (getInfo(meta.getObjectName(), false) != null) {
                throw OsObjectAlreadyExists.instance();
            }
        }

        currentInfo = publishMeta(ObjectInfo.builder(currentInfo)
            .objectName(meta.getObjectName())   // replace the name
            .description(meta.getDescription()) // replace the description
            .headers(meta.getHeaders())         // replace the headers
            .build());

        if (nameChange) {
            // delete the meta from the old name via purge stream for subject
            jsm.purgeStream(streamName, PurgeOptions.subject(rawMetaSubject(objectName)));
        }

        return currentInfo;
    }

    /**
     * Delete the object by name. A No-op if the object is already deleted.
     * @param objectName The name of the object
     * @return the ObjectInfo after delete or throw an exception if it does not exist.
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo delete(String objectName) throws IOException, JetStreamApiException {
        ObjectInfo info = getInfo(objectName, true);
        if (info == null) {
            throw OsObjectNotFound.instance();
        }

        if (info.isDeleted()) {
            return info;
        }

        ObjectInfo deleted = publishMeta(ObjectInfo.builder(info)
            .deleted(true)
            .size(0)
            .chunks(0)
            .digest(null)
            .build());

        jsm.purgeStream(streamName, PurgeOptions.subject(rawChunkSubject(info.getNuid())));
        return deleted;
    }

    /**
     * Add a link to another object. A link cannot be for another link.
     * @param objectName The name of the object
     * @param toInfo the info object of the object to link to
     * @return the ObjectInfo for the link as saved or throws an exception
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo addLink(String objectName, ObjectInfo toInfo) throws IOException, JetStreamApiException {
        validateNotNull(objectName, "object name");
        validateNotNull(toInfo, "Link-To ObjectInfo");
        validateNotNull(toInfo.getObjectName(), "Link-To ObjectMeta");

        if (toInfo.isDeleted()) {
            throw OsObjectIsDeleted.instance();
        }

        if (toInfo.isLink()) {
            throw OsCantLinkToLink.instance();
        }

        ObjectInfo info = getInfo(objectName, false);
        if (info != null && !info.isLink()) {
            throw OsObjectAlreadyExists.instance();
        }

        return publishMeta(ObjectInfo.builder(bucketName, objectName)
            .nuid(NUID.nextGlobal())
            .objectLink(toInfo.getBucket(), toInfo.getObjectName())
            .build());
    }

    /**
     * Add a link to another object store (bucket).
     * @param objectName The name of the object
     * @param toStore the store object to link to
     * @return the ObjectInfo for the link as saved or throws an exception
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectInfo addBucketLink(String objectName, ObjectStore toStore) throws IOException, JetStreamApiException {
        validateNotNull(objectName, "object name");
        validateNotNull(toStore, "Link-To ObjectStore");

        ObjectInfo info = getInfo(objectName, false);
        if (info != null && !info.isLink()) {
            throw OsObjectAlreadyExists.instance();
        }

        return publishMeta(ObjectInfo.builder(bucketName, objectName)
            .nuid(NUID.nextGlobal())
            .bucketLink(toStore.getBucketName())
            .build());
    }

    /**
     * Close (seal) the bucket to changes. The store (bucket) will be read only.
     * @return the status object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ObjectStoreStatus seal() throws IOException, JetStreamApiException {
        StreamInfo si = jsm.getStreamInfo(streamName);
        si = jsm.updateStream(new StreamCreator(si.getConfiguration()).seal());
        return new ObjectStoreStatus(si);
    }

    /**
     * Get a list of all object [infos] in the store.
     * @return the list of objects
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public List<ObjectInfo> getList() throws IOException, JetStreamApiException, InterruptedException {
        List<ObjectInfo> list = new ArrayList<>();
        visitSubject(rawAllMetaSubject(), DeliverPolicy.LastPerSubject, false, true, m -> {
            ObjectInfo oi = new ObjectInfo(m);
            if (!oi.isDeleted()) {
                list.add(oi);
            }
        });
        return list;
    }

    /**
     * Create a watch on the store (bucket).
     * @param watcher the implementation to receive changes.
     * @param watchOptions the watch options to apply. If multiple conflicting options are supplied, the last options wins.
     * @return the ObjectStoreWatchSubscription
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws InterruptedException if the thread is interrupted
     */
    public ObjectStoreWatchSubscription watch(ObjectStoreWatcher watcher, ObjectStoreWatchOption... watchOptions) throws IOException, JetStreamApiException, InterruptedException {
        return new ObjectStoreWatchSubscription(this, watcher, watchOptions);
    }

    /**
     * Get the ObjectStoreStatus object.
     * @return the status object
     * @throws IOException covers various communication issues with the NATS server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data the request had an error related to the data
     */
    public ObjectStoreStatus getStatus() throws IOException, JetStreamApiException {
        return new ObjectStoreStatus(jsm.getStreamInfo(streamName));
    }
}
