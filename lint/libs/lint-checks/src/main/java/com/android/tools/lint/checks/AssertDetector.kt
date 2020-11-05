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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.java.JavaUAssertExpression
import org.jetbrains.uast.java.JavaUInstanceCheckExpression
import org.jetbrains.uast.kotlin.KotlinUTypeCheckExpression
import org.jetbrains.uast.toUElement

/**
 * Looks for assertion usages.
 */
class AssertDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        /** Using assertions  */
        @JvmField
        val DISABLED = Issue.create(
            id = "Assert",
            briefDescription = "Ignored Assertions",
            explanation =
                """
                Assertions will never be turned on in Android. (It was possible to enable \
                it in Dalvik with `adb shell setprop debug.assert 1`, but it is not implemented \
                in ART, the runtime for Android 5.0 and later.)

                This means that the assertion will never catch mistakes, and you should not \
                use assertions from Java or Kotlin for debug build checking.

                Instead, perform conditional checking inside `if (BuildConfig.DEBUG) { }` blocks. \
                That constant is a static final boolean which will be true only in debug builds, \
                and false in release builds, and the compiler will completely remove all code \
                inside the `if`-body from the app.

                For example, you can replace
                ```
                    assert(speed > 0, { "Message" })    // Kotlin
                    assert speed > 0 : "Message"        // Java
                ```
                with
                ```
                    if (BuildConfig.DEBUG && !(speed > 0)) {
                        throw new AssertionError("Message")
                    }
                ```
                (Note: This lint check does not flag assertions purely asserting nullness or \
                non-nullness in Java code; these are typically more intended for tools usage \
                than runtime checks.)""",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
	    // 170657532: AssertDetector warn false positive since AGP4.1
	    enabledByDefault = false,
            implementation = Implementation(
                AssertDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        /** In Kotlin arguments to assertions are always evaluated */
        @JvmField
        val EXPENSIVE = Issue.create(
            id = "ExpensiveAssertion",
            briefDescription = "Expensive Assertions",
            explanation =
                """
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

                ```
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

    /** Whether we should flag assertions in the given context */
    private fun isAndroidContext(context: JavaContext): Boolean {
        // Consider whether to instead check context.driver.platforms.contains(Platform.ANDROID)
        return context.mainProject.isAndroidProject
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        // Java assertions -- must use custom AST visitor to find assert nodes
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (!isAndroidContext(context)) {
            return null
        }

        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node is JavaUAssertExpression) {
                    checkJavaAssertion(node, context)
                }
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? =
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

    private fun checkJavaAssertion(
        expression: JavaUAssertExpression,
        context: JavaContext
    ) {
        // Allow "assert true"; it's basically a no-op
        val condition = expression.condition
        if (condition is ULiteralExpression) {
            val value = condition.value
            if (java.lang.Boolean.TRUE == value) {
                return
            }
        } else {
            // Allow assertions of the form "assert foo != null" because they are often used
            // to make statements to tools about known nullness properties. For example,
            // findViewById() may technically return null in some cases, but a developer
            // may know that it won't be when it's called correctly, so the assertion helps
            // to clear nullness warnings. (Ditto for instance of checks as well.)
            if (isNullOrInstanceCheck(condition)) {
                return
            }
        }

        val message =
            getErrorMessage()

        // Attempt to just get the assert keyword location
        val location: Location
        val firstChild = expression.sourcePsi.firstChild
        location = if (firstChild is PsiKeyword && PsiKeyword.ASSERT == firstChild.getText()) {
            context.getLocation(firstChild)
        } else {
            context.getLocation(expression)
        }

        val fix = createJavaBuildConfigFix(context, expression)
        context.report(DISABLED, expression, location, message, fix)
    }

    private fun checkKotlinAssertion(
        context: JavaContext,
        assertion: UCallExpression
    ) {
        val condition = assertion.valueArguments.firstOrNull() ?: return

        if (isAndroidContext(context) && context.isEnabled(DISABLED)) {
            // In Android, assertions are never even checked, so focus the message on that.
            val location = context.getLocation(assertion)
            val message = getErrorMessage()
            context.report(
                DISABLED,
                assertion,
                location,
                message,
                createKotlinBuildConfigFix(context, assertion)
            )
        }

        if (context.isEnabled(EXPENSIVE) && warnAboutWork(assertion, condition)) {
            val location = context.getLocation(condition)
            var message =
                "Kotlin assertion arguments are always evaluated, even when assertions are off"
            val fix: LintFix?
            val cls = assertion.getParentOfType<UClass>(UClass::class.java, true)
            if (cls?.sourcePsi != null) { // sourcePsi == null: top level functions
                fix = createKotlinAssertionStatusFix(context, assertion)
                message += ". Consider surrounding assertion with `if (javaClass.desiredAssertionStatus()) { assert(...) }`"
            } else {
                fix = null
            }

            context.report(EXPENSIVE, assertion, location, message, fix)
        }
    }

    private fun getErrorMessage(): String =
        "Assertions are never enabled in Android. Use `BuildConfig.DEBUG` conditional checks instead"

    private fun createJavaBuildConfigFix(
        context: JavaContext,
        assertion: JavaUAssertExpression
    ): LintFix? {
        val pkgPrefix = context.mainProject.`package` ?: return null
        val condition = assertion.condition
        val replacement =
            when ((condition as? ULiteralExpression)?.evaluate()) {
                true -> return null
                false -> "$pkgPrefix.BuildConfig.DEBUG" // assert false
                else -> { // assert <some other condition>
                    val check = negateSource(condition) ?: return null
                    "$pkgPrefix.BuildConfig.DEBUG && $check"
                }
            }
        val message = assertion.message?.sourcePsi?.text ?: "\"Assertion failed\""
        return fix().name("Replace with BuildConfig.DEBUG check")
            .replace()
            .range(context.getLocation(assertion))
            .all()
            .with("if ($replacement) { throw new AssertionError($message); }")
            .reformat(true)
            .shortenNames()
            .build()
    }

    private fun createKotlinBuildConfigFix(
        context: JavaContext,
        assertCall: UCallExpression
    ): LintFix? {
        val pkgPrefix = context.mainProject.`package` ?: return null
        val arguments = assertCall.valueArguments
        val condition = arguments[0]
        val replacement =
            when ((condition as? ULiteralExpression)?.evaluate()) {
                true -> return null
                false -> "$pkgPrefix.BuildConfig.DEBUG" // assert false
                else -> { // assert <some other condition>
                    val check = negateSource(condition) ?: return null
                    "$pkgPrefix.BuildConfig.DEBUG && $check"
                }
            }
        val message = (arguments.lastOrNull() as? ULambdaExpression)?.body?.sourcePsi?.text?.trim()
            ?: "\"Assertion failed\""
        return fix().name("Replace with BuildConfig.DEBUG check")
            .replace()
            .range(context.getLocation(assertCall))
            .all()
            .with("if ($replacement) { error($message) }")
            .reformat(true)
            .shortenNames()
            .build()
    }

    private fun createKotlinAssertionStatusFix(
        context: JavaContext,
        assertCall: UCallExpression
    ): LintFix? {
        return fix().name("Surround with desiredAssertionStatus() check")
            .replace()
            .range(context.getLocation(assertCall))
            .pattern("(.*)")
            .with("if (javaClass.desiredAssertionStatus()) { \\k<1> }")
            .reformat(true)
            .build()
    }

    /**
     * Checks whether the given expression is purely a non-null check, e.g. it will return
     * true for expressions like "a != null" and "a != null && b != null" and
     * "b == null || c != null". Similarly it will also return true for things like
     * "a instanceof Foo".
     */
    private fun isNullOrInstanceCheck(expression: UExpression): Boolean {
        if (expression is UParenthesizedExpression) {
            return isNullOrInstanceCheck(expression.expression)
        }
        return when (expression) {
            is JavaUInstanceCheckExpression -> true
            is UBinaryExpression -> {
                val lOperand = expression.leftOperand
                val rOperand = expression.rightOperand
                lOperand.isNullLiteral() || rOperand.isNullLiteral() ||
                    isNullOrInstanceCheck(lOperand) && isNullOrInstanceCheck(rOperand)
            }
            is UPolyadicExpression -> {
                for (operand in expression.operands) {
                    if (!isNullOrInstanceCheck(operand)) {
                        return false
                    }
                }
                true
            }
            else -> false
        }
    }

    /**
     * Returns true if the given assert call is performing computation in its condition
     * without explicitly checking for whether assertions are enabled
     */
    private fun warnAboutWork(assertCall: UCallExpression, condition: UExpression): Boolean {
        return isExpensive(condition, 0) && !isWithinAssertionStatusCheck(assertCall)
    }

    /** Returns true if the given logging call performs "work" to compute the message  */
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

    /** Returns the source code for the negation of the given condition */
    private fun negateSource(condition: UExpression): String? {
        if (condition is UBinaryExpression) {
            val reverse =
                when (condition.operator) {
                    UastBinaryOperator.LESS -> UastBinaryOperator.GREATER_OR_EQUALS
                    UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER
                    UastBinaryOperator.GREATER -> UastBinaryOperator.LESS_OR_EQUALS
                    UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS
                    UastBinaryOperator.EQUALS -> UastBinaryOperator.NOT_EQUALS
                    UastBinaryOperator.IDENTITY_EQUALS -> UastBinaryOperator.IDENTITY_NOT_EQUALS
                    UastBinaryOperator.NOT_EQUALS -> UastBinaryOperator.EQUALS
                    UastBinaryOperator.IDENTITY_NOT_EQUALS -> UastBinaryOperator.IDENTITY_EQUALS
                    else -> null
                }
            if (reverse != null) {
                val left = condition.leftOperand.sourcePsi?.text
                val right = condition.rightOperand.sourcePsi?.text
                if (left != null && right != null) {
                    // We should just be able to use reverse.text as the operator text
                    // but unfortunately it returns === and !== for identity equals in Java
                    // so we have to special case this
                    val operatorText =
                        when {
                            isKotlin(condition.sourcePsi) -> reverse.text
                            reverse == UastBinaryOperator.IDENTITY_EQUALS -> "=="
                            reverse == UastBinaryOperator.IDENTITY_NOT_EQUALS -> "!="
                            else -> reverse.text
                        }
                    return "$left $operatorText $right"
                }
            }
        } else if (condition is UUnaryExpression && condition.operator == UastPrefixOperator.LOGICAL_NOT) {
            val nested = condition.operand.sourcePsi?.text
            if (nested != null) {
                return nested
            }
        } else if (condition is UReferenceExpression ||
            condition is UCallExpression
        ) {
            val source = condition.sourcePsi?.text?.trim()
            if (source != null) {
                return "!$source"
            }
        }

        val source = condition.sourcePsi?.text?.trim() ?: return null
        return "!(${source.removeSurrounding("(", ")")})"
    }
}
