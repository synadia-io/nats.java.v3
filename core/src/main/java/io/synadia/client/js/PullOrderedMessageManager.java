package io.synadia.client.js;

import io.synadia.client.Message;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.jsapi.ConsumerConfiguration;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.impl.MessageManager.ManageResult.MESSAGE;
import static io.synadia.client.impl.MessageManager.ManageResult.STATUS_HANDLED;

public class PullOrderedMessageManager extends PullMessageManager {

    protected final ConsumerConfiguration originalCc;
    protected final JetStream js;
    protected final String stream;
    protected final AtomicLong expectedExternalConsumerSeq;
    protected final AtomicReference<String> targetSid;

    protected PullOrderedMessageManager(NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf) {
        super(conn, subConf);
        this.js = js;
        this.stream = subConf.consumerInfo.getStreamName();
        this.originalCc = subConf.consumerInfo.getConsumerConfiguration();
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
                targetSid.set(null);
                expectedExternalConsumerSeq.set(1); // consumer always starts with consumer sequence 1
                if (pullManagerObserver != null) {
                    pullManagerObserver.pullTerminatedByError();
                }
                return STATUS_HANDLED;
            }
            trackJsMessage(msg);
            checkForPin(msg);
            expectedExternalConsumerSeq.incrementAndGet();
            return MESSAGE;
        }

        return manageStatus(msg);
    }
}
