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

import io.synadia.client.NUID;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ZScratch3 {

    public static void main(String[] args) throws IOException {
        Set<String> set = new HashSet<>();
        long count = 0;
        String id = id();
        String vid = id;
        String first = id.substring(0, 1);
        System.out.println(id);
        set.add(first);
        while (++count < Long.MAX_VALUE) {
            id = id();
            String temp = id.substring(0, 1);
            if (count % 1000 == 0) {
                System.out.println(count + " " + id);
            }
            if (!temp.equals(first)) {
                first = temp;
                if (!set.add(temp)) {
                    System.out.println(count + " " + vid + " " + id);
                    return;
                }
            }
        }
        System.out.println(count + " " + vid);
    }

    public static String id() {
        String temp = NUID.nextGlobal();
        String id = temp.substring(temp.length() - 4);
//        System.out.println(temp);
//        System.out.println(id);
        return id;
    }
}
