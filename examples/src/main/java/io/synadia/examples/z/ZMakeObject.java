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

import java.io.FileOutputStream;

public class ZMakeObject {

    public static void main(String[] args) throws Exception {
        byte[] data = new byte[1270];
        for (int i = 0; i < 10; i++) {
            for (byte b = 0; b < Byte.MAX_VALUE; b++) {
                data[b] = b;
            }
        }

        try (FileOutputStream out = new FileOutputStream("C:\\temp\\object.dat")) {
            long l = 1024 * 1024 * 1000;
            while (l > 0) {

            }
        }
    }
}
