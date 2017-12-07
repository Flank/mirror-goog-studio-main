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

import com.android.tools.lint.checks.AnnotationDetector.CHECK_RESULT_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.ERRORPRONE_CAN_IGNORE_RETURN_VALUE
import com.android.tools.lint.checks.AnnotationDetector.FINDBUGS_ANNOTATIONS_CHECK_RETURN_VALUE
import com.android.tools.lint.checks.AnnotationDetector.JAVAX_ANNOTATION_CHECK_RETURN_VALUE
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastLintUtils.containsAnnotation
import com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValue
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis

class CheckResultDetector : AbstractAnnotationDetector(), Detector.UastScanner {
    override fun applicableAnnotations(): List<String> = listOf(
            CHECK_RESULT_ANNOTATION,
            FINDBUGS_ANNOTATIONS_CHECK_RETURN_VALUE,
            JAVAX_ANNOTATION_CHECK_RETURN_VALUE,
            ERRORPRONE_CAN_IGNORE_RETURN_VALUE,
            "io.reactivex.annotations.CheckReturnValue",
            "com.google.errorprone.annotations.CheckReturnValue"
    )

    override fun visitAnnotationUsage(
            context: JavaContext,
            usage: UElement,
            type: AnnotationUsageType,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>) {
        method ?: return

        // Don't inherit CheckResult from packages for now; see
        //  https://issuetracker.google.com/69344103
        // for a common (dagger) package declaration that doesn't have
        // a @CanIgnoreReturnValue exclusion on inject.
        if (allPackageAnnotations.contains(annotation)) {
            return
        }

        if (qualifiedName == ERRORPRONE_CAN_IGNORE_RETURN_VALUE) {
            return
        }

        checkResult(context, usage, method, annotation,
                allMemberAnnotations, allClassAnnotations)
    }

    private fun checkResult(context: JavaContext, element: UElement,
            method: PsiMethod, annotation: UAnnotation,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>) {
        val expression = element.getParentOfType<UExpression>(
                UExpression::class.java, false) ?: return

        val parent = expression.uastParent
        if (parent is UBlockExpression) {
            val blockParent = parent.uastParent
            if (blockParent is ULambdaExpression) {
                // Used in lambda
                return
            }
        }

        if (isExpressionValueUnused(expression)) {

            // If this CheckResult annotation is from a class, check to see
            // if it's been reversed with @CanIgnoreReturnValue
            if (containsAnnotation(allMemberAnnotations, ERRORPRONE_CAN_IGNORE_RETURN_VALUE)
                    || containsAnnotation(allClassAnnotations,
                    ERRORPRONE_CAN_IGNORE_RETURN_VALUE)) {
                return
            }

            val methodName = JavaContext.getMethodName(expression)
            val suggested = getAnnotationStringValue(annotation,
                    AnnotationDetector.ATTR_SUGGEST)

            // Failing to check permissions is a potential security issue (and had an existing
            // dedicated issue id before which people may already have configured with a
            // custom severity in their LintOptions etc) so continue to use that issue
            // (which also has category Security rather than Correctness) for these:
            var issue = CHECK_RESULT
            if (methodName != null && methodName.startsWith("check")
                    && methodName.contains("Permission")) {
                issue = PermissionDetector.CHECK_PERMISSION
            }

            var message = String.format("The result of `%1\$s` is not used",
                    methodName)
            if (suggested != null) {
                // TODO: Resolve suggest attribute (e.g. prefix annotation class if it starts
                // with "#" etc?
                message = String.format(
                        "The result of `%1\$s` is not used; did you mean to call `%2\$s`?",
                        methodName, suggested)
            } else if ("intersect" == methodName && context.evaluator.isMemberInClass(method,
                    "android.graphics.Rect")) {
                message += ". If the rectangles do not intersect, no change is made and the " +
                        "original rectangle is not modified. These methods return false to " +
                        "indicate that this has happened."
            }

            val fix = if (suggested != null) {
                fix().data(suggested)
            } else {
                null
            }

            val location = context.getLocation(expression)
            report(context, issue, expression, location, message, fix)
        }
    }

    private fun isExpressionValueUnused(expression: UExpression): Boolean {
        return expression.getQualifiedParentOrThis().uastParent is UBlockExpression
    }

    companion object {
        private val IMPLEMENTATION = Implementation(CheckResultDetector::class.java,
                Scope.JAVA_FILE_SCOPE)

        /** Method result should be used  */
        @JvmField
        val CHECK_RESULT = Issue.create(
                "CheckResult",
                "Ignoring results",

                "Some methods have no side effects, an calling them without doing something " +
                        "without the result is suspicious. ",

                Category.CORRECTNESS,
                6,
                Severity.WARNING,
                IMPLEMENTATION)

    }
}