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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Checks related to DiffUtil computation. */
class DiffUtilDetector : Detector(), SourceCodeScanner {

    // ---- implements SourceCodeScanner ----

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            "android.support.v7.util.DiffUtil.ItemCallback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v17.leanback.widget.DiffCallback",
            "androidx.leanback.widget.DiffCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        for (method in declaration.methods) {
            if (method.name == "areContentsTheSame" && evaluator.getParameterCount(method) == 2) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(
        context: JavaContext,
        declaration: UMethod
    ) {
        declaration.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                checkExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCall(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    private fun defaultEquals(context: JavaContext, node: UElement): Boolean {
        val resolved: PsiMethod?

        if (node is UBinaryExpression) {
            resolved = node.resolveOperator()
            if (resolved == null) {
                val left = node.leftOperand.getExpressionType() as? PsiClassType
                return defaultEquals(context, left)
            }
        } else if (node is UCallExpression) {
            resolved = node.resolve()
        } else {
            // We don't know any better
            return false
        }

        resolved ?: return false
        return resolved.containingClass?.qualifiedName == JAVA_LANG_OBJECT
    }

    private fun defaultEquals(
        context: JavaContext,
        type: PsiClassType?
    ): Boolean {
        val cls = type?.resolve() ?: return false

        if (isKotlin(cls) && (context.evaluator.isSealed(cls) || context.evaluator.isData(cls))) {
            // Sealed class doesn't guarantee that it defines equals/hashCode
            // but it's likely (we'd need to go look at each inner class)
            return false
        }

        for (m in cls.findMethodsByName("equals", true)) {
            if (m is PsiMethod) {
                val parameters = m.parameterList.parameters
                if (parameters.size == 1 &&
                    parameters[0].type.canonicalText == JAVA_LANG_OBJECT
                ) {
                    return m.containingClass?.qualifiedName == JAVA_LANG_OBJECT
                }
            }
        }

        return false
    }

    private fun checkCall(context: JavaContext, node: UCallExpression) {
        if (defaultEquals(context, node)) {
            // Within cast or instanceof check which implies a more specific type
            // which provides an equals implementation?
            if (withinCastWithEquals(context, node)) {
                return
            }

            val targetType = node.receiverType?.canonicalText ?: "target"
            val message = "Suspicious equality check: `equals()` is not implemented in $targetType"
            val location = context.getCallLocation(node, false, true)
            context.report(ISSUE, node, location, message)
        }
    }

    /**
     * Is this .equals() call within another if check which checks
     * instanceof on a more specific type than we're calling equals on?
     * If so, does that more specific type define its own equals?
     *
     * Also handle an implicit check via short circuit evaluation; e.g.
     * something like "return a is A && b is B && a.equals(b)".
     */
    private fun withinCastWithEquals(context: JavaContext, node: UExpression): Boolean {
        var parent = node.uastParent
        if (parent is UQualifiedReferenceExpression) {
            parent = parent.uastParent
        }
        val target: PsiElement? = when (node) {
            is UCallExpression -> node.receiver?.tryResolve()
            is UBinaryExpression -> node.leftOperand.tryResolve()
            else -> null
        }

        if (parent is UPolyadicExpression && parent.operator == UastBinaryOperator.LOGICAL_AND) {
            val operands = parent.operands
            for (operand in operands) {
                if (operand === node) {
                    break
                }
                if (isCastWithEquals(context, operand, target)) {
                    return true
                }
            }
        }
        val ifStatement = node.getParentOfType<UElement>(UIfExpression::class.java, false, UMethod::class.java)
            as? UIfExpression ?: return false
        val condition = ifStatement.condition
        return isCastWithEquals(context, condition, target)
    }

    private fun isCastWithEquals(context: JavaContext, node: UExpression, target: PsiElement?): Boolean {
        if (node is UBinaryExpressionWithType) {
            if (target != null) {
                val resolved = node.operand.tryResolve()
                // Unfortunately in some scenarios isEquivalentTo returns false for equal instances
                //noinspection LintImplPsiEquals
                if (resolved != null && !(target == resolved || target.isEquivalentTo(resolved))) {
                    return false
                }
            }
            return !defaultEquals(context, node.type as? PsiClassType)
        } else if (node is UPolyadicExpression && node.operator == UastBinaryOperator.LOGICAL_AND) {
            for (operand in node.operands) {
                if (isCastWithEquals(context, operand, target)) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
            node.operator == UastBinaryOperator.EQUALS
        ) {
            val left = node.leftOperand.getExpressionType() ?: return
            val right = node.rightOperand.getExpressionType() ?: return
            if (left is PsiClassType && right is PsiClassType) {
                if (node.operator == UastBinaryOperator.EQUALS) {
                    if (defaultEquals(context, node)) {
                        if (withinCastWithEquals(context, node)) {
                            return
                        }

                        val message =
                            "Suspicious equality check: `equals()` is not implemented in ${left.className}"
                        val location = node.operatorIdentifier?.let {
                            context.getLocation(it)
                        } ?: context.getLocation(node)
                        context.report(ISSUE, node, location, message)
                    }
                } else {
                    val message = if (isKotlin(node.sourcePsi))
                        "Suspicious equality check: Did you mean `==` instead of `===` ?"
                    else
                        "Suspicious equality check: Did you mean `.equals()` instead of `==` ?"
                    val location = node.operatorIdentifier?.let {
                        context.getLocation(it)
                    } ?: context.getLocation(node)
                    context.report(ISSUE, node, location, message)
                }
            }
        }
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            androidSpecific = true,
            moreInfo = "https://issuetracker.google.com/116789824",
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}
