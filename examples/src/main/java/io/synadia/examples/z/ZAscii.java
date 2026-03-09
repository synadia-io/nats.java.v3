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

import java.nio.charset.StandardCharsets;

public class ZAscii {
    public static void main(String[] args) {
        byte[] bytes = new byte[129];
        for (int x = 0; x < 129; x++) {
            bytes[x] = (byte)x;
        }

        String sa = new String(bytes, StandardCharsets.US_ASCII);
        String su = new String(bytes, StandardCharsets.UTF_8);
        bytes = sa.getBytes(StandardCharsets.US_ASCII);
        for (int x = 0; x < bytes.length; x++) {
            System.out.println("ASCII " + x + " " + bytes[x] + " " + (char)bytes[x]);
        }
        System.out.println();
        bytes = su.getBytes(StandardCharsets.US_ASCII);
        for (int x = 0; x < bytes.length; x++) {
            System.out.println("UTF8 " + x + " " + bytes[x] + " " + (char)bytes[x]);
        }
    }
}