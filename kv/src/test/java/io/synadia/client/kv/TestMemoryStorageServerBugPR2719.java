package io.synadia.client.kv;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR;
import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR_SUBJECT;
import static io.synadia.client.impl.JetStreamTestBase.runInSharedCustomContext;
import static io.synadia.client.kv.KeyValueUtils.KV_OPERATION_HEADER_KEY;
import static io.synadia.client.utils.NatsConstants.MSG_SIZE_HDR;
import static io.synadia.client.utils.TestBase.*;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class TestMemoryStorageServerBugPR2719 {

    static class MemStorBugHandler implements MessageHandler {
        public List<Message> messages = new ArrayList<>();

        @Override
        public void onMessage(Message msg) throws InterruptedException {
            messages.add(msg);
        }
    }

    @Test
    public void testMemoryStorageServerBugPR2719() throws Exception {
        String stream = random();
        String subBase = random();
        String subGt = subjectGt(subBase);
        String key1 = subjectDot(subBase, "key1");
        String key2 = subjectDot(subBase, "key2");

        Headers deleteHeaders = new Headers()
            .put(KV_OPERATION_HEADER_KEY, KeyValueOperation.DELETE.name());
        Headers purgeHeaders = new Headers()
            .put(KV_OPERATION_HEADER_KEY, KeyValueOperation.PURGE.name())
            .put(ROLLUP_HDR, ROLLUP_HDR_SUBJECT);

        runInSharedCustomContext((nc, ctx) -> {

            StreamCreator sc = new StreamCreator(stream)
                .storageType(StorageType.Memory)
                .subjects(subGt)
                .allowRollup(true)
                .denyDelete(true);

            ctx.createOrReplaceStream(sc);

            MemStorBugHandler fullHandler = new MemStorBugHandler();
            MemStorBugHandler onlyHandler = new MemStorBugHandler();

            PushConsumerCreator crFull = new PushConsumerCreator()
                .ackPolicy(AckPolicy.None)
                .deliverPolicy(DeliverPolicy.LastPerSubject)
                .flowControl(5000)
                .filterSubject(subGt)
                .headersOnly(false);

            PushConsumerCreator crOnly = new PushConsumerCreator()
                .ackPolicy(AckPolicy.None)
                .deliverPolicy(DeliverPolicy.LastPerSubject)
                .flowControl(5000)
                .filterSubject(subGt)
                .headersOnly(true);

            ctx.js.pushSubscribe(stream, crFull, fullHandler);
            ctx.js.pushSubscribe(stream, crOnly, onlyHandler);

            Object[] expecteds = new Object[] {
                "a", "aa", "z", "zz",
                KeyValueOperation.DELETE, KeyValueOperation.DELETE,
                "aaa", "zzz",
                KeyValueOperation.DELETE, KeyValueOperation.DELETE,
                KeyValueOperation.PURGE, KeyValueOperation.PURGE,
            };

            ctx.js.publish(NatsMessage.builder().subject(key1).data((String)expecteds[0]).build());
            ctx.js.publish(NatsMessage.builder().subject(key1).data((String)expecteds[1]).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).data((String)expecteds[2]).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).data((String)expecteds[3]).build());

            ctx.js.publish(NatsMessage.builder().subject(key1).headers(deleteHeaders).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).headers(deleteHeaders).build());

            ctx.js.publish(NatsMessage.builder().subject(key1).data((String)expecteds[6]).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).data((String)expecteds[7]).build());

            ctx.js.publish(NatsMessage.builder().subject(key1).headers(deleteHeaders).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).headers(deleteHeaders).build());

            ctx.js.publish(NatsMessage.builder().subject(key1).headers(purgeHeaders).build());
            ctx.js.publish(NatsMessage.builder().subject(key2).headers(purgeHeaders).build());

            sleep(2000); // give time for the handler to get messages

            validateRegular(expecteds, fullHandler);
            validateHeadersOnly(expecteds, onlyHandler);
        });
    }

    private void validateHeadersOnly(Object[] expecteds, MemStorBugHandler handler) {
        int aix = 0;
        for (Message m : handler.messages) {
            Object expected = expecteds[aix++];
            Headers h = m.getHeaders();
            assertNotNull(h);
            assertTrue(m.getData() == null || m.getData().length == 0);
            if (expected instanceof String) {
                assertEquals("" + ((String)expected).length(), h.getFirst(MSG_SIZE_HDR));
                assertNull(h.getFirst(KV_OPERATION_HEADER_KEY));
                assertNull(h.getFirst(ROLLUP_HDR));
            }
            else {
                assertEquals("0", h.getFirst(MSG_SIZE_HDR));
                assertEquals(expected, KeyValueOperation.valueOf(h.getFirst(KV_OPERATION_HEADER_KEY)));
                if (expected == KeyValueOperation.PURGE) {
                    assertEquals(ROLLUP_HDR_SUBJECT, h.getFirst(ROLLUP_HDR));
                }
                else {
                    assertNull(h.getFirst(ROLLUP_HDR));
                }
            }
        }
    }

    private void validateRegular(Object[] expecteds, MemStorBugHandler handler) {
        int aix = 0;
        for (Message m : handler.messages) {
            Object expected = expecteds[aix++];
            if (expected instanceof String) {
                assertEquals(expected, new String(m.getData()));
                assertNull(m.getHeaders());
            }
            else {
                Headers h = m.getHeaders();
                assertNotNull(h);
                assertTrue(m.getData() == null || m.getData().length == 0);
                assertNull(h.getFirst(MSG_SIZE_HDR));
                assertEquals(expected, KeyValueOperation.valueOf(h.getFirst(KV_OPERATION_HEADER_KEY)));
                if (expected == KeyValueOperation.PURGE) {
                    assertEquals(ROLLUP_HDR_SUBJECT, h.getFirst(ROLLUP_HDR));
                }
                else {
                    assertNull(h.getFirst(ROLLUP_HDR));
                }
            }
        }
    }
}
