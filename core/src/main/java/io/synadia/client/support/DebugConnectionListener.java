package io.synadia.client.support;

import io.synadia.client.ConnectionEvents;
import io.synadia.client.ConnectionListener;
import io.synadia.client.impl.NatsConnection;

public class DebugConnectionListener implements ConnectionListener {
    String label;

    public DebugConnectionListener() {
        label(null);
    }

    public DebugConnectionListener(String label) {
        label(label);
    }

    public void label(String clLabel) {
        this.label = clLabel == null ? "CL" : clLabel;
    }

    @Override
    public void connectionEvent(NatsConnection conn, ConnectionEvents type) {
        if (label != null) {
            Debug.info(label, "%s/%s/%s", Integer.toHexString(conn.hashCode()), conn.getStatus(), type.getEvent());
        }
    }

    @Override
    public void connectionEvent(NatsConnection conn, ConnectionEvents type, Long time, String uriDetails) {
        if (label != null) {
            Debug.info(label, "%s@%s", Integer.toHexString(conn.hashCode()).toUpperCase(), time, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
    }
}
