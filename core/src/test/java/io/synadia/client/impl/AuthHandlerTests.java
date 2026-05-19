package io.synadia.client.impl;

import io.nats.nkey.NKey;
import io.nats.nkey.NKeyProvider;
import io.synadia.client.AuthHandler;
import io.synadia.client.Nats;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.synadia.client.utils.ResourceUtils.jwtResource;
import static io.synadia.client.utils.ResourceUtils.resourceAsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class AuthHandlerTests {

    private final static String JWT = "eyJ0eXAiOiJqd3QiLCJhbGciOiJlZDI1NTE5In0.eyJqdGkiOiJTNkFFM0VMWUdCQzVNV0lRUVBFU05BWE8yNzROTDU1RFZFVDVaVDVLWlRRQkRVRVJPUUtBIiwiaWF0IjoxNTQzNjA1OTgxLCJpc3MiOiJBQVlXUTVWUkRUMjJGTVpKT0Y2MlZKSFpLNVBQUldXSlJDTklVVkhGVEQ0WkRMUFBaRkRXUlFSUiIsIm5hbWUiOiJmcmVlX3VzZXIiLCJzdWIiOiJVQVdERDVIRzM1N0tNQklLUjYzSExNNzJLVFFVVVNYWUVJRk1DVUFVQUVRRFRISVg3R1lHRVVWWSIsInR5cGUiOiJ1c2VyIiwibmF0cyI6eyJwdWIiOnt9LCJzdWIiOnt9fX0.HEJI1ADBMbPHRQT5FJFUGDKxd77jMVcA7MictlswfHyepWtifGGRcAkzhsT-EF182ifCz0f3t_9Qy-PEI2PRBQ";
    private final static String SEED = "SUAGVDADILKKEQSTWF7RTC25D3F433K3VWMQOGNJRE2VJGEP3LSSO7PHUE";

    @Test
    public void testCredsFile() throws Exception {
        AuthHandler auth = Nats.credentials(jwtResource("test.creds"));
        assertInstanceOf(FileAuthHandler.class, auth);
        NKey key = NKeyProvider.getProvider().fromSeed(SEED.toCharArray());
        byte[] test = "hello world".getBytes(StandardCharsets.UTF_8);

        char[] pubKey = auth.getID();
        assertArrayEquals(key.getPublicKey(), pubKey);
        assertArrayEquals(key.sign(test), auth.sign(test));
        assertArrayEquals(JWT.toCharArray(), auth.getJWT());
    }

    @Test
    public void testMemoryAuth() throws Exception {
        String creds = resourceAsString("jwt_nkey/test.creds");

        AuthHandler auth = Nats.staticCredentials(creds.getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(MemoryAuthHandler.class, auth);
        NKey key = NKeyProvider.getProvider().fromSeed(SEED.toCharArray());
        byte[] test = "hello world".getBytes(StandardCharsets.UTF_8);

        char[] pubKey = auth.getID();
        assertArrayEquals(key.getPublicKey(), pubKey);
        assertArrayEquals(key.sign(test), auth.sign(test));
        assertArrayEquals(JWT.toCharArray(), auth.getJWT());
    }

    @Test
    public void testSeparateWrappedFiles() throws Exception {
        AuthHandler auth = Nats.credentials(jwtResource("test_wrapped.jwt"), jwtResource("test_wrapped.nk"));
        NKey key = NKeyProvider.getProvider().fromSeed(SEED.toCharArray());
        byte[] test = "hello world again".getBytes(StandardCharsets.UTF_8);

        char[] pubKey = auth.getID();
        assertArrayEquals(key.getPublicKey(), pubKey);
        assertArrayEquals(key.sign(test), auth.sign(test));
        assertArrayEquals(JWT.toCharArray(), auth.getJWT());
    }

    @Test
    public void testSeparateNKeyWrappedFile() throws Exception {
        AuthHandler auth = Nats.credentials(null, jwtResource("test_wrapped.nk"));
        NKey key = NKeyProvider.getProvider().fromSeed(SEED.toCharArray());
        byte[] test = "hello world again".getBytes(StandardCharsets.UTF_8);

        char[] pubKey = auth.getID();
        assertArrayEquals(key.getPublicKey(), pubKey);
        assertArrayEquals(key.sign(test), auth.sign(test));
        assertArrayEquals(null, auth.getJWT());
    }

    @Test
    public void testSeparateBareFiles() throws Exception {
        AuthHandler auth = Nats.credentials(jwtResource("test.jwt"), jwtResource("test.nk"));
        NKey key = NKeyProvider.getProvider().fromSeed(SEED.toCharArray());
        byte[] test = "hello world and again".getBytes(StandardCharsets.UTF_8);

        char[] pubKey = auth.getID();
        assertArrayEquals(key.getPublicKey(), pubKey);
        assertArrayEquals(key.sign(test), auth.sign(test));
        assertArrayEquals(JWT.toCharArray(), auth.getJWT());
    }
}
