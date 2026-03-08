package io.nats.client.impl;

import io.nats.client.Message;
import io.nats.client.ReadListener;

public class ReaderListenerConsoleImpl implements ReadListener {
    @Override
    public void protocol(String op, String text) {
        System.out.println("RL/Protocol " + op + " " + text);
    }

    @Override
    public void message(String op, Message message) {
        String text = op
            + " " + message.getSubject()
            + " " + message.getReplyTo()
            + " data length: " + message.getData().length;
        if (message.isJetStream()) {
            System.out.println("RL/JS-Message " + text);
        }
        else {
            System.out.println("RL/Message " + text);
        }
    }
}
