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

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.tryResolve

/**
 * Looks for places where you accidentally pass a path in as a regular expression;
 * this will fail on Windows where the path separator is \, an escape.
 */
class RegexpPathDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            RegexpPathDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "RegexPath",
            briefDescription = "Using Path as Regular Expression",
            explanation =
                """
                Be careful when passing in a path into a method which expects \
                a regular expression. Your code may work on Linux or OSX, but on \
                Windows the file separator is a back slash, which in a regular \
                expression is interpreted as an escape for the next character!

                For more info, see `go/files-howto`.
            """,
            category = CROSS_PLATFORM,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String>? =
        //  Common APIs which take regular expressions:
        //    Pattern.compile(regex, ...)
        //    String.split(regex, ...)
        //    String.replaceAll(regex, ...)
        //    String.replaceFirst(String regex, ...)
        // TODO: kotlin.text.Regex constructor!
        listOf("compile", "split", "replaceAll", "replaceFirst")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        // Make sure it's the right methods
        when (method.name) {
            "compile" -> {
                if (!evaluator.isMemberInClass(method, "java.util.regex.Pattern")) {
                    return
                }
            }
            "split", "replaceAll", "replaceFirst" -> {
                if (!evaluator.isMemberInClass(method, "java.lang.String")) {
                    return
                }
            }
            else -> {
                error(method.name)
            }
        }

        // In all these methods, the argument is the first one
        val arg = node.valueArguments.firstOrNull() ?: return
        if (isPath(arg)) {
            context.report(
                ISSUE, node, context.getLocation(node),
                "Passing a path to a parameter which expects a regular expression " +
                    "is dangerous; on Windows path separators will look like escapes. " +
                    "Wrap path with `Pattern.quote`."
            )
        }
    }

    private fun isPath(arg: UExpression): Boolean {
        if (arg is ULiteralExpression) {
            // Common: it's a string literal; probably a deliberate regular expression; do nothing
            return false
        }

        if (arg is UReferenceExpression) {
            val name = arg.resolvedName
            if (name != null && (name == "path" || name.endsWith("Path"))) {
                return true
            }
        }

        // Two common scenarios:
        // (1) variable/parameter reference: String.split(path)
        // (2) nested method call, e.g. String.split(file.getPath())

        val resolved = arg.tryResolve() ?: return false
        if (resolved is PsiMethod) {
            // Method call
            // We generally can't reason about these, except File.getPath() is a common one
            if (resolved.name == "getPath") {
                return true
            }
        } else if (resolved is PsiVariable) {
            // TODO: See if it's initialized
            val lastAssignment = UastLintUtils.findLastAssignment(resolved, arg) ?: run {
                // TODO: look at initializer
                return false
            }

            if (lastAssignment is UCallExpression && lastAssignment.methodName == "getPath") {
                return true
            }
            if (lastAssignment is UQualifiedReferenceExpression &&
                lastAssignment.selector is UCallExpression &&
                (lastAssignment.selector as UCallExpression).methodName == "getPath"
            ) {
                return true
            }
        }

        return false
    }
}
