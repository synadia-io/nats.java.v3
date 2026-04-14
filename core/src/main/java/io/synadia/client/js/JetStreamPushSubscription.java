package io.synadia.client.js;

import io.synadia.client.impl.MessageManager;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.NatsDispatcher;

public class JetStreamPushSubscription extends JetStreamSubscription {

    JetStreamPushSubscription(String sid, String subject, String queueName,
                              NatsConnection connection, NatsDispatcher dispatcher,
                              JetStream js,
                              JetStreamSubscribeConfig subConf,
                              MessageManager manager) {
        super(sid, subject, queueName, connection, dispatcher, js, subConf, manager);
    }
}
