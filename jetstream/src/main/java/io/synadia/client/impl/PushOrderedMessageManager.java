package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.PushOrderedConsumerCreator;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.impl.MessageManager.ManageResult.MESSAGE;
import static io.synadia.client.impl.MessageManager.ManageResult.STATUS_HANDLED;

public class PushOrderedMessageManager extends PushMessageManager {

    protected final AtomicLong expectedExternalConsumerSeq;
    protected final AtomicReference<String> targetSid;

    protected PushOrderedMessageManager(
        NatsConnection conn,
        JetStream js,
        JetStreamSubscribeConfig subConf)
    {
        super(conn, js, subConf);
        expectedExternalConsumerSeq = new AtomicLong(1); // always starts at 1
        targetSid = new AtomicReference<>();
    }

    @Override
    public void startup(JetStreamSubscription sub) {
        expectedExternalConsumerSeq.set(1); // consumer always starts with consumer sequence 1
        super.startup(sub);
        targetSid.set(sub.getSID());
    }

    @Override
    public ManageResult manage(Message msg) {
        if (!msg.getSID().equals(targetSid.get())) {
            return STATUS_HANDLED; // wrong sid. message is a throwaway from previous consumer that errored
        }

        if (msg.isJetStream()) {
            long receivedConsumerSeq = msg.metaData().consumerSequence();
            if (expectedExternalConsumerSeq.get() != receivedConsumerSeq) {
                handleErrorCondition();
                return STATUS_HANDLED;
            }
            expectedExternalConsumerSeq.incrementAndGet();
            trackJsMessage(msg);
            return MESSAGE;
        }

        return manageStatus(msg);
    }

    @Override
    protected void handleHeartbeatError() {
        super.handleHeartbeatError();
        handleErrorCondition();
    }

    private void handleErrorCondition() {
        try {
            targetSid.set(null);
            expectedExternalConsumerSeq.set(1); // consumer always starts with consumer sequence 1

            // 1. re-subscribe. This means killing the sub then making a new one.
            //    New sub needs a new deliverSubject
            String newDeliverSubject = createInbox();
            sub.reSubscribe(newDeliverSubject);
            targetSid.set(sub.getSID());

            // 2. consumerCreatorForOrdered understands everything necessary to make a new creator from the old
            PushOrderedConsumerCreator creator =
                new PushOrderedConsumerCreator(
                    (PushOrderedConsumerCreator)subConf.orderedCreator, lastStreamSeq);
            creator.deliverSubject(newDeliverSubject);
            js._createConsumer(creator, ConsumerCreateRequest.Action.Create); // this can fail when a server is down.

            // 3. restart the manager.
            startup(sub);
        }
        catch (Exception e) {
            // don't want this doubly failing for any reason
            try {
                js.conn.processException(e);
            }
            catch (Exception ignore) {}
            initOrResetHeartbeatTimer();
        }
    }
}
