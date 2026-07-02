package io.synadia.client;

import io.synadia.client.api.Status;
import io.synadia.client.impl.AckType;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.JetStreamMetaData;
import io.synadia.client.impl.NatsConnection;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * The NATS library uses a Message object to encapsulate incoming messages. Applications
 * publish and send requests with raw strings and byte[] but incoming messages can have a few
 * values, so they need a wrapper.
 *
 * <p>The byte[] returned by {@link #getData() getData()} is not shared with any library code
 * and is safe to manipulate.
 *
 * NOTICE: This interface is intended only to be implemented internally,
 * although since it is public it is technically available to anyone and
 * breaking changes should be avoided. For instance:
 * - Signatures should not be changed.
 * - If methods are added, a default implementation should be provided.
 */
public interface Message {

	/**
	 * the subject that this message was sent to
	 * @return the subject
	 */
	String getSubject();

	/**
	 * the subject the application is expected to send a reply message on
	 * @return the reply to
	 */
	String getReplyTo();

	/**
	 * true if there are headers
	 * @return the flag
	 */
	boolean hasHeaders();

	/**
	 * the headers object for the message
	 * @return the headers
	 */
	Headers getHeaders();

	/**
	 * true if there is status
	 * @return the flag
	 */
	boolean isStatusMessage();

	/**
	 * the status object message if this is a status message
	 * @return the status object
	 */
	Status getStatus();

	/**
	 * the data from the message
	 * @return the data
	 */
	byte[] getData();

	/**
	 * the Subscription associated with this message, may be owned by a Dispatcher
	 * @return the subscription
	 */
	Subscription getSubscription();

	/**
	 * the id associated with the subscription, used by the connection when processing an incoming
	 * message from the server
	 * @return the SID
	 */
	String getSID();

	/**
	 * the connection which can be used for publishing, will be null if the subscription is null
	 * @return the connection
	 */
	NatsConnection getConnection();

	/**
	 * Gets the metadata associated with a JetStream message.
	 * metadata or null if the message is not a JetStream message.
	 * @return the metadata
	 */
	JetStreamMetaData metaData();

	/**
	 * the last ack that was done with this message
	 * the last ack or null
	 * @return the last ack
	 */
	AckType lastAck();

	/**
	 * ack acknowledges a JetStream messages received from a Consumer, indicating the message
	 * should not be received again later.
	 */
	void ack();

	/**
	 * ack acknowledges a JetStream message received from a Consumer, indicating the message
	 * should not be received again later, and waits for confirmation from the server.
	 * @param timeoutMillis the time in milliseconds to wait for an ack confirmation; less than 1 millisecond uses the default connection timeout
     * @throws TimeoutException if the NATS server does not return a response in time
     * @throws InterruptedException if the thread is interrupted
	 */
	void ackSync(long timeoutMillis) throws TimeoutException, InterruptedException;

	/**
	 * nak acknowledges a JetStream message has been received but indicates that the message
	 * is not completely processed and should be sent again later.
	 */
	void nak();

	/**
	 * nak acknowledges a JetStream message has been received but indicates that the message
	 * is not completely processed and should be sent again later, after at least the delay amount.
	 * @param nakDelayMillis tell the server how long to delay, in milliseconds, before processing the ack
	 */
	void nakWithDelay(long nakDelayMillis);

	/**
	 * nak acknowledges a JetStream message has been received but indicates that the message
	 * is not completely processed and should be sent again later, after at least the delay amount.
	 * Convenient when you want to express the delay in seconds or minutes (e.g. {@code Duration.ofMinutes(5)}).
	 * @param nakDelay how long to tell the server to delay before processing the ack
	 */
	void nakWithDelay(Duration nakDelay);

	/**
	 * term instructs the server to stop redelivery of this message without acknowledging it as
	 * successfully processed.
	 */
	void term();

	/**
	 *  Indicates that this message is being worked on and reset redelivery timer in the server.
	 */
	void inProgress();

	/**
	 * Checks if a message is from JetStream or is a standard message.
	 * @return true if the message is from JetStream.
	 */
	boolean isJetStream();

	/**
	 * The number of bytes the server counts for the message when calculating byte counts.
	 * Only applies to JetStream messages received from the server.
	 * @return the consumption byte count or -1 if the message implementation does not support this method
	 */
	long consumeByteCount();
}
