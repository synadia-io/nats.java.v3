package io.synadia.client;

import io.synadia.client.js.consumer.ConsumerConfiguration;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import static io.synadia.client.support.NatsConstants.EMPTY;
import static io.synadia.client.support.NatsJetStreamClientError.JsSoOrderedNotAllowedWithBind;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SubscribeOptionsTests extends TestBase {
    private static final String[] badNames = {HAS_DOT, GT_NOT_SEGMENT, STAR_NOT_SEGMENT, HAS_FWD_SLASH, HAS_BACK_SLASH};

    @Test
    public void testPushFieldValidation() {
        for (String bad : badNames) {
            PushSubscribeOptions.Builder pushBuilder = PushSubscribeOptions.builder();
            assertThrows(IllegalArgumentException.class, () -> pushBuilder.stream(bad).build());
            assertThrows(IllegalArgumentException.class, () -> pushBuilder.name(bad).build());
            assertThrows(IllegalArgumentException.class, () -> ConsumerConfiguration.builder().durable(bad).build());
            assertThrows(IllegalArgumentException.class, () -> ConsumerConfiguration.builder().name(bad).build());
        }

        String name = random();
        String durable = random();

        // new helper // COVERAGE
        ConsumerConfiguration.builder().durable(durable).buildPushSubscribeOptions();
        ConsumerConfiguration.builder().name(name).buildPushSubscribeOptions();
    }

    @Test
    public void testPullFieldValidation() {
        for (String bad : badNames) {
            PullSubscribeOptions.Builder pullBuilder = PullSubscribeOptions.builder();
            assertThrows(IllegalArgumentException.class, () -> pullBuilder.stream(bad).build());
            assertThrows(IllegalArgumentException.class, () -> pullBuilder.name(bad).build());
            assertThrows(IllegalArgumentException.class, () -> ConsumerConfiguration.builder().durable(bad).build());
            assertThrows(IllegalArgumentException.class, () -> ConsumerConfiguration.builder().name(bad).build());
        }
    }

    @Test
    public void testBindCreationErrors() {
        String random = random();

        // bind
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.bind(null, random));
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.bind(EMPTY, random));
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.bind(random, null));
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.bind(random, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.builder().stream(random).bind(true).build());


        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.builder().stream(EMPTY).name(random).bind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.builder().name(random).bind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PushSubscribeOptions.builder().stream(random).name(EMPTY).bind(true).build());

        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.bind(null, random));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.bind(EMPTY, random));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.bind(random, null));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.bind(random, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(random).bind(true).build());


        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(EMPTY).name(random).bind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().name(random).bind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(random).name(EMPTY).bind(true).build());

        // fast bind
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.fastBind(null, random));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.fastBind(EMPTY, random));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.fastBind(random, null));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.fastBind(random, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(random).fastBind(true).build());


        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(EMPTY).name(random).fastBind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().name(random).fastBind(true).build());
        assertThrows(IllegalArgumentException.class, () -> PullSubscribeOptions.builder().stream(random).name(EMPTY).fastBind(true).build());
    }

    @Test
    public void testOrderedCreation() {
        String random = random();
        assertClientError(JsSoOrderedNotAllowedWithBind,
            () -> PushSubscribeOptions.builder().stream(random).bind(true).ordered(true).build());
    }
}
