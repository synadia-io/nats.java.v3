package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.JetStreamTimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A6 (EXCEPTIONS_AUDIT): the JetStream request path turns a missing / timed-out response into a
 * {@link JetStreamTimeoutException} — v2 threw a synthetic {@code IOException} here.
 * {@code JetStreamImpl.responseRequired(null)} is the exact root site, so testing it directly is
 * deterministic; provoking a real network timeout would be timing-flaky.
 */
public class JetStreamTimeoutTests extends JetStreamTestBase {

    @Test
    public void testNoResponseBecomesTimeoutException() throws Exception {
        runInShared((nc, ctx) -> {
            // null response (server slow, or server/network gone) -> JetStreamTimeoutException.
            // The type conveys "timeout"; the message is just the context — which request timed out.
            String context = "$JS.API.STREAM.INFO.test-stream";
            JetStreamTimeoutException ex = assertThrows(JetStreamTimeoutException.class,
                () -> ctx.js.responseRequired(null, context));
            assertEquals(context, ex.getMessage());

            // and it is catchable as the base type — the point of the consolidation
            assertInstanceOf(JetStreamException.class, ex);

            // a real response passes straight through, unchanged (context is irrelevant then)
            Message resp = NatsMessage.builder().subject(ctx.subject()).data(dataBytes(1)).build();
            assertSame(resp, ctx.js.responseRequired(resp, context));
        });
    }
}
