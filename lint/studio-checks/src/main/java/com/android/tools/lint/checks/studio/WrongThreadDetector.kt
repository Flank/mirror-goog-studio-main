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
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_CLASS
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PACKAGE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PARAMETER
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
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
val WORKER_THREAD = WorkerThread::class.java.canonicalName!!
val THREADING_ANNOTATIONS = setOf(SLOW, UI_THREAD, ANY_THREAD, WORKER_THREAD)

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
        when (type) {
            METHOD_CALL, METHOD_CALL_CLASS, METHOD_CALL_PACKAGE -> {
                checkMethodCallThreading(context, usage, method ?: return, qualifiedName)
            }
            METHOD_CALL_PARAMETER -> {
                checkCallableReference(
                    context,
                    (usage as? UCallableReferenceExpression) ?: return,
                    qualifiedName
                )
            }
            else -> return // We don't care about other [AnnotationUsageType]s.
        }
    }

    /**
     * Checks that the given method call uses the correct threading context
     *
     * @param context the lint scanning context
     * @param methodCallNode [UElement] pointing to the method call node
     * @param method [PsiMethod] being called
     * @param calleeThread the called method annotation being analyzed
     */
    private fun checkMethodCallThreading(
        context: JavaContext,
        methodCallNode: UElement,
        method: PsiMethod,
        calleeThread: String
    ) {
        val callerThreads = getThreadContext(context, methodCallNode) ?: return

        val violation = callerThreads.asSequence()
            .mapNotNull { checkForThreadViolation(it, calleeThread, method) }
            .firstOrNull() ?: return

        report(context, methodCallNode, violation)
    }

    /**
     * Checks that the given callable reference (e.g. `this::compute`), passed as an argument to
     * another method, obeys the contract of threading annotations.
     *
     * @param context the lint scanning context
     * @param reference the UAST node of the reference
     * @param callerThread fully qualified name of the threading annotation present on the method
     *                     parameter, which needs to be compatible with all annotations on the
     *                     method being passed as reference
     */
    private fun checkCallableReference(
        context: JavaContext,
        reference: UCallableReferenceExpression,
        callerThread: String
    ) {
        val referencedMethod = reference.resolve() as? PsiMethod ?: return
        val calleeThreads = context.evaluator
            .getAllAnnotations(referencedMethod, false)
            .filter { it.isThreadingAnnotation() }
            .mapNotNull { it.qualifiedName }

        val violation = calleeThreads.asSequence()
            .mapNotNull { checkForThreadViolation(callerThread, it, referencedMethod) }
            .firstOrNull() ?: return

        report(context, reference, violation)
    }

    /** Checks for a thread annotation violation, returning an error message if found. */
    private fun checkForThreadViolation(
        callerThread: String,
        calleeThread: String,
        method: PsiMethod
    ): String? {

        // We enforce the following constraints:
        // (1) [UI responsiveness] Discourage {@UiThread, @AnyThread} --> {@Slow, @WorkerThread}
        // (2) [Thread safety] Disallow {@WorkerThread, @AnyThread, @Slow} --> {@UiThread}
        when (calleeThread) {
            SLOW, WORKER_THREAD -> {
                when (callerThread) {
                    UI_THREAD, ANY_THREAD -> { /* (1) */ }
                    else -> return null
                }
            }
            UI_THREAD -> {
                when (callerThread) {
                    WORKER_THREAD, ANY_THREAD, SLOW -> { /* (2) */ }
                    else -> return null
                }
            }
            else -> return null
        }

        val methodDesc = when (method.isConstructor) {
            true -> "Constructor ${method.name}"
            else -> "Method ${method.name}"
        }

        val inferredThread = when (callerThread) {
            WORKER_THREAD, SLOW -> "a worker thread"
            UI_THREAD -> "the UI thread"
            ANY_THREAD -> "any thread"
            else -> return null
        }

        val calleeRequirement = when (calleeThread) {
            SLOW -> "$methodDesc is slow and thus should run on a worker thread"
            WORKER_THREAD -> "$methodDesc is intended to run on a worker thread"
            UI_THREAD -> "$methodDesc must run on the UI thread"
            else -> return null
        }

        return "$calleeRequirement, yet the currently inferred thread is $inferredThread"
    }

    private fun PsiAnnotation.isThreadingAnnotation(): Boolean {
        return THREADING_ANNOTATIONS.contains(qualifiedName)
    }

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
                return getThreadsFromExpressionContext(context, anonClassCall)
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

        return getThreadsFromExpressionContext(context, lambdaCall)
    }

    /**
     * Infers the thread context from a lambda or an anonymous class call expression. This will
     * look into the formal parameters annotation to infer the thread context for the given lambda.
     */
    private fun getThreadsFromExpressionContext(
        context: JavaContext,
        lambdaCall: UExpression?
    ): List<String>? {
        val lambdaCallExpression = lambdaCall?.uastParent as? UCallExpression ?: return null
        val lambdaArgument = lambdaCallExpression.getParameterForArgument(lambdaCall) ?: return null

        val annotations = context.evaluator.getAllAnnotations(lambdaArgument, false)
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
        scope: UElement,
        message: String
    ) {
        context.report(ISSUE, scope, context.getLocation(scope), message, null)
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
