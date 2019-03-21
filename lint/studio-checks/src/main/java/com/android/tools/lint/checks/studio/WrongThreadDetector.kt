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

package com.android.tools.lint.checks.studio

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.getParentOfType
import java.util.ArrayList

val SLOW = Slow::class.java.canonicalName!!
val UI_THREAD = UiThread::class.java.canonicalName!!
val ANY_THREAD = AnyThread::class.java.canonicalName!!
val THREADING_ANNOTATIONS = setOf(SLOW, UI_THREAD, ANY_THREAD)

/**
 * Looks for calls in the wrong thread context.
 * See [http://go/do-not-freeze]
 */
class WrongThreadDetector : Detector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = THREADING_ANNOTATIONS.toList()

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        if (method != null) {
            checkMethodCallThreading(context, usage, method, qualifiedName)
        }
    }

    /**
     * Checks that the given method call uses the correct threading context
     *
     * @param context the lint scanning context
     * @param methodCallNode [UElement] pointing to the method call node
     * @param method [PsiMethod] being called
     * @param annotationQualifiedName the called method annotation being analyzed
     */
    private fun checkMethodCallThreading(
        context: JavaContext,
        methodCallNode: UElement,
        method: PsiMethod,
        annotationQualifiedName: String
    ) {
        val callerThreads = getThreadContext(context, methodCallNode) ?: return
        if (allThreadsCompatible(callerThreads, annotationQualifiedName)) {
            return
        }

        val message = String.format(
            "%1\$s %2\$s must be called from the %3\$s thread, currently inferred thread is %4\$s thread",
            if (method.isConstructor) "Constructor" else "Method",
            method.name, describeThreads(listOf(annotationQualifiedName), true),
            describeThreads(callerThreads, false)
        )

        val location = context.getLocation(methodCallNode)
        report(context, methodCallNode, location, message)
    }

    private fun PsiAnnotation.isThreadingAnnotation(): Boolean =
        THREADING_ANNOTATIONS.contains(qualifiedName)

    private fun describeThreads(annotations: List<String>, any: Boolean): String {
        val sb = StringBuilder()
        for (i in annotations.indices) {
            if (i > 0) {
                if (i == annotations.size - 1) {
                    sb.append(if (any) " or " else " and ")
                } else {
                    sb.append(", ")
                }
            }
            sb.append(describeThread(annotations[i]))
        }
        return sb.toString()
    }

    private fun describeThread(annotation: String): String = when (annotation) {
        UI_THREAD -> "UI"
        SLOW -> "worker"
        ANY_THREAD -> "any"
        else -> "other"
    }

    /** returns true if the two threads are compatible  */
    private fun allThreadsCompatible(callers: List<String>, callee: String): Boolean {
        // ALL calling contexts must be valid
        assert(!callers.isEmpty())
        return callers.all { allThreadsCompatible(it, callee) }
    }

    /** returns true if the two threads are compatible  */
    private fun allThreadsCompatible(caller: String, callee: String): Boolean =
        (callee == caller) || (ANY_THREAD == callee)

    /** Attempts to infer the current thread context at the site of the given method call  */
    private fun getThreadContext(context: JavaContext, methodCall: UElement): List<String>? {
        val method = methodCall.getParentOfType<UElement>(
            UMethod::class.java, true,
            UAnonymousClass::class.java, ULambdaExpression::class.java
        ) as? PsiMethod

        if (method != null) {
            val containingClass = methodCall.getContainingUClass()
            if (containingClass is UAnonymousClass) {
                val anonClassCall = methodCall.getParentOfType<UObjectLiteralExpression>(
                    UObjectLiteralExpression::class.java, true,
                    UCallExpression::class.java
                )

                // If it's an anonymous class, infer the context from the formal parameter
                // annotation
                return getThreadsFromExpressionContext(anonClassCall)
                    ?: getThreadsFromMethod(context, method)
            }

            return getThreadsFromMethod(context, method)
        }

        // Similarly to the anonymous class call, this might be a lambda call, check for annotated
        // formal parameters that will give us the thread context
        val lambdaCall = methodCall.getParentOfType<ULambdaExpression>(
            ULambdaExpression::class.java, true,
            UAnonymousClass::class.java, ULambdaExpression::class.java
        )

        return getThreadsFromExpressionContext(lambdaCall)
    }

    /**
     * Infers the thread context from a lambda or an anonymous class call expression. This will
     * look into the formal parameters annotation to infer the thread context for the given lambda.
     */
    private fun getThreadsFromExpressionContext(
        lambdaCall: UExpression?
    ): List<String>? {
        val lambdaCallExpression = lambdaCall?.uastParent as? UCallExpression ?: return null
        val lambdaArgument = lambdaCallExpression.getParameterForArgument(lambdaCall) ?: return null

        val annotations = lambdaArgument.annotations
            .filter { it.isThreadingAnnotation() }
            .mapNotNull { it.qualifiedName }
            .toList()

        return if (annotations.isEmpty()) null else annotations
    }

    /** Attempts to infer the current thread context at the site of the given method call  */
    private fun getThreadsFromMethod(
        context: JavaContext,
        originalMethod: PsiMethod?
    ): List<String>? {
        var method = originalMethod
        if (method != null) {
            val evaluator = context.evaluator
            var result: MutableList<String>? = null
            var cls = method.containingClass

            while (method != null) {
                val annotations = evaluator.getAllAnnotations(method, false)
                for (annotation in annotations) {
                    result = addThreadAnnotations(annotation, result)
                }
                if (result != null) {
                    // We don't accumulate up the chain: one method replaces the requirements
                    // of its super methods.
                    return result
                }

                if (evaluator.isStatic(method)) {
                    // For static methods, don't look at surrounding class or "inherited" methods
                    return null
                }

                method = evaluator.getSuperMethod(method)
            }

            // See if we're extending a class with a known threading context
            while (cls != null) {
                val annotations = evaluator.getAllAnnotations(cls, false)
                for (annotation in annotations) {
                    result = addThreadAnnotations(annotation, result)
                }
                if (result != null) {
                    // We don't accumulate up the chain: one class replaces the requirements
                    // of its super classes.
                    return result
                }
                cls = cls.superClass
            }
        }

        // In the future, we could also try to infer the threading context using
        // other heuristics. For example, if we're in a method with unknown threading
        // context, but we see that the method is called by another method with a known
        // threading context, we can infer that that threading context is the context for
        // this thread too (assuming the call is direct).

        return null
    }

    private fun addThreadAnnotations(
        annotation: PsiAnnotation,
        result: MutableList<String>?
    ): MutableList<String>? {
        var resultList = result
        if (annotation.isThreadingAnnotation()) {
            if (resultList == null) {
                resultList = ArrayList(4)
            }

            val name = annotation.qualifiedName
            if (name != null) {
                resultList.add(name)
            }
        }
        return resultList
    }

    private fun report(
        context: JavaContext,
        scope: UElement?,
        location: Location,
        message: String
    ) {
        context.report(ISSUE, scope, location, message, null)
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongThreadDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /** Calling methods on the wrong thread  */
        @JvmField
        val ISSUE = Issue.create(
            id = "WrongThread",
            briefDescription = "Wrong Thread",

            explanation = """
                Ensures that a method which expects to be called on a specific thread, is \
                actually called from that thread. For example, calls on methods in widgets \
                should always be made on the UI thread.
                """,
            moreInfo = "http://go/do-not-freeze",
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}
