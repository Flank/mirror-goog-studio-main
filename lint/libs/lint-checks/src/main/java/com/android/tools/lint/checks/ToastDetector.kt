/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Detector looking for Toast.makeText() without a corresponding show() call */
class ToastDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String>? {
        return listOf("makeText")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, "android.widget.Toast")) {
            return
        }

        // Make sure you pass the right kind of duration: it's not a delay, it's
        //  LENGTH_SHORT or LENGTH_LONG
        // (see http://code.google.com/p/android/issues/detail?id=3655)
        val args = node.valueArguments
        if (args.size == 3) {
            val duration = args[2]
            if (duration is ULiteralExpression) {
                context.report(
                    ISSUE,
                    duration,
                    context.getLocation(duration),
                    "Expected duration `Toast.LENGTH_SHORT` or `Toast.LENGTH_LONG`, a custom " +
                        "duration value is not supported"
                )
            }
        }
        val surroundingDeclaration = node.getParentOfType(
            true,
            UMethod::class.java,
            UBlockExpression::class.java,
            ULambdaExpression::class.java
        )
            ?: return

        val parent = node.uastParent
        if (parent is UMethod ||
            parent is UReferenceExpression &&
            parent.uastParent is UMethod
        ) {
            // Kotlin expression body
            return
        }

        val finder = ShowFinder(node)
        surroundingDeclaration.accept(finder)
        if (!finder.isShowCalled) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = true, includeArguments = false),
                "Toast created but not shown: did you forget to call `show()` ?"
            )
        }
    }

    private class ShowFinder(
        /** The target makeText call  */
        private val target: UCallExpression
    ) : AbstractUastVisitor() {

        /** Whether we've found the show method  */
        var isShowCalled = false
            private set

        /** Whether we've seen the target makeText node yet  */
        private var seenTarget = false
        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node === target ||
                node.sourcePsi != null && node.sourcePsi === target.psi
            ) {
                seenTarget = true
            } else {
                if ((seenTarget || target == node.receiver) &&
                    "show" == getMethodName(node)
                ) {
                    // TODO: Do more flow analysis to see whether we're really calling show
                    // on the right type of object?
                    isShowCalled = true
                }
            }
            return super.visitCallExpression(node)
        }

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (target.isUastChildOf(node.returnExpression, true)) {
                // If you just do "return Toast.makeText(...) don't warn
                isShowCalled = true
            }
            return super.visitReturnExpression(node)
        }
    }

    companion object {
        @JvmField
        val ISSUE =
            Issue.create(
                id = "ShowToast",
                briefDescription = "Toast created but not shown",
                explanation =
                    """
                    `Toast.makeText()` creates a `Toast` but does **not** show it. You must \
                    call `show()` on the resulting object to actually make the `Toast` \
                    appear.""",
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    ToastDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                )
            )
    }
}
