package io.synadia.client.impl;

import io.synadia.client.AuthHandler;
import io.synadia.client.Options;
import io.synadia.client.Statistics;

import java.io.IOException;

/**
 * Adapter to impl package to minimize access leakage.
 */
public class NatsImpl {
    public static NatsConnection createConnection(Options options, boolean reconnectOnConnect) throws IOException, InterruptedException {
        NatsConnection conn = new NatsConnection(options);
        conn.connect(reconnectOnConnect);
        return conn;
    }

    public static Statistics createEmptyStats() {
        return new NatsStatistics();
    }

    /**
     * Create an AuthHandler from a credentials file that contains the jwt and nkey
     * @param credsFile the fully qualified path to the file
     * @return an AuthHandler implementation
     */
    public static AuthHandler credentials(String credsFile) {
        return new FileAuthHandler(credsFile);
    }

    /**
     * Create an AuthHandler from individual files for the jwt and the nkey
     * @param jwtFile the fully qualified path to the jwt file
     * @param nkeyFile the fully qualified path to the nkey file
     * @return an AuthHandler implementation
     */
    public static AuthHandler credentials(String jwtFile, String nkeyFile) {
        return new FileAuthHandler(jwtFile, nkeyFile);
    }

    /**
     * Create an AuthHandler from a bytes representing a credentials file that contains the jwt and nkey
     * @param credsBytes the bytes of the file
     * @return an AuthHandler implementation
     */
    public static AuthHandler staticCredentials(byte[] credsBytes) {
        return new MemoryAuthHandler(credsBytes);
    }

    /**
     * Create an AuthHandler from char arrays representing the jwt and the nkey
     * @param jwt the chars for the jwt
     * @param nkey the chars for the nkey
     * @return an AuthHandler implementation
     */
    public static AuthHandler staticCredentials(char[] jwt, char[] nkey) {
        return new StringAuthHandler(jwt, nkey);
    }

}
