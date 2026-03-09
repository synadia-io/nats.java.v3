package io.synadia.examples.z;

import io.synadia.client.*;
import io.synadia.client.impl.ErrorListenerConsoleImpl;

import java.util.List;

public class ZNvidiaConsumerSpike {

    static String URL = "54.164.140.246";
    static String STREAM = "testingStream";

    public static void main(String[] args) {
        Options options = Options.builder()
            .server(URL)
            .connectionListener((conn, event) -> System.out.println("CL: " + event))
            .errorListener(new ErrorListenerConsoleImpl())
            .build();
        try (Connection nc = Nats.connect(options)) {
            System.out.println("CONNECTED: " + nc.getServerInfo());
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();
            List<String> consumers = jsm.getConsumerNames(STREAM);
            System.out.println("consumers: " + consumers.size());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}