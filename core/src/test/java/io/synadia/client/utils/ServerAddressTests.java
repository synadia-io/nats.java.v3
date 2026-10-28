// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.utils;

import io.synadia.client.Transport;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class ServerAddressTests {

    @Test
    public void testParse() throws URISyntaxException {
        ServerAddress a = ServerAddress.parse("host:4222");
        assertEquals("host", a.getHost());
        assertEquals(4222, a.getPort());
        assertNull(a.getPath());
        assertFalse(a.hostIsIpAddress());

        a = ServerAddress.parse(" 1.2.3.4:4223 ");
        assertEquals("1.2.3.4", a.getHost());
        assertEquals(4223, a.getPort());
        assertTrue(a.hostIsIpAddress());

        a = ServerAddress.parse("[::1]:4222");
        assertEquals("[::1]", a.getHost());
        assertTrue(a.hostIsIpAddress());
        assertEquals("[::1]:4222", a.toString());

        // what nats-server never sends, or a misconfigured advertise value would produce
        for (String bad : new String[] {"", "  ", "host", "1.2.3.4", "user@host:4222", "u:p@host:4222", "host/p:4222",
            "host:4222/p", "nats://host:4222", "host:4222?x=1", "host:4222#f", "host:0", "host:70000", ":4222"}) {
            assertThrows(URISyntaxException.class, () -> ServerAddress.parse(bad), bad);
        }
    }

    @Test
    public void testConstruct() {
        ServerAddress a = new ServerAddress("host", 80, "/p/q");
        assertEquals("/p/q", a.getPath());
        assertEquals("host:80/p/q", a.toString());
        assertEquals("ws://host:80/p/q", a.toUrl(Transport.Ws));
        assertEquals("wss://host:80/p/q", a.toUrl(Transport.Wss));
        assertEquals("nats://host:4222", new ServerAddress("host", 4222).toUrl(Transport.Nats));
        assertEquals("tls://host:4222", new ServerAddress("host", 4222).toUrl(Transport.Tls));

        assertNull(new ServerAddress("host", 80, "").getPath());
        assertEquals("[1:2:3:4:5:6:7:8]", new ServerAddress("1:2:3:4:5:6:7:8", 4222).getHost());

        assertThrows(IllegalArgumentException.class, () -> new ServerAddress("", 4222));
        assertThrows(IllegalArgumentException.class, () -> new ServerAddress("host", 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerAddress("host", 65536));
        assertThrows(IllegalArgumentException.class, () -> new ServerAddress("host", 80, "p"));
    }

    @Test
    public void testEquality() {
        // host ignoring case, and port; not the path or the tls host
        assertEquals(new ServerAddress("host", 4222), new ServerAddress("HOST", 4222));
        assertEquals(new ServerAddress("host", 4222).hashCode(), new ServerAddress("HOST", 4222).hashCode());
        assertEquals(new ServerAddress("host", 80, "/p"), new ServerAddress("host", 80));
        assertEquals(new ServerAddress("1.2.3.4", 4222), new ServerAddress("1.2.3.4", 4222).withTlsHost("one.test"));
        assertNotEquals(new ServerAddress("host", 4222), new ServerAddress("host", 4223));
        assertNotEquals(new ServerAddress("host1", 4222), new ServerAddress("host2", 4222));
        // host+port collision guard: "host1" port 22 vs "host12" port 2
        assertNotEquals(new ServerAddress("host1", 22), new ServerAddress("host12", 2));
        //noinspection SimplifiableAssertion,ConstantValue
        assertFalse(new ServerAddress("host", 4222).equals(null));
        //noinspection SimplifiableAssertion
        assertFalse(new ServerAddress("host", 4222).equals(new Object()));
    }

    @Test
    public void testReHost() {
        ServerAddress a = new ServerAddress("host", 80, "/p");
        assertEquals("rehost:80/p", a.reHost("rehost").toString());
        assertEquals("1.2.3.4:80/p", a.reHost("1.2.3.4").toString());
        assertEquals("[1:2:3:4:5:6:7:8]:80/p", a.reHost("[1:2:3:4:5:6:7:8]").toString());
        assertEquals("[1:2:3:4:5:6:7:8]:80/p", a.reHost("1:2:3:4:5:6:7:8").toString());

        // rehosting to an ip address keeps the hostname for the TLS handshake
        assertEquals("host", a.reHost("1.2.3.4").getTlsHost());
        assertEquals("other", a.reHost("other").getTlsHost());
        assertEquals("1.2.3.5", new ServerAddress("1.2.3.4", 4222).reHost("1.2.3.5").getTlsHost());
        assertEquals("one.test", new ServerAddress("1.2.3.4", 4222).withTlsHost("one.test").reHost("1.2.3.5").getTlsHost());
    }
}
