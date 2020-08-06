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
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PARAMETER
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.openapi.util.Key
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
 *
 * See [http://go/do-not-freeze] for more information on IntelliJ threading rules and best
 * practices.
 *
 * This is a clone of `ThreadDetector` from "upstream" lint-checks, with slightly simpler rules and
 * no support for annotating methods with more than one threading annotation, since in the IDE the
 * threading annotations are exclusive and not meant to be combined.
 */
class IntellijThreadDetector : Detector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = THREADING_ANNOTATIONS.toList()

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
        type == METHOD_CALL || type == METHOD_CALL_CLASS || type == METHOD_CALL_PARAMETER

    /**
     * Handles a given UAST node relevant to our annotations.
     *
     * [com.android.tools.lint.client.api.AnnotationHandler] will call us repeatedly (once for every
     * element in [annotations]) if there are multiple annotations on the target method or method
     * parameter (see [checkThreading]), but we check every UAST node only once, against all
     * annotations on the target and the caller at once.
     *
     * The reason for this is that depending on [type], [annotations] is populated from either the
     * target ([METHOD_CALL]) or the caller ([METHOD_CALL_PARAMETER]), which makes it hard to handle
     * the two cases consistently.
     *
     * Marking the node also means we will ignore class-level annotations if method-level
     * annotations were present, since [com.android.tools.lint.client.api.AnnotationHandler] handles
     * [METHOD_CALL] before [METHOD_CALL_CLASS].
     */
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
        if (method == null) return
        val usagePsi = usage.sourcePsi ?: return
        if (usagePsi.getUserData(CHECKED) == true) return
        usagePsi.putUserData(CHECKED, true)

        // Meaning of the arguments we are given depends on `type`, get what we need accordingly:
        when (type) {
            METHOD_CALL, METHOD_CALL_CLASS -> {
                checkThreading(
                    context,
                    usage,
                    method,
                    getThreadContext(context, usage) ?: return,
                    getThreadsFromMethod(context, method) ?: return
                )
            }
            METHOD_CALL_PARAMETER -> {
                val reference = usage as? UCallableReferenceExpression ?: return
                val referencedMethod = reference.resolve() as? PsiMethod ?: return
                checkThreading(
                    context,
                    usage,
                    referencedMethod,
                    annotations.mapNotNull { it.qualifiedName },
                    getThreadsFromMethod(context, referencedMethod) ?: return
                )
            }
            else -> {
                // We don't care about other types.
                return
            }
        }
    }

    /**
     * Checks if the given [method] can be referenced from [node] which is either a method
     * call or a callable reference passed to another method as a callback.
     *
     * @param context lint scanning context
     * @param node [UElement] that triggered the check, a method call or a callable reference
     * @param method method that will be called. When [node] is a call expression, this is the
     *     method being called. When [node] is a callable reference, this is the referenced method.
     * @param callerThreads fully qualified names of threading annotations effective in the calling
     *     code. When [node] is a call expression, these are annotations on the method containing
     *     the call (or its class). When [node] is a calling reference, these are annotations on the
     *     parameter to which the reference is passed.
     * @param calleeThreads fully qualified names of threading annotations effective on
     *     [method]. These can be specified on the method itself or its class.
     */
    private fun checkThreading(
        context: JavaContext,
        node: UElement,
        method: PsiMethod,
        callerThreads: List<String>,
        calleeThreads: List<String>
    ) {
        val violation = callerThreads.asSequence()
            .flatMap { caller ->
                calleeThreads.asSequence().map { callee -> Pair(caller, callee) }
            }
            .mapNotNull { (caller, callee) ->
                checkForThreadViolation(caller, callee, method)
            }
            .firstOrNull()
            ?: return

        report(context, node, violation)
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
                    UI_THREAD, ANY_THREAD -> { /* (1) */
                    }
                    else -> return null
                }
            }
            UI_THREAD -> {
                when (callerThread) {
                    WORKER_THREAD, ANY_THREAD, SLOW -> { /* (2) */
                    }
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
            IntellijThreadDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        private val CHECKED: Key<Boolean> =
            Key.create("${::CHECKED.javaClass.name}.${::CHECKED.name}")

        /** Calling methods on the wrong thread  */
        @JvmField
        val ISSUE = Issue.create(
            id = "WrongThread",
            briefDescription = "Wrong Thread",

            explanation =
                """
                Ensures that a method which expects to be called on a specific thread, is \
                actually called from that thread. For example, calls on methods in widgets \
                should always be made on the UI thread.
                """,
            //noinspection LintImplUnexpectedDomain
            moreInfo = "http://go/do-not-freeze",
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}
