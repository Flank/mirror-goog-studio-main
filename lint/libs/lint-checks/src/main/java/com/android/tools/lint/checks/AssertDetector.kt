/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.kotlin.KotlinUTypeCheckExpression
import org.jetbrains.uast.toUElement

/** Looks for assertion usages. */
class AssertDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        /** In Kotlin arguments to assertions are always evaluated. */
        @JvmField
        val EXPENSIVE = Issue.create(
            id = "ExpensiveAssertion",
            briefDescription = "Expensive Assertions",
            explanation = """
                In Kotlin, assertions are not handled the same way as from the Java programming \
                language. In particular, they're just implemented as a library call, and inside \
                the library call the error is only thrown if assertions are enabled.

                This means that the arguments to the `assert` call will **always** \
                be evaluated. If you're doing any computation in the expression being \
                asserted, that computation will unconditionally be performed whether or not \
                assertions are turned on. This typically turns into wasted work in release \
                builds.

                This check looks for cases where the assertion condition is nontrivial, e.g. \
                it is performing method calls or doing more work than simple comparisons \
                on local variables or fields.

                You can work around this by writing your own inline assert method instead:

                ```kotlin
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                inline fun assert(condition: () -> Boolean) {
                    if (_Assertions.ENABLED && !condition()) {
                        throw AssertionError()
                    }
                }
                ```

                In Android, because assertions are not enforced at runtime, instead use this:

                ```kotlin
                inline fun assert(condition: () -> Boolean) {
                    if (BuildConfig.DEBUG && !condition()) {
                        throw AssertionError()
                    }
                }
                ```
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AssertDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            enabledByDefault = false
        )
    }

    override fun getApplicableMethodNames(): List<String> =
        // Kotlin assertions -- regular method call
        listOf("assert")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Make sure it's Kotlin, and that the assert() call being called is the stdlib one
        if (!isKotlin(node.sourcePsi)) {
            return
        }

        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass != "kotlin.PreconditionsKt" &&
            containingClass != "kotlin.PreconditionsKt__AssertionsJVMKt"
        ) {
            return
        }

        checkKotlinAssertion(context, node)
    }

    private fun checkKotlinAssertion(
        context: JavaContext,
        assertion: UCallExpression
    ) {
        val condition = assertion.valueArguments.firstOrNull() ?: return
        if (context.isEnabled(EXPENSIVE) && warnAboutWork(assertion, condition)) {
            val location = context.getLocation(condition)
            var message =
                "Kotlin assertion arguments are always evaluated, even when assertions are off"
            val fix: LintFix?
            val cls = assertion.getParentOfType(UClass::class.java, true)
            if (cls?.sourcePsi != null) { // sourcePsi == null: top level functions
                fix = createKotlinAssertionStatusFix(context, assertion)
                message += ". Consider surrounding assertion with `if (javaClass.desiredAssertionStatus()) { assert(...) }`"
            } else {
                fix = null
            }

            context.report(EXPENSIVE, assertion, location, message, fix)
        }
    }

    private fun createKotlinAssertionStatusFix(
        context: JavaContext,
        assertCall: UCallExpression
    ): LintFix {
        return fix().name("Surround with desiredAssertionStatus() check")
            .replace()
            .range(context.getLocation(assertCall))
            .pattern("(.*)")
            .with("if (javaClass.desiredAssertionStatus()) { \\k<1> }")
            .reformat(true)
            .build()
    }

    /**
     * Returns true if the given assert call is performing computation
     * in its condition without explicitly checking for whether
     * assertions are enabled.
     */
    private fun warnAboutWork(assertCall: UCallExpression, condition: UExpression): Boolean {
        return isExpensive(condition, 0) && !isWithinAssertionStatusCheck(assertCall)
    }

    /**
     * Returns true if the given logging call performs "work" to compute
     * the message.
     */
    private fun isExpensive(argument: UExpression, depth: Int): Boolean {
        if (depth == 4) {
            return true
        }
        if (argument is ULiteralExpression || argument is UInstanceExpression) {
            return false
        }
        if (argument is UBinaryExpressionWithType) {
            return if (argument is KotlinUTypeCheckExpression) {
                false
            } else {
                isExpensive(argument.operand, depth + 1)
            }
        }
        if (argument is UPolyadicExpression) {
            for (value in argument.operands) {
                if (isExpensive((value), depth + 1)) {
                    return true
                }
            }
            return false
        } else if (argument is UParenthesizedExpression) {
            return isExpensive(argument.expression, depth + 1)
        } else if (argument is UBinaryExpression) {
            return isExpensive(
                argument.leftOperand,
                depth + 1
            ) || isExpensive(argument.rightOperand, depth + 1)
        } else if (argument is UUnaryExpression) {
            return isExpensive(argument.operand, depth + 1)
        } else if (argument is USimpleNameReferenceExpression) {
            // Just a simple local variable/field reference
            return false
        } else if (argument is UQualifiedReferenceExpression) {
            if (argument.selector is UCallExpression) {
                return isExpensive(argument.selector, depth + 1)
            }
            val value = argument.evaluate()
            if (value != null) { // constant inlined by compiler
                return false
            }
            val resolved = argument.resolve()
            if (resolved is PsiVariable) {
                // Just a reference to a property/field, parameter or variable
                return false
            }
        } else if (argument is UCallExpression) {
            val method = argument.resolve()
            if (method != null && method !is PsiCompiledElement) {
                val body = method.toUElement(UMethod::class.java)?.uastBody
                    ?: return true
                if (body is UBlockExpression) {
                    val expressions = body.expressions
                    if (expressions.size == 1 && expressions[0] is UReturnExpression) {
                        val retExp = (expressions[0] as UReturnExpression)
                            .returnExpression
                        return retExp == null || isExpensive(retExp, depth + 1)
                    }
                } else {
                    // Expression body
                    return isExpensive(body, depth + 1)
                }
            }
        }

        // Method invocations etc
        return true
    }

    private fun isWithinAssertionStatusCheck(node: UExpression): Boolean {
        var curr = node
        while (true) {
            val ifStatement = curr.getParentOfType<UIfExpression>(true) ?: break
            // This is inefficient, but works around current resolve bug on javaClass access
            if (ifStatement.condition.sourcePsi?.text?.contains("desiredAssertionStatus") == true) {
                return true
            }
            curr = ifStatement
        }

        return false
    }
}
