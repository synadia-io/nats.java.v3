package io.synadia.client.api;

import io.nats.json.LazyJsonParser;
import io.synadia.client.Message;
import io.synadia.client.impl.JetStreamApiException;
import org.junit.jupiter.api.Test;

import static io.synadia.client.api.ApiResponse.NO_TYPE;
import static io.synadia.client.api.Error.*;
import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class ApiResponseTests {

    static class TestApiResponse extends ApiResponse<TestApiResponse> {
        TestApiResponse(String json) {
            super(LazyJsonParser.parseUnchecked(json));
        }

        TestApiResponse(Message msg) {
            super(msg);
        }

        TestApiResponse() { }

        public TestApiResponse(Error error) {
            super(error);
        }
    }

    @Test
    public void testNotErrorAndType() {
        TestApiResponse resp = new TestApiResponse(dataAsString("ConsumerInfo.json"));
        assertFalse(resp.hasError());
        assertNull(resp.getError());
        assertNotNull(new TestApiResponse().toString());
        assertEquals("io.nats.jetstream.api.v1.consumer_info_response", resp.getType());
    }

    @Test
    public void testGarbageJson() {
        TestApiResponse resp = new TestApiResponse(getDataMessage("notjson"));
        assertEquals(500, resp.getErrorCode());
        assertEquals(NOT_SET, resp.getApiErrorCode());
        assertNotNull(resp.getDescription());
        assertTrue(resp.getDescription().startsWith("Error parsing"));
        JetStreamApiException j = assertThrows(JetStreamApiException.class,
            () -> new TestApiResponse(getDataMessage("notjson")).throwOnHasError());
        assertTrue(j.getMessage().contains("Error parsing"));
    }

    @Test
    public void testErrorResponse() {
        String text = dataAsString("ErrorResponses.json.txt");
        String[] jsons = text.split("~");

        TestApiResponse resp = new TestApiResponse(jsons[0]);
        assertTrue(resp.hasError());
        assertEquals("code_and_desc_response", resp.getType());
        assertEquals(500, resp.getErrorCode());
        assertEquals("the description", resp.getDescription());
        assertNotNull(resp.getError());
        assertEquals("the description (500)", resp.getError());

        resp = new TestApiResponse(jsons[1]);
        assertTrue(resp.hasError());
        assertEquals("zero_and_desc_response", resp.getType());
        assertEquals(0, resp.getErrorCode());
        assertEquals("the description", resp.getDescription());
        assertEquals("the description (0)", resp.getError());
        assertNotNull(resp.getErrorObject());
        JetStreamApiException jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(0, jsApiEx.getErrorCode());
        assertEquals("the description", jsApiEx.getErrorDescription());

        resp = new TestApiResponse(jsons[2]);
        assertTrue(resp.hasError());
        assertEquals("non_zero_code_only_response", resp.getType());
        assertEquals(500, resp.getErrorCode());
        assertEquals("Unknown JetStream Error (500)", resp.getError());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(500, jsApiEx.getErrorCode());

        resp = new TestApiResponse(jsons[3]);
        assertTrue(resp.hasError());
        assertEquals("no_code_response", resp.getType());
        assertEquals(NOT_SET, resp.getErrorCode());
        assertEquals("no code", resp.getDescription());
        assertEquals("no code", resp.getError());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(NOT_SET, jsApiEx.getErrorCode());
        assertEquals(NOT_SET, jsApiEx.getApiErrorCode());
        assertEquals("no code", jsApiEx.getErrorDescription());

        resp = new TestApiResponse(jsons[4]);
        assertTrue(resp.hasError());
        assertEquals("empty_response", resp.getType());
        assertEquals(NOT_SET, resp.getErrorCode());
        assertEquals("Unknown JetStream Error", resp.getError());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(NOT_SET, jsApiEx.getErrorCode());
        assertEquals(NOT_SET, jsApiEx.getApiErrorCode());

        resp = new TestApiResponse(resp.getErrorObject());
        assertEquals(NO_TYPE, resp.getType());
        assertEquals(NOT_SET, resp.getErrorCode());
        assertEquals("Unknown JetStream Error", resp.getError());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(NOT_SET, jsApiEx.getErrorCode());
        assertEquals(NOT_SET, jsApiEx.getApiErrorCode());

        TestApiResponse notErrorResponse = new TestApiResponse(jsons[5]);
        assertFalse(notErrorResponse.hasError());
        assertEquals("not_error_response", notErrorResponse.getType());
        assertNotNull(resp.getErrorObject());
        assertEquals("Unknown JetStream Error", resp.getDescription());

        resp = new TestApiResponse(jsons[6]);
        assertTrue(resp.hasError());
        assertEquals(NO_TYPE, resp.getType());
        assertEquals(NO_TYPE, resp.getType()); // coverage!

        resp = new TestApiResponse(jsons[7]);
        assertTrue(resp.hasError());
        assertEquals("code_desc_err_response", resp.getType());
        assertEquals(500, resp.getErrorCode());
        assertEquals("the description", resp.getDescription());
        assertEquals("the description [12345]", resp.getError());
        assertEquals(12345, resp.getApiErrorCode());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(500, jsApiEx.getErrorCode());
        assertEquals("the description", jsApiEx.getErrorDescription());
        assertEquals(12345, jsApiEx.getApiErrorCode());

        resp = new TestApiResponse(jsons[8]);
        assertTrue(resp.hasError());
        assertEquals("no-code_desc_err_response", resp.getType());
        assertEquals(NOT_SET, resp.getErrorCode());
        assertEquals("the description", resp.getDescription());
        assertEquals("the description", resp.getError());
        assertNotNull(resp.getErrorObject());
        jsApiEx = new JetStreamApiException(resp.getErrorObject());
        assertEquals(NOT_SET, jsApiEx.getErrorCode());
        assertEquals("the description", jsApiEx.getErrorDescription());
        assertEquals(12345, jsApiEx.getApiErrorCode());

        resp = new TestApiResponse();
        assertEquals(NOT_SET, resp.getErrorCode());
        assertEquals(NOT_SET, resp.getApiErrorCode());
        assertNull(resp.getDescription());
    }

    @Test
    public void testConvert() {
        //noinspection SimplifiableAssertion We are intentionally testing that the convert gives the exact object
        assertTrue(JsNoMessageFoundErr == convert(new Status(404, "four-oh-four")));
        //noinspection SimplifiableAssertion We are intentionally testing that the convert gives the exact object
        assertTrue(JsBadRequestErr == convert(new Status(408, "four-oh-eight")));
        Error e = convert(new Status(499, "four-nine-nine"));
        assertEquals(499, e.getCode());
        assertEquals(NOT_SET, e.getApiErrorCode());
        assertEquals("four-nine-nine", e.getDescription());
    }

    @Test
    public void testSuccessApiResponseCoverage() {
        SuccessApiResponse r = new SuccessApiResponse(getDataMessage("{}"));
        assertFalse(r.hasError());
        assertTrue(r.getSuccess());

        r = new SuccessApiResponse(getDataMessage("{\"success\":true} }"));
        assertFalse(r.hasError());
        assertTrue(r.getSuccess());

        r = new SuccessApiResponse(getDataMessage("{\"success\":false} }"));
        assertFalse(r.hasError());
        assertFalse(r.getSuccess());
    }
}
