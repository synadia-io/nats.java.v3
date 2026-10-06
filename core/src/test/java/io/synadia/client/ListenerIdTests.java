package io.synadia.client;

import io.synadia.client.impl.NatsConnection;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The default id methods each listener interface supplies, used as the map key when a listener is
 * attached to a connection. See {@link ConnectionListener#getConnectionListenerId()} and {@link ErrorListener#getErrorListenerId()}.
 */
public class ListenerIdTests extends TestBase {

    // ----------------------------------------------------------------------------------------------------
    // ConnectionListener
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testConnectionListenerIdDefault() {
        ConnectionListener cl = (conn, type, date, uriDetails) -> {};
        assertEquals("ConnectionListener-" + cl.hashCode(), cl.getConnectionListenerId());
    }

    @Test
    public void testConnectionListenerIdStable() {
        ConnectionListener cl = (conn, type, date, uriDetails) -> {};
        assertEquals(cl.getConnectionListenerId(), cl.getConnectionListenerId());
    }

    @Test
    public void testConnectionListenerIdDistinctPerInstance() {
        ConnectionListener cl1 = (conn, type, date, uriDetails) -> {};
        ConnectionListener cl2 = (conn, type, date, uriDetails) -> {};
        assertNotEquals(cl1.getConnectionListenerId(), cl2.getConnectionListenerId());
    }

    @Test
    public void testConnectionListenerIdOverride() {
        ConnectionListener cl = new ConnectionListener() {
            @Override
            public String getConnectionListenerId() {
                return "my-connection-listener";
            }

            @Override
            public void connectionEvent(NatsConnection conn, ConnectionEvent type, Long date, String uriDetails) {}
        };
        assertEquals("my-connection-listener", cl.getConnectionListenerId());
    }

    // ----------------------------------------------------------------------------------------------------
    // ErrorListener
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testErrorListenerIdDefault() {
        ErrorListener el = new ErrorListener() {};
        assertEquals("ErrorListener-" + el.hashCode(), el.getErrorListenerId());
    }

    @Test
    public void testErrorListenerIdStable() {
        ErrorListener el = new ErrorListener() {};
        assertEquals(el.getErrorListenerId(), el.getErrorListenerId());
    }

    @Test
    public void testErrorListenerIdDistinctPerInstance() {
        ErrorListener el1 = new ErrorListener() {};
        ErrorListener el2 = new ErrorListener() {};
        assertNotEquals(el1.getErrorListenerId(), el2.getErrorListenerId());
    }

    @Test
    public void testErrorListenerIdOverride() {
        ErrorListener el = new ErrorListener() {
            @Override
            public String getErrorListenerId() {
                return "my-error-listener";
            }
        };
        assertEquals("my-error-listener", el.getErrorListenerId());
    }

    // ----------------------------------------------------------------------------------------------------
    // A class can be both without a naming collision - the whole reason the methods are named separately.
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testBothRolesHaveIndependentIds() {
        BothListener both = new BothListener();
        assertEquals("ConnectionListener-" + both.hashCode(), both.getConnectionListenerId());
        assertEquals("ErrorListener-" + both.hashCode(), both.getErrorListenerId());
        assertNotEquals(both.getConnectionListenerId(), both.getErrorListenerId());
    }

    static class BothListener implements ConnectionListener, ErrorListener {
        @Override
        public void connectionEvent(NatsConnection conn, ConnectionEvent type, Long date, String uriDetails) {}
    }
}
