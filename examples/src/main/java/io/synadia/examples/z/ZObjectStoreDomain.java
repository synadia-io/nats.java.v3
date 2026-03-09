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
import io.synadia.client.api.ObjectMeta;
import io.synadia.client.api.ObjectStoreConfiguration;
import io.synadia.client.api.ObjectStoreStatus;
import io.synadia.client.api.StorageType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public class ZObjectStoreDomain {
    public static final String BUCKET_NAME = "TestBucket";
    public static final String HUB_URL = "nats://localhost:4222";
    public static final String SPOKE_URL = "nats://localhost:4223";
    public static final String USER = "testUser";
    public static final String PASS = "testPass";
    public static final String HUB_DOMAIN = "HUB";
    public static final String OBJECT_NAME = "ObjectName";
    public static String SOURCE_DATA_FILE = "C:\\nats\\temp\\object-store-domain\\data.txt";
    public static String TARGET_DATA_FILE = "C:\\nats\\temp\\object-store-domain\\download.txt";

    public static void main(String[] args) throws IOException {
        Options options = Options.builder().server(HUB_URL).userInfo(USER, PASS).build();
        ObjectStoreOptions osOptions = ObjectStoreOptions.builder()
//            .jsDomain(HUB_DOMAIN)
            .build();

        try (Connection nc = Nats.connect(options)) {
            System.out.println(nc.getServerInfo());
            ObjectStoreManagement osm = nc.objectStoreManagement(osOptions);
            createStore(osm);

            ObjectStore os = nc.objectStore(BUCKET_NAME, osOptions);

            ObjectStoreStatus oss = osm.getStatus(BUCKET_NAME);
            System.out.println("Before Store: " + oss.getBackingStreamInfo().getStreamState());

            // upload
            storeFile(os);
            System.out.println("After Store: " + osm.getStatus(BUCKET_NAME).getBackingStreamInfo().getStreamState());

            System.out.println(os.getInfo(OBJECT_NAME));
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public static void storeFile(ObjectStore os) throws IOException, JetStreamApiException, NoSuchAlgorithmException {
        File file = new File(SOURCE_DATA_FILE);
        try (InputStream in = Files.newInputStream(file.toPath())) {
            ObjectMeta meta = ObjectMeta.builder(OBJECT_NAME)
                .description("a test file")
                .chunkSize(8 * 1024)
                .build();
            os.put(meta, in);
        }
    }

    public static Path prepareDownloadFile() {
        File file = new File(TARGET_DATA_FILE);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return file.toPath();
    }

    public static void createStore(ObjectStoreManagement osm) throws IOException, JetStreamApiException {

        // delete bucket if exists
        try {
            osm.delete(BUCKET_NAME);
        }
        catch (JetStreamApiException e) {
            // 10059 means the bucket does not exist, which is fine. Other errors are bad.
            if (e.getApiErrorCode() != 10059) {
                throw e;
            }
        }

        // create the file kv bucket
        ObjectStoreConfiguration osc = ObjectStoreConfiguration.builder(BUCKET_NAME)
            .storageType(StorageType.File)
            .replicas(1)
            .build();

        ObjectStoreStatus osStatus = osm.create(osc);
        System.out.println("Created: " + osStatus);
    }
}