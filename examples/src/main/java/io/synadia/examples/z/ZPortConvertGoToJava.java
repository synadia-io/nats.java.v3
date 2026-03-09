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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ZPortConvertGoToJava {

    static String CW = "// Copyright 2022-2023 The NATS Authors\n" +
        "// Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
        "// you may not use this file except in compliance with the License.\n" +
        "// You may obtain a copy of the License at\n" +
        "//\n" +
        "// http://www.apache.org/licenses/LICENSE-2.0\n" +
        "//\n" +
        "// Unless required by applicable law or agreed to in writing, software\n" +
        "// distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
        "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
        "// See the License for the specific language governing permissions and\n" +
        "// limitations under the License.\n\n";

    static String USING = "using System;\n" +
        "using System.Diagnostics;\n" +
        "using System.Text;\n" +
        "using System.Threading;\n" +
        "using NATS.Client;\n" +
        "using NATS.Client.Internals;\n" +
        "using NATS.Client.JetStream;\n" +
        "using static NATS.Client.JetStream.BaseConsumeOptions;\n"
        ;

    static String END = "}\n";

    static String EXAMPLE_CLASS = "namespace NAMESPACE\n" +
        "{\n" +
        "    internal static class CLASSNAME\n" +
        "    {\n";

    public static void main(String[] args) throws IOException {
        String IN = "C:\\Users\\batman\\Downloads\\main.go";
        String OFN = "C:\\Users\\batman\\Downloads\\main.java";
        String MARK = "func main";

        try (FileOutputStream out = new FileOutputStream(OFN)) {
            boolean needMark = true;
            List<String> lines = Files.readAllLines(new File(IN).toPath());
            boolean terminateSummary = false;
            for (String line : lines) {
                if (needMark) {
                    if (line.startsWith(MARK)) {
                        needMark = false;
                    }
                    else {
                        continue;
                    }
                }
//            System.out.println("> " + line);
                line = line.trim()
                    .replace("func main() {", "public static void main(String[] args) {")
                    .replace(":=", "=")
                    .replace("_,", "")
                    .replace(",_", "")
                    .replace(", _", "")
                    .replace("nil", "null")
                    .replace("if ", "if (")
                    .replace("for ", "for (")
                    .replace("fmt.Println", "System.out.println")
                    .replace("fmt.Printf", "System.out.print")
                ;
                if (line.contains("if (")) {
                    line = line.replace(" {", ") {");
                }
                else if (line.contains("for (")) {
                    line = line.replace(" {", ") {");
                }
                String text = "    " + line + "\r\n";
                System.out.print(text);
                out.write(text.getBytes());
            }
            out.write(END.getBytes());
        }
    }
}
