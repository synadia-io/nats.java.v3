package io.synadia.client.impl;

import io.synadia.client.AuthHandler;
import io.synadia.client.ConnectionImplementation;
import io.synadia.client.Options;
import io.synadia.client.Statistics;

import java.io.IOException;

/**
 * Adapter to impl package to minimize access leakage.
 */
public class NatsImpl {
    private NatsImpl() {}  /* ensures cannot be constructed */

    /**
     * Construct a connection and connect it to a server.
     * @param options the options to connect with
     * @param reconnectOnConnect whether a failure to make the initial connection should start the
     *                           reconnect logic instead of throwing
     * @return the connected connection
     * @throws IOException if the connection could not be established
     * @throws InterruptedException if the current thread is interrupted while connecting
     */
    public static NatsConnection createConnection(Options options, boolean reconnectOnConnect) throws IOException, InterruptedException {
        NatsConnection conn = options.connectionImplementation() == ConnectionImplementation.V3
            ? new NatsConnectionV3(options)
            : new NatsConnection(options);
        conn.connect(reconnectOnConnect);
        return conn;
    }

    /**
     * Make a statistics object with all counters at zero, for callers that need a Statistics
     * instance without a connection.
     * @return the statistics
     */
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
