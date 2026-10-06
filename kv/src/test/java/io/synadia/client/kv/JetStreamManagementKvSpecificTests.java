package io.synadia.client.kv;

import io.synadia.client.api.*;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.JetStreamTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JetStreamManagementKvSpecificTests extends JetStreamTestBase {

    @Test
    public void testDirectMessageRepublishedSubject() throws Exception {
        runInShared(nc -> {
            String streamBucketName = random();
            String subject = random();
            String streamSubject = subject + ".>";
            String publishSubject1 = subject + ".one";
            String publishSubject2 = subject + ".two";
            String publishSubject3 = subject + ".three";
            String republishDest = "$KV." + streamBucketName + ".>";

            JetStreamManagement jsm = new JetStreamManagement(nc);
            try (KvTestingContext kvCtx = new KvTestingContext(nc)) {
                StreamCreator sc = new StreamCreator(streamBucketName)
                    .storageType(StorageType.Memory)
                    .subjects(streamSubject)
                    .republishCreator(new RepublishCreator(">", republishDest));
                kvCtx.jsm.addStream(sc);

                KeyValue kv = kvCtx.kvCreate(streamBucketName);

                nc.publish(publishSubject1, "uno".getBytes());
                kvCtx.js.publish(publishSubject2, "dos".getBytes());
                kv.put(publishSubject3, "tres");

                KeyValueEntry kve1 = kv.get(publishSubject1);
                assertEquals(streamBucketName, kve1.getBucket());
                assertEquals(publishSubject1, kve1.getKey());
                assertEquals("uno", kve1.getValueAsString());

                KeyValueEntry kve2 = kv.get(publishSubject2);
                assertEquals(streamBucketName, kve2.getBucket());
                assertEquals(publishSubject2, kve2.getKey());
                assertEquals("dos", kve2.getValueAsString());

                KeyValueEntry kve3 = kv.get(publishSubject3);
                assertEquals(streamBucketName, kve3.getBucket());
                assertEquals(publishSubject3, kve3.getKey());
                assertEquals("tres", kve3.getValueAsString());
            }
            finally {
                try {
                    jsm.deleteStream(streamBucketName);
                }
                catch (Exception ignore) {}
            }
        });
    }
}
