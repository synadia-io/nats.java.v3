package io.nats.client.impl;

/**
 * The subscription factory allows for construction of the NatSubscription for different
 * subscription types, i.e. core subscription versus JetStream subscription
 */
interface NatsSubscriptionFactory {
    NatsSubscription createNatsSubscription(String sid, String subject, String queueName, NatsConnection connection, NatsDispatcher dispatcher);
}
