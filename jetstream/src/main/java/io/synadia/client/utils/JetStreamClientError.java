package io.synadia.client.utils;

import static io.synadia.client.utils.ClientError.KIND_ILLEGAL_STATE;

/**
 * The client-side JetStream errors: each a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link ClientError#instance(Object...)} to build the exception to throw.
 *
 * <p>Every constant here is listed in the Client Error Messages table in jetstream/README.md. A new one goes in both.
 */
public abstract class JetStreamClientError {
    private JetStreamClientError() {} /* ensures cannot be constructed */

    private static final String SUB = "SUB";
    private static final String CON = "CON";

    /** No matching streams for subject. */
    public static final ClientError JsSubNoMatchingStreamForSubject = new ClientError(SUB, 90007, "No matching streams for subject.", KIND_ILLEGAL_STATE);

    /** Dispatcher without a handler cannot receive messages. */
    public static final ClientError JsSubDispatcherNoHandlerCantReceiveMessages = new ClientError(SUB, 90023, "Dispatcher without a handler cannot receive messages.", KIND_ILLEGAL_STATE);

    /** An ordered consumer context is already receiving messages. */
    public static final ClientError JsConsumerOrderedAlreadyReceiving = new ClientError(CON, 90304, "The ordered consumer is already receiving messages. Ordered Consumer does not allow multiple instances at time.", KIND_ILLEGAL_STATE);

    /** The operation is not allowed against a pinned client consumer. The label is the operation. */
    public static final ClientError JsConsumerPinnedNotAllowed = new ClientError(CON, 90305, "Pinned not allowed with %s.", KIND_ILLEGAL_STATE);
}
