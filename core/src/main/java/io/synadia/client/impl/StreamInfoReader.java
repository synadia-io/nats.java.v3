package io.synadia.client.impl;

import io.synadia.client.JetStreamApiException;
import io.synadia.client.Message;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.api.StreamInfoOptions;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.synadia.client.support.ApiConstants.DELETED_DETAILS;
import static io.synadia.client.support.ApiConstants.SUBJECTS_FILTER;
import static io.synadia.client.support.JsonUtils.*;

class StreamInfoReader {

    private StreamInfo streamInfo;
    private ListRequestEngine engine;

    StreamInfoReader() {
        engine = new ListRequestEngine();
    }

    void process(@NonNull Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        StreamInfo si = new StreamInfo(msg);
        if (streamInfo == null) {
            streamInfo = si;
        }
        else {
            streamInfo.getStreamState().getSubjects().addAll(si.getStreamState().getSubjects());
        }
    }

    boolean hasMore() {
        return engine.hasMore();
    }

    byte @NonNull [] nextJson(@Nullable StreamInfoOptions options) {
        StringBuilder sb = beginJson();
        addField(sb, "offset", engine.nextOffset());
        if (options != null) {
            addField(sb, SUBJECTS_FILTER, options.getSubjectsFilter());
            addFldWhenTrue(sb, DELETED_DETAILS, options.isDeletedDetails());
        }
        return endJson(sb).toString().getBytes();
    }

    StreamInfo getStreamInfo() {
        return streamInfo;
    }
}
