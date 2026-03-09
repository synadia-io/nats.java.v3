// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.examples.z;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class ZPortNetModel {

    static String START = "// Copyright 2023 The NATS Authors\n" +
        "// Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
        "// you may not use this file except in compliance with the License.\n" +
        "// You may obtain a copy of the License at\n" +
        "//\n" +
        "// http://www.apache.org/licenses/LICENSE-2.0\n" +
        "//\n" +
        "// Unless required by applicable law or agreed to in writing, software\n" +
        "// distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
        "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
        "// See the License for the specific language governing permissions and\n" +
        "// limitations under the License.\n\n" +
        "using System.Text.Json.Serialization;\n" +
        "\n" +
        "namespace NATS.Client.JetStream.Models;\n" +
        "\n" +
        "public record RECORD\n" +
        "{";

    static byte[] END = "}\n".getBytes();

    public static void main(String[] args) throws IOException {
        File[] files = new File("C:\\nats\\nats.net\\src\\NATS.Client\\JetStream").listFiles();
        assert files != null;
        for (File f : files) {
            if (RECORDS.contains(f.getName())) {
                System.out.println(f);
                try (FileOutputStream out = new FileOutputStream("C:\\nats\\nats.net.v2\\src\\NATS.Client.JetStream\\Models\\" + f.getName())) {
                    List<String> done = new ArrayList<>();
                    String recName = f.getName().replace(".cs", "");
                    out.write(START.replace("RECORD", recName).getBytes());

                    List<String> lines = Files.readAllLines(f.toPath());
                    for (String line : lines) {
                        if (line.contains("ApiConstants.")) {
                            int at = line.indexOf("ApiConstants.");
                            StringBuilder con = new StringBuilder();
                            String temp = line.substring(at + 13);
                            for (int x = 0; x < temp.length(); x++) {
                                char c = temp.charAt(x);
                                if (Character.isJavaIdentifierPart(c)) {
                                    con.append(c);
                                }
                                else {
                                    break;
                                }
                            }
                            String cs = con.toString();
                            if (!done.contains(cs)) {
                                String type = getType(line);
                                done.add(cs);
                                out.write("\n    [JsonPropertyName(\"jpn\")]\n".replace("jpn", CMAP.get(cs)).getBytes());
                                out.write("    public type field { get; set; }\n".replace("type", type).replace("field", cs).getBytes());
                            }
                        }
                    }

                    out.write(END);
                }
            }
        }
    }

    private static String getType(String line) {
        if (line.contains("AsLong")) return "long";
        if (line.contains("AsBool")) return "bool";
        if (line.contains("AsUlong")) return "ulong";
        if (line.contains("AsInt")) return "int";
        if (line.contains("AsDate")) return "DateTimeOffset";
        if (line.contains("AsDuration")) return "TimeSpan";
        return "string";
    }

    static List<String> RECORDS = Arrays.asList(
        "AccountLimits.cs",
        "AccountStatistics.cs",
        "AccountTier.cs",
        "ApiResponse.cs",
        "ApiStats.cs",
        "ClusterInfo.cs",
        "ConsumerConfiguration.cs",
        "ConsumerCreateRequest.cs",
        "ConsumerInfo.cs",
        "Error.cs",
        "External.cs",
        "LostStreamData.cs",
        "MessageDeleteRequest.cs",
        "MessageGetRequest.cs",
        "MessageInfo.cs",
        "Mirror.cs",
        "MirrorInfo.cs",
        "PeerInfo.cs",
        "Placement.cs",
        "PublishAck.cs",
        "PublishOptions.cs",
        "PurgeOptions.cs",
        "PurgeResponse.cs",
        "Replica.cs",
        "Republish.cs",
        "SequenceInfo.cs",
        "SequencePair.cs",
        "ServerInfo.cs",
        "Source.cs",
        "SourceInfo.cs",
        "SourceInfoBase.cs",
        "StreamConfiguration.cs",
        "StreamInfo.cs",
        "StreamInfoOptions.cs",
        "StreamState.cs",
        "Subject.cs",
        "SubscribeOptions.cs",
        "SuccessApiResponse.cs"
    );
    
    static Map<String, String> CMAP;
    static {
        CMAP = new HashMap<>();
        CMAP.put("AckFloor", "ack_floor");
        CMAP.put("AckPolicy", "ack_policy");
        CMAP.put("AckWait", "ack_wait");
        CMAP.put("Active", "active");
        CMAP.put("AllowRollupHdrs", "allow_rollup_hdrs");
        CMAP.put("AllowDirect", "allow_direct");
        CMAP.put("Api", "api");
        CMAP.put("ApiUrl", "api_url");
        CMAP.put("AuthRequired", "auth_required");
        CMAP.put("AverageProcessingTime", "average_processing_time");
        CMAP.put("Backoff", "backoff");
        CMAP.put("Batch", "batch");
        CMAP.put("Bucket", "bucket");
        CMAP.put("Bytes", "bytes");
        CMAP.put("Chunks", "chunks");
        CMAP.put("ClientId", "client_id");
        CMAP.put("ClientIp", "client_ip");
        CMAP.put("Cluster", "cluster");
        CMAP.put("Code", "code");
        CMAP.put("Config", "config");
        CMAP.put("ConnectUrls", "connect_urls");
        CMAP.put("ConsumerCount", "consumer_count");
        CMAP.put("ConsumerSeq", "consumer_seq");
        CMAP.put("Consumers", "consumers");
        CMAP.put("Created", "created");
        CMAP.put("Current", "current");
        CMAP.put("Data", "data");
        CMAP.put("Deleted", "deleted");
        CMAP.put("DeletedDetails", "deleted_details");
        CMAP.put("Deliver", "deliver");
        CMAP.put("DeliverGroup", "deliver_group");
        CMAP.put("DeliverPolicy", "deliver_policy");
        CMAP.put("DeliverSubject", "deliver_subject");
        CMAP.put("Delivered", "delivered");
        CMAP.put("DenyDelete", "deny_delete");
        CMAP.put("DenyPurge", "deny_purge");
        CMAP.put("Description", "description");
        CMAP.put("Dest", "dest");
        CMAP.put("Digest", "digest");
        CMAP.put("Discard", "discard");
        CMAP.put("DiscardNewPerSubject", "discard_new_per_subject");
        CMAP.put("Domain", "domain");
        CMAP.put("Duplicate", "duplicate");
        CMAP.put("DuplicateWindow", "duplicate_window");
        CMAP.put("DurableName", "durable_name");
        CMAP.put("Endpoints", "endpoints");
        CMAP.put("ErrCode", "err_code");
        CMAP.put("Error", "error");
        CMAP.put("Errors", "errors");
        CMAP.put("Expires", "expires");
        CMAP.put("External", "external");
        CMAP.put("Filter", "filter");
        CMAP.put("FilterSubject", "filter_subject");
        CMAP.put("FirstSeq", "first_seq");
        CMAP.put("FirstTs", "first_ts");
        CMAP.put("FlowControl", "flow_control");
        CMAP.put("Go", "go");
        CMAP.put("Hdrs", "hdrs");
        CMAP.put("Headers", "headers");
        CMAP.put("HeadersOnly", "headers_only");
        CMAP.put("Host", "host");
        CMAP.put("Id", "id");
        CMAP.put("IdleHeartbeat", "idle_heartbeat");
        CMAP.put("InactiveThreshold", "inactive_threshold");
        CMAP.put("Internal", "internal");
        CMAP.put("Jetstream", "jetstream");
        CMAP.put("Keep", "keep");
        CMAP.put("Lag", "lag");
        CMAP.put("LameDuckMode", "ldm");
        CMAP.put("LastActive", "last_active");
        CMAP.put("LastBySubject", "last_by_subj");
        CMAP.put("LastError", "last_error");
        CMAP.put("LastSeq", "last_seq");
        CMAP.put("LastTs", "last_ts");
        CMAP.put("Leader", "leader");
        CMAP.put("Limit", "limit");
        CMAP.put("Limits", "limits");
        CMAP.put("Link", "link");
        CMAP.put("Lost", "lost");
        CMAP.put("MaxAckPending", "max_ack_pending");
        CMAP.put("MaxAge", "max_age");
        CMAP.put("MaxBatch", "max_batch");
        CMAP.put("MaxBytes", "max_bytes");
        CMAP.put("MaxBytesRequired", "max_bytes_required");
        CMAP.put("MaxConsumers", "max_consumers");
        CMAP.put("MaxChunkSize", "max_chunk_size");
        CMAP.put("MaxDeliver", "max_deliver");
        CMAP.put("MaxExpires", "max_expires");
        CMAP.put("MaxMemory", "max_memory");
        CMAP.put("MaxMsgSize", "max_msg_size");
        CMAP.put("MaxMsgs", "max_msgs");
        CMAP.put("MaxMsgsPerSubject", "max_msgs_per_subject");
        CMAP.put("MaxPayload", "max_payload");
        CMAP.put("MaxStorage", "max_storage");
        CMAP.put("MaxStreams", "max_streams");
        CMAP.put("MaxWaiting", "max_waiting");
        CMAP.put("Memory", "memory");
        CMAP.put("MemoryMaxStreamBytes", "memory_max_stream_bytes");
        CMAP.put("MemStorage", "mem_storage");
        CMAP.put("Message", "message");
        CMAP.put("Messages", "messages");
        CMAP.put("Metadata", "metadata");
        CMAP.put("Mirror", "mirror");
        CMAP.put("MirrorDirect", "mirror_direct");
        CMAP.put("Msgs", "msgs");
        CMAP.put("Name", "name");
        CMAP.put("NextBySubject", "next_by_subj");
        CMAP.put("NoAck", "no_ack");
        CMAP.put("NoErase", "no_erase");
        CMAP.put("Nonce", "nonce");
        CMAP.put("NoWait", "no_wait");
        CMAP.put("Nuid", "nuid");
        CMAP.put("NumAckPending", "num_ack_pending");
        CMAP.put("NumDeleted", "num_deleted");
        CMAP.put("NumErrors", "num_errors");
        CMAP.put("NumPending", "num_pending");
        CMAP.put("NumRedelivered", "num_redelivered");
        CMAP.put("NumReplicas", "num_replicas");
        CMAP.put("NumRequests", "num_requests");
        CMAP.put("NumSubjects", "num_subjects");
        CMAP.put("NumWaiting", "num_waiting");
        CMAP.put("Offline", "offline");
        CMAP.put("Offset", "offset");
        CMAP.put("Options", "options");
        CMAP.put("OptStartSeq", "opt_start_seq");
        CMAP.put("OptStartTime", "opt_start_time");
        CMAP.put("Placement", "placement");
        CMAP.put("ProcessingTime", "processing_time");
        CMAP.put("Republish", "republish");
        CMAP.put("Port", "port");
        CMAP.put("Proto", "proto");
        CMAP.put("Purged", "purged");
        CMAP.put("PushBound", "push_bound");
        CMAP.put("RateLimitBps", "rate_limit_bps");
        CMAP.put("ReplayPolicy", "replay_policy");
        CMAP.put("Replica", "replica");
        CMAP.put("Replicas", "replicas");
        CMAP.put("Request", "request");
        CMAP.put("Response", "response");
        CMAP.put("Retention", "retention");
        CMAP.put("SampleFreq", "sample_freq");
        CMAP.put("Schema", "schema");
        CMAP.put("Sealed", "sealed");
        CMAP.put("Seq", "seq");
        CMAP.put("ServerId", "server_id");
        CMAP.put("ServerName", "server_name");
        CMAP.put("Size", "size");
        CMAP.put("Source", "source");
        CMAP.put("Sources", "sources");
        CMAP.put("Src", "src");
        CMAP.put("Started", "started");
        CMAP.put("State", "state");
        CMAP.put("Stats", "stats");
        CMAP.put("Storage", "storage");
        CMAP.put("StorageMaxStreamBytes", "storage_max_stream_bytes");
        CMAP.put("StreamName", "stream_name");
        CMAP.put("StreamSeq", "stream_seq");
        CMAP.put("Stream", "stream");
        CMAP.put("Streams", "streams");
        CMAP.put("Subject", "subject");
        CMAP.put("Subjects", "subjects");
        CMAP.put("SubjectsFilter", "subjects_filter");
        CMAP.put("Success", "success");
        CMAP.put("Tags", "tags");
        CMAP.put("TemplateOwner", "template_owner");
        CMAP.put("Tiers", "tiers");
        CMAP.put("Time", "time");
        CMAP.put("Tls", "tls_required");
        CMAP.put("Total", "total");
        CMAP.put("Type", "type");
        CMAP.put("Version", "version");
    }
}
