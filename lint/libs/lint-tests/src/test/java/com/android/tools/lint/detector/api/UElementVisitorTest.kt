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
package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.AbstractCheckTest
import org.jetbrains.uast.UClass

// Regression test for b/204342275: UElementVisitor visits subclasses twice in some cases.
class UElementVisitorTest : AbstractCheckTest() {

    @Suppress("LintDocExample")
    fun testSubclassVisitedOnlyOnce() {
        lint().files(
            java(
                """
                package test.pkg;

                class Test {
                    interface A {}
                    class B implements A {}
                    class C extends B implements A {}
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/Test.java:4: Warning: Class A extends interface A [_TestIssueId]
                interface A {}
                          ~
            src/test/pkg/Test.java:5: Warning: Class B extends interface A [_TestIssueId]
                class B implements A {}
                      ~
            src/test/pkg/Test.java:6: Warning: Class C extends interface A [_TestIssueId]
                class C extends B implements A {}
                      ~
            0 errors, 3 warnings
            """
        )
    }

    override fun getDetector(): Detector = TestDetector()

    override fun getIssues(): List<Issue> = listOf(TEST_ISSUE)

    class TestDetector : Detector(), SourceCodeScanner {
        override fun applicableSuperClasses(): List<String> = listOf("test.pkg.Test.A")

        override fun visitClass(context: JavaContext, declaration: UClass) {
            context.report(
                TEST_ISSUE, declaration, context.getNameLocation(declaration),
                "Class `${declaration.name}` extends interface `A`"
            )
        }
    }

    companion object {
        val TEST_ISSUE = Issue.create(
            "_TestIssueId", "Not applicable", "Not applicable",
            Category.CORRECTNESS, 5, Severity.WARNING,
            Implementation(
                TestDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
