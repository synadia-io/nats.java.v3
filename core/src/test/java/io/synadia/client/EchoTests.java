package io.synadia.client;

import io.synadia.client.NatsServerProtocolMock.ExitAt;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.SharedServer;
import io.synadia.client.testutils.ConnectionUtils;
import io.synadia.client.testutils.TestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static io.synadia.client.testutils.ConnectionUtils.assertCanConnect;
import static io.synadia.client.testutils.OptionsUtils.options;
import static io.synadia.client.testutils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

public class EchoTests extends TestBase {
    @Test
    public void testFailWithBadServerProtocol() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            Options options = optionsBuilder(mockTs).noEcho().noReconnect().build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testConnectToOldServerWithEcho() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            Options options = optionsBuilder(mockTs).noReconnect().build();
            assertCanConnect(options);
        }
    }
    
    @Test
    public void testWithEcho() throws Exception {
        runInShared(nc1 -> {
            try (NatsConnection nc2 = ConnectionUtils.managedConnect(options(nc1))) {
                // Echo is on so both sub should get messages from both pub
                String subject = random();
                Subscription sub1 = nc1.subscribe(subject);
                nc1.flush(Duration.ofSeconds(1));
                Subscription sub2 = nc2.subscribe(subject);
                nc2.flush(Duration.ofSeconds(1));

                // Pub from connect 1
                nc1.publish(subject, null);
                nc1.flush(Duration.ofSeconds(1));
                Message msg = sub1.nextMessage(Duration.ofSeconds(1));
                assertNotNull(msg);
                msg = sub2.nextMessage(Duration.ofSeconds(1));
                assertNotNull(msg);

                // Pub from connect 2
                nc2.publish(subject, null);
                nc2.flush(Duration.ofSeconds(1));
                msg = sub1.nextMessage(Duration.ofSeconds(1));
                assertNotNull(msg);
                msg = sub2.nextMessage(Duration.ofSeconds(1));
                assertNotNull(msg);
            }
        });
    }

    @Test
    public void testWithNoEcho() throws Exception {
        runInSharedOwnNc(optionsBuilder().noEcho().noReconnect(), nc1 -> {
            NatsConnection nc2 = SharedServer.sharedConnectionForSameServer(nc1);

            String subject = random();
            Subscription sub1 = nc1.subscribe(subject);
            nc1.flush(Duration.ofSeconds(1));
            Subscription sub2 = nc2.subscribe(subject);
            nc2.flush(Duration.ofSeconds(1));

            // Pub from connect 1
            nc1.publish(subject, null);
            nc1.flush(Duration.ofSeconds(1));
            Message msg = sub1.nextMessage(Duration.ofSeconds(1));
            assertNull(msg); // no message for sub1 from pub 1
            msg = sub2.nextMessage(Duration.ofSeconds(1));
            assertNotNull(msg);

            // Pub from connect 2
            nc2.publish(subject, null);
            nc2.flush(Duration.ofSeconds(1));
            msg = sub1.nextMessage(Duration.ofSeconds(1));
            assertNotNull(msg);
            msg = sub2.nextMessage(Duration.ofSeconds(1));
            assertNotNull(msg); // nc2 is not no echo
        });
    }
}
