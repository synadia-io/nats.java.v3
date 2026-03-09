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
import io.synadia.client.api.*;

public class ZDirectGetFail {

    public static void main(String[] args) throws InterruptedException {
        try (Connection nc = Nats.connect()) {
            System.out.println("Server Version: " + nc.getServerInfo().getVersion());

            JetStreamManagement jsm = nc.jetStreamManagement();
            JetStream js = jsm.jetStream();

            String mirrorSegment = "MirrorMe";
            String sourceStream = "source";
            String mirrorStream = "mirror";
            String sourceStreamSubject = sourceStream + ".>";
            String sourceMirroredSubject = sourceStream + ".MirrorMe.foo";
            String sourceDontMirrorSubject = sourceStream + ".DontMirrorMe.foo";
            String mirrorStreamSubject = mirrorStream + ".>";
            String mirrorMirroredSubject = mirrorStream + ".MirrorMe.foo";

            System.out.println("sourceStream:            " + sourceStream);
            System.out.println("sourceStreamSubject:     " + sourceStreamSubject);
            System.out.println("sourceMirroredSubject:   " + sourceMirroredSubject);
            System.out.println("sourceDontMirrorSubject: " + sourceDontMirrorSubject);
            System.out.println("mirrorStream:            " + mirrorStream);
            System.out.println("mirrorStreamSubject:     " + mirrorStreamSubject);
            System.out.println("mirrorMirroredSubject:   " + mirrorMirroredSubject);

            try {
                jsm.deleteStream(sourceStream);
            }
            catch (Exception ignore) {
            }
            try {
                jsm.deleteStream(mirrorStream);
            }
            catch (Exception ignore) {
            }

            StreamConfiguration sc = StreamConfiguration.builder()
                .name(sourceStream)
                .subjects(sourceStreamSubject)
                .storageType(StorageType.Memory)
                .allowRollup(true)
                .allowDirect(true)
                .discardPolicy(DiscardPolicy.New)
                .denyDelete(true)
                .build();
            StreamInfo si = jsm.addStream(sc);
            System.out.println("\nSource Stream: " + si.getConfiguration().toJson());

            SubjectTransform transform = SubjectTransform.builder()
                .source(sourceStream + "." + mirrorSegment + ".*")
                .destination(mirrorStream + "." + mirrorSegment + ".{{wildcard(1)}}")
                .build();

            Mirror mirror = Mirror.builder()
                .name(sourceStream)
                .subjectTransforms(transform)
                .build();

            sc = StreamConfiguration.builder()
                .name(mirrorStream)
                .storageType(StorageType.Memory)
                .allowRollup(true)
                .allowDirect(true)
                .discardPolicy(DiscardPolicy.New)
                .denyDelete(true)
                .mirror(mirror)
                .build();
            si = jsm.addStream(sc);
            System.out.println("\nMirror Stream: " + si.getConfiguration().toJson());

            js.publish(sourceMirroredSubject, "mirrored".getBytes());
            js.publish(sourceDontMirrorSubject, "notMirrored".getBytes());

            Thread.sleep(1000); // transforming takes some amount of time, otherwise the mirrorKv.getKeys() fails

            Subscription sub = js.subscribe(sourceStreamSubject);
            Message m = sub.nextMessage(300);
            while (m != null) {
                System.out.println("\nMessage from stream '" + sourceStream + "', Sequence: " + m.metaData().streamSequence() + " Subject '" + m.getSubject() + "', Payload '" + new String(m.getData()) + "'");
                try {
                    MessageInfo mi = jsm.getLastMessage(sourceStream, m.getSubject());
                    System.out.println("Last Message for stream '" + sourceStream + "', subject '" + m.getSubject() + "': " + mi);
                }
                catch (JetStreamApiException e) {
                    System.out.println("NOT FOUND Last Message for stream '" + sourceStream + "', subject '" + m.getSubject() + "': " + e.getMessage());
                }
                m = sub.nextMessage(300);
            }
            sub.unsubscribe();

            sub = js.subscribe(mirrorStreamSubject, PushSubscribeOptions.stream(mirrorStream));
            m = sub.nextMessage(300);
            while (m != null) {
                System.out.println("\nMessage from stream '" + mirrorStream + "', Sequence: " + m.metaData().streamSequence() + " Subject '" + m.getSubject() + "', Payload '" + new String(m.getData()) + "'");
                try {
                    MessageInfo mi = jsm.getLastMessage(mirrorStream, m.getSubject());
                    System.out.println("Last Message for stream '" + mirrorStream + "', subject '" + m.getSubject() + "': " + mi);
                }
                catch (JetStreamApiException e) {
                    System.out.println("NOT FOUND Last Message for stream '" + mirrorStream + "', subject '" + m.getSubject() + "': " + e.getMessage());
                }
                m = sub.nextMessage(300);
            }
            sub.unsubscribe();

        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}