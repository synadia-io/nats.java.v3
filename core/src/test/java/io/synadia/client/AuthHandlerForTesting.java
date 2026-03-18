package io.synadia.client;

import io.nats.nkey.NKey;
import io.nats.nkey.NKeyProvider;

public class AuthHandlerForTesting implements AuthHandler {
    private final NKey nkey;
    private final char[] jwt;

    public AuthHandlerForTesting(NKey nkey) {
        this.nkey = nkey;
        this.jwt = null;
    }

    public AuthHandlerForTesting(NKey nkey, char[] jwt) {
        this.nkey = nkey;
        this.jwt = jwt;
    }

    public AuthHandlerForTesting() throws Exception {
        this.nkey = NKeyProvider.getProvider().createUser();
        this.jwt = null;
    }

    public NKey getNKey() {
        return this.nkey;
    }

    public char[] getID() {
        try {
            return this.nkey.getPublicKey();
        } catch (NullPointerException ex) {
            return null;
        }
    }

    public byte[] sign(byte[] nonce) {
        try {
            return this.nkey.sign(nonce);
        } catch (NullPointerException ex) {
            return null;
        }
    }

    public char[] getJWT() {
        return this.jwt;
    }
}
