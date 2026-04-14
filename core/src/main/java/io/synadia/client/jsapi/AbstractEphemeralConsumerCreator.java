package io.synadia.client.jsapi;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * The AbstractEphemeralConsumerCreator helps you create an ephemeral consumer on the fly.
 * Also extend by durable creators since they only differ in allowing a durable name
 */
@NullMarked
public abstract class AbstractEphemeralConsumerCreator<T extends AbstractEphemeralConsumerCreator<T>> extends ConsumerCreator<T> {

    /**
     * Construct an AbstractEphemeralConsumerCreator instance
     * @param stream the stream name
     */
    protected AbstractEphemeralConsumerCreator(String stream, boolean isPush) {
        super(stream, isPush);
    }

    // ----------------------------------------------------------------------------------------------------
    // ConsumerFields setters beyond ConsumerCreator
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the name of the consumer.
     * Null or empty clears the field.
     * @param name name of the consumer.
     * @return this instance for chaining.
     */
    public T name(String name) {
        _name(name);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the acknowledgement policy
     * @param policy the acknowledgement policy.
     * @return this instance for chaining.
     */
    public T ackPolicy(AckPolicy policy) {
        _ackPolicy(policy);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the acknowledgement wait duration
     * @param timeout the wait timeout
     * @return this instance for chaining.
     */
    public T ackWait(Duration timeout) {
        _ackWait(timeout);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the acknowledgement wait duration
     * @param timeoutMillis the wait timeout in milliseconds
     * @return this instance for chaining.
     */
    public T ackWait(long timeoutMillis) {
        _ackWait(timeoutMillis);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the maximum delivery amount
     * @param maxDeliver the maximum delivery amount
     * @return this instance for chaining.
     */
    public T maxDeliver(long maxDeliver) {
        _maxDeliver(maxDeliver);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the maximum ack pending or null to unset / clear.
     * @param maxAckPending maximum pending acknowledgements.
     * @return this instance for chaining.
     */
    public T maxAckPending(Long maxAckPending) {
        _maxAckPending(maxAckPending);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the maximum ack pending.
     * @param maxAckPending maximum pending acknowledgements.
     * @return this instance for chaining.
     */
    public T maxAckPending(long maxAckPending) {
        _maxAckPending(maxAckPending);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the flow control on and set the idle heartbeat
     * @param idleHeartbeat the idle heart beat duration
     * @return this instance for chaining.
     */
    public T flowControl(Duration idleHeartbeat) {
        _flowControl(idleHeartbeat);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the flow control on and set the idle heartbeat
     * @param idleHeartbeatMillis the idle heart beat duration in milliseconds
     * @return this instance for chaining.
     */
    public T flowControl(long idleHeartbeatMillis) {
        _flowControl(idleHeartbeatMillis);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the number of replicas for the consumer. When set do not inherit the
     * replica count from the stream but specifically set it to this amount.
     * @param numReplicas number of replicas for the consumer
     * @return this instance for chaining.
     */
    public T numReplicas(int numReplicas) {
        _numReplicas(numReplicas);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the time to pause the consumer until.
     * @param pauseUntil the time to pause
     * @return this instance for chaining.
     */
    public T pauseUntil(ZonedDateTime pauseUntil) {
        _pauseUntil(pauseUntil);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the mem storage flag to force the consumer state to be kept
     * in memory rather than inherit the setting from the stream
     * @param memStorage the flag
     * @return this instance for chaining.
     */
    public T memStorage(Boolean memStorage) {
        _memStorage(memStorage);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the list of backoff. Will override ack wait setting.
     * @param backoffs zero or more backoff durations or an array of back off
     * @return this instance for chaining.
     * @see <a href="https://docs.nats.io/using-nats/developer/develop_jetstream/consumers#delivery-reliability">Delivery Reliability</a>
     */
    public T backoff(Duration... backoffs) {
        _backoff(backoffs);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the list of backoff. Will override ack wait setting.
     * @param backoffMillis zero or more or array of backoff in millis
     * @return this instance for chaining.
     */
    public T backoff(long... backoffMillis) {
        _backoff(backoffMillis);
        //noinspection unchecked
        return (T)this;
    }

    // ----------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return "EphemeralConsumerCreator " + toJson();
    }
}
