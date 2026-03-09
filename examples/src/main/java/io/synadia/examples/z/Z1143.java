package io.synadia.examples.z;

import io.synadia.client.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Z1143 {

    public static void main(String[] args) throws InterruptedException {
        Options options = Options.builder()
//            .supportUTF8Subjects()
            .build();
        try (Connection nc = Nats.connect(options)) {
            CountDownLatch latch = new CountDownLatch(1);
            Thread tsub = new Thread(() -> {
                Subscription sub = nc.subscribe("test.løp");
                Message msg = null;
                while (msg == null) {
                    try {
                        msg = sub.nextMessage(100);
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                byte[] data = msg.getData();
                System.out.printf("Received \"%s\" on \"%s\"\n",
                    new String(data, StandardCharsets.UTF_8),
                    msg.getSubject());
                latch.countDown();
            });
            tsub.start();

            String message = "Hello world!";
            nc.publish("test.løp", message.getBytes(StandardCharsets.UTF_8));
            latch.await(10, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
