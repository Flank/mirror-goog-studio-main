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

class TypeAliasTestModeTest {
    private fun alias(@Language("kotlin") source: String): String {
        return alias(kotlin(source))
    }

    private fun alias(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        TypeAliasTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testTypeAlias() {
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
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            package test.pkg
            import java.io.File

            abstract class MyTest : TYPE_ALIAS_1(), TYPE_ALIAS_2, TYPE_ALIAS_3 {
                var list: TYPE_ALIAS_4 = null
                var file: TYPE_ALIAS_5 = null
                fun test(vararg strings: String?) {
                    println(file)
                    val s = TYPE_ALIAS_7.separator
                    val o: TYPE_ALIAS_8 = null
                    if (o is TYPE_ALIAS_7) {
                        val f = o
                    }
                }

                var files: TYPE_ALIAS_6 = emptyArray()
            }
            typealias TYPE_ALIAS_1 = Number
            typealias TYPE_ALIAS_2 = Comparable<Number>
            typealias TYPE_ALIAS_3 = MutableCollection<Number>
            typealias TYPE_ALIAS_4 = List<String>?
            typealias TYPE_ALIAS_5 = File?
            typealias TYPE_ALIAS_6 = Array<File>
            typealias TYPE_ALIAS_7 = File
            typealias TYPE_ALIAS_8 = Any?
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
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
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            package test.pkg
            import android.widget.RemoteViews

            fun test(packageName: TYPE_ALIAS_1, other: TYPE_ALIAS_2) {
                val rv = RemoteViews(packageName, R.layout.test)
                val ov = other as TYPE_ALIAS_3
            }
            typealias TYPE_ALIAS_1 = String
            typealias TYPE_ALIAS_2 = Any
            typealias TYPE_ALIAS_3 = RemoteViews
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
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
                val size: Int = 42
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            package test.pkg

            import java.util.concurrent.LinkedBlockingQueue
            import java.util.concurrent.TimeUnit

            class Foo(val requestQueue: TYPE_ALIAS_1) {
                fun takeRequest(timeout: TYPE_ALIAS_5, unit: TYPE_ALIAS_6) = requestQueue.poll(timeout, unit)
                fun something(): TYPE_ALIAS_7 = listOf<String>("foo", "bar")
                fun takeRequestOk(timeout: TYPE_ALIAS_5, unit: TYPE_ALIAS_6): TYPE_ALIAS_8 = requestQueue.poll(timeout, unit)
                fun takeRequestOkTransitive(timeout: TYPE_ALIAS_5, unit: TYPE_ALIAS_6) = takeRequestOk(timeout, unit)
                val type = TYPE_ALIAS_2.TYPE
                val typeClz: TYPE_ALIAS_3 = TYPE_ALIAS_2.TYPE
                val typeClz2 = typeClz
                val size: TYPE_ALIAS_4 = 42
            }
            typealias TYPE_ALIAS_1 = LinkedBlockingQueue<String>
            typealias TYPE_ALIAS_2 = Integer
            typealias TYPE_ALIAS_3 = Class<Int>
            typealias TYPE_ALIAS_4 = Int
            typealias TYPE_ALIAS_5 = Long
            typealias TYPE_ALIAS_6 = TimeUnit
            typealias TYPE_ALIAS_7 = List<String>
            typealias TYPE_ALIAS_8 = String
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testConstructInner() {
        @Language("kotlin")
        val kotlin = """
            package com.google.android.play.core.splitinstall

            import android.content.res.Configuration
            import java.util.Locale
            import com.google.android.play.core.splitinstall.SplitInstallRequest

            fun example(configuration: Configuration, locale: Locale) {
                configuration.setLocale(locale)
                SplitInstallRequest.Builder().addLanguage(locale).build()
            }

            class SplitInstallRequest {
                class Builder {
                    fun addLanguage(locale: Locale): Builder {
                        return this
                    }
                }
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            package com.google.android.play.core.splitinstall

            import android.content.res.Configuration
            import java.util.Locale
            import com.google.android.play.core.splitinstall.SplitInstallRequest

            fun example(configuration: TYPE_ALIAS_1, locale: TYPE_ALIAS_2) {
                configuration.setLocale(locale)
                SplitInstallRequest.Builder().addLanguage(locale).build()
            }

            class SplitInstallRequest {
                class Builder {
                    fun addLanguage(locale: TYPE_ALIAS_2): Builder {
                        return this
                    }
                }
            }
            typealias TYPE_ALIAS_1 = Configuration
            typealias TYPE_ALIAS_2 = Locale
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testTypeAliasMultipleFiles() {
        // Make sure that when we process multiple files, we reuse the same type alias
        // names across
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
            class Test2(val file1: File, val file2: File?) {
            }
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
        TypeAliasTestMode().processTestFiles(testFiles, sdkHome) { c: JavaContext, s: String ->
            val name = c.uastFile?.classes?.firstOrNull()?.qualifiedName
            assertNotNull(name)
            map[name!!] = s
        }
        assertEquals("[test.pkg.Test1, test.pkg.Test2, test.pkg.sub.Test3]", map.keys.sorted().toString())
        assertEquals(
            """
            package test.pkg
            import java.io.File
            class Test1(val activity: android.app.Activity) {
                var file: TYPE_ALIAS_1 = null
            }
            typealias TYPE_ALIAS_1 = File?
            """.trimIndent(),
            map["test.pkg.Test1"]!!.trim()
        )
        assertEquals(
            """
            package test.pkg
            import java.io.File
            class Test2(val file1: TYPE_ALIAS_2, val file2: TYPE_ALIAS_1) {
            }
            typealias TYPE_ALIAS_2 = File
            """.trimIndent(),
            map["test.pkg.Test2"]!!.trim()
        )
        // Different package: must create new alias for File
        assertEquals(
            """
            package test.pkg.sub
            import java.io.File
            class Test3 {
                var file: TYPE_ALIAS_3 = null
            }
            typealias TYPE_ALIAS_3 = File?
            """.trimIndent(),
            map["test.pkg.sub.Test3"]!!.trim()
        )
    }

    @Test
    fun testAlertDialog() {
        @Language("kotlin")
        val kotlin = """
            @file:Suppress("unused", "UNUSED_PARAMETER")
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog
            import android.content.ContentResolver
            import android.provider.MediaStore
            import org.w3c.dom.DOMErrorHandler

            class AlertDialogTestKotlin {
                fun test(activity: Activity) {
                    AlertDialog.Builder(activity)
                    val theme = AlertDialog.THEME_TRADITIONAL
                }
            }
            class MediaStoreVideoUsage {
                fun example(contentResolver: ContentResolver): Unit {
                     contentResolver.query(MediaStore.Video.Media.INTERNAL_CONTENT_URI, null, null, null, null)
                }
            }
            fun test() {
                val clz = DOMErrorHandler::class // API 8
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            @file:Suppress("unused", "UNUSED_PARAMETER")
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog
            import android.content.ContentResolver
            import android.provider.MediaStore
            import org.w3c.dom.DOMErrorHandler

            class AlertDialogTestKotlin {
                fun test(activity: TYPE_ALIAS_1) {
                    AlertDialog.Builder(activity)
                    val theme = TYPE_ALIAS_2.THEME_TRADITIONAL
                }
            }
            class MediaStoreVideoUsage {
                fun example(contentResolver: TYPE_ALIAS_4): TYPE_ALIAS_3 {
                     contentResolver.query(MediaStore.Video.Media.INTERNAL_CONTENT_URI, null, null, null, null)
                }
            }
            fun test() {
                val clz = DOMErrorHandler::class // API 8
            }
            typealias TYPE_ALIAS_1 = Activity
            typealias TYPE_ALIAS_2 = AlertDialog
            typealias TYPE_ALIAS_3 = Unit
            typealias TYPE_ALIAS_4 = ContentResolver
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testWildcards() {
        @Language("kotlin")
        val kotlin = """
            import java.util.HashMap

            fun test() {
                val x = HashMap<String, String>()
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            import java.util.HashMap

            fun test() {
                val x = HashMap<String, String>()
            }
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testGridLayout() {
        @Language("kotlin")
        val kotlin = """
            import android.graphics.drawable.BitmapDrawable
            import android.widget.GridLayout

            fun test(resources: android.content.res.Resources) {
                val layout = GridLayout(null) // requires API 14
                val drawable = BitmapDrawable(resources) // requires API 4
            }
        """.trimIndent()

        @Language("kotlin")
        val expected = """
            import android.graphics.drawable.BitmapDrawable
            import android.widget.GridLayout

            fun test(resources: android.content.res.Resources) {
                val layout = GridLayout(null) // requires API 14
                val drawable = BitmapDrawable(resources) // requires API 4
            }
        """.trimIndent()

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testGenerics() {
        @Language("kotlin")
        val kotlin = """
            interface TypeWithGenerics<T>
            fun <T> functionReturningTypeWithGenerics(): TypeWithGenerics<T> = error("stub")
            // Nest various PSI types (PsiArrayType, PsiWildardType, etc) to make sure we recurse properly
            fun <T> functionReturningTypeWithGenerics2(): Array<Map<in String, TypeWithGenerics<T>>> = error("stub")
        """.trimIndent()

        @Suppress("UnnecessaryVariable")
        @Language("kotlin")
        val expected = kotlin

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
    }

    @Test
    fun testJvmStatic() {
        @Language("kotlin")
        val kotlin = """
            annotation class MyAnnotation
            class Test {
                companion object {
                    @MyAnnotation @JvmStatic fun test() {
                    }
                }
            }
        """.trimIndent()

        @Suppress("UnnecessaryVariable")
        @Language("kotlin")
        val expected = kotlin

        val aliased = alias(kotlin)
        assertEquals(expected, aliased.trim())
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
