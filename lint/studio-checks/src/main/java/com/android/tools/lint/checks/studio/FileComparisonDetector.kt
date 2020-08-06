/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.CommonClassNames.JAVA_IO_FILE
import com.intellij.psi.CommonClassNames.JAVA_UTIL_OBJECTS
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastBinaryOperator

/**
 * Makes sure we compare files properly to handle cross platform issues like
 * case insensitive file systems
 *
 * TODO: Check for calling file.toURL or file.toURI.toURL: use our sdk utils instead
 */
class FileComparisonDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            FileComparisonDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FileComparisons",
            briefDescription = "Invalid File Comparisons",
            explanation =
                """
                Never call `equals` (or worse, `==`) on a `java.io.File`:
                this will not do the right thing on case insensitive file systems.

                Always call `equals` instead of identity equals on VirtualFiles.

                For more info, see `go/files-howto`.
            """,
            category = CROSS_PLATFORM,
            priority = 3,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UBinaryExpression::class.java, UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName == "equals")
                    if (node.valueArgumentCount == 1) {
                        val lhs = node.receiver ?: return
                        check(context, node, lhs, node.valueArguments.first())
                    } else if (node.valueArgumentCount == 2) {
                        if (!context.evaluator.isMemberInClass(node.resolve(), JAVA_UTIL_OBJECTS)) {
                            return
                        }
                        check(context, node, node.valueArguments[0], node.valueArguments[1])
                    }
            }

            override fun visitBinaryExpression(node: UBinaryExpression) {
                val operator = node.operator
                if (operator == UastBinaryOperator.EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    operator == UastBinaryOperator.NOT_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    check(context, node, node.leftOperand, node.rightOperand)
                }
            }
        }
    }

    private fun check(
        context: JavaContext,
        node: UExpression,
        lhs: UExpression,
        rhs: UExpression
    ) {
        val evaluator = context.evaluator
        if (!evaluator.typeMatches(lhs.getExpressionType(), JAVA_IO_FILE)) {
            return
        }
        if (!evaluator.typeMatches(rhs.getExpressionType(), JAVA_IO_FILE)) {
            return
        }
        context.report(
            ISSUE, node, context.getLocation(node),
            "Do not compare java.io.File with `equals` or `==`: will not work correctly on case insensitive file systems! See `go/files-howto`."
        )
    }
}
