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

package io.synadia.examples.z.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Port {


    public static void main(String[] args) throws IOException {
        port(new String[]{"C:\\nats\\nats.java.android\\"
            , "C:\\nats\\nats.java.android\\app\\src\\main\\java\\io\\nats\\client"
            , "C:\\nats\\nats.java.android\\app\\src\\main\\java\\io\\nats\\service"
            , "C:\\nats\\nats.java.android\\app\\src\\test\\java\\io\\nats"});
    }

    public static void port(String[] args) throws IOException {
        Map<String, String> srMap = buildSearchReplaceMap(Paths.get(args[0], "port-data", "port-search-replace.txt"));

        // This builds the list of files in those directories
        List<File> allFiles = new ArrayList<>();
        appendFiles(allFiles, Paths.get(args[0], "app", "src", "main", "java", "io", "nats", "client"));
        appendFiles(allFiles, Paths.get(args[0], "app", "src", "main", "java", "io", "nats", "service"));
        appendFiles(allFiles, Paths.get(args[0], "app", "src", "test", "java", "io", "nats"));

        for (File f : allFiles) {
            boolean hasEdit = false;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            for (String key : srMap.keySet()) {
                if (text.contains(key)) {
                    if (!hasEdit) {
                        System.out.println(f.getAbsolutePath());
                        hasEdit = true;
                    }
                    text = text.replace(key, srMap.get(key));
                }
            }

            if (hasEdit) {
                File bak = new File(f.getAbsolutePath() + ".bak");
                if (f.renameTo(bak)) {
                    writeFile(f, text.getBytes(StandardCharsets.UTF_8));
                    bak.delete();
                }
            }
        }

        File[] fullReplace = getFiles(Paths.get(args[0], "port-data"));
        for (File fr : fullReplace) {
            String name = fr.getName();
            if (name.endsWith(".java")) {
                String temp = Paths.get(args[0], "app", "src", "main", "java").toString();
                Path target = Paths.get(temp, name.split("\\Q-\\E"));
                writeFile(target.toFile(), Files.readAllBytes(fr.toPath()));
            }
        }
    }

    private static void writeFile(File f, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
            out.flush();
        }
    }

    private static Map<String, String> buildSearchReplaceMap(Path srFilePath) throws IOException {
        Map<String, String> srMap = new HashMap<>();
        List<String> lines = Files.readAllLines(srFilePath);
        int x = 0;
        while (x < lines.size()) {
            String key = lines.get(x++).trim();
            if (key.isEmpty()) {
                break;
            }
            key = key.replace("&sp;", " ");
            String value = lines.get(x++).replace("&sp;", " ");
            System.out.println("'" + key + "' --> '" + value + "'");
            srMap.put(key, value);
        }
        return srMap;
    }

    private static void appendFiles(List<File> allFiles, Path p) {
        File[] files = getFiles(p);
        for (File ff : files) {
            if (ff.isDirectory()) {
                appendFiles(allFiles, ff.toPath());
            }
            else if (ff.getName().endsWith(".java")) {
                allFiles.add(ff);
            }
        }
    }

    private static File[] getFiles(Path p) {
        File[] files = p.toFile().listFiles();
        return files == null ? new File[0] : files;
    }
}
