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

import io.synadia.client.support.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Zy {

    public static void main(String[] args) {
        int x = 0;
        JsonValue[] jvs = new JsonValue[19];
        jvs[x++] = new JsonValue(true);
        jvs[x++] = new JsonValue(Boolean.TRUE);
        jvs[x++] = new JsonValue(false);
        jvs[x++] = new JsonValue(Boolean.FALSE);
        jvs[x++] = new JsonValue("hello world!");
        jvs[x++] = new JsonValue("h\be\tllo w\u1234orld!");
        jvs[x++] = JsonValue.NULL;
        jvs[x++] = new JsonValue(Integer.MAX_VALUE);
        jvs[x++] = new JsonValue(Integer.MIN_VALUE);
        jvs[x++] = new JsonValue(Long.MAX_VALUE);
        jvs[x++] = new JsonValue(Long.MIN_VALUE);
        jvs[x++] = new JsonValue(Double.MAX_VALUE);
        jvs[x++] = new JsonValue(Double.MIN_VALUE);
        jvs[x++] = new JsonValue(Float.MAX_VALUE);
        jvs[x++] = new JsonValue(Float.MIN_VALUE);
        jvs[x++] = new JsonValue(new BigDecimal("9223372036854775807.123"));
        jvs[x++] = new JsonValue(new BigDecimal("-9223372036854775808.123"));
        jvs[x++] = new JsonValue(new BigInteger("9223372036854775807"));
        jvs[x++] = new JsonValue(new BigInteger("-9223372036854775808"));

        long start = System.nanoTime();
        for (int w = 0; w < 10000000; w++) {
            for (int i = 0; i < x; i++) {
                for (int j = 0; j < x; j++) {
                    boolean b = jvs[i].equals(jvs[j]);
                    int h = jvs[i].hashCode();
                }
            }
        }
        long elapsed = System.nanoTime() - start;
        System.out.println("Elapsed time: " + elapsed + "ns " + (elapsed/1_000_000) + "ms");
    }
}
