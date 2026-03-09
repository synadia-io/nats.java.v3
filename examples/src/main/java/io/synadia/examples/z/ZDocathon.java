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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ZDocathon {
    public static final String CSV_HEADER = "file,url,C,Go,Python,Java,CSharpV1,TypeScript,Ruby,JavaScript";
    protected int level = 0;

    HashMap<String, List<File>> map = new HashMap<>();

    static class Info {
        File f;
        boolean bC;
        boolean bGo;
        boolean bPython;
        boolean bJava;
        boolean bCSharpV1;
        boolean bTypeScript;
        boolean bRuby;
        boolean bJavaScript;

        public String header() {
            return CSV_HEADER;
        }

        public String toString() {
            String ff = f.getAbsolutePath().replace("C:\\nats\\nats.docs\\", "");
            String url = "https://docs.nats.io/using-nats/developer" + ff.replace("using-nats\\developing-with-nats", "")
                .replace("\\", "/")
                .replace(".md", "")
                .replace("/js/", "/develop_jetstream/")
                .replace("/connecting/security/", "/connecting/")
                ;
            return ff +
                "," + url +
                " ," + bC +
                "," + bGo +
                "," + bPython +
                "," + bJava +
                "," + bCSharpV1 +
                "," + bTypeScript +
                "," + bRuby +
                "," + bJavaScript
                ;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(CSV_HEADER);

        ZDocathon dv = new ZDocathon();
        dv.visit( new File("C:\\nats\\nats.docs") );
//        for (String key : dv.map.keySet()) {
//            System.out.println(key);
//        }
    }

    protected void processFile(File sourceDir, File childFile) throws IOException {
        String name = childFile.getName();
        if (name.endsWith(".md")) {
            Info info = new Info();
//            System.out.println(childFile);
            List<String> lines = Files.readAllLines(childFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (line.contains("{% tab ")) {
                    map.computeIfAbsent(line, k -> new ArrayList<>()).add(childFile);
                    if (line.contains("{% tab title=\"C\" %}")) { info.bC = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"Go\" %}")) { info.bGo = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"Python\" %}")) { info.bPython = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"Java\" %}")) { info.bJava = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"C# V1\" %}")) { info.bCSharpV1 = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"TypeScript\" %}")) { info.bTypeScript = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"Ruby\" %}")) { info.bRuby = true; info.f = childFile; }
                    if (line.contains("{% tab title=\"JavaScript\" %}")) { info.bJavaScript = true; info.f = childFile; }
                }
            }
            if (info.f != null) {
                System.out.println(info);
            }
        }
    }

    protected void processDir(File dir) throws IOException {
        visit(dir);
    }

    protected void visit(File sourceDir) throws IOException {
        ++level;
        File[] list = sourceDir.listFiles();
        if (list != null) {
            for (File childFile : list) {
                if (childFile.isDirectory()) {
                    processDir(childFile);
                }
                else {
                    processFile(sourceDir, childFile);
                }
            }
        }
        --level;
    }
}
