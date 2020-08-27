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

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.junit.Test
import java.io.File
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

    @Test
    fun testSuppressImportWarningOnClass() {
        // Regression test for https://issuetracker.google.com/121092580
        lint()
            .allowCompilationErrors()
            .files(
                java(
                    """
                      package foo;
                      import org.assertj.core.api.Assertions;
                      @SuppressWarnings("_AssertjImport")
                      public class Foo {
                      }
                      """
                ).indented()
            )
            .sdkHome(TestUtils.getSdk())
            .issues(AssertjDetector.ISSUE_ASSERTJ_IMPORT)
            .run()
            .expectClean()
    }

    @Test
    fun testModifyOtherFiles() {
        // Regression test for 161410735
        lint()
            .allowCompilationErrors()
            .files(
                java(
                    """
                    package test.pkg;
                    class Test {
                       public void oldName() {
                           renameMethodNameInstead();
                           updateBuildGradle();
                       }
                       private void renameMethodNameInstead() {
                       }
                       private void updateBuildGradle() {
                           String x = "Say hello, lint!";
                       }
                    }
                    """
                ).indented(),
                gradle(
                    """
                    // Dummy Gradle File
                    apply plugin: 'java'
                    """
                ).indented()
            )
            .sdkHome(TestUtils.getSdk())
            .issues(*LintFixVerifierRegistry().issues.toTypedArray())
            .run()
            .expect(
                """
                src/main/java/test/pkg/Test.java:4: Warning: This error has a quickfix which edits parent method name instead [_LintFixVerifier]
                       renameMethodNameInstead();
                       ~~~~~~~~~~~~~~~~~~~~~~~~~
                src/main/java/test/pkg/Test.java:5: Warning: This error has a quickfix which edits something in a separate build.gradle file instead [_LintFixVerifier]
                       updateBuildGradle();
                       ~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
            .expectFixDiffs(
                """
                Fix for src/main/java/test/pkg/Test.java line 4: Rename Containing Method:
                @@ -3 +3
                -    public void oldName() {
                +    public void renamedMethod() {
                Fix for src/main/java/test/pkg/Test.java line 5: Update build.gradle:
                @@ -2 +2
                - apply plugin: 'java'
                @@ -3 +2
                + apply plugin: 'kotlin'
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
                // Not great grammar but just a unit test, not an issue shown to users:
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

@SuppressWarnings("ALL")
class LintFixVerifierDetector : Detector(), Detector.UastScanner {
    override fun getApplicableMethodNames(): List<String>? {
        return listOf("renameMethodNameInstead", "updateBuildGradle")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (method.name == "renameMethodNameInstead") {
            val parentMethod = node.getParentOfType<UMethod>(
                UMethod::class.java,
                false
            )
            val fix = fix()
                .name("Rename Containing Method")
                .replace()
                .all()
                .with("renamedMethod")
                .range(context.getNameLocation(parentMethod!!))
                .build()
            context.report(
                ISSUE, context.getLocation(node),
                "This error has a quickfix which edits parent method name instead", fix
            )
        } else if (method.name == "updateBuildGradle") {
            val file = File(
                context.file.parentFile?.parentFile?.parentFile?.parentFile!!,
                FN_BUILD_GRADLE
            ).let { file ->
                if (file.isFile) {
                    file
                } else {
                    File(file.parentFile.parentFile.parentFile, FN_BUILD_GRADLE)
                }
            }
            val source = file.readText()
            val start = source.indexOf("java")
            val end = start + 4
            val range = Location.Companion.create(file, source, start, end)
            val fix = fix()
                .name("Update build.gradle")
                .replace()
                .all()
                .with("kotlin")
                .range(range)
                .build()
            context.report(
                ISSUE,
                context.getLocation(node),
                "This error has a quickfix which edits something in a separate build.gradle file instead",
                fix
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "_LintFixVerifier",
            briefDescription = "Lint check for test purposes with a quickfix.",
            explanation = "Check with fix to exercise more complicated replacement fixes",
            category = Category.TESTING, priority = 10, severity = Severity.WARNING,
            implementation = Implementation(
                LintFixVerifierDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}

class LintFixVerifierRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(LintFixVerifierDetector.ISSUE)

    override val minApi: Int
        get() = 1

    override val api: Int
        // Set a far future API level:
        // Real lint check should never do this, but here we actively turn off API version
        // checking since this registry will be used as part of lint's own unit tests
        // and therefore if it didn't work right the test would fail
        get() = 10000
}

/*
   When creating unit test for the IDE side (LintIdeTest#testLintJar),
   use the following (after compiling the tests):
       cd tools/base/out/test/android.sdktools.base.lint.tests
       mkdir -p META-INF/service
       echo "com.android.tools.lint.checks.infrastructure.LintFixVerifierRegistry" > META-INF/services/com.android.tools.lint.client.api.IssueRegistry
       jar cf lint-fix-verifier.jar META-INF \
           com/android/tools/lint/checks/infrastructure/LintFixVerifierDetector.class \
           com/android/tools/lint/checks/infrastructure/LintFixVerifierDetector\$Companion.class \
           com/android/tools/lint/checks/infrastructure/LintFixVerifierRegistry.class
 */
