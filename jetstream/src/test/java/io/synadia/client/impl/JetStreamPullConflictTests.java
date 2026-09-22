package io.synadia.client.impl;

import io.synadia.client.api.AckPolicy;
import io.synadia.client.api.PullConsumerCreator;
import io.synadia.client.api.PushConsumerCreator;
import io.synadia.client.api.SubscribeBehavior;
import io.synadia.client.utils.ConnectionUtils;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.ListenerStatusType;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.time.Duration;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.api.Status.*;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ListenerStatusType.PullError;
import static io.synadia.client.utils.ListenerStatusType.PullWarning;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class JetStreamPullConflictTests extends JetStreamTestBase {

    static final String CONFLICT_SERVER_NAME = "conflict";

    static NatsConnection conflictNc;
    static Listener conflictListener;

    @AfterAll
    public static void afterAll() {
        if (conflictNc != null) {
            conflictNc.close();
            SharedNamedServers.remove(CONFLICT_SERVER_NAME);
            SharedServer.shutdown(CONFLICT_SERVER_NAME);
        }
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println(info.getDisplayName());
    }

    static class BadPullRequestOptions extends PullRequestOptions {
        public BadPullRequestOptions() {
            super(PullRequestOptions.builder(1));
        }

        @Override
        @NonNull
        public String toJson() {
            StringBuilder sb = beginJson();
            addField(sb, BATCH, 1);
            addField(sb, NO_WAIT, true);
            addFieldAsNanos(sb, IDLE_HEARTBEAT, Duration.ofMillis(1));
            return endJson(sb).toString();
        }
    }

    private interface SubscriptionSupplier {
        JetStreamPullSubscription get() throws IOException, JetStreamApiException;
    }

    interface ConflictSetup {
        JetStreamPullSubscription setup(NatsConnection nc, JetStreamTestingContext ctx) throws Exception;
    }

    private PullConsumerCreator newCreator() {
        return new PullConsumerCreator().ackPolicy(AckPolicy.None).inactiveThreshold(INACTIVE_THRESHOLD);
    }

    static final long NEXT_MESSAGE_TIMEOUT = 5000;
    static final long INACTIVE_THRESHOLD = 30_000;
    private void _testConflictStatuses(int statusCode, String statusText, ListenerStatusType statusType, boolean sync, ConflictSetup setup) throws Exception {
        runInSharedNamed(CONFLICT_SERVER_NAME, ts -> {
            if (conflictNc == null) {
                System.out.println("First Test");
                conflictListener = new Listener();
                conflictNc = ConnectionUtils.managedConnect(
                    optionsBuilder(ts).errorListener(conflictListener).connectionListener(conflictListener).build());
            }
            else {
                System.out.println("NOT First Test");
                conflictListener.reset();
            }
            try (JetStreamTestingContext tcsCtx = new JetStreamTestingContext(conflictNc, 1)) {
                if (statusType != null) {
                    conflictListener.queueStatus(statusType, statusCode, Listener.LONG_VALIDATE_TIMEOUT);
                }
                JetStreamPullSubscription sub = setup.setup(conflictNc, tcsCtx);
                if (sync) {
                    if (statusType == PullError) {
                        JetStreamStatusException jsse = assertThrows(JetStreamStatusException.class, () -> sub.nextMessage(NEXT_MESSAGE_TIMEOUT));
                        assertEquals(statusCode, jsse.getStatus().getCode());
                        assertNotNull(jsse.getSubscription());
                        assertEquals(sub.hashCode(), jsse.getSubscription().hashCode());
                        assertTrue(jsse.getMessage().contains(statusText)); // coverage
                    }
                    else {
                        sub.nextMessage(NEXT_MESSAGE_TIMEOUT);
                    }
                }
                if (statusType != null) {
                    conflictListener.validate();
                }
            }
        });
    }

    @Test
    public void testExceededMaxWaitingSync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_WAITING, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator().maxPullWaiting(1).filterSubject(ctx.subject());
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pull(1);
                sub.pull(1);
                return sub;
            });
    }

    @Test
    public void testExceededMaxWaitingAsync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_WAITING, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator().maxPullWaiting(1);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pull(1);
                sub.pull(1);
                return sub;
            });
    }

    @Test
    public void testExceedsMaxRequestBatchSync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_BATCH, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator().maxBatch(1);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pull(2);
                return sub;
            });
    }


    @Test
    public void testExceedsMaxRequestBatchAsync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_BATCH, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator().maxBatch(1);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pull(2);
                return sub;
            }
        );
    }

    @Test
    public void testMessageSizeExceedsMaxBytesSync() throws Exception {
        _testConflictStatuses(409, MESSAGE_SIZE_EXCEEDS_MAX_BYTES, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator();
                ctx.js.publish(ctx.subject(), new byte[1000]);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pull(PullRequestOptions.builder(1).maxBytes(100).build());
                return sub;
            });
    }


    @Test
    public void testMessageSizeExceedsMaxBytesAsync() throws Exception {
        _testConflictStatuses(409, MESSAGE_SIZE_EXCEEDS_MAX_BYTES, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator();
                ctx.js.publish(ctx.subject(), new byte[1000]);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pull(PullRequestOptions.builder(1).maxBytes(100).build());
                return sub;
            }
        );
    }

    @Test
    public void testExceedsMaxRequestExpiresSync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_EXPIRES, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator().maxExpires(1000);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pullExpiresIn(1, 2000);
                return sub;
            });
    }


    @Test
    public void testExceedsMaxRequestExpiresAsync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_EXPIRES, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator().maxExpires(1000);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pullExpiresIn(1, 2000);
                return sub;
            }
        );
    }

    @Test
    public void testConsumerIsPushBasedSync() throws Exception {
        _testConflictStatuses(409, CONSUMER_IS_PUSH_BASED, PullError, true,
            (nc, ctx) -> {
                String dur = random();
                PullConsumerCreator lCreator = newCreator().durable(dur).ackPolicy(AckPolicy.None);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, lCreator);
                ctx.jsm.deleteConsumer(ctx.stream, dur);
                // consumer with same name but is push now
                ctx.jsm.createConsumer(ctx.stream, new PushConsumerCreator().durable(dur));
                sub.pull(1);
                return sub;
            });
    }

    @Test
    public void testConsumerIsPushBasedAsync() throws Exception {
        _testConflictStatuses(409, CONSUMER_IS_PUSH_BASED, PullError, false,
            (nc, ctx) -> {
                String dur = random();
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator lCreator = newCreator().durable(dur).ackPolicy(AckPolicy.None);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, lCreator, b);
                ctx.jsm.deleteConsumer(ctx.stream, dur);
                // consumer with same name but is push now
                ctx.jsm.createConsumer(ctx.stream, new PushConsumerCreator().durable(dur));
                sub.pull(1);
                return sub;
            }
        );
    }

    @Test
    public void testConsumerDeletedSyncSub() throws Exception {
        _testConflictStatuses(409, CONSUMER_DELETED, PullError, true,
            (nc, ctx) -> {
                String dur = random();
                PullConsumerCreator creator = newCreator().durable(dur).ackPolicy(AckPolicy.None);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pullExpiresIn(1, 30000);
                nc.flush(1000); // flush outgoing communication with/to the server
                ctx.jsm.deleteConsumer(ctx.stream, dur);
                ctx.js.publish(ctx.subject(), (String)null);
                return sub;
            });
    }

    @Test
    public void testConsumerDeletedAsyncSub() throws Exception {
        _testConflictStatuses(409, CONSUMER_DELETED, PullError, false,
            // Async
            (nc, ctx) -> {
                String dur = random();
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator().durable(dur).ackPolicy(AckPolicy.None);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pullExpiresIn(1, 30000);
                nc.flush(1000); // flush outgoing communication with/to the server
                ctx.jsm.deleteConsumer(ctx.stream, dur);
                ctx.js.publish(ctx.subject(), (String)null);
                return sub;
            }
        );
    }

    @Test
    public void testBadRequestSync() throws Exception {
        _testConflictStatuses(400, BAD_REQUEST, PullError, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator();
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pull(new BadPullRequestOptions());
                return sub;
            });
    }

    @Test
    public void testBadRequestAsync() throws Exception {
        _testConflictStatuses(400, BAD_REQUEST, PullError, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator();
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pull(new BadPullRequestOptions());
                return sub;
            });
    }

    @Test
    public void testNotFoundSync() throws Exception {
        _testConflictStatuses(404, NO_MESSAGES, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator();
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pullNoWait(1);
                return sub;
            });
    }

    @Test
    public void testNotFoundAsync() throws Exception {
        _testConflictStatuses(404, NO_MESSAGES, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator();
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pullNoWait(1);
                return sub;
            });
    }

    @Test
    public void testExceedsMaxRequestBytes1stMessageSync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_MAX_BYTES, PullWarning, true,
            (nc, ctx) -> {
                PullConsumerCreator creator = newCreator().maxBytes(1);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
                sub.pull(PullRequestOptions.builder(1).maxBytes(2).build());
                return sub;
            });
    }

    @Test
    public void testExceedsMaxRequestBytes1stMessageAsync() throws Exception {
        _testConflictStatuses(409, EXCEEDED_MAX_REQUEST_MAX_BYTES, PullWarning, false,
            (nc, ctx) -> {
                SubscribeBehavior b = new SubscribeBehavior().handler(m -> {});
                PullConsumerCreator creator = newCreator().maxBytes(1);
                JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator, b);
                sub.pull(PullRequestOptions.builder(1).maxBytes(2).build());
                return sub;
            });
    }
}
