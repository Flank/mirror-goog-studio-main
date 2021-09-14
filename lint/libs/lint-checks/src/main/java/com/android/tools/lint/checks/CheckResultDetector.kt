/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValue
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isJava
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.nextStatement
import com.android.tools.lint.detector.api.previousStatement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.java.JavaUTernaryIfExpression
import org.jetbrains.uast.skipParenthesizedExprUp
import java.util.EnumSet

class CheckResultDetector : AbstractAnnotationDetector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(
        CHECK_RESULT_ANNOTATION.oldName(),
        CHECK_RESULT_ANNOTATION.newName(),
        FINDBUGS_ANNOTATIONS_CHECK_RETURN_VALUE,
        JAVAX_ANNOTATION_CHECK_RETURN_VALUE,
        ERRORPRONE_CAN_IGNORE_RETURN_VALUE,
        "io.reactivex.annotations.CheckReturnValue",
        "com.google.errorprone.annotations.CheckReturnValue"
    )

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type != AnnotationUsageType.METHOD_OVERRIDE && super.isApplicableAnnotationUsage(type)
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        if (annotationInfo.qualifiedName == ERRORPRONE_CAN_IGNORE_RETURN_VALUE) {
            return
        }

        val method = usageInfo.referenced as? PsiMethod ?: return
        if (method.returnType == PsiType.VOID || method.isConstructor) {
            return
        }

        if (isExpressionValueUnused(element)) {
            // If this CheckResult annotation is from a class, check to see
            // if it's been reversed with @CanIgnoreReturnValue
            if (usageInfo.anyCloser { it.qualifiedName == ERRORPRONE_CAN_IGNORE_RETURN_VALUE }) {
                return
            }
            if (context.isTestSource && expectsSideEffect(context, element)) {
                return
            }

            val methodName = JavaContext.getMethodName(element)
            val annotation = annotationInfo.annotation
            val suggested = getAnnotationStringValue(
                annotation,
                AnnotationDetector.ATTR_SUGGEST
            )

            // Failing to check permissions is a potential security issue (and had an existing
            // dedicated issue id before which people may already have configured with a
            // custom severity in their LintOptions etc) so continue to use that issue
            // (which also has category Security rather than Correctness) for these:
            var issue = CHECK_RESULT
            if (methodName != null && methodName.startsWith("check") &&
                methodName.contains("Permission")
            ) {
                issue = CHECK_PERMISSION
            }

            var message = String.format(
                "The result of `%1\$s` is not used",
                methodName
            )
            if (suggested != null) {
                // TODO: Resolve suggest attribute (e.g. prefix annotation class if it starts
                // with "#" etc?
                message = String.format(
                    "The result of `%1\$s` is not used; did you mean to call `%2\$s`?",
                    methodName, suggested
                )
            } else if ("intersect" == methodName && context.evaluator.isMemberInClass(
                    method,
                    "android.graphics.Rect"
                )
            ) {
                message += ". If the rectangles do not intersect, no change is made and the " +
                    "original rectangle is not modified. These methods return false to " +
                    "indicate that this has happened."
            }

            val fix = if (suggested != null) {
                fix().data(KEY_SUGGESTION, suggested)
            } else {
                null
            }

            val location = context.getLocation(element)
            report(context, issue, element, location, message, fix)
        }
    }

    /**
     * In unit tests it's often acceptable to ignore the return
     * value because you're either describing a mock of checking for
     * exceptions being thrown.
     */
    private fun expectsSideEffect(context: JavaContext, element: UElement): Boolean {
        val containingMethod = element.getParentOfType(UMethod::class.java)

        // (1) try { annotated(); fail()/error()/throw X } catch { }
        val nextStatement = element.nextStatement()?.findSelector()
        if (nextStatement is UCallExpression) {
            val methodName = nextStatement.methodName
            // (Ideally we'd look for the Kotlin type `Nothing` here instead of checking
            // for methods named error and TODO, but UAST does not expose Kotlin types,
            // only the mapped types (e.g. both Unit and Nothing maps to void).
            if (methodName == "fail" || methodName == "error" || methodName == "TODO") {
                return true
            }
        }

        // (2) @Test(expect=Exception.class) method() { ...; annotated(); ... }
        //noinspection ExternalAnnotations
        val annotations = containingMethod?.uAnnotations
        if (annotations != null && annotations.any {
            it.qualifiedName == "org.junit.Test" && it.findAttributeValue("expected")?.evaluate() != null
        }
        ) {
            return true
        }

        // (3) Within the context of a ThrowingRunnable/Executable, which includes
        //     assertThrows(Throwable.class, () => { me(); })
        if (nextStatement == null) {
            val lambda = skipParenthesizedExprUp(element.getParentOfType(ULambdaExpression::class.java))
            if (lambda is UExpression) {
                val call = lambda.uastParent
                if (call is UCallExpression) {
                    val resolved = call.resolve()
                    if (resolved != null) {
                        val parameter: PsiParameter? =
                            context.evaluator.computeArgumentMapping(call, resolved)[lambda]
                        if (parameter != null && isThrowingRunnable(parameter.type.canonicalText)) {
                            return true
                        }
                    }
                }
            } else if (containingMethod != null) {
                // Anonymous inner class?
                //  assertThrows(Throwable.class, new ThrowingRunable() { ... me(); });
                val containingClass = containingMethod.uastParent
                if (containingClass is UAnonymousClass) {
                    for (type in containingClass.superTypes) {
                        if (isThrowingRunnable(type.canonicalText)) {
                            return true
                        }
                    }
                }
            }
        }

        // (4) expectedException.expect(Foo.class); me();
        val previousStatement = element.previousStatement()?.findSelector()
        if (previousStatement is UCallExpression) {
            previousStatement.resolve()?.let { calledMethod ->
                val containingClass = calledMethod.containingClass?.qualifiedName
                if (containingClass == "org.junit.rules.ExpectedException") {
                    return true
                }
            }
        }

        // (5) Mockito invocation
        if (element is UCallExpression) {
            val receiver = element.receiver
            if (receiver is UResolvable) {
                val resolved = receiver.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass?.qualifiedName
                    if (containingClass != null && containingClass.startsWith("org.mockito.")) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun isThrowingRunnable(s: String): Boolean {
        // See Matchers.CLASSES_CONSIDERED_THROWING in errorprone
        return when (s) {
            "org.junit.function.ThrowingRunnable",
            "org.junit.jupiter.api.function.Executable",
            "org.assertj.core.api.ThrowableAssert\$ThrowingCallable",
            "com.google.devtools.build.lib.testutil.MoreAsserts\$ThrowingRunnable",
            "com.google.truth.ExpectFailure.AssertionCallback",
            "com.google.truth.ExpectFailure.DelegatedAssertionCallback",
            "com.google.truth.ExpectFailure.StandardSubjectBuilderCallback",
            "com.google.truth.ExpectFailure.SimpleSubjectBuilderCallback" -> true
            else -> false
        }
    }

    companion object {
        fun isExpressionValueUnused(element: UElement): Boolean {
            if (element is UParenthesizedExpression) {
                return isExpressionValueUnused(element.expression)
            }

            var prev: UElement = element.getParentOfType(UExpression::class.java, false) ?: return true
            var curr: UElement = prev.uastParent ?: return true
            while (curr is UQualifiedReferenceExpression && curr.selector === prev || curr is UParenthesizedExpression) {
                prev = curr
                curr = curr.uastParent ?: return true
            }

            @Suppress("RedundantIf")
            if (curr is UBlockExpression) {
                // In Java, it's apparent when an expression is unused:
                // the parent is a block expression. However, in Kotlin it's
                // much trickier: values can flow through blocks and up through
                // if statements, try statements.
                //
                // In Kotlin, we consider an expression unused if its parent
                // is not a block, OR, the expression is not the last statement
                // in the block, OR, recursively the parent expression is not
                // used (e.g. you're in an if, but that if statement is itself
                // not doing anything with the value.)
                val block = curr
                val expression = prev
                val index = block.expressions.indexOf(expression)
                if (index == -1) {
                    return true
                }

                if (index < block.expressions.size - 1) {
                    // Not last child
                    return true
                }

                // It's the last child: see if the parent is unused
                val parent = skipParenthesizedExprUp(curr.uastParent)
                if (parent is ULambdaExpression && isKotlin(curr.sourcePsi)) {
                    val expressionType = parent.getExpressionType()?.canonicalText
                    if (expressionType != null &&
                        expressionType.startsWith("kotlin.jvm.functions.Function") &&
                        expressionType.endsWith("kotlin.Unit>")
                    ) {
                        // We know that this lambda does not return anything so the value is unused
                        return true
                    }
                    // Lambda block: for now assume used (e.g. parameter
                    // in call. Later consider recursing here to
                    // detect if the lambda itself is unused.
                    return false
                }

                if (isJava(curr.sourcePsi)) {
                    // In Java there's no implicit passing to the parent
                    return true
                }

                // It's the last child: see if the parent is unused
                parent ?: return true
                if (parent is UMethod || parent is UClassInitializer) {
                    return true
                }
                return isExpressionValueUnused(parent)
            } else if (curr is UMethod && curr.isConstructor) {
                return true
            } else if (curr is UIfExpression) {
                if (curr.condition === prev) {
                    return false
                } else if (curr is JavaUTernaryIfExpression) {
                    // Ternary expressions can only be used as expressions, not statements,
                    // so we know that the value is used
                    return false
                }
                val parent = skipParenthesizedExprUp(curr.uastParent) ?: return true
                if (parent is UMethod || parent is UClassInitializer) {
                    return true
                }
                return isExpressionValueUnused(curr)
            } else if (curr is UMethod || curr is UClassInitializer) {
                return true
            } else {
                @Suppress("UnstableApiUsage")
                if (curr is UYieldExpression) {
                    val p2 = skipParenthesizedExprUp((skipParenthesizedExprUp(curr.uastParent))?.uastParent)
                    val body = p2 as? USwitchClauseExpressionWithBody ?: return false
                    val switch = body.getParentOfType(USwitchExpression::class.java) ?: return true
                    return isExpressionValueUnused(switch)
                }
                // Some other non block node type, such as assignment,
                // method declaration etc: not unused
                // TODO: Make sure that a void/unit method inline declaration
                // works correctly
                return false
            }
        }

        const val KEY_SUGGESTION = "suggestion"

        private val IMPLEMENTATION = Implementation(
            CheckResultDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            Scope.JAVA_FILE_SCOPE
        )

        /** Method result should be used. */
        @JvmField
        val CHECK_RESULT = Issue.create(
            id = "CheckResult",
            briefDescription = "Ignoring results",
            explanation = """
                Some methods have no side effects, and calling them without doing something \
                without the result is suspicious.""",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /**
         * Failing to enforce security by just calling check permission.
         */
        @JvmField
        val CHECK_PERMISSION = Issue.create(
            id = "UseCheckPermission",
            briefDescription = "Using the result of check permission calls",
            explanation = """
                You normally want to use the result of checking a permission; these methods \
                return whether the permission is held; they do not throw an error if the \
                permission is not granted. Code which does not do anything with the return \
                value probably meant to be calling the enforce methods instead, e.g. rather \
                than `Context#checkCallingPermission` it should call \
                `Context#enforceCallingPermission`.""",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
    }
}
