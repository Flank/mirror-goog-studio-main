/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;
import org.intellij.lang.annotations.Language;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName"})
public class SdCardDetectorTest extends AbstractCheckTest {
    @SuppressWarnings("SpellCheckingInspection")
    private static final String IDEA_MAX_INTELLISENSE_FILESIZE = "idea.max.intellisense.filesize";

    @Override
    protected Detector getDetector() {
        return new SdCardDetector();
    }

    public void testBasic() {
        //noinspection all // Sample code
        String expected =
                ""
                        + "src/test/pkg/SdCardTest.java:13: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + " private static final String SDCARD_TEST_HTML = \"/sdcard/test.html\";\n"
                        + "                                                ~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:14: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + " public static final String SDCARD_ROOT = \"/sdcard\";\n"
                        + "                                          ~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:15: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + " public static final String PACKAGES_PATH = \"/sdcard/o/packages/\";\n"
                        + "                                            ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:16: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + " File deviceDir = new File(\"/sdcard/vr\");\n"
                        + "                           ~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:20: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "   android.os.Debug.startMethodTracing(\"/sdcard/launcher\");\n"
                        + "                                       ~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:22: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  if (new File(\"/sdcard\").exists()) {\n"
                        + "               ~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:24: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  String FilePath = \"/sdcard/\" + new File(\"test\");\n"
                        + "                    ~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:29: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  intent.setDataAndType(Uri.parse(\"file://sdcard/foo.json\"), \"application/bar-json\");\n"
                        + "                                  ~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:30: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  intent.putExtra(\"path-filter\", \"/sdcard(/.+)*\");\n"
                        + "                                 ~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:31: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  intent.putExtra(\"start-dir\", \"/sdcard\");\n"
                        + "                               ~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:32: Warning: Do not hardcode \"/data/\"; use Context.getFilesDir().getPath() instead [SdCardPath]\n"
                        + "  String mypath = \"/data/data/foo\";\n"
                        + "                  ~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:33: Warning: Do not hardcode \"/data/\"; use Context.getFilesDir().getPath() instead [SdCardPath]\n"
                        + "  String base = \"/data/data/foo.bar/test-profiling\";\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/SdCardTest.java:34: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  String s = \"file://sdcard/foo\";\n"
                        + "             ~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 13 warnings\n";

        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import java.io.File;\n"
                                        + "\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.net.Uri;\n"
                                        + "\n"
                                        + "/**\n"
                                        + " * Ignore comments - create(\"/sdcard/foo\")\n"
                                        + " */\n"
                                        + "public class SdCardTest {\n"
                                        + "\tprivate static final boolean PROFILE_STARTUP = true;\n"
                                        + "\tprivate static final String SDCARD_TEST_HTML = \"/sdcard/test.html\";\n"
                                        + "\tpublic static final String SDCARD_ROOT = \"/sdcard\";\n"
                                        + "\tpublic static final String PACKAGES_PATH = \"/sdcard/o/packages/\";\n"
                                        + "\tFile deviceDir = new File(\"/sdcard/vr\");\n"
                                        + "\n"
                                        + "\tpublic SdCardTest() {\n"
                                        + "\t\tif (PROFILE_STARTUP) {\n"
                                        + "\t\t\tandroid.os.Debug.startMethodTracing(\"/sdcard/launcher\");\n"
                                        + "\t\t}\n"
                                        + "\t\tif (new File(\"/sdcard\").exists()) {\n"
                                        + "\t\t}\n"
                                        + "\t\tString FilePath = \"/sdcard/\" + new File(\"test\");\n"
                                        + "\t\tSystem.setProperty(\"foo.bar\", \"file://sdcard\");\n"
                                        + "\n"
                                        + "\n"
                                        + "\t\tIntent intent = new Intent(Intent.ACTION_PICK);\n"
                                        + "\t\tintent.setDataAndType(Uri.parse(\"file://sdcard/foo.json\"), \"application/bar-json\");\n"
                                        + "\t\tintent.putExtra(\"path-filter\", \"/sdcard(/.+)*\");\n"
                                        + "\t\tintent.putExtra(\"start-dir\", \"/sdcard\");\n"
                                        + "\t\tString mypath = \"/data/data/foo\";\n"
                                        + "\t\tString base = \"/data/data/foo.bar/test-profiling\";\n"
                                        + "\t\tString s = \"file://sdcard/foo\";\n"
                                        + "\t}\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testSuppress() {
        String expected =
                ""
                        + "src/test/pkg/SuppressTest5.java:40: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "  String notAnnotated = \"/sdcard/mypath\";\n"
                        + "                        ~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        //noinspection all // Sample code
        lint().files(
                        // File with lots of /sdcard references, but with @SuppressLint warnings
                        // on fields, methods, variable declarations etc
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class SuppressTest5 {\n"
                                        + "\tprivate String suppressVariable() {\n"
                                        + "\t\t@SuppressLint(\"SdCardPath\")\n"
                                        + "\t\tString string = \"/sdcard/mypath1\";\n"
                                        + "\t\treturn string;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"SdCardPath\")\n"
                                        + "\tprivate String suppressMethod() {\n"
                                        + "\t\tString string = \"/sdcard/mypath2\";\n"
                                        + "\t\treturn string;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"SdCardPath\")\n"
                                        + "\tprivate static class SuppressClass {\n"
                                        + "\t\tprivate String suppressMethod() {\n"
                                        + "\t\t\tString string = \"/sdcard/mypath3\";\n"
                                        + "\t\t\treturn string;\n"
                                        + "\t\t}\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\tprivate String suppressAll() {\n"
                                        + "\t\t@SuppressLint(\"all\")\n"
                                        + "\t\tString string = \"/sdcard/mypath4\";\n"
                                        + "\t\treturn string;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\tprivate String suppressCombination() {\n"
                                        + "\t\t@SuppressLint({\"foo1\", \"foo2\", \"SdCardPath\"})\n"
                                        + "\t\tString string = \"/sdcard/mypath5\";\n"
                                        + "\n"
                                        + "\t\t// This is NOT annotated and *should* generate\n"
                                        + "\t\t// a warning (here to make sure we don't just\n"
                                        + "\t\t// suppress everything when we see an annotation\n"
                                        + "\t\tString notAnnotated = \"/sdcard/mypath\";\n"
                                        + "\n"
                                        + "\t\treturn string;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\tprivate String suppressWarnings() {\n"
                                        + "\t\t@SuppressWarnings(\"all\")\n"
                                        + "\t\tString string = \"/sdcard/mypath6\";\n"
                                        + "\n"
                                        + "\t\t@SuppressWarnings(\"SdCardPath\")\n"
                                        + "\t\tString string2 = \"/sdcard/mypath7\";\n"
                                        + "\n"
                                        + "\t\t@SuppressWarnings(\"AndroidLintSdCardPath\")\n"
                                        + "\t\tString string3 = \"/sdcard/mypath9\";\n"
                                        + "\n"
                                        + "\t\t//noinspection AndroidLintSdCardPath\n"
                                        + "\t\tString string4 = \"/sdcard/mypath9\";\n"
                                        + "\t\t/** @noinspection SdCardPath*/\n"
                                        + "\t\tString string5 = \"/sdcard/mypath10\";\n"
                                        + "\n"
                                        + "\t\treturn string;\n"
                                        + "\t}\n"
                                        + "\n"
                                        + "\t@SuppressLint(\"SdCardPath\")\n"
                                        + "\tprivate String supressField = \"/sdcard/mypath8\";\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testUtf8Bom() {
        String expected =
                ""
                        + "src/test/pkg/Utf8BomTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/mydir\";\n"
                        + "               ~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        // THIS ERROR IS INTENTIONAL
                                        + "\ufeffpackage test.pkg;\n"
                                        + "\n"
                                        + "public class Utf8BomTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void testSuppressInAnnotation() {
        lint().files(
                        java(
                                "src/test/pkg/MyInterface.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "public @interface MyInterface {\n"
                                        + "    @SuppressLint(\"SdCardPath\")\n"
                                        + "    String engineer() default \"/sdcard/this/is/wrong\";\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testMatchInTestIfEnabled() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        java(
                                "src/androidTest/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkTestSources true\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/androidTest/java/test/pkg/MyTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    String s = \"/sdcard/mydir\";\n"
                                + "               ~~~~~~~~~~~~~~~\n"
                                + "src/test/java/test/pkg/MyTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    String s = \"/sdcard/mydir\";\n"
                                + "               ~~~~~~~~~~~~~~~\n"
                                + "0 errors, 2 warnings\n");
    }

    public void testNothingInTests() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        java(
                                "src/androidTest/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkTestSources false\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testNothingInGenerated() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                "generated/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkGeneratedSources false\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testMatchInGeneratedIfEnabled() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                "generated/java/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "public class MyTest {\n"
                                        + "    String s = \"/sdcard/mydir\";\n"
                                        + "}\n"),
                        gradle(
                                ""
                                        + "android {\n"
                                        + "    lintOptions {\n"
                                        + "        checkGeneratedSources true\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "generated/java/test/pkg/MyTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    String s = \"/sdcard/mydir\";\n"
                                + "               ~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings\n");
    }

    public void testWindowsCRLF() {
        // Ensure that offsets are correct on Windows when using CRLF instead of just LF as
        // line separators.

        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/MyTest.java",
                                ""
                                        + "package test.pkg;\r\n"
                                        + "\r\n"
                                        + "public class MyTest {\r\n"
                                        + "    String s = \"/sdcard/mydir\";\r\n"
                                        + "}\r\n"))
                .run()
                .expect(
                        "src/test/pkg/MyTest.java:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    String s = \"/sdcard/mydir\";\n"
                                + "               ~~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings\n");
    }

    public void testKotlin() {
        //noinspection all // Sample code
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "class MyTest {\n"
                                        + "    val s: String = \"/sdcard/mydir\"\n"
                                        + "}\n"),
                        gradle(""))
                .run()
                .expect(
                        ""
                                + "src/main/kotlin/test/pkg/MyTest.kt:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    val s: String = \"/sdcard/mydir\"\n"
                                + "                     ~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings\n");
    }

    public void testSuppressFileAnnotation() {
        //noinspection all // Sample code
        lint().files(
                        kotlin(
                                ""
                                        + "@file:Suppress(\"unused\", \"SdCardPath\")\n"
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "class MyTest {\n"
                                        + "    val s: String = \"/sdcard/mydir\"\n"
                                        + "}\n"),
                        gradle(""))
                .run()
                .expectClean();
    }

    public void testGenericsInSignatures() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "\n"
                                        + "package test.pkg;\n"
                                        + "import java.util.Map;\n"
                                        +
                                        // The main purpose of this test is to ensure that the lint
                                        // test
                                        // infrastructure is able to compute the correct class name
                                        // (and
                                        // target file path) for this interface and package
                                        // declaration
                                        "public interface MyMap<P extends Number, T extends Map<P, ?>>\n"
                                        + "                            extends InteractorBaseComponent<T> {\n"
                                        + "    public String foo = \"/sdcard/foo\"; "
                                        + "}\n"))
                .run()
                .expect(
                        "src/test/pkg/MyMap.java:6: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                + "    public String foo = \"/sdcard/foo\"; }\n"
                                + "                        ~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings\n");
    }

    public void testKotlinKtAnnotated() {
        // Regression test for https://issuetracker.google.com/125138962
        // Makes sure that we correctly process annotation in an annotated expression
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "class SimpleClass() {\n"
                                        + "    var foo: String\n"
                                        + "    init {\n"
                                        + "        @Suppress(\"SdCardPath\")\n"
                                        + "        foo = \"/sdcard/foo\"\n"
                                        + "\n"
                                        + "    }\n"
                                        + "\n"
                                        // Elvis expressions
                                        + "    fun foo(x: String?) {\n"
                                        + "        @Suppress(\"SdCardPath\")\n"
                                        + "        x?.toString()\n"
                                        + "            ?: \"/sdcard/foo\"\n"
                                        + "            ?: \"redundant\"\n"
                                        + "    }\n"
                                        + "\n"
                                        // Object literals
                                        + "    fun objectLiteral() {\n"
                                        + "        val runnable =\n"
                                        + "                @Suppress(\"SdCardPath\")\n"
                                        + "                object : Runnable {\n"
                                        + "                    override fun run() {\n"
                                        + "                        println(\"/sdcard/foo\")\n"
                                        + "                    }\n"
                                        + "                }\n"
                                        + "    }\n"
                                        + "\n"
                                        // Annotated Blocks
                                        + "    fun block() {\n"
                                        + "        @Suppress(\"SdCardPath\")\n"
                                        + "        {\n"
                                        + "            val x = \"/sdcard/foo\"\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    // We've recently removed the large file limit (look for PersistentFSConstants)
    public void testLargeFiles() {
        int max = 2600 * 1024;
        StringBuilder large = new StringBuilder(max + 100); // default is 2500
        large.append("package test.pkg;\nclass VeryLarge {\n");
        for (int i = 0; i < max; i++) {
            large.append(' ');
        }
        large.append("\n}");

        @Language("JAVA")
        String javaSource = large.toString();

        lint().files(java("src/test/pkg/VeryLarge.java", javaSource))
                .allowSystemErrors(true)
                .allowCompilationErrors()
                .run()
                /* We've recently removed the large file limit (look for PersistentFSConstants
                   references)
                .expect("src/test/pkg/VeryLarge.java: Error: Source file too large for lint to " +
                        "process (2600KB); the current max size is 2560000KB. You can increase " +
                        "the limit by setting this system property: " +
                        "idea.max.intellisense.filesize=4096 (or even higher) [LintError]\n" +
                        "1 errors, 0 warnings\n");
                        */
                .expectClean();

        // Bump up the file limit and make sure it no longer complains!
        String prev = System.getProperty("idea.max.intellisense.filesize");
        try {
            System.setProperty(IDEA_MAX_INTELLISENSE_FILESIZE, "4096");
            lint().files(java("src/test/pkg/VeryLarge.java", javaSource))
                    .allowSystemErrors(true)
                    .allowCompilationErrors()
                    .run()
                    .expectClean();
        } finally {
            if (prev != null) {
                System.setProperty(IDEA_MAX_INTELLISENSE_FILESIZE, prev);
            } else {
                System.clearProperty(IDEA_MAX_INTELLISENSE_FILESIZE);
            }
        }
    }
}
