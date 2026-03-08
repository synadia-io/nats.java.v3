package io.synadia.client;

import java.io.IOException;
import java.security.GeneralSecurityException;

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
        this.nkey = NKey.createUser(null);
        this.jwt = null;
    }

    public NKey getNKey() {
        return this.nkey;
    }

    public char[] getID() {
        try {
            return this.nkey.getPublicKey();
        } catch (GeneralSecurityException|IOException|NullPointerException ex) {
            return null;
        }
    }

    public byte[] sign(byte[] nonce) {
        try {
            return this.nkey.sign(nonce);
        } catch (GeneralSecurityException|IOException|NullPointerException ex) {
            return null;
        }
    }

    public char[] getJWT() {
        return this.jwt;
    }
}
