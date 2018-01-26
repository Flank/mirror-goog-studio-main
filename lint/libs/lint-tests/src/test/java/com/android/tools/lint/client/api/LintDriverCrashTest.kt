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

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.truth.Truth.assertThat
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.w3c.dom.Element

class LintDriverCrashTest : AbstractCheckTest() {
    fun testLintDriverError() {
        // Regression test for 34248502
        lint().files(
                xml("res/layout/foo.xml", "<LinearLayout/>"),
                java("""
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """))
                .allowSystemErrors(true)
                .issues(CrashingDetector.CRASHING_ISSUE)
                .run()
                // Checking for manual substrings instead of doing an actual equals check
                // since the stacktrace contains a number of specific line numbers from
                // the lint implementation, including this test, which keeps shifting every
                // time there is an edit
                .check {
                    assertThat(it).contains("Foo.java: Error: Unexpected failure during lint analysis of Foo.java (this is a bug in lint or one of the libraries it depends on)")
                    assertThat(it).contains("You can set environment variable LINT_PRINT_STACKTRACE=true to dump a full stacktrace to stdout. [LintError]")
                    assertThat(it).contains("ArithmeticException:LintDriverCrashTest\$CrashingDetector\$createUastHandler$1.visitFile(LintDriverCrashTest.kt:")
                    assertThat(it).contains("1 errors, 0 warnings")
                }
        LintDriver.clearCrashCount()
    }

    fun testLinkageError() {
        // Regression test for 34248502
        lint().files(
            java("""
                    package test.pkg;
                    @SuppressWarnings("ALL") class Foo {
                    }
                    """))
            .allowSystemErrors(true)
            .issues(LinkageErrorDetector.LINKAGE_ERROR)
            .run()
            .expect(
                """
                    project0: Error: Lint crashed because it is being invoked with the wrong version of Guava
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

    override fun getIssues(): List<Issue> = listOf(CrashingDetector.CRASHING_ISSUE,
            DisposedThrowingDetector.DISPOSED_ISSUE, LinkageErrorDetector.LINKAGE_ERROR)

    override fun getDetector(): Detector = CrashingDetector()

    class CrashingDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
                listOf<Class<out UElement>>(UFile::class.java)

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
                    .create("_TestCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                            Implementation(CrashingDetector::class.java, Scope.JAVA_FILE_SCOPE))
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
            val DISPOSED_ISSUE = Issue.create("_TestDisposed", "test", "test", Category.LINT,
                    10, Severity.FATAL,
                    Implementation(DisposedThrowingDetector::class.java,
                            Scope.RESOURCE_FILE_SCOPE))
        }
    }

    class LinkageErrorDetector : Detector(), SourceCodeScanner {

        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
            listOf<Class<out UElement>>(UFile::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler? =
            object : UElementHandler() {
                override fun visitFile(node: UFile) {
                    throw LinkageError("loader constraint violation: when resolving field " +
                            "\"QUALIFIER_SPLITTER\" the class loader (instance of " +
                            "com/android/tools/lint/gradle/api/DelegatingClassLoader) of the " +
                            "referring class, " +
                            "com/android/ide/common/resources/configuration/FolderConfiguration, " +
                            "and the class loader (instance of " +
                            "org/gradle/internal/classloader/VisitableURLClassLoader) for the " +
                            "field's resolved type, com/google/common/base/Splitter, have " +
                            "different Class objects for that type")
                }
            }

        companion object {
            val LINKAGE_ERROR = Issue
                .create("_LinkageCrash", "test", "test", Category.LINT, 10, Severity.FATAL,
                    Implementation(LinkageErrorDetector::class.java, Scope.JAVA_FILE_SCOPE))
        }
    }
}
