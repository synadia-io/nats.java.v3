package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static io.synadia.client.utils.JetStreamApiUtils.generateConsumerName;
import static io.synadia.client.utils.JsValidator.validateStreamName;
import static io.synadia.client.utils.NatsRequestCompletableFuture.CancelAction;

@NullMarked
public class JetStreamImpl implements JetStreamConstants {

    // currently the only thing we care about caching is the allowDirect setting
    static class CachedStreamInfo {
        public final boolean allowDirect;

        public CachedStreamInfo(StreamInfo si) {
            allowDirect = si.getConfiguration().getAllowDirect();
        }
    }

    private static final ConcurrentHashMap<String, CachedStreamInfo> CACHED_STREAM_INFO_MAP = new ConcurrentHashMap<>();

    final NatsConnection conn;
    final JetStreamOptions jso;
    final long timeoutMillis;
    final boolean consumerCreate290Available;
    final boolean multipleSubjectFilter210Available;
    final boolean directBatchGet211Available;

    // ----------------------------------------------------------------------------------------------------
    // Create / Init
    // ----------------------------------------------------------------------------------------------------
    JetStreamImpl(NatsConnection connection, @Nullable JetStreamOptions jsOptions) {
        if (connection.isClosing() || connection.isClosed()) {
            throw new IllegalStateException("A JetStream context can't be established during close.");
        }
        conn = connection;

        // Get a working version of JetStream Options...
        // Clone the input jsOptions (JetStreamOptions.builder(...) handles null.
        // If jsOptions is not supplied or the jsOptions request timeout
        // was not set, use the connection options connect timeout.
        timeoutMillis = jsOptions == null || jsOptions.getRequestTimeout() <= 0 ? conn.getOptions().getConnectionTimeout() : jsOptions.getRequestTimeout();
        jso = JetStreamOptions.builder(jsOptions).requestTimeout(timeoutMillis).build();

        ServerInfo si = conn.getServerInfo();
        consumerCreate290Available = si.isSameOrNewerThanVersion("2.9.0") && !jso.isOptOut290ConsumerCreate();
        multipleSubjectFilter210Available = si.isNewerVersionThan("2.9.99");
        directBatchGet211Available = si.isNewerVersionThan("2.10.99");
    }

    JetStreamImpl(JetStreamImpl impl) {
        conn = impl.conn;
        jso = impl.jso;
        timeoutMillis = impl.timeoutMillis;
        consumerCreate290Available = impl.consumerCreate290Available;
        multipleSubjectFilter210Available = impl.multipleSubjectFilter210Available;
        directBatchGet211Available = impl.directBatchGet211Available;
    }

    public long getTimeout() {
        return timeoutMillis;
    }

    // ----------------------------------------------------------------------------------------------------
    // Management that is also needed by regular context
    // ----------------------------------------------------------------------------------------------------
    ConsumerInfo strictGetConsumerInfo(String streamName, String consumerName) throws IOException, JetStreamApiException {
        String subj = String.format(JSAPI_CONSUMER_INFO, streamName, consumerName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout());
        return new ConsumerInfo(resp).throwOnHasError();
    }

    @Nullable
    ConsumerInfo lenientGetConsumerInfo(String streamName, String consumerName) throws IOException, JetStreamApiException {
        try {
            return strictGetConsumerInfo(streamName, consumerName);
        }
        catch (JetStreamApiException e) {
            // The right side of this condition (after the ||) is for backward compatibility with server versions that did not provide api error codes
            if (e.getApiErrorCode() == JS_CONSUMER_NOT_FOUND_ERR || (e.getErrorCode() == 404 && e.getErrorDescription().contains("consumer"))) {
                return null;
            }
            throw e;
        }
    }

    ConsumerInfo _createConsumer(String stream, ConsumerCreator<?> creator, ConsumerCreateRequest.Action action) throws IOException, JetStreamApiException {
        validateStreamName(stream, true);

        String consumerName = creator.getName();
        boolean hasMultipleFilterSubjects = creator.hasMultipleFilterSubjects();

        // the creator does not require setting the deliver subject,
        // so we do it here if the creator is push, and it doesn't have one.
        if (creator instanceof PushDeliverSubjectInterface pdsi) {
            if (pdsi.getDeliverSubject() == null) {
                pdsi.deliverSubject(conn.createInbox());
            }
        }

        String durable = creator.getDurable();
        String subj;
        // new consumer create not available before 290 and can't be used with multiple filter subjects
        if (!hasMultipleFilterSubjects) {
            if (consumerName == null) {
                // if both consumerName and durable are null, generate a name
                consumerName = durable == null ? generateConsumerName() : durable;
            }
            String fs = creator.getFilterSubject(); // we've already determined there are not more than 1 filter subjects, so this gives us one or null
            if (fs == null || fs.equals(GREATER_THAN)) {
                subj = String.format(JSAPI_CONSUMER_CREATE_V290, stream, consumerName);
            }
            else {
                subj = String.format(JSAPI_CONSUMER_CREATE_V290_W_FILTER, stream, consumerName, fs);
            }
        }
        else if (durable == null) {
            subj = String.format(JSAPI_CONSUMER_CREATE, stream);
        }
        else {
            subj = String.format(JSAPI_DURABLE_CREATE, stream, durable);
        }

        ConsumerCreateRequest ccr = new ConsumerCreateRequest(stream, creator, action);
        Message resp = makeRequestResponseRequired(subj, ccr.serialize(), getTimeout());
        return new ConsumerInfo(resp).throwOnHasError();
    }

    StreamInfo _getStreamInfo(String streamName, @Nullable StreamInfoOptions options) throws IOException, JetStreamApiException {
        String subj = String.format(JSAPI_STREAM_INFO, streamName);
        StreamInfoReader sir = new StreamInfoReader();
        while (sir.hasMore()) {
            Message resp = makeRequestResponseRequired(subj, sir.nextJson(options), getTimeout());
            sir.process(resp);
        }
        return cacheStreamInfo(streamName, sir.getStreamInfo());
    }

    StreamInfo createAndCacheStreamInfoThrowOnError(String streamName, Message resp) throws JetStreamApiException {
        return cacheStreamInfo(streamName, new StreamInfo(resp).throwOnHasError());
    }

    StreamInfo cacheStreamInfo(String streamName, StreamInfo si) {
        CACHED_STREAM_INFO_MAP.put(streamName, new CachedStreamInfo(si));
        return si;
    }

    List<StreamInfo> cacheStreamInfo(List<StreamInfo> list) {
        list.forEach(si -> CACHED_STREAM_INFO_MAP.put(si.getConfiguration().getName(), new CachedStreamInfo(si)));
        return list;
    }


    @Nullable
    String lookupStreamBySubject(String subject) throws IOException, JetStreamApiException {
        List<String> list = getStreamNamesInternal(subject);
        return list.size() == 1 ? list.get(0) : null;
    }

    List<String> getStreamNamesInternal(@Nullable String subjectFilter) throws IOException, JetStreamApiException {
        StreamNamesReader snr = new StreamNamesReader();
        while (snr.hasMore()) {
            Message resp = makeRequestResponseRequired(JSAPI_STREAM_NAMES, snr.nextJson(subjectFilter), getTimeout());
            snr.process(resp);
        }
        return snr.getStrings();
    }

    // ----------------------------------------------------------------------------------------------------
    // Request Utils
    // ----------------------------------------------------------------------------------------------------
    Message makeRequestResponseRequired(String subject, byte @Nullable[] bytes, long timeoutMillis) throws IOException {
        try {
            return responseRequired(conn.request(prependPrefix(subject), null, bytes, timeoutMillis, CancelAction.REPORT, conn.isForceFlushOnRequest()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    Message makeInternalRequestResponseRequired(String subject, @Nullable Headers headers, byte @Nullable [] data, long timeoutMillis) throws IOException {
        try {
            return responseRequired(conn.request(subject, headers, data, timeoutMillis, CancelAction.COMPLETE, conn.isForceFlushOnRequest()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    Message responseRequired(@Nullable Message respMessage) throws IOException {
        if (respMessage == null) {
            throw new IOException("Timeout or no response waiting for NATS JetStream server");
        }
        return respMessage;
    }

    String prependPrefix(String subject) {
        return jso.getPrefix() + subject;
    }

    CachedStreamInfo getCachedStreamInfo(String streamName) throws IOException, JetStreamApiException {
        CachedStreamInfo csi = CACHED_STREAM_INFO_MAP.get(streamName);
        if (csi != null) {
            return csi;
        }
        _getStreamInfo(streamName, null);
        return CACHED_STREAM_INFO_MAP.get(streamName);
    }
}
