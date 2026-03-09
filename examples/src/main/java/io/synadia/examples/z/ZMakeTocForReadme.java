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
import java.util.List;

public class ZMakeTocForReadme {

    public static final String README = "C:\\nats\\nats.java\\README.md";
    public static final String INDENT = "                                                ";

    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(README));
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("## ")) {
                int at = line.indexOf(" "); // ### FOO will give index 3
                int indent = (at - 2) * 4;
                String text = line.substring(at + 1);
                String link = text.toLowerCase().replace(" ", "-");
                System.out.println(INDENT.substring(0, indent) + "* [" + text + "](#" + link + ")");
            }
        }
    }
}
