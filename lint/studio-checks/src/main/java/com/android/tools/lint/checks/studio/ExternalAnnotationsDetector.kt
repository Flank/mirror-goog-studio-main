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

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUMethod

/**
 * Detects Lint code that doesn't handle external annotations correctly.
 */
class ExternalAnnotationsDetector : Detector(), SourceCodeScanner {

    companion object Issues {

        @JvmField
        val ISSUE = Issue.create(
            id = "ExternalAnnotations",
            briefDescription = "External annotations not considered",
            explanation =
                """
                Lint supports XML files with "external annotations", which means any detectors that \
                recognize certain annotations should get them from `JavaEvaluator.getAllAnnotations` \
                and not by calling `uAnnotations` directly on UAST or PSI elements.
            """,
            severity = Severity.ERROR,
            implementation = Implementation(
                ExternalAnnotationsDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val relevantClasses = listOf(
            "com.intellij.psi.PsiModifierListOwner",
            "com.intellij.psi.PsiAnnotationOwner",
            "org.jetbrains.uast.UAnnotated",
            "com.intellij.lang.jvm.JvmAnnotatedElement"
        )
    }

    override fun getApplicableMethodNames() = listOf("getAnnotations", "getUAnnotations")
    override fun getApplicableReferenceNames() = listOf("annotations", "uAnnotations")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        check(node, method, context)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        check(reference, referenced as? PsiMember ?: return, context)
    }

    private fun check(
        expression: UExpression,
        member: PsiMember,
        context: JavaContext
    ) {
        val evaluator = context.evaluator
        if (relevantClasses.any { evaluator.isMemberInClass(member, it) } &&
            isRelevantCaller(expression, evaluator)
        ) {
            context.report(
                ISSUE,
                expression,
                context.getLocation(expression),
                "${member.name} used instead of `JavaContext.getAllAnnotations`."
            )
        }
    }

    private fun isRelevantCaller(node: UExpression, evaluator: JavaEvaluator): Boolean {
        val callerClass = node.getContainingUMethod()?.containingClass ?: return false
        return evaluator.inheritsFrom(callerClass, Detector::class.java.name, false) ||
            callerClass.qualifiedName.orEmpty().startsWith("com.android.tools.lint.")
    }
}
