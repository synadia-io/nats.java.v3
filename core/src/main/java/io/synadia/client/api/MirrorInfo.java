package io.synadia.client.api;

import io.nats.json.JsonValue;

/**
 * Information about an upstream stream source in a mirror
 */
public class MirrorInfo extends SourceInfoBase {

    static MirrorInfo optionalInstance(JsonValue vMirror) {
        return vMirror == null ? null : new MirrorInfo(vMirror);
    }

    MirrorInfo(JsonValue vMirror) {
        super(vMirror);
    }

    @Override
    public String toString() {
        return "MirrorInfo " + super.toString();
    }
}
