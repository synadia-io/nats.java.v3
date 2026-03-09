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

public class ZPortConvertToNet {

    static String CW = "// Copyright 2023 The NATS Authors\n" +
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
        "using NATS.Client.JetStream;\n"
        ;

    static String END = "}\n";

    static String EXAMPLE_CLASS = "\nnamespace NAMESPACE\n" +
        "{\n" +
        "    internal static class CLASSNAME\n" +
        "    {\n";

    static String TARGET_CLASSNAME;
    static String IN;
    static String OFN;
    static String CLASS_MARK
        = "public class";
    //            = "class " + TARGET_CLASSNAME;
    static String NAMESPACE
//        = "NATSExamples.NatsByExample";
//        = "NATS.Client.JetStream";
//        = "NATSExamples.ClientCompatibility";
        = "IntegrationTests";

    public static void main(String[] args) throws IOException {
        TARGET_CLASSNAME = "ValidatorTests";
        IN = "C:\\nats\\nats.java\\src\\test\\java\\io\\nats\\client\\support\\ValidatorTests.java";
        OFN = "C:\\temp\\" + TARGET_CLASSNAME + ".cs";

        try (FileOutputStream out = new FileOutputStream(OFN)) {
            out.write(CW.getBytes());
            out.write(USING.getBytes());
            out.write(EXAMPLE_CLASS.replace("NAMESPACE", NAMESPACE).replace("CLASSNAME", TARGET_CLASSNAME).getBytes());

            boolean needPackage = true;
            boolean needClassMark = true;
            List<String> lines = Files.readAllLines(new File(IN).toPath());
            boolean terminateSummary = false;

            for (String line : lines) {
                if (needPackage) {
                    if (line.startsWith("package")) {
                        needPackage = false;
                    }
                    continue;
                }

//                System.out.println("> " + line);

                if (needClassMark) {
                    if (line.startsWith("import") || line.trim().isEmpty()) {
                        continue;
                    }
                    if (line.contains(CLASS_MARK)) {
                        needClassMark = false;
                        line = "";
                    }
                    else {
                        line = "// " + line;
                        if (line.startsWith("//  * ")) {
                            line = "/// " + line.substring(6);
                        }
                    }
                }

                // need to come first
                if (line.contains("@link")) {
                    line = line.replace("\\Q{@link \\E", "<see cref=\"").replace("\\Q}\\E", "\">");
                }

                String trim = line.trim();
                if (trim.startsWith("/**")) {
                    line = line.replace("/**", "/// <summary>");
                    terminateSummary = true;
                }
                else if (trim.equals("*/")) {
                    terminateSummary = false;
                    continue;
                }
                else if (trim.startsWith("* @throws")) {
                    if (terminateSummary) {
                        line = "    /// </summary>";
                        terminateSummary = false;
                    }
                    else {
                        continue;
                    }
                }
                else if (trim.startsWith("* {@inheritDoc}")) {
                    terminateSummary = false;
                    continue;
                }
                else if (trim.startsWith("* @return")) {
                    line = line.replace(" * @return ", "/// <returns>") + "</returns>";
                    if (terminateSummary) {
                        line = "    /// </summary>\n        " + line;
                        terminateSummary = false;
                    }
                }
                else if (trim.startsWith("* @param")) {
                    line = line.replace(" * @param ", "/// <param name=\"") + "</param>";
                    int at = line.indexOf("name=\"");
                    at = line.indexOf(' ', at);
                    line = line.substring(0, at) + "\">" + line.substring(at + 1);
                    if (terminateSummary) {
                        line = "    /// </summary>\n    " + line;
                        terminateSummary = false;
                    }
                }
                else if (trim.startsWith("* ")) {
                    line = line.replace(" * ", "/// ");
                }
                else if (trim.startsWith("@Override")) {
                    terminateSummary = false;
                    continue;
                }

                else {
                    line = line
                        .replace("MessageHandler ", "EventHandler<MsgHandlerEventArgs> ")
                        .replace("synchronized", "lock")
                        .replace("final", "readonly")
                        .replace(" implements ", " : ")
                        .replace("sleep(", "Thread.Sleep(")
                        .replace("Thread.Thread", "Thread") // fixes last
                        .replace("JetStreamManagement jsm = nc.jetStreamManagement();", "IJetStreamManagement jsm = c.CreateJetStreamManagementContext();")
                        .replace("JetStream js = nc.jetStream();", "IJetStream js = c.CreateJetStreamContext();")
                        .replace(".getBytes()", " Encoding.UTF8.GetBytes(\"fixme\"")
                        .replace(".getBytes", " Encoding.UTF8.GetBytes(\"fixme\"").replace("\"fixme\"()", "\"fixme\"")
                        .replace("new String", "Encoding.UTF8.GetString(")
                        .replace("IllegalArgumentException", "ArgumentException")
                        .replace("JetStreamApiException", "NATSJetStreamException")
                        .replace("@Test", "[Fact]")
                        .replace("public void test", "public void Test")
                        .replace("assertEquals", "Assert.Equal")
                        .replace("assertNotEquals", "Assert.NotEqual")
                        .replace("assertTrue", "Assert.True")
                        .replace("assertFalse", "Assert.False")
                        .replace("assertNull", "Assert.Null")
                        .replace("assertNotNull", "Assert.NotNull")
                        .replace("<Message", "<Msg")
                        .replace("Message ", "Msg ")
                        .replace("toCharArray", "ToCharArray")
                        .replace("<p>", "<para>")
                        .replace("</p>", "</para>")

                        .replace("List<", "IList<")
                        .replace("new IList<", "new List<")
                        .replace("new Arraylist<", "new List<")

                        .replace("new Map<", "new Dictionary<")
                        .replace("Map<", "IDictionary<")

                        .replace("<String", "<string")
                        .replace("String ", "string ")
                        .replace("String... ", "params string[]")
                        .replace(", String>", ", string>")
                        .replace("boolean ", "bool ")
                        .replace(".build", ".Build")
                        .replace(".length()", ".Length")
                        .replace(".size()", ".Count")
                        .replace(".replace(", ".Replace(")
                        .replace(".startsWith(", ".StartsWith(")
                        .replace(".contains(", ".Contains(")
                        .replace(".toString(", ".ToString(")
                        .replace("runInJsServer(nc -> {", "Context.RunInJsServer(c =>\r\n            {")
                        .replace("runInJsServer(", "Context.RunInJsServer(")
                        .replace("System.out.println", "Console.WriteLine")
                        .replace("System.out.printf(", "Console.WriteLine($")
                        .replace("System.err.println", "Console.Error.WriteLine")
                        .replace(" nc ", " c ")
                        .replace("->", "=>")
                        .replace("CountDownLatch", "CountdownEvent")
                        .replace(".countDown()", ".Signal()")
                        .replace("EMPTY", "string.Empty")
                        .replace("HAS_SPACE", "HasSpace")
                        .replace("HAS_PRINTABLE", "HasPrintable")
                        .replace("HAS_DOT", "HasDot")
                        .replace("STAR_NOT_SEGMENT", "StarNotSegment")
                        .replace("GT_NOT_SEGMENT", "GtNotSegment")
                        .replace("HAS_DOLLAR", "HasDollar")
                        .replace("HAS_LOW", "HasLow")
                        .replace("HAS_127", "Has127")
                        .replace("HAS_FWD_SLASH", "HasFwdSlash")
                        .replace("HAS_BACK_SLASH", "HasBackSlash")
                        .replace("HAS_EQUALS", "HasEquals")
                        .replace("HAS_TIC", "HasTic")
                        .replace("DOT", "\".\"")
                        .replace("ZonedDateTime.now()", "DateTime.Now")
                        .replace("ZonedDateTime", "DateTime")
                        .replace("DateTimeUtils.gmtNow()", "DateTime.UtcNow")
                        .replace("gmtNow()", "DateTime.UtcNow")
                        .replace("AtomicInteger", "InterlockedInt")
                        .replace("AtomicLong", "InterlockedLong")
                        .replace("AtomicBoolean", "InterlockedBoolean")
                    ;

                    int at = line.indexOf(") throws");
                    if (at != -1) {
                        line = line.substring(0, at + 1) + " {";
                    }

                    if (line.contains("assertThrows")) {
                        line = line.replace("assertThrows(", "Assert.Throws<")
                            .replace(".class, ", ">(")
                            .replace(".class,", ">(")
                        ;
                    }
                }
                String text = "    " + line + "\r\n";
                System.out.print(text);
                out.write(text.getBytes());
            }
            out.write(END.getBytes());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
