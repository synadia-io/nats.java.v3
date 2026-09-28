package io.synadia.client.kv;

import io.synadia.client.api.SourceCreator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KeyValueCreatorTests {

    @Test
    public void testKeyValueConfigurationCreatorCollectionInvariants() {
        // ---- metadata (not @Nullable on KV-level setter; don't pass null) ----
        KeyValueCreator c = new KeyValueCreator("b");
        assertEquals("b", c.getBucketName());
        assertEquals(1, c.getMaxHistoryPerKey());
        assertEquals(1, c.getReplicas());
        assertNotNull(c.getMetadata());
        assertTrue(c.getMetadata().isEmpty());
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        c.metadata(m);
        assertEquals(1, c.getMetadata().size());
        c.metadata(new HashMap<>());
        assertTrue(c.getMetadata().isEmpty());

        // ---- sourceCreators (not @Nullable on KV-level setter; don't pass null) ----
        c = new KeyValueCreator("b");
        assertNotNull(c.getSourceCreators());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sourceCreators(new SourceCreator("a"), new SourceCreator("b"));
        assertEquals(2, c.getSourceCreators().size());
        c.sourceCreators(new ArrayList<>());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sourceCreators(new SourceCreator("a"), new SourceCreator("b"));
        c.sourceCreators();
        assertTrue(c.getSourceCreators().isEmpty());
    }
}
