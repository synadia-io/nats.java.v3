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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZCheckLog {

    enum Kind { STARTED, PASSED, FAILED, SKIPPED, MORE_INFO}

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("C:\\temp\\actionlog.txt"));
        Map<String, String> map = new HashMap<>();
        for (String line : lines) {
            Kind kind = kind(line);

            if (kind == null) { continue; }

            if (kind == Kind.MORE_INFO) {
                System.out.println("       --> " + line.trim().substring(33));
                continue;
            }

            // System.out.println(line);
            int at = line.indexOf("Z ");
            line = line.substring(at + 2);
            at = line.indexOf("()");
            String key = line.substring(0, at);

            switch (kind) {
                case STARTED:
                    map.put(key, "STARTED");
                    break;
                case PASSED:
                case SKIPPED:
                    map.remove(key);
                    break;
                case FAILED:
                    System.out.println("FAILED " + key);
                    break;
            }
        }

        for (String key : map.keySet()) {
            System.out.println("DNF " + key);
        }
    }

    private static Kind kind(String line) {
        if (line.contains("() STARTED")) { return Kind.STARTED; }
        if (line.contains("() PASSED")) { return Kind.PASSED; }
        if (line.contains("() FAILED")) { return Kind.FAILED; }
        if (line.contains("() SKIPPED")) { return Kind.SKIPPED; }
        if (line.contains("java.lang.IllegalStateException: Failed to run"))  { return Kind.MORE_INFO; }
        return null;
    }
}
