package io.synadia.client;

import io.nats.ConsoleOutput;
import io.nats.NatsRunnerUtils;
import io.nats.NatsServerRunner;

import java.io.IOException;
import java.util.logging.Level;

import static io.synadia.client.utils.ResourceUtils.configResource;
import static io.synadia.client.utils.TestBase.WS;
import static io.synadia.client.utils.TestBase.WSS;

public class NatsTestServer extends NatsServerRunner implements TestServer {

    static {
        NatsTestServer.quiet();
        NatsRunnerUtils.setDefaultConnectValidateTries(10);
        NatsRunnerUtils.setDefaultConnectValidateTimeout(200);
        NatsRunnerUtils.setDefaultOutputSupplier(ConsoleOutput::new);
    }

    public static void quiet() {
        NatsRunnerUtils.setDefaultOutputLevel(Level.WARNING);
    }

    public static void verbose() {
        NatsRunnerUtils.setDefaultOutputLevel(Level.ALL);
    }

    public static Builder configFileBuilder(String configFilePath) {
        return NatsServerRunner.builder().configFilePath(configResource(configFilePath));
    }

    public NatsTestServer() throws IOException {
        this(builder());
    }

    public NatsTestServer(int port) throws IOException {
        this(builder().port(port));
    }

    public NatsTestServer(int port, boolean jetstream) throws IOException {
        this(builder().port(port).jetstream(jetstream));
    }

    public NatsTestServer(String configFilePath, String[] configInserts, int port) throws IOException {
        this(builder().configFilePath(configResource(configFilePath)).configInserts(configInserts).port(port));
    }

    public NatsTestServer(String[] customArgs) throws IOException {
        this(builder().customArgs(customArgs));
    }

    public NatsTestServer(String[] customArgs, int port) throws IOException {
        this(builder().customArgs(customArgs).port(port));
    }

    public NatsTestServer(int port, boolean jetstream, String configFilePath, String[] configInserts, String[] customArgs) throws IOException {
        this(builder().port(port).jetstream(jetstream).configFilePath(configResource(configFilePath)).configInserts(configInserts).customArgs(customArgs));
    }

    public NatsTestServer(Builder b) throws IOException {
        super(b);
    }

    public static int nextPort() throws IOException {
        return NatsRunnerUtils.nextPort();
    }

    public String getLocalhostUri(String schema) {
        int port = schema.equals(WS) || schema.equals(WSS) ? getNonNatsPort() : getNatsPort();
        return NatsRunnerUtils.getLocalhostUri(schema, port);
    }

    @Override
    public String getServerUri() {
        return NatsRunnerUtils.getNatsLocalhostUri(getNatsPort());
    }

    public static String getLocalhostUri(int port) {
        return NatsRunnerUtils.getNatsLocalhostUri(port);
    }

    public static String getLocalhostUri(String schema, int port) {
        return NatsRunnerUtils.getLocalhostUri(schema, port);
    }

    public static String[] getLocalhostUris(String schema, NatsTestServer... servers) {
        String[] results = new String[servers.length];
        for (int x = 0; x < servers.length; x++) {
            int port = schema.equals(WS) || schema.equals(WSS) ? servers[x].getNonNatsPort() : servers[x].getNatsPort();
            results[x] = NatsRunnerUtils.getLocalhostUri(schema, port);
        }
        return results;
    }
}
