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
import io.synadia.client.api.KeyValueConfiguration;
import io.synadia.client.api.Mirror;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.SubjectTransform;

import java.util.List;

public class ZDirectGetFailKv {

    public static void main(String[] args) throws InterruptedException {
        try (Connection nc = Nats.connect()) {
            JetStreamManagement jsm = nc.jetStreamManagement();
            KeyValueManagement kvm = nc.keyValueManagement();

            String sourceBucketName = "bucket";
            String mirrorBucketName = "mirror";
            String mirrorSegment = "MirrorMe";
            String dontMirrorSegment = "DontMirrorMe";
            String extraSegment = "foo";
            String sourceStream = "KV_" + sourceBucketName;
            String sourceMirrorKeySubject = "$KV." + sourceBucketName + ".MirrorMe.foo";
            String sourceDontMirrorKeySubject = "$KV." + sourceBucketName + ".DontMirrorMe.foo";
            String mirrorStream = "KV_" + mirrorBucketName;
            String mirrorMirrorKeySubject = "$KV." + mirrorBucketName + ".MirrorMe.foo";

            System.out.println("sourceBucketName:           " + sourceBucketName);
            System.out.println("mirrorBucketName:           " + mirrorBucketName);
            System.out.println("mirror key:                 " + mirrorSegment + "." + extraSegment);
            System.out.println("don't mirror key:           " + dontMirrorSegment + "." + extraSegment);
            System.out.println("sourceStream:               " + sourceStream);
            System.out.println("sourceMirrorKeySubject:     " + sourceMirrorKeySubject);
            System.out.println("sourceDontMirrorKeySubject: " + sourceDontMirrorKeySubject);
            System.out.println("mirrorStream:               " + mirrorStream);
            System.out.println("mirrorMirrorKeySubject:     " + mirrorMirrorKeySubject);

            try { kvm.delete(sourceBucketName); } catch (Exception ignore) {}
            try { kvm.delete(mirrorBucketName); } catch (Exception ignore) {}

            kvm.create(KeyValueConfiguration.builder()
                .name(sourceBucketName)
                .storageType(StorageType.Memory)
                .build());

            SubjectTransform transform = SubjectTransform.builder()
                .source("$KV." + sourceBucketName + "." + mirrorSegment + ".*")
                .destination("$KV." + mirrorBucketName + "." + mirrorSegment + ".{{wildcard(1)}}")
                .build();

            Mirror mirr = Mirror.builder()
                .name(sourceBucketName)
                .subjectTransforms(transform)
                .build();

            kvm.create(KeyValueConfiguration.builder()
                .name(mirrorBucketName)
                .mirror(mirr)
                .storageType(StorageType.Memory)
                .build());

            KeyValue sourceKv = nc.keyValue(sourceBucketName);
            KeyValue mirrorKv = nc.keyValue(mirrorBucketName);

            String mirroredKey = mirrorSegment + "." + extraSegment;
            String dontMirrorKey = dontMirrorSegment + "." + extraSegment;
            sourceKv.put(mirroredKey, mirrorSegment.getBytes());
            sourceKv.put(dontMirrorKey, dontMirrorSegment.getBytes());

            Thread.sleep(1000); // transforming takes some amount of time, otherwise the mirrorKv.getKeys() fails

            System.out.println("Server Version: " + nc.getServerInfo().getVersion());

            List<String> sourceKeys = sourceKv.keys();
            report("source keys contains mirroredKey", sourceKeys.contains(mirroredKey));
            report("source keys contains dontMirrorKey", sourceKeys.contains(dontMirrorKey));
            report("source KV [direct] get mirroredKey is found", sourceKv.get(mirroredKey) != null);
            report("source KV [direct] get dontMirrorKey is found", sourceKv.get(dontMirrorKey) != null);
            try {
                jsm.getLastMessage(sourceStream, sourceMirrorKeySubject);
                report("source JSM direct get mirroredKey is found", true);
            }
            catch (JetStreamApiException e) {
                System.out.println(e);
                report("source JSM direct get mirroredKey is found", false);
            }
            try {
                jsm.getLastMessage(sourceStream, sourceDontMirrorKeySubject);
                report("source JSM direct get dontMirrorKey is found", true);
            }
            catch (JetStreamApiException e) {
                System.out.println(e);
                report("source JSM direct get dontMirrorKey is found", false);
            }

            List<String> mirrorKeys = mirrorKv.keys();
            report("mirror keys contains mirroredKey", mirrorKeys.contains(mirroredKey));
            report("mirror keys does not contain dontMirrorKey", !mirrorKeys.contains(dontMirrorKey));
            report("mirror KV [direct] get mirroredKey is found", mirrorKv.get(mirroredKey) != null);
            report("mirror KV [direct] get dontMirrorKey is not found", mirrorKv.get(dontMirrorKey) == null);
            try {
                jsm.getLastMessage(mirrorStream, mirrorMirrorKeySubject);
                report("source JSM direct get mirroredKey is found", true);
            }
            catch (JetStreamApiException e) {
                System.out.println(e);
                report("source JSM direct get mirroredKey is found", false);
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static void report(String label, boolean result) {
        if (result) {
            System.out.println("Pass: " + label);
        }
        else {
            System.out.println("FAIL: " + label);
            System.exit(-1);
        }
        System.out.println();
    }
}
