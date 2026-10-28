package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.utils.ServerAddress;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.*;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

public class ServerPoolTests extends TestBase {

    public static final String BOOT_ONE = "b1:4222";
    public static final String BOOT_TWO = "b2:4222";
    public static final String DISC_ONE = "d1:4222";
    public static final String DISC_TWO = "d2:4222";
    public static final String DISC_THREE = "d3:4222";
    public static final String HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS = "connect.ngs.global";
    public static final String HOST_THAT_CAN_BE_RESOLVED_TO_ONE_IP = "demo.nats.io";
    public static final String HOST_THAT_CANT_BE_RESOLVED = "not.real.host";
    public static final String[] bootstrap = new String[]{BOOT_ONE, BOOT_TWO};
    public static final String[] combined = new String[]{BOOT_ONE, BOOT_TWO, DISC_ONE, DISC_TWO, DISC_THREE};
    public static final List<ServerAddress> discoveredServers = addresses(BOOT_TWO, DISC_ONE, DISC_TWO, DISC_THREE);

    @Test
    public void testPoolOptions() throws URISyntaxException {
        ServerAddress lastConnectedServer = ServerAddress.parse(BOOT_ONE);

        // testing that the expected show up in the pool
        Options o = optionsBuilder(bootstrap).build();
        NatsServerPool nsp = newNatsServerPool(o, null, discoveredServers);
        validateNslp(nsp, null, false, combined);

        // ... and last connected is moved to the end
        nsp = newNatsServerPool(o, lastConnectedServer, discoveredServers);
        validateNslp(nsp, lastConnectedServer, false, combined);

        // testing that noRandomize maintains order
        o = optionsBuilder(bootstrap).noRandomize().build();
        nsp = newNatsServerPool(o, null, discoveredServers);
        validateNslp(nsp, null, true, combined);

        // ... and still properly moves last connected server to end of list
        nsp = newNatsServerPool(o, lastConnectedServer, discoveredServers);
        validateNslp(nsp, lastConnectedServer, true, combined);

        // testing that ignoreDiscoveredServers ignores discovered servers
        o = optionsBuilder(bootstrap).ignoreDiscoveredServers().build();
        nsp = newNatsServerPool(o, null, discoveredServers);
        validateNslp(nsp, null, false, BOOT_ONE, BOOT_TWO);

        // testing that duplicates don't get added: the same host and port with another scheme, or no scheme
        o = optionsBuilder("nats://b1", "tls://b1").build();
        nsp = newNatsServerPool(o, null, null);
        validateNslp(nsp, null, false, BOOT_ONE);

        o = optionsBuilder("tls://b1", "b1").build();
        nsp = newNatsServerPool(o, null, null);
        validateNslp(nsp, null, false, BOOT_ONE);
    }

    @Test
    public void testMaxReconnects() throws URISyntaxException {
        ServerAddress failed = ServerAddress.parse(BOOT_ONE);

        // testing that servers that fail max times and is removed
        Options o = optionsBuilder(BOOT_ONE).maxReconnects(3).build();
        NatsServerPool nsp = newNatsServerPool(o, null, null);
        for (int x = 0; x < 4; x++) {
            nsp.nextServer();
            validateNslp(nsp, null, false, BOOT_ONE);
            nsp.connectFailed(failed);
        }
        assertNull(nsp.nextServer());

        // and that it's put back
        nsp.acceptDiscoveredUrls(addresses(BOOT_ONE), null);
        validateNslp(nsp, null, false, BOOT_ONE);

        // testing that servers that fail max times and is removed
        o = optionsBuilder(BOOT_ONE).maxReconnects(0).build();
        nsp = newNatsServerPool(o, null, null);
        nsp.nextServer();
        validateNslp(nsp, null, false, BOOT_ONE);
        nsp.connectFailed(failed);
        assertNull(nsp.nextServer());

        // and that it's put back
        nsp.acceptDiscoveredUrls(addresses(BOOT_ONE), null);
        validateNslp(nsp, null, false, BOOT_ONE);
    }

    @Test
    public void testPruning() throws URISyntaxException {
        // making sure that pruning happens. get baseline
        Options o = optionsBuilder(bootstrap).maxReconnects(0).build();
        NatsServerPool nsp = newNatsServerPool(o, null, discoveredServers);
        validateNslp(nsp, null, false, combined);

        // new discovered list is missing d3
        nsp.acceptDiscoveredUrls(addresses(DISC_ONE, DISC_TWO), null);
        validateNslp(nsp, null, false, BOOT_ONE, BOOT_TWO, DISC_ONE, DISC_TWO);

        // new discovered list is missing the last connected
        nsp.connectSucceeded(ServerAddress.parse(DISC_TWO));
        nsp.acceptDiscoveredUrls(addresses(DISC_ONE), null);
        validateNslp(nsp, null, false, BOOT_ONE, BOOT_TWO, DISC_ONE, DISC_TWO);
    }

    private static NatsServerPool newNatsServerPool(Options o, ServerAddress last, List<ServerAddress> discoveredServers) {
        NatsServerPool nsp = new NatsServerPool();
        nsp.initialize(o);
        if (last != null) {
            ServerAddress next = nsp.nextServer();
            while (next != null && !next.equals(last)) {
                next = nsp.nextServer();
            }
            assertEquals(last, next);
            nsp.connectSucceeded(last);
        }
        if (discoveredServers != null) {
            nsp.acceptDiscoveredUrls(discoveredServers, null);
        }
        return nsp;
    }

    private static List<ServerAddress> addresses(String... hostPorts) {
        final List<ServerAddress> list = new ArrayList<>();
        for (String s : hostPorts) {
            try {
                list.add(ServerAddress.parse(s));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return list;
    }

    private static void validateNslp(NatsServerPool nsp, ServerAddress last, boolean notRandom, String... expectedUrls) throws URISyntaxException {
        List<ServerAddress> supplied = addresses(nsp.getServerList().toArray(new String[0]));
        int expectedSize = expectedUrls.length;
        assertEquals(expectedSize, supplied.size());
        for (int i = 0; i < expectedUrls.length; i++) {
            ServerAddress expected = ServerAddress.parse(expectedUrls[i]);
            assertTrue(supplied.contains(expected));
            if (notRandom && last == null) {
                assertEquals(expected, supplied.get(i));
            }
        }
        if (last != null) {
            assertEquals(last, supplied.get(supplied.size() - 1));
        }
    }

    @Test
    public void testServerPoolEntry() throws URISyntaxException {
        ServerAddress address = ServerAddress.parse(BOOT_ONE);
        ServerPoolEntry entry = new ServerPoolEntry(address, true);
        assertEquals(address, entry.address);
        assertTrue(entry.isGossiped);
        assertTrue(entry.toString().contains(address.toString()));
        assertTrue(entry.toString().contains("true/0"));

        entry = new ServerPoolEntry(address, false);
        entry.failedAttempts = 1;
        assertEquals(address, entry.address);
        assertFalse(entry.isGossiped);
        assertTrue(entry.toString().contains(address.toString()));
        assertTrue(entry.toString().contains("false/1"));
    }

    @Test
    public void testNatsHostResolver() throws URISyntaxException {
        List<String> resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS, true, false);
        assertNotNull(resolved);
        assertEquals(1, resolved.size());

        // this is coverage since I can't guarantee that any the host will be resolved to an IPV6
        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS, true, true);
        assertNotNull(resolved);
        assertEquals(1, resolved.size());

        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS, false, false);
        assertNotNull(resolved);
        assertTrue(resolved.size() > 1);

        // this is coverage since I can't guarantee that any the host will be resolved to an IPV6
        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS, false, true);
        assertNotNull(resolved);
        assertTrue(resolved.size() > 1);

        ServerAddress ngs = new ServerAddress(HOST_THAT_CAN_BE_RESOLVED_TO_MULTIPLE_IPS, 4222);
        for (String ip : resolved) {
            ServerAddress rehosted = ngs.reHost(ip);
            assertTrue(rehosted.hostIsIpAddress());
        }

        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_ONE_IP, true, false);
        assertNotNull(resolved);
        assertEquals(1, resolved.size());

        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CAN_BE_RESOLVED_TO_ONE_IP, false, false);
        assertNotNull(resolved);
        assertEquals(1, resolved.size());

        resolved = NatsHostResolver.resolveHostToIps(HOST_THAT_CANT_BE_RESOLVED, false, false);
        assertNull(resolved);
    }
}
