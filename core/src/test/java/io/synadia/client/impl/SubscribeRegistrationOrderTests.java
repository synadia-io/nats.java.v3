package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.Options;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// The server can push the first message the instant it has the SUB - a JetStream consumer with
// messages already waiting does exactly that. So the handler must be registered on the dispatcher
// BEFORE the SUB goes out. Registering after leaves a window where the message is queued to the
// dispatcher but its sid has no handler, and NatsDispatcher's run loop cannot route it.
//
// That window is too narrow to hit on demand, so these tests assert the ordering itself: the
// connection is subclassed to look at the dispatcher's handler map at the moment the SUB is sent.
public class SubscribeRegistrationOrderTests extends TestBase {

    @Test
    public void testHandlerIsRegisteredBeforeTheSubIsSent() {
        OrderProbeConnection conn = new OrderProbeConnection(Options.builder().build());
        NatsDispatcher d = new NatsDispatcher(conn, null); // no default handler, as an internal one has none
        d.startImpl(false); // marks it running without spawning the drain thread

        MessageHandler handler = msg -> {};
        conn.watch(d);
        NatsSubscription sub = d._subscribeByFactory(random(), null, handler, null);

        assertNotNull(conn.handlerAtSendTime.get(),
            "the SUB was sent before the handler was registered - a fast push would be unroutable");
        assertSame(handler, conn.handlerAtSendTime.get(), "a different handler was registered");
        assertEquals(sub.getSID(), conn.sidAtSendTime.get());
    }

    @Test
    public void testReSubscribeRegistersBeforeTheSubIsSent() {
        OrderProbeConnection conn = new OrderProbeConnection(Options.builder().build());
        NatsDispatcher d = new NatsDispatcher(conn, null);
        d.startImpl(false);

        MessageHandler first = msg -> {};
        conn.watch(d);
        NatsSubscription sub = d._subscribeByFactory(random(), null, first, null);

        MessageHandler second = msg -> {};
        conn.reset();
        String newSid = d.reSubscribe(sub, random(), null, second);

        assertNotNull(conn.handlerAtSendTime.get(),
            "reSubscribe sent the SUB before registering - a fast push would be unroutable");
        assertSame(second, conn.handlerAtSendTime.get());
        assertEquals(newSid, conn.sidAtSendTime.get());
    }

    // Records what the dispatcher knew at the exact moment the SUB was handed to the writer.
    private static class OrderProbeConnection extends NatsConnection {
        final AtomicReference<MessageHandler> handlerAtSendTime = new AtomicReference<>();
        final AtomicReference<String> sidAtSendTime = new AtomicReference<>();
        private NatsDispatcher watched;

        OrderProbeConnection(Options options) {
            super(options);
        }

        void watch(NatsDispatcher d) {
            watched = d;
        }

        void reset() {
            handlerAtSendTime.set(null);
            sidAtSendTime.set(null);
        }

        @Override
        protected void sendSubscriptionMessage(String sid, String subject, String queueName, boolean treatAsInternal) {
            if (watched != null) {
                sidAtSendTime.set(sid);
                handlerAtSendTime.set(watched.getNonDefaultHandlerBySid(sid));
            }
            // deliberately not calling super - there is no connection, and the protocol write is
            // not what this test is about
        }
    }
}
