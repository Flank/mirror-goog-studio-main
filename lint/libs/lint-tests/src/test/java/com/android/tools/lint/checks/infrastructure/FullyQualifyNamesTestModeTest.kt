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
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Test

class FullyQualifyNamesTestModeTest {
    private fun expandKotlin(@Language("kotlin") source: String): String {
        return expand(kotlin(source))
    }

    private fun expandJava(@Language("java") source: String): String {
        return expand(java(source))
    }

    private fun expand(testFile: TestFile): String {
        val sdkHome = TestUtils.getSdk().toFile()
        var source = testFile.contents
        FullyQualifyNamesTestMode().processTestFiles(listOf(testFile), sdkHome) { _, s -> source = s }
        return source
    }

    @Test
    fun testBasic() {
        @Language("java")
        val java = """
            package test.pkg;
            import java.io.File;
            import java.util.Collection;
            import java.util.List;

            public abstract class MyTest extends Number implements Comparable<Number>, Collection<Number> {
                List<String> list;
                File file;
                public void test(String... strings) {
                    System.out.println(file);
                    String s = File.separator;
                    Object o = null;
                    if (o instanceof File) {
                        File f = (File)o;
                    }
                }
                File[] files;
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            package test.pkg;
            import java.io.File;
            import java.util.Collection;
            import java.util.List;

            public abstract class MyTest extends java.lang.Number implements java.lang.Comparable<Number>, java.util.Collection<Number> {
                java.util.List<String> list;
                java.io.File file;
                public void test(java.lang.String... strings) {
                    java.lang.System.out.println(file);
                    java.lang.String s = java.io.File.separator;
                    java.lang.Object o = null;
                    if (o instanceof java.io.File) {
                        java.io.File f = (java.io.File)o;
                    }
                }
                java.io.File[] files;
            }
        """.trimIndent().trim()

        val expanded = expandJava(java)
        assertEquals(expected, expanded)
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

            fun test(packageName: String, other: Any) {
                val rv = android.widget.RemoteViews(packageName, R.layout.test)
                val ov = other as android.widget.RemoteViews
            }
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testCornerCase() {
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

            class Foo(val requestQueue: java.util.concurrent.LinkedBlockingQueue<String>) {
                fun takeRequest(timeout: Long, unit: java.util.concurrent.TimeUnit) = requestQueue.poll(timeout, unit)
                fun something(): List<String> = listOf<String>("foo", "bar")
                fun takeRequestOk(timeout: Long, unit: java.util.concurrent.TimeUnit): String = requestQueue.poll(timeout, unit)
                fun takeRequestOkTransitive(timeout: Long, unit: java.util.concurrent.TimeUnit) = takeRequestOk(timeout, unit)
                val type = Integer.TYPE
                val typeClz: java.lang.Class<Int> = Integer.TYPE
                val typeClz2 = typeClz
            }
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testCornerCase2() {
        // Visiting types in this AST results in a type reference that has a null
        // containingFile; regression test to make sure we handle this gracefully
        @Language("java")
        val java = """
            package test.pkg;

            import android.location.LocationManager;

            @SuppressWarnings({"FieldCanBeLocal", "unused"})
            public class ApiDetectorTest2 {
                public enum HealthChangeHandler {
                    LOCATION_MODE_CHANGED(LocationManager.MODE_CHANGED_ACTION) {
                        @Override public String toString() { return super.toString(); }
                    };

                    HealthChangeHandler(String mode) {
                    }
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            package test.pkg;

            import android.location.LocationManager;

            @java.lang.SuppressWarnings({"FieldCanBeLocal", "unused"})
            public class ApiDetectorTest2 {
                public enum HealthChangeHandler {
                    LOCATION_MODE_CHANGED(android.location.LocationManager.MODE_CHANGED_ACTION) {
                        @Override public String toString() { return super.toString(); }
                    };

                    HealthChangeHandler(String mode) {
                    }
                }
            }
        """.trimIndent().trim()

        val expanded = expandKotlin(java)
        assertEquals(expected, expanded)
    }

    @Test
    fun testUnionTypes() {
        @Language("java")
        val java = """
            package test.pkg;
            import android.hardware.camera2.CameraAccessException;
            import android.media.MediaDrmResetException;
            @SuppressWarnings({"unused", "WeakerAccess"})
            public class CatchTest {
                public class C4 {
                    public void test() {
                        try {
                            thrower();
                        } catch (CameraAccessException | MediaDrmResetException e) {
                            logger(e.toString());
                        }
                    }
                }

                private void logger(String e) {
                }

                public void thrower() throws CameraAccessException, MediaDrmResetException {
                    throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
                }
            }
        """.trimIndent().trim()

        @Language("java")
        val expected = """
            package test.pkg;
            import android.hardware.camera2.CameraAccessException;
            import android.media.MediaDrmResetException;
            @java.lang.SuppressWarnings({"unused", "WeakerAccess"})
            public class CatchTest {
                public class C4 {
                    public void test() {
                        try {
                            thrower();
                        } catch (CameraAccessException | MediaDrmResetException e) {
                            logger(e.toString());
                        }
                    }
                }

                private void logger(String e) {
                }

                public void thrower() throws CameraAccessException, MediaDrmResetException {
                    throw new CameraAccessException(android.hardware.camera2.CameraAccessException.CAMERA_ERROR);
                }
            }
        """.trimIndent().trim()

        val expanded = expandKotlin(java)
        assertEquals(expected, expanded)
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
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package com.google.android.play.core.splitinstall

            import android.content.res.Configuration
            import java.util.Locale
            import com.google.android.play.core.splitinstall.SplitInstallRequest

            fun example(configuration: android.content.res.Configuration, locale: java.util.Locale) {
                configuration.setLocale(locale)
                com.google.android.play.core.splitinstall.SplitInstallRequest.Builder().addLanguage(locale).build()
            }

            class SplitInstallRequest {
                class Builder {
                    fun addLanguage(locale: java.util.Locale): com.google.android.play.core.splitinstall.SplitInstallRequest.Builder {
                        return this
                    }
                }
            }
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testImportAlias() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg

            import android.app.Activity
            import android.os.Bundle
            import test.pkg.R as RC
            import test.pkg.LongerName as AliasIsLongerThanName
            import test.pkg.ShortName as SN

            class MainIsUsed : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(RC.layout.main)
                    println(AliasIsLongerThanName())
                    println(SN().toString())
                }
            }
            class R {
                class layout {
                    val main = 1
                }
            }
            class LongerName
            class ShortName
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg

            import android.app.Activity
            import android.os.Bundle
            import test.pkg.R as RC
            import test.pkg.LongerName as AliasIsLongerThanName
            import test.pkg.ShortName as SN

            class MainIsUsed : android.app.Activity() {
                override fun onCreate(savedInstanceState: android.os.Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(test.pkg.R.layout.main)
                    println(test.pkg.LongerName())
                    println(test.pkg.ShortName().toString())
                }
            }
            class R {
                class layout {
                    val main = 1
                }
            }
            class LongerName
            class ShortName
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testExtends() {
        @Language("kotlin")
        val kotlin = """
            package test.pkg
            abstract class NotFragment : android.view.View(null)
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            package test.pkg
            abstract class NotFragment : android.view.View(null)
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testAnnotationKotlin() {
        @Language("kotlin")
        val kotlin = """
            import android.annotation.SuppressLint

            @SuppressLint("test")
            class Name1

            @android.annotation.SuppressLint("test")
            class Name2
        """.trimIndent().trim()

        @Language("kotlin")
        val expected = """
            import android.annotation.SuppressLint

            @android.annotation.SuppressLint("test")
            class Name1

            @android.annotation.SuppressLint("test")
            class Name2
        """.trimIndent().trim()

        val expanded = expandKotlin(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testAnnotationsJava() {
        @Language("java")
        val java = """
            package test.pkg;

            import android.view.MenuItem;

            public class ActionTest1 {
                @android.annotation.SuppressLint("AlwaysShowAction")
                public void foo() {
                    System.out.println(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
            }
        """.trimIndent()

        @Language("java")
        val expected = """
            package test.pkg;

            import android.view.MenuItem;

            public class ActionTest1 {
                @android.annotation.SuppressLint("AlwaysShowAction")
                public void foo() {
                    java.lang.System.out.println(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
            }
        """.trimIndent()

        val expanded = expandJava(java)
        assertEquals(expected, expanded)
    }

    @Test
    fun testAlertDialog() {
        @Language("kotlin")
        val kotlin = kotlin(
            """
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog

            class AlertDialogTestKotlin {
                fun test(activity: Activity) {
                    AlertDialog.Builder(activity)
                    val theme = AlertDialog.THEME_TRADITIONAL
                }
            }
            """
        ).indented()

        @Language("kotlin")
        val expected = """
            package test.pkg

            import android.app.Activity
            import android.app.AlertDialog

            class AlertDialogTestKotlin {
                fun test(activity: android.app.Activity) {
                    android.app.AlertDialog.Builder(activity)
                    val theme = android.app.AlertDialog.THEME_TRADITIONAL
                }
            }
        """.trimIndent().trim()

        val expanded = expand(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testVariableConflict() {
        // Makes sure that if there's a local variable which is the same as the imported symbol's
        // first name segment, we don't expand, since the local variable "wins"
        @Language("kotlin")
        val kotlin = kotlin(
            """
            package test.pkg

            class Test {
                operator fun get(key: Int, key2: A) {}
            }

            open class A
            class B : A()

            fun test(test: Test, color: Int) {
                val b = B()
                test[color, b]
            }
            """
        ).indented()

        @Language("kotlin")
        val expected = """
            package test.pkg

            class Test {
                operator fun get(key: Int, key2: test.pkg.A) {}
            }

            open class A
            class B : test.pkg.A()

            fun test(test: Test, color: Int) {
                val b = B()
                test[color, b]
            }
        """.trimIndent().trim()

        val expanded = expand(kotlin)
        assertEquals(expected, expanded)
    }

    @Test
    fun testTransformMessage() {
        val mode = FullyQualifyNamesTestMode()
        assertEquals(
            "This field should be annotated with ChecksSdkIntAtLeast(api=LOLLIPOP)",
            mode.transformMessage("This field should be annotated with ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.LOLLIPOP)")
        )
        assertEquals(
            "The id duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]",
            mode.transformMessage("The id test.pkg.R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]")
        )
        assertEquals(
            mode.transformMessage("The id test.pkg.R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]"),
            mode.transformMessage("The id R.id.duplicated has already been looked up in this method; possible cut & paste error? [CutPasteId]")
        )
    }
}
