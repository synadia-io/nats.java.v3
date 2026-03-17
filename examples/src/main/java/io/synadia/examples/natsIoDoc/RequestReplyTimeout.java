package io.synadia.examples.natsIoDoc;

import io.synadia.client.Message;
import io.synadia.client.Nats;
import io.synadia.client.impl.NatsConnection;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;

public class RequestReplyTimeout {
    public static void main(String[] args) {
        try (NatsConnection nc = Nats.connect("nats://localhost:4222")) {

            // NATS-DOC-START
            // Make a request expecting a future
            CompletableFuture<Message> responseFuture = nc.request("service", null);
            try {
                Message m = responseFuture.get(500, TimeUnit.MILLISECONDS);
                System.out.println("1) Response: " + new String(m.getData()));
            }
            catch (CancellationException | ExecutionException | TimeoutException e) {
                System.out.println("1) No Response: " + e);
            }

            // Make a request with a timeout and direct response
            Message m = nc.request("service", null, Duration.ofMillis(500));
            if (m == null) {
                System.out.println("2) No Response");
            }
            else {
                System.out.println("2) Response: " + new String(m.getData()));
            }
            // NATS-DOC-END
        }
        catch (InterruptedException e) {
            // can be thrown by connect
            Thread.currentThread().interrupt();
        }
        catch (IOException e) {
            // can be thrown by connect
        }
    }
}