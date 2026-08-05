package io.synadia.client.impl;

/**
 * The subscription factory allows for construction of the NatSubscription for different
 * subscription types, i.e. core subscription versus JetStream subscription
 */
public interface NatsSubscriptionFactory {
    /**
     * Create the subscription instance for a subscribe request.
     * @param sid the connection unique subscription id
     * @param subject the subject to subscribe to
     * @param queueName the queue group name, or null if this is not a queue subscription
     * @param connection the connection the subscription belongs to
     * @param dispatcher the dispatcher delivering the messages, or null for a synchronous subscription
     * @return the new subscription
     */
    NatsSubscription createNatsSubscription(String sid, String subject, String queueName, NatsConnection connection, NatsDispatcher dispatcher);
}
