package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.ReadListener;

/**
 * A read listener that prints everything the reader sees to standard out. Intended for debugging.
 */
public class ReaderListenerConsoleImpl implements ReadListener {
    /**
     * Create a console read listener.
     */
    public ReaderListenerConsoleImpl() {}

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
