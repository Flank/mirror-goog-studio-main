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

package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.client.api.UElementHandler
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getValueIfStringLiteral

class ContextTest : AbstractCheckTest() {
    fun testSuppressFileAnnotation() {
        // Regression test for https://issuetracker.google.com/116838536
        lint().files(
            kotlin(
                """
                @file:Suppress("unused", "_TestIssueId")
                package test.pkg

                class MyTest {
                    val s: String = "/sdcard/mydir"
                }
                """
            ).indented(),
            gradle("")
        ).issues(TEST_ISSUE).run().expectClean()
    }

    fun testSuppressObjectAnnotation() {
        // Regression test for https://issuetracker.google.com/116838536
        lint().files(
            kotlin(
                """
                package test.pkg
                import android.annotation.SuppressLint
                @SuppressLint("_TestIssueId")
                object TestClass1 {
                    const val s: String = "/sdcard/mydir"
                }"""
            ).indented(),
            gradle("")
        ).issues(TEST_ISSUE).run().expectClean()
    }

    override fun getDetector(): Detector = NoLocationNodeDetector()

    override fun getIssues(): List<Issue> = listOf(
        TEST_ISSUE
    )

    // Detector which reproduces problem in issue https://issuetracker.google.com/116838536
    class NoLocationNodeDetector : Detector(), SourceCodeScanner {
        override fun getApplicableUastTypes(): List<Class<out UElement>>? =
            listOf(ULiteralExpression::class.java)

        override fun createUastHandler(context: JavaContext): UElementHandler? =
            object : UElementHandler() {
                override fun visitLiteralExpression(node: ULiteralExpression) {
                    val s = node.getValueIfStringLiteral()
                    if (s != null && s.startsWith("/sdcard/")) {
                        val message =
                            """Sample error message"""
                        val location = context.getLocation(node)
                        // Note: We're calling
                        //    context.report(Issue, Location, String)
                        // NOT:
                        //    context.report(Issue, UElement, Location, String)
                        // to test that we suppress based on stashed location
                        // source element from above; this tests issue 116838536
                        context.report(TEST_ISSUE, location, message)
                    }
                }
            }
    }

    companion object {
        val TEST_ISSUE = Issue.create(
            "_TestIssueId", "Not applicable", "Not applicable",
            Category.MESSAGES, 5, Severity.WARNING,
            Implementation(
                NoLocationNodeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}