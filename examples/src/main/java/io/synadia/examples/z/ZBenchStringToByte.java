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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class ZBenchStringToByte {
    private static final String BASE = "Some US-ASCII with few 128..255 and randomness × \u00A0 !";
    private static String data;// not final ⇒ not const

    static {
        System.out.println(System.getProperty("java.specification.version"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append(BASE);
        }
        data = sb.toString();
    }

    public static byte[] utf8_4() {
        return data.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] iso88591_2 () {
        return data.getBytes(ISO_8859_1); // first 256 Unicode chars
    }

    public static byte[] usAscii_3 () {
        return data.getBytes(US_ASCII); // first 128 Unicode chars
    }

    public static byte[] rawIso88591_1 () {
        int len = data.length();
        byte[] bytes = new byte[len];
        data.getBytes(0, len, bytes, 0);// deprecated: first 256 Unicode chars
        return bytes;
    }

    public static byte[] rawIso88591_direct () {
        int len = data.length();
        data.getBytes(0, len, targetArray, 0);
        return targetArray;
    }
    final static byte[] targetArray = new byte[50_000];// in reality this is some array passed as argument

    public static byte[] manualIso88591_5 () {
        int len = data.length();
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++){
            bytes[i] = (byte) data.charAt(i);
        }
        return bytes;
    }

    private static final int ROUNDS = 10_000_000;
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        for (int x = 0; x < ROUNDS; x++) {
            rawIso88591_1();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("rawIso88591_1: " + elapsed);

        start = System.currentTimeMillis();
        for (int x = 0; x < ROUNDS; x++) {
            usAscii_3();
        }
        elapsed = System.currentTimeMillis() - start;
        System.out.println("usAscii_3: " + elapsed);

    }
}
