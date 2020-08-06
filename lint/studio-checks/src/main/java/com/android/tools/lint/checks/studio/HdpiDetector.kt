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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment

/**
 * Detect incorrect handling of high density screens.
 */
class HdpiDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            HdpiDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "JbUiStored",
            briefDescription = "Storing scaled pixel sizes",
            explanation =
                """
                `JBUI.scale()` can return different values at different points in time during the \
                same Studio session (for example when the user changes themes, or changes the \
                default font size). This means that storing the result of `JBUI.scale()` in \
                static (final) fields is incorrect.
                """,
            category = CORRECTNESS,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION,
            moreInfo = "https://issuetracker.google.com/132441250"
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("scale", "scaleFontSize")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if ((
            evaluator.isMemberInClass(method, "com.intellij.util.ui.JBUI") ||
                evaluator.isMemberInClass(method, "com.intellij.ui.scale.JBUIScale")
            ) &&
            evaluator.getParameterCount(method) == 1
        ) {
            // Make sure it's not stored in a field
            var curr: UElement = node.uastParent ?: return
            while (curr is UQualifiedReferenceExpression) {
                curr = curr.uastParent ?: return
            }
            if (curr is UField) {
                report(context, node)
            } else if (curr.isAssignment()) {
                val left = (curr as UBinaryExpression).leftOperand
                if (left is UQualifiedReferenceExpression &&
                    left.receiver is UThisExpression &&
                    left.selector is USimpleNameReferenceExpression
                ) {
                    report(context, node)
                } else {
                    val resolved = left.tryResolve()
                    if (resolved is PsiField) {
                        report(context, node)
                    }
                }
            }
        }
    }

    private fun report(
        context: JavaContext,
        node: UElement
    ) {
        context.report(
            ISSUE, node, context.getNameLocation(node),
            "Do not store `JBUI.scale` scaled results in fields; this will not work correctly on dynamic theme or font size changes"
        )
    }
}
