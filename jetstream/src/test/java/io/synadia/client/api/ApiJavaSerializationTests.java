package io.synadia.client.api;

import io.synadia.client.impl.ConsumeOptions;
import io.synadia.client.impl.FetchConsumeOptions;
import io.synadia.client.impl.JetStreamTestBase;
import org.junit.jupiter.api.Test;

import java.io.*;

import static io.synadia.client.impl.BaseConsumeOptions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ApiJavaSerializationTests extends JetStreamTestBase {

    // ----------------------------------------------------------------------------------------------------
    // ConsumeOptions
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testConsumeOptions() throws IOException, ClassNotFoundException {
        ConsumeOptions co = ConsumeOptions.builder().build();
        check_values(co, DEFAULT_MESSAGE_COUNT, 0, DEFAULT_THRESHOLD_PERCENT, null, -1, -1);
        check_values(roundTripSerialize(co), DEFAULT_MESSAGE_COUNT, 0, DEFAULT_THRESHOLD_PERCENT, null, -1, -1);

        co = ConsumeOptions.builder().batchSize(1000).thresholdPercent(50).build();
        check_values(co, 1000, 0, 50, null, -1, -1);
        check_values(roundTripSerialize(co), 1000, 0, 50, null, -1, -1);

        co = ConsumeOptions.builder().batchBytes(2000).build();
        check_values(co, DEFAULT_MESSAGE_COUNT_WHEN_BYTES, 2000, DEFAULT_THRESHOLD_PERCENT, null, -1, -1);
        check_values(roundTripSerialize(co), DEFAULT_MESSAGE_COUNT_WHEN_BYTES, 2000, DEFAULT_THRESHOLD_PERCENT, null, -1, -1);

        co = ConsumeOptions.builder().group("g").minPending(1).minAckPending(2).build();
        check_values(co, DEFAULT_MESSAGE_COUNT, 0, DEFAULT_THRESHOLD_PERCENT, "g", 1, 2);
        check_values(roundTripSerialize(co), DEFAULT_MESSAGE_COUNT, 0, DEFAULT_THRESHOLD_PERCENT, "g", 1, 2);
    }

    private void check_values(ConsumeOptions co, int batchSize, long batchBytes, int thresholdPercent, String group, long minPending, long minAckPending) {
        assertEquals(batchSize, co.getBatchSize());
        assertEquals(batchBytes, co.getBatchBytes());
        assertEquals(thresholdPercent, co.getThresholdPercent());
        assertEquals(group, co.getGroup());
        assertEquals(minPending, co.getMinPending());
        assertEquals(minAckPending, co.getMinAckPending());
    }

    private ConsumeOptions roundTripSerialize(ConsumeOptions co) throws IOException, ClassNotFoundException {
        return (ConsumeOptions) roundTripSerialize((Serializable) co);
    }

    // ----------------------------------------------------------------------------------------------------
    // FetchConsumeOptions
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testFetchConsumeOptions() throws IOException, ClassNotFoundException {
        FetchConsumeOptions fco = FetchConsumeOptions.builder().build();
        check_default_values(fco);
        check_default_values(roundTripSerialize(fco));

        fco = FetchConsumeOptions.builder().maxMessages(1000).build();
        check_values(fco, 1000, 0, DEFAULT_THRESHOLD_PERCENT);
        check_values(roundTripSerialize(fco), 1000, 0, DEFAULT_THRESHOLD_PERCENT);

        fco = FetchConsumeOptions.builder().maxMessages(1000).thresholdPercent(50).build();
        check_values(fco, 1000, 0, 50);
        check_values(roundTripSerialize(fco), 1000, 0, 50);

        fco = FetchConsumeOptions.builder().max(1000, 100).build();
        check_values(fco, 100, 1000, DEFAULT_THRESHOLD_PERCENT);
        check_values(roundTripSerialize(fco), 100, 1000, DEFAULT_THRESHOLD_PERCENT);

        fco = FetchConsumeOptions.builder().max(1000, 100).thresholdPercent(50).build();
        check_values(fco, 100, 1000, 50);
        check_values(roundTripSerialize(fco), 100, 1000, 50);

        fco = FetchConsumeOptions.builder().group("g").minPending(1).minAckPending(2).build();
        assertEquals("g", fco.getGroup());
        assertEquals(1, fco.getMinPending());
        assertEquals(2, fco.getMinAckPending());

        fco = roundTripSerialize(fco);
        assertEquals("g", fco.getGroup());
        assertEquals(1, fco.getMinPending());
        assertEquals(2, fco.getMinAckPending());
    }

    private void check_default_values(FetchConsumeOptions fco) {
        assertEquals(DEFAULT_MESSAGE_COUNT, fco.getMaxMessages());
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS, fco.getExpiresInMillis());
        assertEquals(DEFAULT_THRESHOLD_PERCENT, fco.getThresholdPercent());
        assertEquals(0, fco.getMaxBytes());
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS * MAX_IDLE_HEARTBEAT_PERCENT / 100, fco.getIdleHeartbeat());
    }

    private void check_values(FetchConsumeOptions fco, int maxMessages, int maxBytes, int thresholdPercent) {
        assertEquals(maxMessages, fco.getMaxMessages());
        assertEquals(maxBytes, fco.getMaxBytes());
        assertEquals(thresholdPercent, fco.getThresholdPercent());
        assertNull(fco.getGroup());
        assertEquals(-1, fco.getMinPending());
        assertEquals(-1, fco.getMinAckPending());
    }

    private FetchConsumeOptions roundTripSerialize(FetchConsumeOptions fco) throws IOException, ClassNotFoundException {
        return (FetchConsumeOptions) roundTripSerialize((Serializable) fco);
    }

    // ----------------------------------------------------------------------------------------------------
    // Round trip
    // ----------------------------------------------------------------------------------------------------
    private Object roundTripSerialize(Serializable s) throws IOException, ClassNotFoundException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(s);
            oos.flush();
            return new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject();
        }
    }
}
