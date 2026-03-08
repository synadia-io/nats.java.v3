package io.synadia.client;

import io.synadia.client.impl.Headers;
import io.synadia.client.support.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpRequestTests {
    @Test
    public void testDefaults() {
        HttpRequest request = new HttpRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/", request.getURI());
        assertEquals("1.1", request.getVersion());
        assertEquals(new Headers(), request.getHeaders());
        assertEquals(
            "GET / HTTP/1.1\r\n" +
            "\r\n",
            request.toString());
    }

    @Test
    public void testSetters() {
        HttpRequest request = new HttpRequest()
            .method("PUT")
            .uri("/fun")
            .version("1.1");
        request.getHeaders()
            .add("One", "1")
            .add("Two", "2");
        assertEquals("PUT", request.getMethod());
        assertEquals("/fun", request.getURI());
        assertEquals("1.1", request.getVersion());
        assertEquals(
            "PUT /fun HTTP/1.1\r\n" +
            "One: 1\r\n" +
            "Two: 2\r\n" +
            "\r\n",
            request.toString());
    }

    @Test
    public void testNulls() {
        HttpRequest request = new HttpRequest();
        assertThrows(IllegalArgumentException.class, () -> request.method(null));
        assertThrows(IllegalArgumentException.class, () -> request.uri(null));
        assertThrows(IllegalArgumentException.class, () -> request.version(null));
    }
}
