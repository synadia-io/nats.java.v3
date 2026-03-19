package io.synadia.compatibility;

import io.nats.json.JsonParseException;
import io.nats.json.JsonParser;
import io.nats.json.JsonValue;
import io.synadia.client.impl.NatsConnection;

import static io.nats.json.JsonValueUtils.readMapObjectOrEmpty;

public abstract class Command extends TestMessage {
    public final NatsConnection nc;

    // info from the message data
    public final JsonValue full;
    public final JsonValue config;

    protected Command(NatsConnection nc, TestMessage tm) {
        super(tm);
        this.nc = nc;

        JsonValue tempDataValue = null;
        JsonValue tempConfig = null;
        if (payload != null && payload.length > 0) {
            try {
                tempDataValue = JsonParser.parse(payload);
                Log.info("CMD", subject, tempDataValue .toJson());
                tempConfig = readMapObjectOrEmpty(tempDataValue, "config");
            }
            catch (JsonParseException e) {
                handleException(e);
            }
        }

        full = tempDataValue;
        config = tempConfig;
    }

    protected void respond() {
        Log.info("RESPOND " + subject);
        nc.publish(replyTo, null);
    }

    protected void respond(String payload) {
        Log.info("RESPOND " + subject + " with " + payload);
        nc.publish(replyTo, payload.getBytes());
    }

    @Override
    public String toString() {
        return full.toJson();
    }

    protected void handleException(Exception e) {
        Log.error(subject, e);
    }
}
