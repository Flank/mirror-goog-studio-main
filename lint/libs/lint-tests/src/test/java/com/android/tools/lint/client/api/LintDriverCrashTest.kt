/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.resources.ResourceFolderType
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestResultChecker
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.truth.Truth.assertThat
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

class LintDriverCrashTest : AbstractCheckTest() {
    fun testLintDriverError() {
        // Regression test for 34248502
        lint().files(
            xml("res/layout/foo.xml", "<LinearLayout/>"),
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(CrashingDetector.CRASHING_ISSUE)
            .run()
            // Checking for manual substrings instead of doing an actual equals check
            // since the stacktrace contains a number of specific line numbers from
            // the lint implementation, including this test, which keeps shifting every
            // time there is an edit
            .check(
                TestResultChecker {
                    assertThat(it).contains("Foo.java: Error: Unexpected failure during lint analysis of Foo.java (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(
                        it.contains(
                            """
                    The crash seems to involve the detector com.android.tools.lint.client.api.LintDriverCrashTest＄CrashingDetector.
                    You can try disabling it with something like this:
                        android {
                            lintOptions {
                                disable "_TestCrash"
                            }
                        }
                            """.trimIndent()
                        )
                    )
                    assertThat(it).contains("You can set environment variable LINT_PRINT_STACKTRACE=true to dump a full stacktrace to stdout. [LintError]")
                    assertThat(it).contains("ArithmeticException:LintDriverCrashTest＄CrashingDetector＄createUastHandler＄1.visitFile(LintDriverCrashTest.kt:")
                    assertThat(it).contains("1 errors, 0 warnings")
                }
            )
        LintDriver.clearCrashCount()
    }

    fun testLinkageError() {
        // Regression test for 34248502
        lint().files(
            java(
                """
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """
            )
        )
            .allowSystemErrors(true)
            .allowExceptions(true)
            .issues(LinkageErrorDetector.LINKAGE_ERROR)
            .run()
            .expect(
                """
                    src/test/pkg/Foo.java: Error: Lint crashed because it is being invoked with the wrong version of Guava
                    (the Android version instead of the JRE version, which is required in the
                    Gradle plugin).

                    This usually happens when projects incorrectly install a dependency resolution
                    strategy in all configurations instead of just the compile and run
                    configurations.

                    See https://issuetracker.google.com/71991293 for more information and the
                    proper way to install a dependency resolution strategy.

                    (Note that this breaks a lot of lint analysis so this report is incomplete.) [LintError]
                    1 errors, 0 warnings"""
            )
        LintDriver.clearCrashCount()
    }

    fun testUnitTestErrors() {
        // Regression test for https://issuetracker.google.com/74058591
        // Make sure the test itself fails with an error, not just an exception pretty printed
        // into the output as used to be the case
        try {
            lint().files(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings("ALL") class Foo {
                        }
                        """
                )
            )
                .allowSystemErrors(true)
                .issues(LinkageErrorDetector.LINKAGE_ERROR)
                .run()
                .expect(
                    "<doesn't matter, we shouldn't get this far>"
                )
            fail("Expected LinkageError to be thrown")
        } catch (e: LinkageError) {
            // OK
            LintDriver.clearCrashCount()
        }
    }

    override fun getIssues(): List<Issue> = listOf(
        CrashingDetector.CRASHING_ISSUE,
        DisposedThrowingDetector.DISPOSED_ISSUE, LinkageErrorDetector.LINKAGE_ERROR
    )

    override fun getDetector(): Detector = CrashingDetector()

    class CrashingDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler? =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    @Suppress("DIVISION_BY_ZERO", "UNUSED_VARIABLE") // Intentional crash
                    val x = 1 / 0
                    super.visitFile(node)
                }
            }

        companion object {
            val CRASHING_ISSUE = Issue
                .create(
                    "_TestCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(CrashingDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }

    // Regression test for https://issuetracker.google.com/123835101

    fun testHalfUppercaseColor2() {
        lint()
            .files(
                xml(
                    "res/drawable/drawable.xml",
                    """
          <vector xmlns:android="http://schemas.android.com/apk/res/android"
              android:height="800dp"
              android:viewportHeight="800"
              android:viewportWidth="800"
              android:width="800dp">
            <path
                android:fillColor="#ffe000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#fff000"
                android:pathData="M644.161,530.032 L644.161,529.032
          C644.161,522.469,638.821,517.129,632.258,517.129 L24.807,517.129 L24.807,282.871
          L775.194,282.871 L775.194,517.129 L683.872,517.129
          C677.309,517.129,671.969,522.469,671.969,529.032 L671.969,530.032
          L644.161,530.032 Z"/>
            <path
                android:fillColor="#ffe000"
                android:pathData="M683.871,516.129 L774.193,516.129 L774.193,283.871 L25.807,283.871
          L25.807,516.129 L632.258,516.129
          C639.384,516.129,645.161,521.906,645.161,529.032 L670.968,529.032
          C670.968,521.906,676.745,516.129,683.871,516.129 Z"/>
          </vector>"""
                ).indented()
            )
            .issues(ColorCasingDetector.ISSUE_COLOR_CASING)
            .run()
            .expect(
                """
                res/drawable/drawable.xml:7: Warning: Should be using uppercase letters [ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                res/drawable/drawable.xml:14: Warning: Should be using uppercase letters [ColorCasing]
                      android:fillColor="#fff000"
                                         ~~~~~~~
                res/drawable/drawable.xml:21: Warning: Should be using uppercase letters [ColorCasing]
                      android:fillColor="#ffe000"
                                         ~~~~~~~
                0 errors, 3 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for res/drawable/drawable.xml line 7: Convert to uppercase:
                @@ -7 +7
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                Fix for res/drawable/drawable.xml line 14: Convert to uppercase:
                @@ -14 +14
                -       android:fillColor="#fff000"
                +       android:fillColor="#FFF000"
                Fix for res/drawable/drawable.xml line 21: Convert to uppercase:
                @@ -21 +21
                -       android:fillColor="#ffe000"
                +       android:fillColor="#FFE000"
                """
            )
    }

    class ColorCasingDetector : ResourceXmlDetector() {
        override fun appliesTo(folderType: ResourceFolderType) = true
        override fun getApplicableElements() = ALL
        override fun visitElement(context: XmlContext, element: Element) {
            element.attributes()
                .filter { it.nodeValue.matches(COLOR_REGEX) }
                .filter { it.nodeValue.any { it.isLowerCase() } }
                .forEach {
                    val fix = fix()
                        .name("Convert to uppercase")
                        .replace()
                        // .range(context.getValueLocation(it as Attr))
                        .text(it.nodeValue)
                        .with(it.nodeValue.toUpperCase(Locale.US))
                        .autoFix()
                        .build()

                    context.report(
                        ISSUE_COLOR_CASING, it, context.getValueLocation(it as Attr),
                        "Should be using uppercase letters", fix
                    )
                }
        }

        companion object {
            val COLOR_REGEX = Regex("#[a-fA-F\\d]{3,8}")
            val ISSUE_COLOR_CASING = Issue.create(
                "ColorCasing",
                "Raw colors should be defined with uppercase letters.",
                "Colors should have uppercase letters. #FF0099 is valid while #ff0099 isn't since the ff should be written in uppercase.",
                Category.CORRECTNESS, 5, Severity.WARNING,
                Implementation(ColorCasingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
            )
            internal fun Node.attributes() = (0 until attributes.length).map { attributes.item(it) }
        }
    }

    class DisposedThrowingDetector : LayoutDetector(), XmlScanner {

        override fun getApplicableElements(): Collection<String> {
            return arrayListOf("LinearLayout")
        }

        override fun visitElement(context: XmlContext, element: Element) {
            throw AssertionError("Already disposed: " + this)
        }

        companion object {
            val DISPOSED_ISSUE = Issue.create(
                "_TestDisposed", "test", "test", Category.LINT,
                10, Severity.FATAL,
                Implementation(
                    DisposedThrowingDetector::class.java,
                    Scope.RESOURCE_FILE_SCOPE
                )
            )
        }
    }

    class LinkageErrorDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
            listOf(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler? =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    throw LinkageError(
                        "loader constraint violation: when resolving field " +
                            "\"QUALIFIER_SPLITTER\" the class loader (instance of " +
                            "com/android/tools/lint/gradle/api/DelegatingClassLoader) of the " +
                            "referring class, " +
                            "com/android/ide/common/resources/configuration/FolderConfiguration, " +
                            "and the class loader (instance of " +
                            "org/gradle/internal/classloader/VisitableURLClassLoader) for the " +
                            "field's resolved type, com/google/common/base/Splitter, have " +
                            "different Class objects for that type"
                    )
                }
            }

        companion object {
            val LINKAGE_ERROR = Issue
                .create(
                    "_LinkageCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(LinkageErrorDetector::class.java, Scope.JAVA_FILE_SCOPE)
                )
        }
    }
}
