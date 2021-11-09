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

class UElementVisitorTest : AbstractCheckTest() {

    @Suppress("LintDocExample")
    fun testSubclassVisitedOnlyOnce() {
        // Regression test for b/204342275: UElementVisitor visits subclasses twice in some cases.
        lint().files(
            java(
                """
                package test.pkg;

                class Test {
                    interface I1 {}
                    class C1 implements I1 {}
                    class C2 extends C1 implements I1 {}

                    interface I2 {}
                    class C3 implements I1, I2 {}

                    class C4 {}
                }
                """
            ).indented()
        ).run().expect(
            """
            src/test/pkg/Test.java:4: Warning: Visited I1 [_TestIssueId]
                interface I1 {}
                          ~~
            src/test/pkg/Test.java:5: Warning: Visited C1 [_TestIssueId]
                class C1 implements I1 {}
                      ~~
            src/test/pkg/Test.java:6: Warning: Visited C2 [_TestIssueId]
                class C2 extends C1 implements I1 {}
                      ~~
            src/test/pkg/Test.java:8: Warning: Visited I2 [_TestIssueId]
                interface I2 {}
                          ~~
            src/test/pkg/Test.java:9: Warning: Visited C3 [_TestIssueId]
                class C3 implements I1, I2 {}
                      ~~
            0 errors, 5 warnings
            """
        )
    }

    override fun getDetector(): Detector = TestDetector()

    override fun getIssues(): List<Issue> = listOf(TEST_ISSUE)

    class TestDetector : Detector(), SourceCodeScanner {
        override fun applicableSuperClasses(): List<String> = listOf("test.pkg.Test.I1", "test.pkg.Test.I2")

        override fun visitClass(context: JavaContext, declaration: UClass) {
            context.report(
                TEST_ISSUE, declaration, context.getNameLocation(declaration),
                "Visited `${declaration.name}`"
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
