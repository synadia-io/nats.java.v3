package io.synadia.client.impl;

/**
 * The subscription returned for a push consumer, where the server delivers messages
 * to the deliver subject without the client asking for them.
 */
public class JetStreamPushSubscription extends JetStreamSubscription {

    JetStreamPushSubscription(String sid, String subject, String queueName,
                              NatsConnection connection, NatsDispatcher dispatcher,
                              JetStream js,
                              JetStreamSubscribeConfig subConf,
                              MessageManager manager) {
        super(sid, subject, queueName, connection, dispatcher, js, subConf, manager);
    }
}
