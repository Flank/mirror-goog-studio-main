/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PendingIntentMutableFlagDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames() = METHOD_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.app.PendingIntent"))
            return
        val flagsArgument = node.getArgumentForParameter(FLAG_ARGUMENT_POSITION) ?: return
        val flags = ConstantEvaluator.evaluate(context, flagsArgument) as? Int ?: return
        if (flags and FLAG_MASK == 0) {
            context.report(
                ISSUE,
                node,
                context.getLocation(flagsArgument),
                "Missing `PendingIntent` mutability flag"
            )
        }
    }

    companion object {
        private val METHOD_NAMES =
            listOf("getActivity", "getActivities", "getBroadcast", "getService")
        private const val FLAG_ARGUMENT_POSITION = 3
        private const val FLAG_IMMUTABLE = 1 shl 26
        private const val FLAG_MUTABLE = 1 shl 25
        private const val FLAG_MASK = FLAG_IMMUTABLE or FLAG_MUTABLE

        @JvmField
        val ISSUE = Issue.create(
            id = "UnspecifiedImmutableFlag",
            briefDescription = "Missing `PendingIntent` mutability flag",
            explanation = """
                Apps targeting Android 12 and higher must specify either `FLAG_IMMUTABLE` or \
                `FLAG_MUTABLE` when constructing a `PendingIntent`.
            """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PendingIntentMutableFlagDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
