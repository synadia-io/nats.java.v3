package io.synadia.client.utils;

import static io.synadia.client.utils.ClientError.KIND_ILLEGAL_STATE;

/**
 * The client-side JetStream errors: each a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link ClientError#instance()} to build the exception to throw.
 */
public abstract class JetStreamClientError {
    private JetStreamClientError() {} /* ensures cannot be constructed */

    private static final String SUB = "SUB";

    /** No matching streams for subject. */
    public static final ClientError JsSubNoMatchingStreamForSubject = new ClientError(SUB, 90007, "No matching streams for subject.", KIND_ILLEGAL_STATE);

    /** Dispatcher without a handler cannot receive messages. */
    public static final ClientError JsSubDispatcherNoHandlerCantReceiveMessages = new ClientError(SUB, 90023, "Dispatcher without a handler cannot receive messages.", KIND_ILLEGAL_STATE);
}
