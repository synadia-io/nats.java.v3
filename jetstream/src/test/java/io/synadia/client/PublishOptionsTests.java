package io.synadia.client;

import io.synadia.client.impl.PublishOptions;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class PublishOptionsTests extends TestBase {

    @Test
    public void testBuilder() {
        PublishOptions.Builder builder = PublishOptions.builder();
        PublishOptions po = builder.build();
        assertEquals(PublishOptions.DEFAULT_TIMEOUT, po.getStreamTimeout());
        assertNull(po.getExpectedStream());
        assertNull(po.getExpectedLastMsgId());
        assertEquals(PublishOptions.UNSET_LAST_SEQUENCE, po.getExpectedLastSequence());
        assertEquals(PublishOptions.UNSET_LAST_SEQUENCE, po.getExpectedLastSubjectSequence());
        assertNull(po.getExpectedLastSubjectSequenceSubject());
        assertNull(po.getMessageTtl());

        long streamTimeout = 99000;

        po = builder
            .streamTimeout(streamTimeout)
            .expectedStream("expectedStream")
            .expectedLastMsgId("1")
            .expectedLastSequence(42)
            .expectedLastSubjectSequence(43)
            .expectedLastSubjectSequenceSubject("sss")
            .messageId("msgId")
            .messageTtlCustom("custom")
            .build();

        assertEquals(streamTimeout, po.getStreamTimeout());
        assertEquals("expectedStream", po.getExpectedStream());
        assertEquals("1", po.getExpectedLastMsgId());
        assertEquals(42, po.getExpectedLastSequence());
        assertEquals(43, po.getExpectedLastSubjectSequence());
        assertEquals("sss", po.getExpectedLastSubjectSequenceSubject());
        assertEquals("msgId", po.getMessageId());
        assertEquals("custom", po.getMessageTtl());

        // test clearExpected
        po = builder.clearExpected().build();

        // these are not cleared
        assertEquals("expectedStream", po.getExpectedStream());
        assertEquals(99000, po.getStreamTimeout());
        assertEquals("custom", po.getMessageTtl());

        // these are cleared
        assertNull(po.getExpectedLastMsgId());
        assertEquals(PublishOptions.UNSET_LAST_SEQUENCE, po.getExpectedLastSequence());
        assertEquals(PublishOptions.UNSET_LAST_SEQUENCE, po.getExpectedLastSubjectSequence());
        assertNull(po.getExpectedLastSubjectSequenceSubject());
        assertNull(po.getMessageId());
    }

    @Test
    public void testProperties() {
        // plain milliseconds
        Properties p = new Properties();
        p.setProperty(PublishOptions.PROP_PUBLISH_TIMEOUT, "1200000");
        PublishOptions po = new PublishOptions.Builder(p).build();
        assertEquals(1200000, po.getStreamTimeout(), "millis timeout");

        // ISO-8601 duration form (accepted, converted to millis)
        p = new Properties();
        p.setProperty(PublishOptions.PROP_PUBLISH_TIMEOUT, "PT20M");
        po = new PublishOptions.Builder(p).build();
        assertEquals(1200000, po.getStreamTimeout(), "20M timeout");

        p = new Properties();
        po = new PublishOptions.Builder(p).build();
        assertEquals(PublishOptions.DEFAULT_TIMEOUT, po.getStreamTimeout());
    }

    @Test
    public void testMessageTtl() {
        PublishOptions po = PublishOptions.builder().messageTtlSeconds(3).build();
        assertEquals("3s", po.getMessageTtl());

        po = PublishOptions.builder().messageTtlCustom("abcd").build();
        assertEquals("abcd", po.getMessageTtl());

        po = PublishOptions.builder().messageTtlNever().build();
        assertEquals("never", po.getMessageTtl());

        po = PublishOptions.builder().messageTtl(io.synadia.client.api.MessageTtl.seconds(3)).build();
        assertEquals("3s", po.getMessageTtl());

        po = PublishOptions.builder().messageTtl(io.synadia.client.api.MessageTtl.custom("abcd")).build();
        assertEquals("abcd", po.getMessageTtl());

        po = PublishOptions.builder().messageTtl(io.synadia.client.api.MessageTtl.never()).build();
        assertEquals("never", po.getMessageTtl());

        po = PublishOptions.builder().messageTtlSeconds(0).build();
        assertNull(po.getMessageTtl());

        po = PublishOptions.builder().messageTtlSeconds(-1).build();
        assertNull(po.getMessageTtl());

        po = PublishOptions.builder().messageTtlCustom(null).build();
        assertNull(po.getMessageTtl());

        po = PublishOptions.builder().messageTtlCustom("").build();
        assertNull(po.getMessageTtl());

        po = PublishOptions.builder().messageTtl(null).build();
        assertNull(po.getMessageTtl());

        assertThrows(IllegalArgumentException.class, () -> io.synadia.client.api.MessageTtl.seconds(0));
        assertThrows(IllegalArgumentException.class, () -> io.synadia.client.api.MessageTtl.seconds(-1));
        assertThrows(IllegalArgumentException.class, () -> io.synadia.client.api.MessageTtl.custom(null));
        assertThrows(IllegalArgumentException.class, () -> io.synadia.client.api.MessageTtl.custom(""));

        assertTrue(io.synadia.client.api.MessageTtl.seconds(3).toString().contains("3s")); // COVERAGE
    }
}
