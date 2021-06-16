/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class DefaultEncodingDetectorTest {
    @Test
    fun testDocumentationExample() {
        lint().files(
            java(
                """
                package test.pkg;
                import java.io.BufferedWriter;
                import java.io.File;
                import java.io.FileDescriptor;
                import java.io.FileWriter;
                import java.io.IOException;
                import java.io.PrintWriter;
                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;

                @SuppressWarnings({"Since15", "unused", "CharsetObjectCanBeUsed"})
                public class Test {
                    public void testFileWriter(File file, FileDescriptor fd) throws IOException {
                        new FileWriter("/path");                      // ERROR 1
                        new FileWriter(file);                         // ERROR 2
                        new FileWriter(file, true);                   // ERROR 3
                        new PrintWriter(new BufferedWriter(new FileWriter(file))); // ERROR 4

                        new FileWriter(fd);                           // OK 1
                        new FileWriter(file, StandardCharsets.UTF_8); // OK 2
                    }

                    public void testPrintWriter(File file) throws IOException {
                        new PrintWriter(System.out, true);            // ERROR 5
                        new PrintWriter("/path");                     // ERROR 6
                        new PrintWriter(file);                        // ERROR 7

                        new PrintWriter("/path", "utf-8");            // OK 3
                        new PrintWriter("/path", StandardCharsets.UTF_8);          // OK 4
                        new PrintWriter(System.out, true, StandardCharsets.UTF_8); // OK 5
                        new PrintWriter(file, "utf-8");               // OK 6
                        new PrintWriter(file, Charset.defaultCharset()); // OK 7
                    }

                    public void test(byte[] bytes) throws IOException {
                        new String(bytes);                         // ERROR 8
                        new String(bytes, StandardCharsets.UTF_8); // OK 9
                        new String(bytes, "UTF-8");                // OK 10
                        new String(bytes, 0, 5, "UTF-8");          // OK 11
                    }
                }
                """
            ).indented()
        )
            .issues(DefaultEncodingDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:14: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                        new FileWriter("/path");                      // ERROR 1
                        ~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:15: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                        new FileWriter(file);                         // ERROR 2
                        ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:16: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                        new FileWriter(file, true);                   // ERROR 3
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:17: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                        new PrintWriter(new BufferedWriter(new FileWriter(file))); // ERROR 4
                                                           ~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:24: Error: This PrintWriter will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, PrintWriter(..., UTF_8)? [DefaultEncoding]
                        new PrintWriter(System.out, true);            // ERROR 5
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:25: Error: This PrintWriter will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, PrintWriter(..., UTF_8)? [DefaultEncoding]
                        new PrintWriter("/path");                     // ERROR 6
                        ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:26: Error: This PrintWriter will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, PrintWriter(..., UTF_8)? [DefaultEncoding]
                        new PrintWriter(file);                        // ERROR 7
                        ~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/Test.java:36: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, String(..., UTF_8)? [DefaultEncoding]
                        new String(bytes);                         // ERROR 8
                        ~~~~~~~~~~~~~~~~~
                8 errors, 0 warnings
                """
            )
    }

    @Test
    fun testJava() {
        // From errorprone's `positive` and `reader` unit tests
        lint().files(
            java(
                """
                import java.io.*;
                class Test {
                  void f(String s, byte[] b, OutputStream out, InputStream in, File f) throws Exception {
                    byte[] bs = s.getBytes();    // WARN 1
                    new String(b);               // WARN 2
                    new String(b, 0, 0);         // WARN 3
                    new OutputStreamWriter(out); // WARN 4
                    new InputStreamReader(in);   // WARN 5
                    new FileReader(s);           // WARN 6
                    new FileReader(f);           // WARN 7
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:4: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, getBytes(UTF_8)? [DefaultEncoding]
                byte[] bs = s.getBytes();    // WARN 1
                            ~~~~~~~~~~~~
            src/Test.java:5: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, String(..., UTF_8)? [DefaultEncoding]
                new String(b);               // WARN 2
                ~~~~~~~~~~~~~
            src/Test.java:6: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, String(..., UTF_8)? [DefaultEncoding]
                new String(b, 0, 0);         // WARN 3
                ~~~~~~~~~~~~~~~~~~~
            src/Test.java:7: Error: This OutputStreamWriter will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, OutputStreamWriter(..., UTF_8)? [DefaultEncoding]
                new OutputStreamWriter(out); // WARN 4
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:8: Error: This InputStreamReader will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, InputStreamReader(..., UTF_8)? [DefaultEncoding]
                new InputStreamReader(in);   // WARN 5
                ~~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:9: Error: This file will be read with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                new FileReader(s);           // WARN 6
                ~~~~~~~~~~~~~~~~~
            src/Test.java:10: Error: This file will be read with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileReader(..., UTF_8)? [DefaultEncoding]
                new FileReader(f);           // WARN 7
                ~~~~~~~~~~~~~~~~~
            7 errors, 0 warnings
            """
        )
    }

    @Test
    fun testKotlin() {
        lint().files(
            kotlin(
                """
                import java.io.*
                class Test {
                    fun f(f: File, s: String, flag: Boolean) {
                        FileWriter(s)        // WARN 1
                        FileWriter(s, true)  // WARN 2
                        FileWriter(f)        // WARN 3
                    }

                    fun ok(b: ByteArray) {
                        val s = String(b)    // OK, already UTF-8
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.kt:4: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add Charsets.UTF_8? [DefaultEncoding]
                    FileWriter(s)        // WARN 1
                    ~~~~~~~~~~~~~
            src/Test.kt:5: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add Charsets.UTF_8? [DefaultEncoding]
                    FileWriter(s, true)  // WARN 2
                    ~~~~~~~~~~~~~~~~~~~
            src/Test.kt:6: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; replace with f.writer() (uses UTF-8)? [DefaultEncoding]
                    FileWriter(f)        // WARN 3
                    ~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
        )
    }

    @Test
    fun testAndroid() {
        // No warnings: the system encoding is always UTF-8
        lint().files(
            kotlin(
                """
                import java.io.FileWriter
                fun f(s: String) {
                    FileWriter(s)
                }
                """
            ).indented(),
            gradle(
                """
                apply plugin: 'com.android.application'
                """
            )
        ).run().expectClean()
    }

    @Test
    fun testWriters() {
        // From errorprone's `writer` unit test
        lint().files(
            java(
                """
                import java.io.*;
                class Test {
                  static final boolean CONST = true;
                  void f(File f, String s, boolean flag) throws Exception {
                    new FileWriter(s);        // WARN 1
                    new FileWriter(s, true);  // WARN 2
                    new FileWriter(s, CONST); // WARN 3
                    new FileWriter(f);        // WARN 4
                    new FileWriter(f, true);  // WARN 5
                    new FileWriter(f, false); // WARN 6
                    new FileWriter(f, flag);  // WARN 7
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:5: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                new FileWriter(s);        // WARN 1
                ~~~~~~~~~~~~~~~~~
            src/Test.java:6: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                new FileWriter(s, true);  // WARN 2
                ~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:7: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                new FileWriter(s, CONST); // WARN 3
                ~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:8: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                new FileWriter(f);        // WARN 4
                ~~~~~~~~~~~~~~~~~
            src/Test.java:9: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                new FileWriter(f, true);  // WARN 5
                ~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:10: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                new FileWriter(f, false); // WARN 6
                ~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:11: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, FileWriter(..., UTF_8)? [DefaultEncoding]
                new FileWriter(f, flag);  // WARN 7
                ~~~~~~~~~~~~~~~~~~~~~~~
            7 errors, 0 warnings
            """
        )
    }

    @Test
    fun buffered() {
        // From errorprone's `buffered` unit test
        @Suppress("EmptyTryBlock")
        lint().files(
            java(
                """
                import java.io.*;
                class Test {
                  void f(String s) throws Exception {
                    try (BufferedReader reader = new BufferedReader(new FileReader(s))) {} // WARN 1
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(s))) {} // WARN 2
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:4: Error: This file will be read with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                try (BufferedReader reader = new BufferedReader(new FileReader(s))) {} // WARN 1
                                                                ~~~~~~~~~~~~~~~~~
            src/Test.java:5: Error: This file will be written with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(s))) {} // WARN 2
                                                                ~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun negative() {
        // From errorprone's `negative` and `ignoreFileDescriptor` unit tests
        @Suppress("ResultOfMethodCallIgnored", "RedundantThrows", "EmptyTryBlock", "CharsetObjectCanBeUsed")
        lint().files(
            java(
                """
                import static java.nio.charset.StandardCharsets.UTF_8;
                import java.io.*;
                import java.nio.charset.CharsetDecoder;

                class Test {
                  void f(String s, byte[] b, OutputStream out, InputStream in, File f, CharsetDecoder decoder)
                      throws Exception {
                    s.getBytes(UTF_8);
                    s.getBytes("UTF-8");
                    new String(b, UTF_8);
                    new String(b, "UTF-8");
                    new String(b, 0, 0, UTF_8);
                    new OutputStreamWriter(out, UTF_8);
                    new OutputStreamWriter(out, "UTF-8");
                    new InputStreamReader(in, UTF_8);
                    new InputStreamReader(in, "UTF-8");
                    new InputStreamReader(in, decoder);
                  }
                  void f(FileDescriptor fd) throws Exception {
                    try (BufferedReader reader = new BufferedReader(new FileReader(fd))) {}
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fd))) {}
                  }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    @Test
    fun testScanner() {
        // From errorprone's `scannerDefaultCharset` unit test
        @Suppress("ConstantConditions")
        lint().files(
            java(
                """
                import java.util.Scanner;
                import java.io.File;
                import java.io.InputStream;
                import java.nio.channels.ReadableByteChannel;
                import java.nio.file.Path;
                import java.lang.Readable;

                class Test {
                  void f() throws Exception {
                    new Scanner((InputStream) null);         // WARN 1
                    new Scanner((File) null);                // WARN 2
                    new Scanner((Path) null);                // WARN 3
                    new Scanner((ReadableByteChannel) null); // WARN 4
                    new Scanner((File) null, "UTF-8");       // OK
                    new Scanner((String) null);              // OK
                    new Scanner((Readable) null);            // OK
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:10: Error: This Scanner will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, Scanner(..., UTF_8)? [DefaultEncoding]
                new Scanner((InputStream) null);         // WARN 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:11: Error: This Scanner will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, Scanner(..., UTF_8)? [DefaultEncoding]
                new Scanner((File) null);                // WARN 2
                ~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:12: Error: This Scanner will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, Scanner(..., UTF_8)? [DefaultEncoding]
                new Scanner((Path) null);                // WARN 3
                ~~~~~~~~~~~~~~~~~~~~~~~~
            src/Test.java:13: Error: This Scanner will use the default system encoding instead of a specific charset which is usually a mistake; add charset argument, Scanner(..., UTF_8)? [DefaultEncoding]
                new Scanner((ReadableByteChannel) null); // WARN 4
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            4 errors, 0 warnings
            """
        )
    }

    @Test
    fun testAsciiConstants() {
        @Suppress("ResultOfMethodCallIgnored")
        lint().files(
            java(
                """
                class Test {
                    final String S1 = "2 >= 1";
                    final String S2 = "2 ≥ 1";
                    void test() {
                        "values file content".getBytes(); // OK 1
                        S1.getBytes();                    // OK 2
                        "2 ≥ 1".getBytes();               // WARN 1
                        S2.getBytes();                    // WARN 2
                    }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:7: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, getBytes(UTF_8)? [DefaultEncoding]
                    "2 ≥ 1".getBytes();               // WARN 1
                    ~~~~~~~~~~~~~~~~~~
            src/Test.java:8: Error: This string will be interpreted with the default system encoding instead of a specific charset which is usually a mistake; add charset argument, getBytes(UTF_8)? [DefaultEncoding]
                    S2.getBytes();                    // WARN 2
                    ~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
        )
    }

    @Test
    fun testByteArrayOutputStream() {
        // From errorprone's `byteArrayOutputStream` unit test
        @Suppress("Since15")
        lint().files(
            java(
                """
                import java.io.ByteArrayOutputStream;
                import java.nio.charset.Charset;
                class Test {
                  String f(ByteArrayOutputStream b) {
                    b.toString(Charset.defaultCharset()); // OK 1
                    b.toString("UTF-8"); // OK 2
                    return b.toString(); // WARN
                  }
                }
                """
            ).indented()
        ).run().expect(
            """
            src/Test.java:7: Error: This string will be decoded with the default system encoding instead of a specific charset which is usually a mistake; add StandardCharsets.UTF_8? [DefaultEncoding]
                return b.toString(); // WARN
                       ~~~~~~~~~~~~
            1 errors, 0 warnings
            """
        )
    }

    @Test
    fun testFixGetBytes() {
        lint().files(
            kotlin(
                """
                import kotlin.text.Charsets
                import java.nio.charset.Charset.*
                class TestKotlin {
                    fun f(s: String, b: ByteArray) {
                        // Kotlin doesn't expose default-charset methods, so we have to
                        // work a little harder here:
                        val js = s as java.lang.String
                        js.getBytes()
                        js.bytes

                        val s2 = java.lang.String(b)
                    }
                }
                """
            ).indented(),
            java(
                """
                import java.nio.charset.StandardCharsets;
                import static java.nio.charset.Charset.defaultCharset;
                class TestJava {
                    void f(String s, byte[] b) {
                        byte[] bs = s.getBytes();
                        String s2 = new String(bs);
                    }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/TestJava.java line 5: Add charset argument, `getBytes(UTF_8)`:
            @@ -5 +5
            -         byte[] bs = s.getBytes();
            +         byte[] bs = s.getBytes(StandardCharsets.UTF_8);
            Fix for src/TestJava.java line 5: Add charset argument, `getBytes(defaultCharset())`:
            @@ -5 +5
            -         byte[] bs = s.getBytes();
            +         byte[] bs = s.getBytes(defaultCharset());
            Fix for src/TestJava.java line 6: Add charset argument, `String(..., UTF_8)`:
            @@ -6 +6
            -         String s2 = new String(bs);
            +         String s2 = new String(bs, StandardCharsets.UTF_8);
            Fix for src/TestJava.java line 6: Add charset argument, `String(..., defaultCharset())`:
            @@ -6 +6
            -         String s2 = new String(bs);
            +         String s2 = new String(bs, defaultCharset());
            Fix for src/TestKotlin.kt line 8: Replace with `encodeToByteArray()`:
            @@ -8 +8
            -         js.getBytes()
            +         js.encodeToByteArray()
            Fix for src/TestKotlin.kt line 8: Replace with `toByteArray(UTF_8)`:
            @@ -8 +8
            -         js.getBytes()
            +         js.toByteArray(Charsets.UTF_8)
            Fix for src/TestKotlin.kt line 8: Replace with `toByteArray(defaultCharset())`:
            @@ -8 +8
            -         js.getBytes()
            +         js.toByteArray(defaultCharset())
            Fix for src/TestKotlin.kt line 9: Replace with `encodeToByteArray()`:
            @@ -9 +9
            -         js.bytes
            +         js.encodeToByteArray()
            Fix for src/TestKotlin.kt line 9: Replace with `toByteArray(UTF_8)`:
            @@ -9 +9
            -         js.bytes
            +         js.toByteArray(Charsets.UTF_8)
            Fix for src/TestKotlin.kt line 9: Replace with `toByteArray(defaultCharset())`:
            @@ -9 +9
            -         js.bytes
            +         js.toByteArray(defaultCharset())
            Fix for src/TestKotlin.kt line 11: Add charset argument, `String(..., UTF_8)`:
            @@ -11 +11
            -         val s2 = java.lang.String(b)
            +         val s2 = java.lang.String(b, Charsets.UTF_8)
            Fix for src/TestKotlin.kt line 11: Add charset argument, `String(..., defaultCharset())`:
            @@ -11 +11
            -         val s2 = java.lang.String(b)
            +         val s2 = java.lang.String(b, defaultCharset())
            """
        )
    }

    @Test
    fun testFixFileReaderWriter() {
        lint().files(
            kotlin(
                """
                import java.io.*
                class TestKotlin {
                  fun f(file: File) {
                    FileReader(file)
                    FileWriter(file)
                    BufferedReader(FileReader(file))
                    BufferedWriter(FileWriter(file))
                    FileWriter("/tmp/test.txt")
                  }
                }
                """
            ).indented(),
            java(
                """
                import java.io.*;
                class TestJava {
                  void f(File file) throws Exception {
                    new FileReader(file);
                    new FileWriter(file);
                    new BufferedReader(new FileReader(file));
                    new BufferedWriter(new FileWriter(file));
                  }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/TestJava.java line 4: Add charset argument, `FileReader(..., UTF_8)`:
            @@ -4 +4
            -     new FileReader(file);
            +     new FileReader(file, java.nio.charset.StandardCharsets.UTF_8);
            Fix for src/TestJava.java line 4: Add charset argument, `FileReader(..., defaultCharset())`:
            @@ -4 +4
            -     new FileReader(file);
            +     new FileReader(file, java.nio.charset.Charset.defaultCharset());
            Fix for src/TestJava.java line 5: Add charset argument, `FileWriter(..., UTF_8)`:
            @@ -5 +5
            -     new FileWriter(file);
            +     new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8);
            Fix for src/TestJava.java line 5: Add charset argument, `FileWriter(..., defaultCharset())`:
            @@ -5 +5
            -     new FileWriter(file);
            +     new FileWriter(file, java.nio.charset.Charset.defaultCharset());
            Fix for src/TestJava.java line 6: Add charset argument, `FileReader(..., UTF_8)`:
            @@ -6 +6
            -     new BufferedReader(new FileReader(file));
            +     new BufferedReader(new FileReader(file, java.nio.charset.StandardCharsets.UTF_8));
            Fix for src/TestJava.java line 6: Add charset argument, `FileReader(..., defaultCharset())`:
            @@ -6 +6
            -     new BufferedReader(new FileReader(file));
            +     new BufferedReader(new FileReader(file, java.nio.charset.Charset.defaultCharset()));
            Fix for src/TestJava.java line 7: Add charset argument, `FileWriter(..., UTF_8)`:
            @@ -7 +7
            -     new BufferedWriter(new FileWriter(file));
            +     new BufferedWriter(new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8));
            Fix for src/TestJava.java line 7: Add charset argument, `FileWriter(..., defaultCharset())`:
            @@ -7 +7
            -     new BufferedWriter(new FileWriter(file));
            +     new BufferedWriter(new FileWriter(file, java.nio.charset.Charset.defaultCharset()));
            Fix for src/TestKotlin.kt line 4: Replace with `file.reader()` (uses UTF-8):
            @@ -4 +4
            -     FileReader(file)
            +     file.reader()
            Fix for src/TestKotlin.kt line 4: Replace with `file.reader(defaultCharset())`:
            @@ -4 +4
            -     FileReader(file)
            +     file.reader(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/TestKotlin.kt line 5: Replace with `file.writer()` (uses UTF-8):
            @@ -5 +5
            -     FileWriter(file)
            +     file.writer()
            Fix for src/TestKotlin.kt line 5: Replace with `file.writer(defaultCharset())`:
            @@ -5 +5
            -     FileWriter(file)
            +     file.writer(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/TestKotlin.kt line 6: Replace with `file.bufferedReader()` (uses UTF-8):
            @@ -6 +6
            -     BufferedReader(FileReader(file))
            +     file.bufferedReader()
            Fix for src/TestKotlin.kt line 6: Replace with `file.bufferedReader(defaultCharset())`:
            @@ -6 +6
            -     BufferedReader(FileReader(file))
            +     file.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/TestKotlin.kt line 7: Replace with `file.bufferedWriter()` (uses UTF-8):
            @@ -7 +7
            -     BufferedWriter(FileWriter(file))
            +     file.bufferedWriter()
            Fix for src/TestKotlin.kt line 7: Replace with `file.bufferedWriter(defaultCharset())`:
            @@ -7 +7
            -     BufferedWriter(FileWriter(file))
            +     file.bufferedWriter(java.nio.charset.StandardCharsets.UTF_8)
            """
        )
    }

    @Test
    fun testFixFileReaderWriterPreJava11() {
        lint().files(
            java(
                """
                import java.io.*;
                import java.nio.charset.*;
                class TestJava {
                  void f(File file) throws Exception {
                    new FileReader(file);
                    new FileWriter(file);
                  }
                }
                """
            ).indented(),
            // Set Java language level to 1.8; quickfixes for FileReader/FileWriter in Java depend on the language
            // level since the best replacement isn't available before Java 11
            gradle(
                """
                apply plugin: 'java'
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                    }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/main/java/TestJava.java line 5: Replace with `InputStreamReader(FileInputStream(..., UTF8)`:
            @@ -5 +5
            -     new FileReader(file);
            +     new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            Fix for src/main/java/TestJava.java line 5: Replace with `InputStreamReader(FileInputStream(..., defaultCharset())`:
            @@ -5 +5
            -     new FileReader(file);
            +     new InputStreamReader(new FileInputStream(file), Charset.defaultCharset());
            Fix for src/main/java/TestJava.java line 6: Replace with `OutputStreamWriter(FileOutputStream(..., ..., UTF8)`:
            @@ -6 +6
            -     new FileWriter(file);
            +     new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            Fix for src/main/java/TestJava.java line 6: Replace with `OutputStreamWriter(FileOutputStream(..., ..., defaultCharset())`:
            @@ -6 +6
            -     new FileWriter(file);
            +     new OutputStreamWriter(new FileOutputStream(file), Charset.defaultCharset());
            """
        )
    }

    @Test
    fun testFixPrintWriter() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import java.io.File
                import java.io.FileWriter
                import java.io.PrintWriter

                class TestKotlin {
                  fun testFileWriter(file: File) {
                    PrintWriter(System.out, true)
                    PrintWriter("/path")
                    PrintWriter(file)
                  }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import java.io.File;
                import java.io.IOException;
                import java.io.PrintWriter;
                import java.io.FileWriter;
                class TestJava {
                    public void testFileWriter(File file) throws IOException {
                        new PrintWriter(System.out, true);
                        new PrintWriter("/path");
                        new PrintWriter(file);
                    }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/test/pkg/TestJava.java line 8: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -8 +8
            -         new PrintWriter(System.out, true);
            +         new PrintWriter(System.out, true, java.nio.charset.StandardCharsets.UTF_8);
            Fix for src/test/pkg/TestJava.java line 8: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -8 +8
            -         new PrintWriter(System.out, true);
            +         new PrintWriter(System.out, true, java.nio.charset.Charset.defaultCharset());
            Fix for src/test/pkg/TestJava.java line 9: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -9 +9
            -         new PrintWriter("/path");
            +         new PrintWriter("/path", java.nio.charset.StandardCharsets.UTF_8);
            Fix for src/test/pkg/TestJava.java line 9: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -9 +9
            -         new PrintWriter("/path");
            +         new PrintWriter("/path", java.nio.charset.Charset.defaultCharset());
            Fix for src/test/pkg/TestJava.java line 10: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -10 +10
            -         new PrintWriter(file);
            +         new PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8);
            Fix for src/test/pkg/TestJava.java line 10: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -10 +10
            -         new PrintWriter(file);
            +         new PrintWriter(file, java.nio.charset.Charset.defaultCharset());
            Fix for src/test/pkg/TestKotlin.kt line 8: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -8 +8
            -     PrintWriter(System.out, true)
            +     PrintWriter(System.out, true, kotlin.text.Charsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 8: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -8 +8
            -     PrintWriter(System.out, true)
            +     PrintWriter(System.out, true, java.nio.charset.Charset.defaultCharset())
            Fix for src/test/pkg/TestKotlin.kt line 9: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -9 +9
            -     PrintWriter("/path")
            +     PrintWriter("/path", kotlin.text.Charsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 9: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -9 +9
            -     PrintWriter("/path")
            +     PrintWriter("/path", java.nio.charset.Charset.defaultCharset())
            Fix for src/test/pkg/TestKotlin.kt line 10: Add charset argument, `PrintWriter(..., UTF_8)`:
            @@ -10 +10
            -     PrintWriter(file)
            +     PrintWriter(file, kotlin.text.Charsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 10: Add charset argument, `PrintWriter(..., defaultCharset())`:
            @@ -10 +10
            -     PrintWriter(file)
            +     PrintWriter(file, java.nio.charset.Charset.defaultCharset())
            """
        )
    }

    @Test
    fun testFixInputStreamReader() {
        lint().files(
            kotlin(
                """
                package test.pkg
                import java.io.BufferedReader
                import java.io.BufferedWriter
                import java.io.InputStream
                import java.io.InputStreamReader
                import java.io.OutputStream
                import java.io.OutputStreamWriter

                class TestKotlin {
                    fun f(s: String, b: ByteArray, out: OutputStream, input: InputStream) {
                        OutputStreamWriter(out)
                        InputStreamReader(input)
                        BufferedWriter(OutputStreamWriter(out))
                        BufferedReader(InputStreamReader(input))
                    }
                }
                """
            ).indented(),
            java(
                """
                package test.pkg;
                import java.io.File;
                import java.io.IOException;
                import java.io.PrintWriter;
                import java.io.FileWriter;
                import java.nio.charset.StandardCharsets;
                import java.nio.charset.Charset;
                class TestJava {
                  void f(String s, byte[] b, OutputStream out, InputStream in, File f) throws Exception {
                    new OutputStreamWriter(out);
                    new InputStreamReader(in);
                    new BufferedWriter(new OutputStreamWriter(out));
                    new BufferedReader(new InputStreamReader(in));
                  }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/test/pkg/TestKotlin.kt line 11: Replace with `out.writer()` (uses UTF-8):
            @@ -11 +11
            -         OutputStreamWriter(out)
            +         out.writer()
            Fix for src/test/pkg/TestKotlin.kt line 11: Replace with `out.writer(defaultCharset())`:
            @@ -11 +11
            -         OutputStreamWriter(out)
            +         out.writer(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 12: Replace with `input.reader()` (uses UTF-8):
            @@ -12 +12
            -         InputStreamReader(input)
            +         input.reader()
            Fix for src/test/pkg/TestKotlin.kt line 12: Replace with `input.reader(defaultCharset())`:
            @@ -12 +12
            -         InputStreamReader(input)
            +         input.reader(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 13: Replace with `out.bufferedWriter()` (uses UTF-8):
            @@ -13 +13
            -         BufferedWriter(OutputStreamWriter(out))
            +         out.bufferedWriter()
            Fix for src/test/pkg/TestKotlin.kt line 13: Replace with `out.bufferedWriter(defaultCharset())`:
            @@ -13 +13
            -         BufferedWriter(OutputStreamWriter(out))
            +         out.bufferedWriter(java.nio.charset.StandardCharsets.UTF_8)
            Fix for src/test/pkg/TestKotlin.kt line 14: Replace with `input.bufferedReader()` (uses UTF-8):
            @@ -14 +14
            -         BufferedReader(InputStreamReader(input))
            +         input.bufferedReader()
            Fix for src/test/pkg/TestKotlin.kt line 14: Replace with `input.bufferedReader(defaultCharset())`:
            @@ -14 +14
            -         BufferedReader(InputStreamReader(input))
            +         input.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)
            """
        )
    }

    @Test
    fun testFixScanner() {
        lint().files(
            java(
                """
                package test.pkg;
                import java.util.Scanner;
                import java.nio.charset.StandardCharsets;
                import java.nio.charset.Charset;
                class TestJava {
                    public void testScanner() throws IOException {
                        new Scanner((java.io.InputStream) null);
                        new Scanner((java.io.File) null);
                    }
                }
                """
            ).indented()
        ).run().expectFixDiffs(
            """
            Fix for src/test/pkg/TestJava.java line 7: Add charset argument, `Scanner(..., UTF_8)`:
            @@ -7 +7
            -         new Scanner((java.io.InputStream) null);
            +         new Scanner((java.io.InputStream) null, StandardCharsets.UTF_8);
            Fix for src/test/pkg/TestJava.java line 7: Add charset argument, `Scanner(..., defaultCharset())`:
            @@ -7 +7
            -         new Scanner((java.io.InputStream) null);
            +         new Scanner((java.io.InputStream) null, Charset.defaultCharset());
            Fix for src/test/pkg/TestJava.java line 8: Add charset argument, `Scanner(..., UTF_8)`:
            @@ -8 +8
            -         new Scanner((java.io.File) null);
            +         new Scanner((java.io.File) null, StandardCharsets.UTF_8);
            Fix for src/test/pkg/TestJava.java line 8: Add charset argument, `Scanner(..., defaultCharset())`:
            @@ -8 +8
            -         new Scanner((java.io.File) null);
            +         new Scanner((java.io.File) null, Charset.defaultCharset());
            """
        )
    }

    private fun lint(): TestLintTask {
        return TestLintTask.lint().allowMissingSdk().issues(DefaultEncodingDetector.ISSUE)
    }
}
