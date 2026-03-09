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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ZScratch6 {

    private static final String FN = "C:\\nats\\nats.java\\src\\main\\java\\io\\nats\\client\\BaseConsumeOptions.java";

    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(FN));
        for (String l : lines) {
            int at = l.indexOf("MAX");
            if (at != -1) {
                int at2 = l.indexOf(" ", at + 1);
                System.out.println("/** constant for " + l.substring(at, at2).toLowerCase().replace("_", " ") + " */");
            }
        }

//        justReturns(lines);
//        apiconst(lines);
    }

    private static void justReturns(List<String> lines) {
        for (String l : lines) {
            if (l.contains("* @return")) {
                System.out.println(l.replace("* @return", "*"));
            }
            System.out.println(l);
        }
    }

    private static void apiconst(List<String> lines) {
        //    String DISCARD_NEW_PER_SUBJECT = "discard_new_per_subject";
        String pad = "                                                                          ";
        String tpl = "    /** C */" + pad;
        for (String l : lines) {
            if (l.startsWith("    String")) {
//                System.out.println(l);
                int at1 = l.indexOf('"');
                int at2 = l.indexOf("\";");
                String val = tpl.replace("C", l.substring(at1 + 1, at2));
                at2 = l.indexOf("=");
                String var = l.substring(0, at2).trim() + pad;
                System.out.println(val.substring(0, 37) + var.substring(0, 37) + "= " + l.substring(at1));
            }
        }
    }
}
