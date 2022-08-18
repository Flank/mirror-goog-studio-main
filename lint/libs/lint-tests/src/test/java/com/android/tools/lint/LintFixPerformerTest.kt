/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.checks.LintDetectorDetector
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.utils.toSystemLineSeparator
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class LintFixPerformerTest : TestCase() {
    @Suppress("LintImplTextFormat")
    fun check(
        file: File,
        source: String,
        vararg fixes: LintFix,
        expected: String,
        expectedOutput: String? = null,
        expectedFailure: String? = null,
        includeMarkers: Boolean = false
    ) {
        val client = TestLintClient()
        for (fix in fixes) {
            assertTrue(LintFixPerformer.canAutoFix(fix))
        }
        var after: String = source
        var output = ""
        val printStatistics = expectedOutput != null
        val performer = object : LintFixPerformer(client, printStatistics, includeMarkers = includeMarkers) {
            override fun writeFile(pendingFile: PendingEditFile, contents: String) {
                after = contents
            }

            override fun printStatistics(
                writer: PrintWriter,
                editMap: MutableMap<String, Int>,
                appliedEditCount: Int,
                editedFileCount: Int
            ) {
                val stringWriter = StringWriter()
                val collector = PrintWriter(stringWriter)
                super.printStatistics(collector, editMap, appliedEditCount, editedFileCount)
                output = stringWriter.toString()
            }
        }
        val testIncident = Incident().apply {
            issue =
                Issue.create(
                    "_FixPerformerTestIssue",
                    "Sample",
                    "Sample",
                    Category.CORRECTNESS, 5, Severity.WARNING,
                    Implementation(LintDetectorDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
                )
        }
        try {
            performer.fix(file, testIncident, fixes.toList(), source)
        } catch (e: Throwable) {
            if (expectedFailure != null) {
                assertEquals(expectedFailure.trimIndent().trim(), e.message)
            } else {
                throw e
            }
        }
        assertEquals(expected.trimIndent().trim(), after.trim())
        if (expectedOutput != null) {
            assertEquals(
                expectedOutput.trimIndent().trim().toSystemLineSeparator(),
                output.trim()
            )
        }
    }

    fun testSingleReplace() {
        val file = File("bogus.txt")
        val source =
            """
            First line.
            Second line.
            Third line.
            """.trimIndent()

        val range = Location.create(file, source, 0, source.length)
        val fix =
            fix().replace().text("Second").range(range).with("2nd").autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            First line.
            2nd line.
            Third line.""",
            expectedOutput = "Applied 1 edits across 1 files for this fix: Replace with 2nd"
        )
    }

    fun testInvalidTextReplaceFix() {
        val file = File("source.txt")
        val source =
            """
            First line.
            """.trimIndent()

        val range = Location.create(file, source, 0, source.length)
        val fix =
            fix().replace().text("Not Present").range(range).with("2nd").autoFix().build()
        check(
            file, source, fix,
            expected = "First line.",
            expectedFailure = """
                Did not find "Not Present" in "First line." in source.txt as suggested in the quickfix.

                Consider calling ReplaceStringBuilder#range() to set a larger range to
                search than the default highlight range.

                (This fix is associated with the issue id `_FixPerformerTestIssue`,
                reported via com.android.tools.lint.checks.LintDetectorDetector.)
                """
        )
    }

    fun testInvalidRegexReplaceFix() {
        val file = File("source.txt")
        val source = "First line."
        val range = Location.create(file, source, 0, source.length)
        val fix =
            fix().replace().pattern("(Not Present)").range(range).with("2nd").autoFix().build()
        check(
            file, source, fix,
            expected = "First line.",
            expectedFailure = """
                Did not match pattern "(Not Present)" in "First line." in source.txt as suggested in the quickfix.

                (This fix is associated with the issue id `_FixPerformerTestIssue`,
                reported via com.android.tools.lint.checks.LintDetectorDetector.)
                """
        )
    }

    fun testLineCleanup() {
        // Regression test for b/185853711
        val file = File("Test.java")
        val source =
            """
            import android.util.Log;
            public class Test {
            }
            """.trimIndent()

        val startOffset = source.indexOf("public")
        val range = Location.create(file, source, startOffset, startOffset + "public".length)
        val fix = fix().replace().range(range).with("").autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            import android.util.Log;
             class Test {
            }
            """,
            expectedOutput = "Applied 1 edits across 1 files for this fix: Delete"
        )
    }

    fun testMultipleReplaces() {
        // Ensures we reorder edits correctly
        val file = File("bogus.txt")
        val source =
            """
            First line.
            Second line.
            Third line.
            """.trimIndent()

        val range = Location.create(file, source, 0, source.length)
        val fix1 =
            fix().replace().text("Third").range(range).with("3rd").autoFix().build()
        val fix2 =
            fix().replace().text("First").range(range).with("1st").autoFix().build()
        val fix3 =
            fix().replace().text("Second").range(range).with("2nd").autoFix().build()
        check(
            file, source, fix1, fix2, fix3,
            expected =
            """
            1st line.
            2nd line.
            3rd line.""",
            expectedOutput =
            """
            Applied 3 edits across 1 files
            1: Replace with 3rd
            1: Replace with 2nd
            1: Replace with 1st
            """
        )
    }

    fun testXmlSetAttribute() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root>
                <element1 attribute1="value1" />
                <element2 attribute1="value1" attribute2="value2"/>
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("attribute1"), source.length)
        val fix =
            fix().set(SdkConstants.ANDROID_URI, "new_attribute", "new value")
                .range(range).autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 attribute1="value1" android:new_attribute="new value"  />
                <element2 attribute1="value1" attribute2="value2"/>
            </root>
            """
        )
    }

    fun testXmlSetAttributeOrder1() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:layout_width="wrap_content" android:width="foo" />
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("element1"), source.length)
        val fix =
            fix().set(SdkConstants.ANDROID_URI, "layout_height", "wrap_content")
                .range(range).autoFix().build()
        check(
            file, source, fix,
            expected =

            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:layout_width="wrap_content" android:layout_height="wrap_content" android:width="foo" />
            </root>
            """
        )
    }

    fun testXmlSetAttributeOrder2() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:layout_width="wrap_content" android:width="foo" />
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("element1"), source.length)
        val fix =
            fix().set(SdkConstants.ANDROID_URI, "id", "@+id/my_id")
                .range(range).autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:id="@+id/my_id" android:layout_width="wrap_content" android:width="foo" />
            </root>
            """
        )
    }

    fun testXmlSetAttributeOrder3() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:layout_width="wrap_content" android:width="foo" />
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("element1"), source.length)
        val fix =
            fix().set(SdkConstants.ANDROID_URI, "z-order", "5")
                .range(range).autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1 android:layout_width="wrap_content" android:width="foo" android:z-order="5"  />
            </root>
            """
        )
    }

    fun testXmlDeleteAttribute() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root>
                <element1 attribute1="value1" />
                <element2 attribute1="value1" attribute2="value2"/>
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("attribute2"), source.length)
        val fix =
            fix().unset(null, "attribute2")
                .range(range).autoFix().build()
        check(
            file, source, fix,
            expected =
            """
            <root>
                <element1 attribute1="value1" />
                <element2 attribute1="value1" />
            </root>
            """
        )
    }

    fun testXmlComposite() {
        val file = File("bogus.txt")
        @Language("XML")
        val source =
            """
            <root>
                <element1 attribute1="value1" />
                <element2 attribute1="value1" attribute2="value2"/>
            </root>
            """.trimIndent()

        val range = Location.create(file, source, source.indexOf("attribute1"), source.length)
        val unsetFix = fix().unset(null, "attribute1")
            .range(range).build()
        val setFix =
            fix().set(SdkConstants.ANDROID_URI, "new_attribute", "new value")
                .range(range).build()

        val fix = fix().composite(setFix, unsetFix).autoFix()
        check(
            file, source, fix,
            expected =
            """
            <root xmlns:android="http://schemas.android.com/apk/res/android">
                <element1  android:new_attribute="new value"  />
                <element2 attribute1="value1" attribute2="value2"/>
            </root>
            """
        )
    }

    private fun fix() = LintFix.create()

    fun testAttributeSorting() {
        val attributes = listOf(
            "foo",
            "bar",
            "layout_foo",
            "layout_bar",
            "layout_width",
            "layout_height",
            "id",
            "name"
        )
        val sorted = attributes.sortedWith(
            Comparator { a, b ->
                LintFixPerformer.compareAttributeNames(
                    a,
                    b
                )
            }
        )
        assertEquals(
            "[id, name, layout_width, layout_height, layout_bar, layout_foo, bar, foo]",
            sorted.toString()
        )
    }

    fun testShortenNames() {
        // Regression test for b/https://issuetracker.google.com/241573146
        val file = File("Test.java")
        @Language("java")
        val source =
            """
            package test.pkg;
            import android.graphics.drawable.Drawable;
            import android.graphics.Outline;

            class Test {
                static void getOutline() {
                }
            }
            """.trimIndent()

        val range = Location.create(file, source, 0, source.length)
        val fix =
            fix().replace()
                .text("()")
                .with("(android.graphics.drawable.Drawable drawable, android.graphics.Outline outline)")
                .shortenNames()
                .autoFix()
                .range(range)
                .build()
        check(
            file, source, fix,
            expected =
            """
            package test.pkg;
            import android.graphics.drawable.Drawable;
            import android.graphics.Outline;

            class Test {
                static void getOutline(Drawable drawable, Outline outline) {
                }
            }
            """,
            expectedOutput = "Applied 1 edits across 1 files for this fix: Replace with (android.graphics.drawable.Drawable drawable, android.graphics.Outline outline)",
            includeMarkers = true
        )
    }
}
