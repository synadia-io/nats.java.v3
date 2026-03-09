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

import io.synadia.client.*;
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StreamInfo;
import io.synadia.client.api.StreamInfoOptions;
import io.synadia.client.api.Subject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class ZMendixSetup {

    public static final String STREAM = "exampleStream";
    public static final String STREAM_SUBJECT = "symbol.>";

    public static void main(String[] args) throws IOException {

        Options options = new Options.Builder()
            .server(Options.DEFAULT_URL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = nc.jetStream();

//            createOrReplaceStream(jsm, STREAM, STREAM_SUBJECT);
            populateData(js);

            StreamInfo si = jsm.getStreamInfo(STREAM, StreamInfoOptions.allSubjects());
            List<Subject> subjects = si.getStreamState().getSubjects();
            for (Subject s : subjects) {
                System.out.println(s);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    private static void populateData(JetStream js) throws IOException {
        List<CompletableFuture<PublishAck>> paList = new ArrayList<>();
        for (int x = 1; x <= 1000; x++) {
            String json = new String(Files.readAllBytes(Paths.get("C:\\dev\\coin-ticker-data\\ticker" + pad4(x) + ".json")), StandardCharsets.UTF_8);
            int at = json.indexOf("{\"symbol\":");
            while (at != -1) {
                int end = json.indexOf(",{\"symbol\":", at + 1);
                if (end == -1) {
                    end = json.lastIndexOf(']');
                }
                String data = json.substring(at, end);

                // fix open time
                at = data.indexOf("\"openTime");
                int end2 = data.indexOf(",\"closeTime\"");
                Date d = new Date(Long.parseLong(data.substring(at + 11, end2)));
                String repl = data.substring(at, end2);
                String with = "\"openTime\":\"" + FORMATTER.format(d) + "\"";
                data = data.replace(repl, with);

                // fix close time
                at = data.indexOf("\"closeTime");
                end2 = data.indexOf(",\"firstId\"");
                d = new Date(Long.parseLong(data.substring(at + 12, end2)));
                repl = data.substring(at, end2);
                with = "\"closeTime\":\"" + FORMATTER.format(d) + "\"";
                data = data.replace(repl, with);

                // figure subject
                at = data.indexOf(":\"");
                end2 = data.indexOf("\",");
                String subject = "symbol." + data.substring(at + 2, end2);

                boolean worthy = !data.contains("\"bidPrice\":\"0.");

                if (worthy) {
                    System.out.println(subject + " -> " + data);
                    paList.add(js.publishAsync(subject, data.getBytes(StandardCharsets.UTF_8)));
                    if (paList.size() > 100) {
                        clearFutures(paList);
                        paList.clear();
                    }
                }

                at = json.indexOf("{\"symbol\":", end - 1);
            }
            clearFutures(paList);
        }
    }

    private static void clearFutures(List<CompletableFuture<PublishAck>> paList) {
        List<CompletableFuture<PublishAck>> notDone = new ArrayList<>();
        while (paList.size() > 0) {
            notDone.clear();
            for (CompletableFuture<PublishAck> f : paList) {
                if (!f.isDone()) {
                    notDone.add(f);
                }
            }
            paList = notDone;
        }
    }

    public static String pad4(int x) {
        if (x < 10) {
            return "000" + x;
        }
        if (x < 100) {
            return "00" + x;
        }
        if (x < 1000) {
            return "0" + x;
        }
        return "" + x;
    }
}
