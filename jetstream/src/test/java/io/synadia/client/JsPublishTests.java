package io.synadia.client;

import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.PushConsumerCreator;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.impl.JetStream;
import io.synadia.client.impl.JetStreamManagement;
import io.synadia.client.impl.JetStreamPushSubscription;
import io.synadia.client.impl.JetStreamTestBase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.utils.ResourceUtils.dataAsLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsPublishTests extends JetStreamTestBase {

    @Test
    public void testUtf8Subjects() throws Exception {
        List<String> rawUtfSubjects = dataAsLines("utf8-test-strings.txt");
        List<String> noSpacesUtfSubjects = new ArrayList<>(rawUtfSubjects.size());
        for (String s : rawUtfSubjects) {
            noSpacesUtfSubjects.add(s.replace(" ", ""));
        }

        String streamName = random();

        runInSharedCustom((nc, ctx) -> {
            StreamCreator sc = new StreamCreator(streamName)
                .subjects(noSpacesUtfSubjects)
                .storageType(StorageType.Memory);
            ctx.createOrReplaceStream(sc);

            JetStreamManagement jsm = new JetStreamManagement(nc);
            JetStream js = jsm.jetStream();

            for (String s : noSpacesUtfSubjects) {
                String data = random() + " " + s;
                js.publish(s, data);
                PushConsumerCreator creator = new PushConsumerCreator().subjects(s);
                ConsumerInfo ci = jsm.createConsumer(streamName, creator);
                JetStreamPushSubscription sub = js.pushSubscribe(ci);
                Message m = sub.nextMessage(1000L);
                assertEquals(data, new String(m.getData(), StandardCharsets.UTF_8));
            }
        });
    }
}
