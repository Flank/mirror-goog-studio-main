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
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.utils.toSystemLineSeparator
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class LintFixPerformerTest : TestCase() {
    fun check(
        file: File,
        source: String,
        vararg fixes: LintFix,
        expected: String,
        expectedOutput: String? = null
    ) {
        val client = TestLintClient()
        for (fix in fixes) {
            assertTrue(LintFixPerformer.canAutoFix(fix))
        }
        var after: String = source
        var output = ""
        val printStatistics = expectedOutput != null
        val performer = object : LintFixPerformer(client, printStatistics) {
            override fun writeFile(file: PendingEditFile, contents: String) {
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
        performer.fix(file, fixes.toList(), source)
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
}
