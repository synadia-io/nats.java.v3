package io.synadia.client.os;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStoreConfigurationCreatorTests {

    @Test
    public void testObjectStoreConfigurationCreatorCollectionInvariants() {
        // ---- metadata (not @Nullable on OS-level setter; don't pass null) ----
        ObjectStoreConfigurationCreator c = new ObjectStoreConfigurationCreator("b");
        assertNotNull(c.getMetadata());
        assertTrue(c.getMetadata().isEmpty());
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        c.metadata(m);
        assertEquals(1, c.getMetadata().size());
        c.metadata(new HashMap<>());
        assertTrue(c.getMetadata().isEmpty());
    }
}
