package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.synadia.client.api.ConsumerPauseResponse;
import io.synadia.client.api.PurgeResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class ResponseTests {

    @Test
    public void testPurgeResponse() {
        String json = dataAsString("PurgeResponse.json");
        PurgeResponse pr = new PurgeResponse(getDataMessage(json));
        assertTrue(pr.isSuccess());
        assertEquals(5, pr.getPurged());
        assertNotNull(pr.toString()); // COVERAGE
    }

    @Test
    public void testPauseResponse() {
        String json = dataAsString("ConsumerPauseResponse.json");
        ConsumerPauseResponse pr = new ConsumerPauseResponse(getDataMessage(json));
        assertTrue(pr.isPaused());
        assertEquals(DateTimeUtils.parseDateTime("2024-03-02T13:21:45.198423724Z"), pr.getPauseUntil());
        assertEquals(Duration.ofSeconds(30), pr.getPauseRemaining());
        assertNotNull(pr.toString()); // COVERAGE
    }

    @Test
    public void testPauseResumeResponse() {
        String json = dataAsString("ConsumerResumeResponse.json");
        ConsumerPauseResponse pr = new ConsumerPauseResponse(getDataMessage(json));
        assertFalse(pr.isPaused());
        assertEquals(DateTimeUtils.parseDateTime("0001-01-01T00:00:00Z"), pr.getPauseUntil());
        assertNull(pr.getPauseRemaining());
        assertNotNull(pr.toString()); // COVERAGE
    }
}
