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

@file:Suppress(
    "CastCanBeRemovedNarrowingVariableType", "RemoveRedundantQualifierName", "RemoveExplicitTypeArguments",
    "HasPlatformType", "ConstantConditions", "MemberVisibilityCanBePrivate"
)

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.JavaContext
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAliasTestModeTest {
    private fun alias(@Language("kotlin") source: String): String {
        return alias(kotlin(source))
    }

    private fun alias(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        ImportAliasTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testImportAlias() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg
            import java.io.File

            abstract class MyTest : Number(), Comparable<Number>, MutableCollection<Number> {
                var list: List<String>? = null
                var file: File? = null
                fun test(vararg strings: String?) {
                    println(file)
                    val s = File.separator
                    val o: Any? = null
                    if (o is File) {
                        val f = o
                    }
                }

                var files: Array<File> = emptyArray()
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg
            import java.io.File
            import java.io.File as IMPORT_ALIAS_1_FILE

            abstract class MyTest : Number(), Comparable<Number>, MutableCollection<Number> {
                var list: List<String>? = null
                var file: IMPORT_ALIAS_1_FILE? = null
                fun test(vararg strings: String?) {
                    println(file)
                    val s = IMPORT_ALIAS_1_FILE.separator
                    val o: Any? = null
                    if (o is IMPORT_ALIAS_1_FILE) {
                        val f = o
                    }
                }

                var files: Array<IMPORT_ALIAS_1_FILE> = emptyArray()
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testConstructorCalls() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg
            import android.widget.RemoteViews

            fun test(packageName: String, other: Any) {
                val rv = RemoteViews(packageName, R.layout.test)
                val ov = other as RemoteViews
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg
            import android.widget.RemoteViews
            import android.widget.RemoteViews as IMPORT_ALIAS_1_REMOTEVIEWS

            fun test(packageName: String, other: Any) {
                val rv = IMPORT_ALIAS_1_REMOTEVIEWS(packageName, R.layout.test)
                val ov = other as IMPORT_ALIAS_1_REMOTEVIEWS
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testBasic() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg

            import java.util.concurrent.LinkedBlockingQueue
            import java.util.concurrent.TimeUnit

            class Foo(val requestQueue: LinkedBlockingQueue<String>) {
                fun takeRequest(timeout: Long, unit: TimeUnit) = requestQueue.poll(timeout, unit)
                fun something(): List<String> = listOf<String>("foo", "bar")
                fun takeRequestOk(timeout: Long, unit: TimeUnit): String = requestQueue.poll(timeout, unit)
                fun takeRequestOkTransitive(timeout: Long, unit: TimeUnit) = takeRequestOk(timeout, unit)
                val type = Integer.TYPE
                val typeClz: Class<Int> = Integer.TYPE
                val typeClz2 = typeClz
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg

            import java.util.concurrent.LinkedBlockingQueue
            import java.util.concurrent.TimeUnit
            import java.util.concurrent.LinkedBlockingQueue as IMPORT_ALIAS_1_LINKEDBLOCKINGQUEUE
            import java.util.concurrent.TimeUnit as IMPORT_ALIAS_2_TIMEUNIT

            class Foo(val requestQueue: IMPORT_ALIAS_1_LINKEDBLOCKINGQUEUE<String>) {
                fun takeRequest(timeout: Long, unit: IMPORT_ALIAS_2_TIMEUNIT) = requestQueue.poll(timeout, unit)
                fun something(): List<String> = listOf<String>("foo", "bar")
                fun takeRequestOk(timeout: Long, unit: IMPORT_ALIAS_2_TIMEUNIT): String = requestQueue.poll(timeout, unit)
                fun takeRequestOkTransitive(timeout: Long, unit: IMPORT_ALIAS_2_TIMEUNIT) = takeRequestOk(timeout, unit)
                val type = Integer.TYPE
                val typeClz: Class<Int> = Integer.TYPE
                val typeClz2 = typeClz
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testConstructInner() {
        @Language("kotlin")
        val kotlin = """
            package com.google.android.play.core.splitinstall

            import android.content.res.Configuration as IMPORT_ALIAS_1
            import java.util.Locale as IMPORT_ALIAS_2
            import com.google.android.play.core.splitinstall.SplitInstallRequest as IMPORT_ALIAS_3

            fun example(configuration: IMPORT_ALIAS_1, locale: IMPORT_ALIAS_2) {
                configuration.setLocale(locale)
                IMPORT_ALIAS_3.Builder().addLanguage(locale).build()
            }

            class SplitInstallRequest {
                class Builder {
                    fun addLanguage(locale: IMPORT_ALIAS_2): Builder {
                        return this
                    }
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package com.google.android.play.core.splitinstall

            import android.content.res.Configuration as IMPORT_ALIAS_1
            import java.util.Locale as IMPORT_ALIAS_2
            import com.google.android.play.core.splitinstall.SplitInstallRequest as IMPORT_ALIAS_3

            fun example(configuration: IMPORT_ALIAS_1, locale: IMPORT_ALIAS_2) {
                configuration.setLocale(locale)
                IMPORT_ALIAS_3.Builder().addLanguage(locale).build()
            }

            class SplitInstallRequest {
                class Builder {
                    fun addLanguage(locale: IMPORT_ALIAS_2): Builder {
                        return this
                    }
                }
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testTypeExpressions() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg.application
            import android.content.res.AssetManager

            class ReflectionTestKotlin {
                private fun addAssetPath(assetManager: AssetManager) {
                    val m1 = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                    val m3 = assetManager.javaClass.getDeclaredMethod("invalidateCachesLocked", AssetManager::class.java)
                }
            }
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg.application
            import android.content.res.AssetManager
            import android.content.res.AssetManager as IMPORT_ALIAS_1_ASSETMANAGER

            class ReflectionTestKotlin {
                private fun addAssetPath(assetManager: IMPORT_ALIAS_1_ASSETMANAGER) {
                    val m1 = IMPORT_ALIAS_1_ASSETMANAGER::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                    val m3 = assetManager.javaClass.getDeclaredMethod("invalidateCachesLocked", IMPORT_ALIAS_1_ASSETMANAGER::class.java)
                }
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testMultipleFiles() {
        // Make sure that when we process multiple files, we don't reuse import aliases per package
        // the way we do for type aliases
        val testFile1 = kotlin(
            """
            package test.pkg
            import java.io.File
            class Test1(val activity: android.app.Activity) {
                var file: File? = null
            }
            """
        ).indented()

        val testFile2 = kotlin(
            """
            package test.pkg
            import java.io.File
            class Test2(val file1: File, val file2: File)
            """
        ).indented()

        val testFile3 = kotlin(
            """
            package test.pkg.sub
            import java.io.File
            class Test3 {
                var file: File? = null
            }
            """
        ).indented()

        val sdkHome = TestUtils.getSdk().toFile()
        val testFiles = listOf(testFile1, testFile2, testFile3)
        val map = mutableMapOf<String, String>()
        ImportAliasTestMode().processTestFiles(testFiles, sdkHome) { c: JavaContext, s: String ->
            val name = c.uastFile?.classes?.firstOrNull()?.qualifiedName
            assertNotNull(name)
            map[name!!] = s
        }
        assertEquals("[test.pkg.Test1, test.pkg.Test2, test.pkg.sub.Test3]", map.keys.sorted().toString())
        assertEquals(
            """
            package test.pkg
            import java.io.File
            import java.io.File as IMPORT_ALIAS_1_FILE
            class Test1(val activity: android.app.Activity) {
                var file: IMPORT_ALIAS_1_FILE? = null
            }
            """.trimIndent().trim(),
            map["test.pkg.Test1"]!!.trim()
        )
        assertEquals(
            """
            package test.pkg
            import java.io.File
            import java.io.File as IMPORT_ALIAS_1_FILE
            class Test2(val file1: IMPORT_ALIAS_1_FILE, val file2: IMPORT_ALIAS_1_FILE)
            """.trimIndent().trim(),
            map["test.pkg.Test2"]!!.trim()
        )
        assertEquals(
            """
            package test.pkg.sub
            import java.io.File
            import java.io.File as IMPORT_ALIAS_1_FILE
            class Test3 {
                var file: IMPORT_ALIAS_1_FILE? = null
            }
            """.trimIndent().trim(),
            map["test.pkg.sub.Test3"]!!.trim()
        )
    }

    @Test
    fun testAlertDialog() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog

            class AlertDialogTestKotlin {
                fun test(activity: Activity) {
                    AlertDialog.Builder(activity)
                    val theme = AlertDialog.THEME_TRADITIONAL
                }
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog
            import android.app.Activity as IMPORT_ALIAS_1_ACTIVITY
            import android.app.AlertDialog as IMPORT_ALIAS_2_ALERTDIALOG

            class AlertDialogTestKotlin {
                fun test(activity: IMPORT_ALIAS_1_ACTIVITY) {
                    IMPORT_ALIAS_2_ALERTDIALOG.Builder(activity)
                    val theme = IMPORT_ALIAS_2_ALERTDIALOG.THEME_TRADITIONAL
                }
            }
        """.trimIndent().trim()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased)
    }

    @Test
    fun testTransformMessage() {
        val mode = ImportAliasTestMode()
        assertTrue(
            mode.messagesMatch(
                "This method should be annotated with @ChecksSdkIntAtLeast(api=BUILD.VERSION_CODES.GINGERBREAD)",
                "This method should be annotated with @ChecksSdkIntAtLeast(api=IMPORT_ALIAS_3_BUILD.VERSION_CODES.GINGERBREAD)"
            )
        )
        assertTrue(
            mode.messagesMatch(
                "This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1)",
                "This method should be annotated with @ChecksSdkIntAtLeast(api=IMPORT_ALIAS_3_BUILD.VERSION_CODES.O, lambda=1)"
            )
        )
        assertFalse(
            mode.messagesMatch(
                "This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.O, lambda=1)",
                "This method should be annotated with @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.P, lambda=1)"
            )
        )
    }
}
