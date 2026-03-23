package io.synadia.client.api;

import io.nats.json.JsonParser;
import io.nats.json.JsonValue;
import io.synadia.client.impl.JetStreamTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.synadia.client.support.NatsConstants.EMPTY;
import static org.junit.jupiter.api.Assertions.*;

public class KeyValueConfigurationTests extends JetStreamTestBase {

    @Test
    public void testConstruction() {
        Placement p = Placement.builder().cluster("cluster").tags("a", "b").build();
        Republish r = Republish.builder().source("src").destination("dest").headersOnly(true).build();

        // builder
        KeyValueConfiguration bc = KeyValueConfiguration.builder()
            .name("bucketName")
            .description("bucketDesc")
            .maxHistoryPerKey(44)
            .maxBucketSize(555)
            .maximumValueSize(666)  // shows that order matters too
            .ttl(Duration.ofMillis(777))
            .storageType(StorageType.Memory)
            .replicas(2)
            .placement(p)
            .republish(r)
            .compression(true)
            .limitMarker(8888)
            .build();
        validate(bc);

        validate(KeyValueConfiguration.builder(bc).build());

        JsonValue jvSc = JsonParser.parseUnchecked(bc.getBackingConfig().toJson());
        validate(new KeyValueConfiguration(StreamConfiguration.instance(jvSc)));

        bc = KeyValueConfiguration.builder()
                .name("bucketName")
                .build();

        assertEquals(1, bc.getMaxHistoryPerKey());
    }

    private void validate(KeyValueConfiguration kvc) {
        assertEquals("bucketName", kvc.getBucketName());
        assertEquals("bucketDesc", kvc.getDescription());
        assertEquals(44, kvc.getMaxHistoryPerKey());
        assertEquals(555, kvc.getMaxBucketSize());
        assertEquals(666, kvc.getMaxValueSize());
        assertEquals(Duration.ofMillis(777), kvc.getTtl());
        assertEquals(StorageType.Memory, kvc.getStorageType());
        assertEquals(2, kvc.getReplicas());
        assertNotNull(kvc.getPlacement());
        assertEquals("cluster", kvc.getPlacement().getCluster());
        assertNotNull(kvc.getPlacement().getTags());
        assertEquals(2, kvc.getPlacement().getTags().size());
        assertNotNull(kvc.getRepublish());
        assertEquals("src", kvc.getRepublish().getSource());
        assertEquals("dest", kvc.getRepublish().getDestination());
        assertTrue(kvc.getRepublish().isHeadersOnly());
        assertTrue(kvc.isCompressed());
        assertNotNull(kvc.getLimitMarkerTtl());
        assertEquals(8888, kvc.getLimitMarkerTtl().toMillis());

        assertTrue(kvc.toString().contains("bucketName"));
    }

    @Test
    public void testConstructionInvalidsCoverage() {
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().build());
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(null));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(EMPTY));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(HAS_PRINTABLE));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(HAS_DOT));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(STAR_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(GT_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().name(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder(HAS_FWD_SLASH));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder(HAS_BACK_SLASH));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder(HAS_EQUALS));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder(HAS_TIC));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maxHistoryPerKey(0));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maxHistoryPerKey(-1));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maxHistoryPerKey(65));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maxBucketSize(0));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maxBucketSize(-2));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maximumValueSize(0));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().maximumValueSize(-2));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().ttl(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().replicas(0));
        assertThrows(IllegalArgumentException.class, () -> KeyValueConfiguration.builder().replicas(6));
    }
}
