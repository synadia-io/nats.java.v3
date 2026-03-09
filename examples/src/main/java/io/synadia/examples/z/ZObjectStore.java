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
import io.synadia.client.api.MessageInfo;
import io.synadia.client.api.ObjectInfo;
import io.synadia.client.api.ObjectStoreConfiguration;
import io.synadia.client.support.Debug;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;

public class ZObjectStore {

    public static final String BUCKET_NAME = "zbucket";

    public static void main(String[] args) {
        try (Connection nc = Nats.connect()) {
            ObjectStoreManagement osm = nc.objectStoreManagement();
            osm.create(extractObjectStoreConfiguration());
            ObjectStore os = nc.objectStore(BUCKET_NAME);
            try (FileInputStream fin = new FileInputStream((File) getInput(500 * 1024)[1])) {
                os.put("ob", fin);
            }

            String streamName = "OBJ_" + BUCKET_NAME;
            String metaSubject = "$O." + BUCKET_NAME + ".M.>";
            String chunkSubject = "$O." + BUCKET_NAME + ".C.>";
            JetStreamManagement js = nc.jetStreamManagement();
            MessageInfo mi = js.getLastMessage(streamName, chunkSubject);
            Debug.info("MI", mi.getSeq(), mi.getSubject());

            nc.jetStreamManagement().deleteMessage(streamName, mi.getSeq());

            mi = js.getLastMessage(streamName, chunkSubject);
            Debug.info("MI", mi.getSeq(), mi.getSubject());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectInfo oi = os.get("ob", baos);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ObjectStoreConfiguration extractObjectStoreConfiguration() {
        ObjectStoreConfiguration.Builder builder = ObjectStoreConfiguration.builder();
        builder.name(BUCKET_NAME).ttl(Duration.ofMillis(10_000));
        return builder.build();
    }

    private static Object[] getInput(int size) {
        File found = null;
        long foundLen = Long.MAX_VALUE;
        final String classPath = System.getProperty("java.class.path", ".");
        final String[] classPathElements = classPath.split(File.pathSeparator);
        for(final String element : classPathElements){
            File f = new File(element);
            if (f.isFile()) {
                long flen = f.length();
                if (flen == size) {
                    found = f;
                    break;
                }
                if (flen >= size && flen < foundLen){
                    foundLen = flen;
                    found = f;
                }
            }
        }
        return new Object[] {foundLen, found};
    }
}
