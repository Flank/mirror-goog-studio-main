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
import com.android.tools.lint.checks.infrastructure.TestFiles.gradle
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UImportStatement
import org.junit.Test

/**
 * Checks that some lint checks cannot be suppressed with the normal suppression
 * annotations or mechanisms
 */
class SuppressLintTest {

    @Test
    fun checkErrorFlagged() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                java(
                    """
                    import forbidden;
                    class Test {
                    }
                    """
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/Test.java:1: Warning: Some error message here [_SecureIssue]
                import forbidden;
                ~~~~~~~~~~~~~~~~~
                src/test.kt:2: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                0 errors, 2 warnings
                """
            )
    }

    @Test
    fun checkOkSuppress() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    import foo.bar.MyOwnAnnotation
                    @MyOwnAnnotation
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                java(
                    """
                    import foo.bar.MyOwnAnnotation;
                    import forbidden;
                    @MyOwnAnnotation
                    class Test {
                        public void forbidden() {
                            forbidden();
                        }
                    }
                    """
                ).indented(),
                java(
                    """
                    package foo.bar;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public @interface MyOwnAnnotation {
                    }
                    """
                )
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expectClean()
    }

    @Test
    fun checkForbiddenSuppressWithComment() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        //noinspection AndroidLint_SecureIssue
                        forbidden()
                    }"""
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/test.kt:3: Error: Issue _SecureIssue is not allowed to be suppressed (but can be with @MyOwnAnnotation) [LintError]
                    forbidden()
                    ~~~~~~~~~~~
                src/test.kt:3: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                1 errors, 1 warnings
                """
            )
    }

    @Test
    fun checkForbiddenSuppressWithAnnotation() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    import android.annotation.SuppressLint
                    @SuppressLint("_SecureIssue")
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                java(
                    """
                    import android.annotation.SuppressLint
                    import forbidden;
                    @SuppressLint("_SecureIssue")
                    class Test {
                    }
                    """
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/Test.java:3: Error: Issue _SecureIssue is not allowed to be suppressed (but can be with @MyOwnAnnotation) [LintError]
                @SuppressLint("_SecureIssue")
                ^
                src/test.kt:2: Error: Issue _SecureIssue is not allowed to be suppressed (but can be with @MyOwnAnnotation) [LintError]
                @SuppressLint("_SecureIssue")
                ^
                src/Test.java:2: Warning: Some error message here [_SecureIssue]
                import forbidden;
                ~~~~~~~~~~~~~~~~~
                src/test.kt:4: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                2 errors, 2 warnings
                """
            )
    }

    @Test
    fun checkForbiddenSuppressWithLintXml() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                xml(
                    "lint.xml",
                    """
                    <lint>
                        <issue id="all" severity="ignore" />
                        <issue id="_SecureIssue" severity="ignore" />
                    </lint>
                """
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/test.kt:2: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    @Test
    fun checkIgnoredSuppressWithLintXmlAll() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                xml(
                    "lint.xml",
                    """
                    <lint>
                        <!-- Not specifically targeting the forbidden issue with "all"
                         so we skip it for this issue but don't complain about it -->
                        <issue id="all" severity="ignore" />
                    </lint>
                """
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/test.kt:2: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    @Test
    fun checkIgnoredBaseline() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .baseline(
                xml(
                    "baseline.xml",
                    """
                <issues format="5" by="lint 3.3.0">
                    <issue
                        id="_SecureIssue"
                        severity="Warning"
                        message="Some error message here"
                        category="Security"
                        priority="10"
                        summary="Some important security issue"
                        explanation="Blahdiblah"
                        errorLine1="    forbidden()"
                        errorLine2="    ~~~~~~~~~~~">
                        <location
                            file="src/test.kt"
                            line="2"
                            column="5"/>
                    </issue>
                </issues>
            """
                ).indented()
            )
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                baseline.xml: Error: Issue _SecureIssue is not allowed to be suppressed (but can be with @MyOwnAnnotation) [LintError]
                src/test.kt:2: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                1 errors, 1 warnings
                """
            )
    }

    @Test
    fun checkForbiddenSuppressWithLintOptions() {
        lint()
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    fun forbidden() {
                        forbidden()
                    }"""
                ).indented(),
                gradle(
                    """
                    apply plugin: 'com.android.application'

                    android {
                        lintOptions {
                            disable '_SecureIssue'
                        }
                    }
                    """
                ).indented()
            )
            .issues(MySecurityDetector.TEST_ISSUE)
            .sdkHome(TestUtils.getSdk())
            .run()
            .expect(
                """
                src/main/kotlin/test.kt:2: Warning: Some error message here [_SecureIssue]
                    forbidden()
                    ~~~~~~~~~~~
                0 errors, 1 warnings
                """
            )
    }

    // Sample detector which just flags calls to a method called "forbidden"
    @SuppressWarnings("ALL")
    class MySecurityDetector : Detector(), Detector.UastScanner {
        override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

        override fun getApplicableMethodNames(): List<String>? {
            return listOf("forbidden")
        }

        override fun visitMethodCall(
            context: JavaContext,
            node: UCallExpression,
            method: PsiMethod
        ) {
            val message = "Some error message here"
            val location = context.getLocation(node)
            context.report(TEST_ISSUE, node, location, message)
        }

        override fun createUastHandler(context: JavaContext) = AssertjDetectorHandler(context)

        class AssertjDetectorHandler(private val context: JavaContext) : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                node.importReference?.let { importReference ->
                    if (importReference.asSourceString().contains("forbidden")) {
                        val message = "Some error message here"
                        val location = context.getLocation(node)
                        context.report(TEST_ISSUE, node, location, message)
                    }
                }
            }
        }

        companion object {
            @Suppress("SpellCheckingInspection")
            @JvmField
            val TEST_ISSUE = Issue.create(
                id = "_SecureIssue",
                briefDescription = "Some important security issue",
                explanation = "Blahdiblah",
                category = Category.SECURITY, priority = 10, severity = Severity.WARNING,
                suppressAnnotations = listOf("foo.bar.MyOwnAnnotation"),
                implementation = Implementation(
                    MySecurityDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
        }
    }
}
