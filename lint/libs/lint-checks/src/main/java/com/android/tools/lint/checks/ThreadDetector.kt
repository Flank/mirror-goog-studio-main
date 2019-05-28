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

import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.tools.lint.checks.AnnotationDetector.ANY_THREAD_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.BINDER_THREAD_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.MAIN_THREAD_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.THREAD_SUFFIX
import com.android.tools.lint.checks.AnnotationDetector.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.AnnotationDetector.WORKER_THREAD_ANNOTATION
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_CLASS
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PARAMETER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.utils.reflection.qualifiedName
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

class ThreadDetector : AbstractAnnotationDetector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(
        UI_THREAD_ANNOTATION.oldName(),
        UI_THREAD_ANNOTATION.newName(),
        MAIN_THREAD_ANNOTATION.oldName(),
        MAIN_THREAD_ANNOTATION.newName(),
        BINDER_THREAD_ANNOTATION.oldName(),
        BINDER_THREAD_ANNOTATION.newName(),
        WORKER_THREAD_ANNOTATION.oldName(),
        WORKER_THREAD_ANNOTATION.newName(),
        ANY_THREAD_ANNOTATION.oldName(),
        ANY_THREAD_ANNOTATION.newName()
    )

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
        if (calleeThreads.any { isCompatibleThread(callerThreads, it) }) {
            return
        }

        val name = method.name
        if (name.startsWith("post") && context.evaluator.isMemberInClass(method, CLASS_VIEW)) {
            // The post()/postDelayed() methods are (currently) missing
            // metadata (@AnyThread); they're on a class marked @UiThread
            // but these specific methods are not @UiThread.
            return
        }

        if (calleeThreads.containsAll(callerThreads)) {
            return
        }

        if (calleeThreads.contains(ANY_THREAD_ANNOTATION.oldName()) ||
            calleeThreads.contains(ANY_THREAD_ANNOTATION.newName())
        ) {
            // Any thread allowed? Then we're good!
            return
        }

        val message = String.format(
            "%1\$s %2\$s must be called from the %3\$s thread, currently inferred thread is %4\$s thread",
            if (method.isConstructor) "Constructor" else "Method",
            method.name, describeThreads(calleeThreads, true),
            describeThreads(callerThreads, false)
        )
        val location = context.getLocation(node)
        report(context, THREAD, node, location, message)
    }

    private fun PsiAnnotation.isThreadingAnnotation(): Boolean {
        val signature = this.qualifiedName
        return (signature != null &&
                signature.endsWith(THREAD_SUFFIX) &&
                SUPPORT_ANNOTATIONS_PREFIX.isPrefix(signature))
    }

    private fun describeThreads(annotations: List<String>, any: Boolean): String {
        val sb = StringBuilder()
        for (i in annotations.indices) {
            if (i > 0) {
                if (i == annotations.size - 1) {
                    if (any) {
                        sb.append(" or ")
                    } else {
                        sb.append(" and ")
                    }
                } else {
                    sb.append(", ")
                }
            }
            sb.append(describeThread(annotations[i]))
        }
        return sb.toString()
    }

    private fun describeThread(annotation: String): String = when (annotation) {
        UI_THREAD_ANNOTATION.oldName(), UI_THREAD_ANNOTATION.newName() -> "UI"
        MAIN_THREAD_ANNOTATION.oldName(), MAIN_THREAD_ANNOTATION.newName() -> "main"
        BINDER_THREAD_ANNOTATION.oldName(), BINDER_THREAD_ANNOTATION.newName() -> "binder"
        WORKER_THREAD_ANNOTATION.oldName(), WORKER_THREAD_ANNOTATION.newName() -> "worker"
        ANY_THREAD_ANNOTATION.oldName(), ANY_THREAD_ANNOTATION.newName() -> "any"
        else -> "other"
    }

    /** returns true if the two threads are compatible  */
    private fun isCompatibleThread(callers: List<String>, callee: String): Boolean {
        // ALL calling contexts must be valid
        assert(callers.isNotEmpty())
        for (caller in callers) {
            if (!isCompatibleThread(caller, callee)) {
                return false
            }
        }

        return true
    }

    /** returns true if the two threads are compatible  */
    private fun isCompatibleThread(caller: String, callee: String): Boolean {
        if (callee == caller) {
            return true
        }

        if (ANY_THREAD_ANNOTATION.isEquals(callee)) {
            return true
        }

        // Allow @UiThread and @MainThread to be combined
        if (UI_THREAD_ANNOTATION.isEquals(callee)) {
            if (MAIN_THREAD_ANNOTATION.isEquals(caller)) {
                return true
            }
        } else if (MAIN_THREAD_ANNOTATION.isEquals(callee)) {
            if (UI_THREAD_ANNOTATION.isEquals(caller)) {
                return true
            }
        }

        // Mismatched androidx: ignore package, just match on class name
        val callerNameIndex = caller.lastIndexOf('.')
        val calleeNameIndex = callee.lastIndexOf('.')
        if (callerNameIndex != -1 && calleeNameIndex != -1) {
            return caller.regionMatches(
                callerNameIndex, callee, calleeNameIndex,
                caller.length - callerNameIndex, false
            )
        }

        return false
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
    private fun getThreadsFromMethod(context: JavaContext, originalMethod: PsiMethod?): List<String>? {
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
        val name = annotation.qualifiedName
        if (name != null && SUPPORT_ANNOTATIONS_PREFIX.isPrefix(name) &&
            name.endsWith(THREAD_SUFFIX)
        ) {
            if (resultList == null) {
                resultList = ArrayList(4)
            }

            // Ensure that we always use the same package such that we don't think
            // android.support.annotation.UiThread != androidx.annotation.UiThread

            if (name.startsWith(SUPPORT_ANNOTATIONS_PREFIX.newName())) {
                val oldName = SUPPORT_ANNOTATIONS_PREFIX.oldName() +
                        name.substring(SUPPORT_ANNOTATIONS_PREFIX.newName().length)
                resultList.add(oldName)
            } else {
                resultList.add(name)
            }
        }
        return resultList
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            ThreadDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        private val CHECKED: Key<Boolean> = Key.create(::CHECKED.qualifiedName)

        /** Calling methods on the wrong thread  */
        @JvmField
        val THREAD = Issue.create(
            id = "WrongThread",
            briefDescription = "Wrong Thread",

            explanation = """
                Ensures that a method which expects to be called on a specific thread, is \
                actually called from that thread. For example, calls on methods in widgets \
                should always be made on the UI thread.
                """,
            moreInfo = "http://developer.android.com/guide/components/processes-and-threads.html#Threads",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )
    }
}
