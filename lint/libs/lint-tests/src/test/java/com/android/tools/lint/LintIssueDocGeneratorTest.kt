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

package com.android.tools.lint

import com.android.tools.lint.client.api.LintClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class LintIssueDocGeneratorTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
    }

    @Test
    fun testMarkDeep() {
        // (This is the default output format)
        val outputFolder = temporaryFolder.root
        LintIssueDocGenerator.run(
            arrayOf(
                "--no-index",
                "--issues",
                "SdCardPath,MissingClass",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("MissingClass.md.html, SdCardPath.md.html", names)
        val text = files[1].readText()
        assertEquals(
            """
            <meta charset="utf-8">
            (#) Hardcoded reference to `/sdcard`

            !!! WARNING: Hardcoded reference to `/sdcard`
               This is a warning.

            Id
            :   `SdCardPath`
            Summary
            :   Hardcoded reference to `/sdcard`
            Severity
            :   Warning
            Category
            :   Correctness
            Platform
            :   Android
            Vendor
            :   Android Open Source Project
            Feedback
            :   https://issuetracker.google.com/issues/new?component=192708
            Affects
            :   Kotlin and Java files
            Editing
            :   This check runs on the fly in the IDE editor
            See
            :   https://developer.android.com/training/data-storage#filesExternal

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.

            (##) Suppressing

            You can suppress false positives using one of the following mechanisms:

            * Using a suppression annotation like this on the enclosing
              element:

              ```kt
              // Kotlin
              @Suppress("SdCardPath")
              fun method() {
                 problematicStatement()
              }
              ```

              or

              ```java
              // Java
              @SuppressWarnings("SdCardPath")
              void method() {
                 problematicStatement();
              }
              ```

            * Using a suppression comment like this on the line above:

              ```kt
              //noinspection SdCardPath
              problematicStatement()
              ```

            * Using a special `lint.xml` file in the source tree which turns off
              the check in that folder and any sub folder. A simple file might look
              like this:
              ```xml
              &lt;?xml version="1.0" encoding="UTF-8"?&gt;
              &lt;lint&gt;
                  &lt;issue id="SdCardPath" severity="ignore" /&gt;
              &lt;/lint&gt;
              ```
              Instead of `ignore` you can also change the severity here, for
              example from `error` to `warning`. You can find additional
              documentation on how to filter issues by path, regular expression and
              so on
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

            * In Gradle projects, using the DSL syntax to configure lint. For
              example, you can use something like
              ```gradle
              lintOptions {
                  disable 'SdCardPath'
              }
              ```
              In Android projects this should be nested inside an `android { }`
              block.

            * For manual invocations of `lint`, using the `--ignore` flag:
              ```
              ${'$'} lint --ignore SdCardPath ...`
              ```

            * Last, but not least, using baselines, as discussed
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).

            <!-- Markdeep: --><style class="fallback">body{visibility:hidden;white-space:pre;font-family:monospace}</style><script src="markdeep.min.js" charset="utf-8"></script><script src="https://morgan3d.github.io/markdeep/latest/markdeep.min.js" charset="utf-8"></script><script>window.alreadyProcessedMarkdeep||(document.body.style.visibility="visible")</script>
            """.trimIndent(),
            text
        )
    }

    @Test
    fun testMarkdown() {
        val outputFolder = temporaryFolder.root
        LintIssueDocGenerator.run(
            arrayOf(
                "--md",
                "--no-index",
                "--issues",
                "SdCardPath,BatteryLife",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("BatteryLife.md, SdCardPath.md", names)
        val text = files[0].readText()
        assertEquals(
            """
            # Battery Life Issues

            Id       | `BatteryLife`
            ---------|--------------------------------------------------------------
            Summary  | Battery Life Issues
            Severity | Warning
            Category | Correctness
            Platform | Android
            Vendor   | Android Open Source Project
            Feedback | https://issuetracker.google.com/issues/new?component=192708
            Affects  | Kotlin and Java files and manifest files
            Editing  | This check runs on the fly in the IDE editor
            See      | https://developer.android.com/topic/performance/background-optimization

            This issue flags code that either
            * negatively affects battery life, or
            * uses APIs that have recently changed behavior to prevent background
              tasks from consuming memory and battery excessively.

            Generally, you should be using `WorkManager` instead.

            For more details on how to update your code, please see
            https://developer.android.com/topic/performance/background-optimization.

            (##) Suppressing

            You can suppress false positives using one of the following mechanisms:

            * Using a suppression annotation like this on the enclosing
              element:

              ```kt
              // Kotlin
              @Suppress("BatteryLife")
              fun method() {
                 problematicStatement()
              }
              ```

              or

              ```java
              // Java
              @SuppressWarnings("BatteryLife")
              void method() {
                 problematicStatement();
              }
              ```

            * Using a suppression comment like this on the line above:

              ```kt
              //noinspection BatteryLife
              problematicStatement()
              ```

            * Adding the suppression attribute `tools:ignore="BatteryLife"` on the
              problematic XML element (or one of its enclosing elements). You may
              also need to add the following namespace declaration on the root
              element in the XML file if it's not already there:
              `xmlns:tools="http://schemas.android.com/tools"`.

              ```xml
              <?xml version="1.0" encoding="UTF-8"?>
              <manifest xmlns:tools="http://schemas.android.com/tools">
                  ...
                  <action tools:ignore="BatteryLife" .../>
                ...
              </manifest>
              ```

            * Using a special `lint.xml` file in the source tree which turns off
              the check in that folder and any sub folder. A simple file might look
              like this:
              ```xml
              <?xml version="1.0" encoding="UTF-8"?>
              <lint>
                  <issue id="BatteryLife" severity="ignore" />
              </lint>
              ```
              Instead of `ignore` you can also change the severity here, for
              example from `error` to `warning`. You can find additional
              documentation on how to filter issues by path, regular expression and
              so on
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

            * In Gradle projects, using the DSL syntax to configure lint. For
              example, you can use something like
              ```gradle
              lintOptions {
                  disable 'BatteryLife'
              }
              ```
              In Android projects this should be nested inside an `android { }`
              block.

            * For manual invocations of `lint`, using the `--ignore` flag:
              ```
              ${'$'} lint --ignore BatteryLife ...`
              ```

            * Last, but not least, using baselines, as discussed
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).
            """.trimIndent(),
            text
        )
    }

    @Test
    fun testMarkdownIndex() {
        val outputFolder = temporaryFolder.root
        LintIssueDocGenerator.run(
            arrayOf(
                "--md",
                "--issues",
                "SdCardPath,MissingClass,ViewTag,LambdaLast",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("LambdaLast.md, MissingClass.md, SdCardPath.md, ViewTag.md, categories.md, index.md, severity.md, vendors.md", names)
        val alphabetical = files[5].readText()
        assertEquals(
            """
            # Lint Issue Index

            Order: Alphabetical | [By category](categories.md) | [By vendor](vendors.md) | [By severity](severity.md)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """.trimIndent(),
            alphabetical
        )
        val categories = files[4].readText()
        assertEquals(
            """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | By category | [By vendor](vendors.md) | [By severity](severity.md)

            * Correctness (2)

              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Interoperability: Kotlin Interoperability (1)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """.trimIndent(),
            categories
        )
        val severities = files[6].readText()
        assertEquals(
            """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | [By category](categories.md) | [By vendor](vendors.md) | By severity


            * Error (1)

              - [MissingClass: Missing registered class](MissingClass.md)

            * Warning (2)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Disabled By Default (1)

              - [LambdaLast](LambdaLast.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """.trimIndent(),
            severities
        )
        val vendors = files[7].readText()
        assertEquals(
            """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | [By category](categories.md) | By vendor | [By severity](severity.md)

            * Built In (3)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """.trimIndent(),
            vendors
        )
    }

    @Test
    fun testMarkdownDeleted() {
        val outputFolder = temporaryFolder.root
        LintIssueDocGenerator.run(
            arrayOf(
                "--md",
                "--no-index",
                "--issues",
                // MissingRegistered has been renamed, ViewTag has been deleted
                "SdCardPath,MissingRegistered,ViewTag",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("MissingRegistered.md, SdCardPath.md, ViewTag.md", names)
        val text = files[0].readText()
        assertEquals(
            """
            # MissingRegistered

            This issue id is an alias for [MissingClass](MissingClass.md).

            (Additional metadata not available.)
            """.trimIndent(),
            text
        )
        val text2 = files[2].readText()
        assertEquals(
            """
            # ViewTag

            The issue for this id has been deleted or marked obsolete and can now be
            ignored.

            (Additional metadata not available.)
            """.trimIndent(),
            text2
        )
    }

    @Test
    fun testSingleDoc() {
        val output = temporaryFolder.newFile()
        LintIssueDocGenerator.run(
            arrayOf(
                "--single-doc",
                "--md",
                "--issues",
                "SdCardPath,MissingClass",
                "--output",
                output.path
            )
        )
        val text = output.readText()
        assertEquals(
            """
            # Lint Issues
            This document lists the built-in issues for Lint. Note that lint also reads additional
            checks directly bundled with libraries, so this is a subset of the checks lint will
            perform.

            ## Correctness

            ### Missing registered class

            Id         | `MissingClass`
            -----------|------------------------------------------------------------
            Previously | MissingRegistered
            Summary    | Missing registered class
            Severity   | Error
            Category   | Correctness
            Platform   | Android
            Vendor     | Android Open Source Project
            Feedback   | https://issuetracker.google.com/issues/new?component=192708
            Affects    | Manifest files and resource files
            Editing    | This check runs on the fly in the IDE editor
            See        | https://developer.android.com/guide/topics/manifest/manifest-intro.html

            If a class is referenced in the manifest or in a layout file, it must
            also exist in the project (or in one of the libraries included by the
            project. This check helps uncover typos in registration names, or
            attempts to rename or move classes without updating the XML references
            properly.

            ### Hardcoded reference to `/sdcard`

            Id       | `SdCardPath`
            ---------|--------------------------------------------------------------
            Summary  | Hardcoded reference to `/sdcard`
            Severity | Warning
            Category | Correctness
            Platform | Android
            Vendor   | Android Open Source Project
            Feedback | https://issuetracker.google.com/issues/new?component=192708
            Affects  | Kotlin and Java files
            Editing  | This check runs on the fly in the IDE editor
            See      | https://developer.android.com/training/data-storage#filesExternal

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun testLintMainIntegration() {
        // Also allow invoking the documentation tool from the main lint
        // binary (so that you don't have to construct a long java command
        // with full classpath etc). This test makes sure this works.
        val outputFolder = temporaryFolder.root
        Main().run(
            arrayOf(
                "--generate-docs", // Flag to lint
                "--md", // the rest of the flags are interpreted by this tool
                "--no-index",
                "--issues",
                "SdCardPath,MissingClass,ViewTag",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("MissingClass.md, SdCardPath.md, ViewTag.md", names)
        val text = files[2].readText()
        assertEquals(
            """
            # ViewTag

            The issue for this id has been deleted or marked obsolete and can now be
            ignored.

            (Additional metadata not available.)
            """.trimIndent(),
            text
        )
    }

    @Test
    fun testUsage() {
        val bytes = ByteArrayOutputStream()
        val printStream = PrintStream(bytes)
        LintIssueDocGenerator.printUsage(false, printStream)
        val usage = String(bytes.toByteArray(), Charsets.UTF_8).trim()
        assertEquals(
            """
            Usage: lint-issue-docs-generator [flags] --output <directory or file>]

            Flags:

            --help                            This message.
            --output <dir>                    Sets the path to write the documentation to.
                                              Normally a directory, unless --single-doc is
                                              also specified
            --single-doc                      Instead of writing one page per issue into a
                                              directory, write a single page containing
                                              all the issues
            --md                              Write to plain Markdown (.md) files instead
                                              of Markdeep (.md.html)
            --builtins                        Generate documentation for the built-in
                                              issues. This is implied if --lint-jars is
                                              not specified
            --lint-jars <jar-path>            Read the lint issues from the specific path
                                              (separated by : of custom jar files
            --issues [issues]                 Limits the issues documented to the specific
                                              (comma-separated) list of issue id's
            --source-url <url-prefix> <path>  Searches for the detector source code under
                                              the given source folder or folders separated
                                              by semicolons, and if found, prefixes the
                                              path with the given URL prefix and includes
                                              this source link in the issue
                                              documentation.
            --test-url <url-prefix> <path>    Like --source-url, but for detector unit
                                              tests instead. These must be named the same
                                              as the detector class, plus `Test` as a
                                              suffix.
            --no-index                        Do not include index files
            --no-suppress-info                Do not include suppression information
            --no-examples                     Do not include examples pulled from unit
                                              tests, if found
            --no-links                        Do not include hyperlinks to detector source
                                              code
            --no-severity                     Do not include the red, orange or green
                                              informational boxes showing the severity of
                                              each issue
            """.trimIndent(),
            usage
        )
    }

    @Test
    fun testCodeSample() {
        // TODO: Point it to source and test classes
        val sources = temporaryFolder.newFolder("sources")
        val testSources = temporaryFolder.newFolder("test-sources")
        val outputFolder = temporaryFolder.newFolder("report")

        val sourceFile = File(sources, "com/android/tools/lint/checks/SdCardDetector.kt")
        val testSourceFile = File(testSources, "com/android/tools/lint/checks/SdCardDetectorTest.java")
        sourceFile.parentFile?.mkdirs()
        testSourceFile.parentFile?.mkdirs()
        sourceFile.createNewFile()
        // TODO: Test Kotlin test as well
        testSourceFile.writeText(
            """
            package com.android.tools.lint.checks;

            import com.android.tools.lint.detector.api.Detector;
            import org.intellij.lang.annotations.Language;

            public class SdCardDetectorTest extends AbstractCheckTest {
                @Override
                protected Detector getDetector() {
                    return new SdCardDetector();
                }
                public void testKotlin() {
                    //noinspection all // Sample code
                    lint().files(
                                    kotlin(
                                            ""
                                                    + "package test.pkg\n"
                                                    + "import android.support.v7.widget.RecyclerView // should be rewritten to AndroidX in docs\n"
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

                public void testSuppressExample() {
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
            }
            """
        )

        LintIssueDocGenerator.run(
            arrayOf(
                "--md",
                "--no-index",
                "--source-url", "http://example.com/lint-source-code/src/",
                sources.path,
                "--test-url",
                "http://example.com/lint-source-code/tests/",
                testSources.path,
                "--issues",
                "SdCardPath",
                "--no-suppress-info",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("SdCardPath.md", names)
        val text = files[0].readText()
        assertEquals(
            """
            # Hardcoded reference to `/sdcard`

            Id             | `SdCardPath`
            ---------------|--------------------------------------------------------
            Summary        | Hardcoded reference to `/sdcard`
            Severity       | Warning
            Category       | Correctness
            Platform       | Android
            Vendor         | Android Open Source Project
            Feedback       | https://issuetracker.google.com/issues/new?component=192708
            Affects        | Kotlin and Java files
            Editing        | This check runs on the fly in the IDE editor
            See            | https://developer.android.com/training/data-storage#filesExternal
            Implementation | [Source Code](http://example.com/lint-source-code/src/com/android/tools/lint/checks/SdCardDetector.kt)
            Tests          | [Source Code](http://example.com/lint-source-code/tests/com/android/tools/lint/checks/SdCardDetectorTest.java)

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.

            (##) Example

            Here is an example of lint warnings produced by this check:
            ```text
            src/main/kotlin/test/pkg/MyTest.kt:4:Warning: Do not hardcode
            "/sdcard/"; use Environment.getExternalStorageDirectory().getPath()
            instead [SdCardPath]

                val s: String = "/sdcard/mydir"
                                 ~~~~~~~~~~~~~

            ```

            Here is the source file referenced above:

            `src/main/kotlin/test/pkg/MyTest.kt`:
            ```kotlin
            package test.pkg
            import androidx.recyclerview.widget.RecyclerView // should be rewritten to AndroidX in docs
            class MyTest {
                val s: String = "/sdcard/mydir"
            }
            ```

            You can also visit the
            [source code](http://example.com/lint-source-code/tests/com/android/tools/lint/checks/SdCardDetectorTest.java)
            for the unit tests for this check to see additional scenarios.

            The above example was automatically extracted from the first unit test
            found for this lint check, `SdCardDetector.testKotlin`.
            To report a problem with this extracted sample, visit
            https://issuetracker.google.com/issues/new?component=192708.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun testCuratedCodeSample() {
        // Like testCodeSample, but here the test has a special name which indicates
        // that it's curated and in that case we include ALL the test files in the
        // output, along with file names, and all the output from that test.
        // (We also test using empty source and test urls.)
        val sources = temporaryFolder.newFolder("sources")
        val testSources = temporaryFolder.newFolder("test-sources")
        val outputFolder = temporaryFolder.newFolder("report")

        val sourceFile = File(sources, "com/android/tools/lint/checks/StringFormatDetector.kt")
        val testSourceFile = File(testSources, "com/android/tools/lint/checks/StringFormatDetectorTest.java")
        sourceFile.parentFile?.mkdirs()
        testSourceFile.parentFile?.mkdirs()
        sourceFile.createNewFile()
        // TODO: Test Kotlin test as well
        testSourceFile.writeText(
            """
            package com.android.tools.lint.checks;

            import com.android.tools.lint.detector.api.Detector;
            import org.intellij.lang.annotations.Language;

            public class StringFormatDetectorTest extends AbstractCheckTest {
                @Override
                protected Detector getDetector() {
                    return new StringFormatDetector();
                }
                public void testDocumentationExampleStringFormatMatches() {
                    lint().files(
                            xml(
                                    "res/values/formatstrings.xml",
                                    ""
                                            + "<resources>\n"
                                            + "    <string name=\"score\">Score: %1${'$'}d</string>\n"
                                            + "</resources>\n"),
                            java(
                                    ""
                                            + "import android.app.Activity;\n"
                                            + "\n"
                                            + "public class Test extends Activity {\n"
                                            + "    public void test() {\n"
                                            + "        String score = getString(R.string.score);\n"
                                            + "        String output4 = String.format(score, true);  // wrong\n"
                                            + "    }\n"
                                            + "}"),
                            java(""
                                    + "/*HIDE-FROM-DOCUMENTATION*/public class R {\n"
                                    + "    public static class string {\n"
                                    + "        public static final int score = 1;\n"
                                    + "    }\n"
                                    + "}\n")
                    ).run().expect(""
                            + "src/Test.java:6: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) (Did you mean formatting character b?) [StringFormatMatches]\n"
                            + "        String output4 = String.format(score, true);  // wrong\n"
                            + "                                              ~~~~\n"
                            + "    res/values/formatstrings.xml:2: Conflicting argument declaration here\n"
                            + "    <string name=\"score\">Score: %1＄d</string>\n"
                            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings");
                }
            }
            """
        )

        LintIssueDocGenerator.run(
            arrayOf(
                "--md",
                "--no-index",
                "--source-url", "",
                sources.path,
                "--test-url",
                "",
                testSources.path,
                "--issues",
                "StringFormatMatches",
                "--no-suppress-info",
                "--output",
                outputFolder.path
            )
        )
        val files = outputFolder.listFiles()!!.sorted()
        val names = files.joinToString { it.name }
        assertEquals("StringFormatMatches.md", names)
        val text = files[0].readText()
        assertEquals(
            """
            # `String.format` string doesn't match the XML format string

            Id       | `StringFormatMatches`
            ---------|------------------------------------------------------------
            Summary  | `String.format` string doesn't match the XML format string
            Severity | Error
            Category | Correctness: Messages
            Platform | Android
            Vendor   | Android Open Source Project
            Feedback | https://issuetracker.google.com/issues/new?component=192708
            Affects  | Kotlin and Java files and resource files
            Editing  | This check runs on the fly in the IDE editor

            This lint check ensures the following:
            (1) If there are multiple translations of the format string, then all
            translations use the same type for the same numbered arguments
            (2) The usage of the format string in Java is consistent with the format
            string, meaning that the parameter types passed to String.format matches
            those in the format string.

            (##) Example

            Here is an example of lint warnings produced by this check:
            ```text
            src/Test.java:6:Error: Wrong argument type for formatting argument '#1'
            in score: conversion is 'd', received boolean (argument #2 in method
            call) (Did you mean formatting character b?) [StringFormatMatches]

                String output4 = String.format(score, true);  // wrong
                                                      ~~~~

            ```

            Here are the relevant source files:

            `res/values/formatstrings.xml`:
            ```xml
            <resources>
                <string name="score">Score: %1${"$"}d</string>
            </resources>
            ```

            `src/Test.java`:
            ```java
            import android.app.Activity;

            public class Test extends Activity {
                public void test() {
                    String score = getString(R.string.score);
                    String output4 = String.format(score, true);  // wrong
                }
            }
            ```
            """.trimIndent(),
            text
        )
    }
}
