package io.synadia.client.kv;

import io.synadia.client.impl.JetStreamTestBase;

public class JetStreamManagementKvSpecificTests extends JetStreamTestBase {

//    @Test
//    public void testDirectMessageRepublishedSubject() throws Exception {
//        runInSharedCustom((nc, ctx) -> {
//            String bucketName = random();
//            String subject = random();
//            String streamSubject = subject + ".>";
//            String publishSubject1 = subject + ".one";
//            String publishSubject2 = subject + ".two";
//            String publishSubject3 = subject + ".three";
//            String republishDest = "$KV." + bucketName + ".>";
//
//            ctx.createOrReplaceStream(ctx.scBuilder(streamSubject)
//                .republishCreator(new RepublishCreator(">", republishDest)));
//
//            ctx.kvCreate(bucketName);
//            KeyValue kv = nc.keyValue(bucketName);
//
//            nc.publish(publishSubject1, "uno".getBytes());
//            new JetStream(nc).publish(publishSubject2, "dos".getBytes());
//            kv.put(publishSubject3, "tres");
//
//            KeyValueEntry kve1 = kv.get(publishSubject1);
//            assertEquals(bucketName, kve1.getBucket());
//            assertEquals(publishSubject1, kve1.getKey());
//            assertEquals("uno", kve1.getValueAsString());
//
//            KeyValueEntry kve2 = kv.get(publishSubject2);
//            assertEquals(bucketName, kve2.getBucket());
//            assertEquals(publishSubject2, kve2.getKey());
//            assertEquals("dos", kve2.getValueAsString());
//
//            KeyValueEntry kve3 = kv.get(publishSubject3);
//            assertEquals(bucketName, kve3.getBucket());
//            assertEquals(publishSubject3, kve3.getKey());
//            assertEquals("tres", kve3.getValueAsString());
//        });
//    }
}
