package io.synadia.client.utils;

import static io.synadia.client.utils.ClientError.KIND_ILLEGAL_ARGUMENT;
import static io.synadia.client.utils.ClientError.KIND_ILLEGAL_STATE;

/**
 * The client-side ObjectStore errors: each a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link ClientError#instance()} to build the exception to throw.
 */
public abstract class ObjectStoreClientError {
    private ObjectStoreClientError() {} /* ensures cannot be constructed */

    private static final String OS = "OS";

    /** The object was not found. The name is well-formed, the bucket does not hold it, so this is state. */
    public static final ClientError OsObjectNotFound = new ClientError(OS, 90201, "The object was not found.", KIND_ILLEGAL_STATE);
    /** The stored object is deleted. */
    public static final ClientError OsObjectIsDeleted = new ClientError(OS, 90202, "The object is deleted.", KIND_ILLEGAL_STATE);
    /** An object with that name already exists. The name is well-formed, the bucket already holds it, so this is state. */
    public static final ClientError OsObjectAlreadyExists = new ClientError(OS, 90203, "An object with that name already exists.", KIND_ILLEGAL_STATE);
    /** A link cannot link to another link. */
    public static final ClientError OsCantLinkToLink = new ClientError(OS, 90204, "A link cannot link to another link.", KIND_ILLEGAL_ARGUMENT);
    /** Digest does not match metadata. The download completed but the bytes do not, so this is state, not a bad argument. */
    public static final ClientError OsGetDigestMismatch = new ClientError(OS, 90205, "Digest does not match metadata.", KIND_ILLEGAL_STATE);
    /** Number of chunks does not match metadata. The download completed but the bytes do not, so this is state, not a bad argument. */
    public static final ClientError OsGetChunksMismatch = new ClientError(OS, 90206, "Number of chunks does not match metadata.", KIND_ILLEGAL_STATE);
    /** Total size does not match metadata. The download completed but the bytes do not, so this is state, not a bad argument. */
    public static final ClientError OsGetSizeMismatch = new ClientError(OS, 90207, "Total size does not match metadata.", KIND_ILLEGAL_STATE);
    /** Cannot get object, it is a link to a bucket. The stored object decides this, not the argument, so this is state. */
    public static final ClientError OsGetLinkToBucket = new ClientError(OS, 90208, "Cannot get object, it is a link to a bucket.", KIND_ILLEGAL_STATE);
    /** Link not allowed in metadata when putting an object. */
    public static final ClientError OsLinkNotAllowOnPut = new ClientError(OS, 90209, "Link not allowed in metadata when putting an object.", KIND_ILLEGAL_ARGUMENT);
    /** Cannot link to a deleted object. The caller supplied it, so unlike {@link #OsObjectIsDeleted} this is a bad argument. */
    public static final ClientError OsCantLinkToDeletedObject = new ClientError(OS, 90210, "Cannot link to a deleted object.", KIND_ILLEGAL_ARGUMENT);
}
