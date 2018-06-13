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

package com.android.tools.lint.checks.infrastructure

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UImportStatement
import org.junit.Test
import java.util.EnumSet

class LintFixVerifierTest {

    // Regression test for 80491636: AssertionError: Didn't find test file src/test.kt
    @Test
    fun kotlinAssertionsImport() {
        lint()
            .allowCompilationErrors()
            .files(kotlin("import org.assertj.core.api.Assertions"))
            .issues(AssertjDetector.ISSUE_ASSERTJ_IMPORT)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                  src/test.kt:1: Warning: Should use Java6Assertions instead [_AssertjImport]
                  import org.assertj.core.api.Assertions
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                  0 errors, 1 warnings"""
            )
            .expectFixDiffs(
                """
                Fix for src/test.kt line 1: Replace with org.assertj.core.api.Java6Assertions:
                @@ -1 +1
                - import org.assertj.core.api.Assertions
                @@ -2 +1
                + import org.assertj.core.api.Java6Assertions
                """
            )
    }

    // Regression test for 80491636: AssertionError: Didn't find test file src/test.kt
    @Test
    fun testJavaAssertionsImport() {
        lint()
            .allowCompilationErrors()
            .files(
                java(
                    """
                      package foo;

                      import org.assertj.core.api.Assertions;
                      """
                ).indented()
            )
            .sdkHome(TestUtils.getSdk())
            .issues(AssertjDetector.ISSUE_ASSERTJ_IMPORT)
            .run()
            .expect(
                """
                  src/foo/package-info.java:3: Warning: Should use Java6Assertions instead [_AssertjImport]
                  import org.assertj.core.api.Assertions;
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                  0 errors, 1 warnings"""
            )
            .expectFixDiffs(
                """
                  Fix for src/foo/package-info.java line 3: Replace with org.assertj.core.api.Java6Assertions:
                  @@ -3 +3
                  - import org.assertj.core.api.Assertions;
                  @@ -4 +3
                  + import org.assertj.core.api.Java6Assertions;
                  """
            )
    }

    // Copied from above bug report:
    //     https://issuetracker.google.com/80491636
    // which in turn looks like it comes from
    //     https://github.com/vanniktech/lint-rules
    // which has the Apache 2 license.
    @SuppressWarnings("ALL")
    class AssertjDetector : Detector(), Detector.UastScanner {
        override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

        override fun createUastHandler(context: JavaContext) = AssertjDetectorHandler(context)

        class AssertjDetectorHandler(private val context: JavaContext) : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                node.importReference?.let { importReference ->
                    if (importReference.asSourceString().startsWith("org.assertj.core.api.Assertions")) {
                        val fix = LintFix.create()
                            .replace()
                            .text(importReference.asSourceString())
                            .with(
                                importReference.asSourceString().replace(
                                    "org.assertj.core.api.Assertions",
                                    "org.assertj.core.api.Java6Assertions"
                                )
                            )
                            .build()

                        context.report(
                            ISSUE_ASSERTJ_IMPORT,
                            node,
                            context.getLocation(importReference),
                            "Should use Java6Assertions instead",
                            fix
                        )
                    }
                }
            }
        }

        companion object {
            @JvmField
            val ISSUE_ASSERTJ_IMPORT = Issue.create(
                id = "_AssertjImport",
                briefDescription = "Flags Java 6 incompatible imports.",
                explanation = "Importing org.assertj.core.api.Assertions is not ideal. " +
                        "Since it can require Java 8. It's simple as " +
                        "instead org.assertj.core.api.Java6Assertions can be imported " +
                        "and provides guarantee to run on Java 6 as well.",
                category = Category.CORRECTNESS, priority = 10, severity = Severity.WARNING,
                implementation = Implementation(
                    AssertjDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
                )
            )
        }
    }
}