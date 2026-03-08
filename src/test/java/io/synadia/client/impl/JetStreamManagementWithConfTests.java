package io.synadia.client.impl;

import io.synadia.client.Connection;
import io.synadia.client.JetStream;
import io.synadia.client.JetStreamManagement;
import io.synadia.client.api.*;
import io.synadia.client.utils.ConnectionUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.synadia.client.utils.OptionsUtils.options;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JetStreamManagementWithConfTests extends JetStreamTestBase {

    @Test
    public void testGetStreamInfoSubjectPagination() throws Exception {
        runInConfiguredServer("pagination.conf", ts -> {
            try (Connection nc = ConnectionUtils.managedConnect(options(ts))) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                JetStream js = jsm.jetStream();

                String stream1 = random();
                String stream2 = random();
                long rounds = 101;
                long size = 1000;
                long count = rounds * size;
                jsm.addStream(StreamConfiguration.builder()
                    .name(stream1)
                    .storageType(StorageType.Memory)
                    .subjects("s.*.*")
                    .build());

                jsm.addStream(StreamConfiguration.builder()
                    .name(stream2)
                    .storageType(StorageType.Memory)
                    .subjects("t.*.*")
                    .build());

                for (int x = 1; x <= rounds; x++) {
                    for (int y = 1; y <= size; y++) {
                        js.publish("s." + x + "." + y, null);
                    }
                }

                for (int y = 1; y <= size; y++) {
                    js.publish("t.7." + y, null);
                }

                StreamInfo si = jsm.getStreamInfo(stream1);
                validateStreamInfo(si.getStreamState(), 0, 0, count);

                si = jsm.getStreamInfo(stream1, StreamInfoOptions.allSubjects());
                validateStreamInfo(si.getStreamState(), count, count, count);

                si = jsm.getStreamInfo(stream1, StreamInfoOptions.filterSubjects("s.7.*"));
                validateStreamInfo(si.getStreamState(), size, size, count);

                si = jsm.getStreamInfo(stream1, StreamInfoOptions.filterSubjects("s.7.1"));
                validateStreamInfo(si.getStreamState(), 1L, 1, count);

                si = jsm.getStreamInfo(stream2, StreamInfoOptions.filterSubjects("t.7.*"));
                validateStreamInfo(si.getStreamState(), size, size, size);

                si = jsm.getStreamInfo(stream2, StreamInfoOptions.filterSubjects("t.7.1"));
                validateStreamInfo(si.getStreamState(), 1L, 1, size);

                List<StreamInfo> infos = jsm.getStreams();
                assertEquals(2, infos.size());
                si = infos.get(0);
                if (si.getConfiguration().getSubjects().get(0).equals("s.*.*")) {
                    validateStreamInfo(si.getStreamState(), 0, 0, count);
                    validateStreamInfo(infos.get(1).getStreamState(), 0, 0, size);
                }
                else {
                    validateStreamInfo(si.getStreamState(), 0, 0, size);
                    validateStreamInfo(infos.get(1).getStreamState(), 0, 0, count);
                }

                infos = jsm.getStreams(">");
                assertEquals(2, infos.size());

                infos = jsm.getStreams("*.7.*");
                assertEquals(2, infos.size());

                infos = jsm.getStreams("*.7.1");
                assertEquals(2, infos.size());

                infos = jsm.getStreams("s.7.*");
                assertEquals(1, infos.size());
                assertEquals("s.*.*", infos.get(0).getConfiguration().getSubjects().get(0));

                infos = jsm.getStreams("t.7.1");
                assertEquals(1, infos.size());
                assertEquals("t.*.*", infos.get(0).getConfiguration().getSubjects().get(0));
            }
        });
    }

    private void validateStreamInfo(StreamState streamState, long subjectsList, long filteredCount, long subjectCount) {
        assertEquals(subjectsList, streamState.getSubjects().size());
        assertEquals(filteredCount, streamState.getSubjects().size());
        assertEquals(subjectCount, streamState.getSubjectCount());
    }

    @Test
    public void testGoodAuthAccount() throws Exception {
        runInConfiguredServer("js_authorization.conf", ts -> {
            try (Connection nc = ConnectionUtils.managedConnect(optionsBuilder(ts).userInfo("serviceup", "uppass").build())) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                // add streams with both account
                String stream = random();
                String subject1 = random();
                String subject2 = random();
                StreamConfiguration sc = StreamConfiguration.builder()
                    .name(stream)
                    .storageType(StorageType.Memory)
                    .subjects(subject1)
                    .build();
                StreamInfo si = jsm.addStream(sc);

                sc = StreamConfiguration.builder(si.getConfiguration())
                    .addSubjects(subject2)
                    .build();

                jsm.updateStream(sc);
            }
        });
    }
}
