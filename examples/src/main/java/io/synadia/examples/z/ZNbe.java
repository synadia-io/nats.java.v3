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

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ZNbe {
    public static final String HOME = "C:\\nats\\nats-by-example\\examples";
    public static final String[] LANGUAGES = new String[]{
        "cli",
        "go",
        "python",
        "deno",
        "rust",
        "dotnet",
        "dotnet2",
        "java",
        "ruby",
        "elixir",
        "crystal",
        "c"
    };

    public static void main(String[] args) throws Exception {
        ZNbe nbe = new ZNbe();
        nbe.visit(new File(HOME));
        nbe.report();
    }

    private void report() {
        StringBuilder header = new StringBuilder("Category,Instance");
        for (String l : LANGUAGES) {
            header.append("," + l);
        }
        System.out.println("\n\n" + header);

        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            boolean fl = true;
            List<String> mapped = map.get(key);
            StringBuilder row = new StringBuilder(key);
            for (String l : LANGUAGES) {
                if (fl) {
                    fl = false;
                    System.out.println("https://natsbyexample.com/examples/" + key.replace(",", "/") + "/" + l);
                }
                row.append(",").append(mapped.contains(l));
            }
            System.out.println(row);
        }

    }

    int level = -1;
    String cat = "";
    String subcat = "";
    String language = "";
    Map<String, List<String>> map = new HashMap<>();

    protected void visit(File sourceDir) throws IOException {
        ++level;
        if (level > 0) {
            String name = sourceDir.getAbsolutePath().substring(33);
            System.out.println(level + " " + levelString(level-1, 3) + name);
            int at = name.lastIndexOf("\\");
            switch (level) {
                case 1:
                    cat = name.substring(at + 1);
                    break;
                case 2:
                    subcat = name.substring(at + 1);
                    break;
                case 3:
                    language = name.substring(at + 1);
                    map.computeIfAbsent(getKey(cat, subcat), k -> new ArrayList<>()).add(language);
                    break;
            }
        }
        File[] list = sourceDir.listFiles();
        if (list != null) {
            for (File childFile : list) {
                if (childFile.isDirectory()) {
                    if (level < 3) {
                        visit(childFile);
                    }
                }
            }
        }
        --level;
    }

    private static String getKey(String cat, String subcat) {
        return cat + "," + subcat;
    }

    static final String PAD = "                                                                                                    ";
    private static String levelString(int level, int pad) {
        return PAD.substring(0, level * pad);
    }
}
