package io.synadia.client.support;

import io.synadia.client.Connection;
import io.synadia.client.ConnectionListener;

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
    public void connectionEvent(Connection conn, Events type) {
        if (label != null) {
            Debug.info(label, "%s/%s/%s", Integer.toHexString(conn.hashCode()), conn.getStatus(), type.getEvent());
        }
    }

    @Override
    public void connectionEvent(Connection conn, Events type, Long time, String uriDetails) {
        if (label != null) {
            Debug.info(label, "%s@%s", Integer.toHexString(conn.hashCode()).toUpperCase(), time, "%s(%s)", type.getEvent(), conn.getStatus(), uriDetails);
        }
    }
}
