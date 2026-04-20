package io.synadia.client.impl;

import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.testutils.TestBase;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JetStreamTestingContext implements AutoCloseable {
    public final NatsConnection nc;
    public final JetStreamManagement jsm;
    public final JetStream js;

    private final String subjectBase;
    private final Map<Object, String> subjects;
    private final String consumerNameBase;
    private final Map<Object, String> consumerNames;
    public String stream;
    public StreamInfo si;
    public NatsDispatcher dispatcher;

    private final Set<String> streams;

    public JetStreamTestingContext(NatsConnection nc, int subjectCount) throws JetStreamApiException, IOException {
        this.nc = nc;
        jsm = new JetStreamManagement(nc);
        js = jsm.jetStream();

        stream = TestBase.random();
        subjectBase = TestBase.random();
        subjects = new HashMap<>();
        consumerNameBase = TestBase.random();
        consumerNames = new HashMap<>();

        streams = new HashSet<>();

        if (subjectCount > 0) {
            createOrReplaceStream(subjectCount);
        }
    }

    public NatsDispatcher getDispatcher() {
        if (dispatcher == null) {
            dispatcher = nc.createDispatcher();
        }
        return dispatcher;
    }

    // ----------------------------------------------------------------------------------------------------
    // JetStream
    // ----------------------------------------------------------------------------------------------------
    public String[] getSubjects(int subjectCount) {
        String[] subjects = new String[subjectCount];
        for (int x = 0; x < subjectCount; x++) {
            subjects[x] = subject(x);
        }
        return subjects;
    }

    public void createOrReplaceStream() throws JetStreamApiException, IOException {
        createOrReplaceStream(scBuilder(subject(0)));
    }

    public void createOrReplaceStream(int subjectCount) throws JetStreamApiException, IOException {
        createOrReplaceStream(scBuilder(getSubjects(subjectCount)));
    }

    public void createOrReplaceStream(String... subjects) throws JetStreamApiException, IOException {
        createOrReplaceStream(scBuilder(subjects));
    }

    public StreamInfo createOrReplaceStream(StreamCreator sc) throws JetStreamApiException, IOException {
        String streamName = sc.getName();
        try { jsm.deleteStream(streamName); } catch (Exception ignore) {}
        streams.remove(streamName);
        si = jsm.addStream(sc);
        streams.add(streamName);
        return si;
    }

    public StreamInfo addStream(StreamCreator sc) throws JetStreamApiException, IOException {
        String streamName = sc.getName();
        si = jsm.addStream(sc);
        streams.add(streamName);
        return si;
    }

    public StreamCreator scBuilder(int subjectCount) {
        StreamCreator sc = new StreamCreator(stream)
            .storageType(StorageType.Memory);
        if (subjectCount > 0) {
            sc.subjects(getSubjects(subjectCount));
        }
        return sc;
    }

    public StreamCreator scBuilder(String... subjects) {
        if (subjects.length == 0) {
            subjects = new String[]{subject(0)};
        }
        return new StreamCreator(stream)
            .storageType(StorageType.Memory)
            .subjects(subjects);
    }

    public boolean deleteStream() throws JetStreamApiException, IOException {
        boolean deleted = jsm.deleteStream(stream);
        if (deleted) {
            streams.remove(stream);
        }
        return deleted;
    }

    public String subject() {
        return subject(0);
    }

    public String subject(Object variant) {
        return subjects.computeIfAbsent(variant, v -> subjectBase + "_" + v);
    }

    public String consumerName() {
        return consumerNameBase;
    }

    public String consumerName(Object variant) {
        return consumerNames.computeIfAbsent(variant, v -> consumerNameBase + "-" + v);
    }

    @Override
    public void close() throws Exception {
        for (String strm : streams) {
            try { jsm.deleteStream(strm); } catch (Exception ignore) {}
        }
    }
}
