package io.synadia.client.os;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import org.jspecify.annotations.NullUnmarked;

import static io.nats.json.JsonValueUtils.readString;
import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.BUCKET;
import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.JsValidator.validateBucketName;

/**
 * The ObjectLink is used to embed links to other objects.
 */
@NullUnmarked
public class ObjectLink implements JsonSerializable {

    private final String bucket;
    private final String objectName;

    static ObjectLink optionalInstance(JsonValue vLink) {
        return vLink == null ? null : new ObjectLink(vLink);
    }

    ObjectLink(JsonValue vLink) {
        bucket = readString(vLink, BUCKET);
        objectName = readString(vLink, NAME);
    }

    private ObjectLink(String bucket, String objectName) {
        this.bucket = validateBucketName(bucket, true);
        this.objectName = objectName;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, BUCKET, bucket);
        addField(sb, NAME, objectName);
        return endJson(sb).toString();
    }

    /**
     * Get the bucket the linked object is in
     * @return the bucket name
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * Get the name of the object for the link
     * @return the object name
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * True if the object is a link to an object versus a link to a bucket
     * @return true if the object is a link
     */
    public boolean isObjectLink() {
        return objectName != null;
    }

    /**
     * True if the object is a bucket to an object versus a link to a link
     * @return true if the object is a bucket
     */
    public boolean isBucketLink() {
        return objectName == null;
    }

    /**
     * create a bucket link
     * @param bucket the bucket name
     * @return the ObjectLink
     */
    public static ObjectLink bucket(String bucket) {
        return new ObjectLink(bucket, null);
    }

    /**
     * create an object link
     * @param bucket the bucket the object is in
     * @param objectName the object name
     * @return the ObjectLink
     */
    public static ObjectLink object(String bucket, String objectName) {
        return new ObjectLink(bucket, objectName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectLink that = (ObjectLink) o;

        if (!bucket.equals(that.bucket)) return false; // bucket never null
        return objectName != null ? objectName.equals(that.objectName) : that.objectName == null;
    }

    @Override
    public int hashCode() {
        return bucket.hashCode() * 31
            + (objectName == null ? 0 : objectName.hashCode());
    }

    @Override
    public String toString() {
        return "ObjectLink{" +
            "bucket='" + bucket + '\'' +
            ", objectName='" + objectName + '\'' +
            '}';
    }
}
