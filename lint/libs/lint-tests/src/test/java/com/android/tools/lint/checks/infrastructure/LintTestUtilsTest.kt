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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.checks.SdCardDetector
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.AssertionError

@Suppress("LintDocExample")
class LintTestUtilsTest {
    @get:Rule
    var tempFolder = TemporaryFolder()

    @Test
    fun testOk() {
        val list = listOf("def", "abc", "ghijklm")
        checkTransitiveComparator(list)
    }

    @Test
    fun testNotTransitive() {
        class Test(val v: Int) : Comparable<Test> {
            override fun compareTo(other: Test): Int {
                return v.compareTo(-other.v)
            }

            override fun toString(): String {
                return v.toString()
            }
        }

        val list = listOf(Test(1), Test(2), Test(3), Test(4))
        try {
            checkTransitiveComparator(list)
        } catch (a: AssertionError) {
            assertEquals("x.compareTo(y) != -y.compareTo(x) for x=1 and y=1", a.message)
        }
    }

    @Test
    fun testNotTransitiveComparator() {
        val comparator = Comparator<Int> { o1, o2 -> o1.compareTo(-o2) }
        val list = listOf(1, 2, 3, 4)
        try {
            checkTransitiveComparator(list, comparator)
        } catch (a: AssertionError) {
            assertEquals("x.compareTo(y) != -y.compareTo(x) for x=1 and y=1", a.message)
        }
    }

    @Test
    fun testDos2Unix() {
        assertEquals("", "".dos2unix())
        assertEquals(";", ";".dos2unix())
        assertEquals("/", "\\".dos2unix())
        assertEquals("\n", "\r\n".dos2unix())
        assertEquals("This is a test", "This is a test".dos2unix())
        assertEquals("This is a test\n", "This is a test\n".dos2unix())
        assertEquals("This is a test\n", "This is a test\r\n".dos2unix())
        assertEquals("This is a path:\nC:/a/b/c.kt\n", "This is a path:\nC:\\a\\b\\c.kt\r\n".dos2unix())
        assertEquals(
            "This is a path separator:\nsrc/java/main:src/java/test\n",
            "This is a path separator:\nsrc\\java\\main;src\\java\\test\r\n".dos2unix()
        )
        assertEquals("This is &quot;XML&QUOT; &lt; and &#9029;.", "This is &quot;XML&QUOT; &lt; and &#9029;.".dos2unix())
        assertEquals("First, a test; ", "First, a test; ".dos2unix())
        assertEquals("style=\"display: block;\"", "style=\"display: block;\"".dos2unix())
    }

    @Test
    fun testRunOnSources() {
        val dir = tempFolder.newFolder("src", "some", "sub", "dir")
        dir.mkdirs()
        @Language("kotlin") val code = """const val path = "/sdcard/test""""
        File(dir, "kotlin1.kt").writeText(code)
        File(dir, "kotlin2.kt").writeText(code)
        File(dir, "kotlin3.kt").writeText(code)

        runOnSources(
            dir = tempFolder.root,
            lintFactory = { lint().allowMissingSdk().issues(SdCardDetector.ISSUE) }, // small, but just making sure we're actually slicing up the data in sorted order above
            """
            src/some/sub/dir/kotlin1.kt:1: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
            const val path = "/sdcard/test"
                              ~~~~~~~~~~~~
            src/some/sub/dir/kotlin2.kt:1: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
            const val path = "/sdcard/test"
                              ~~~~~~~~~~~~
            0 errors, 2 warnings

            src/some/sub/dir/kotlin3.kt:1: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
            const val path = "/sdcard/test"
                              ~~~~~~~~~~~~
            0 errors, 1 warnings
            """.trimIndent(),
            bucketSize = 2,
            absolutePaths = false
        )
    }
}
