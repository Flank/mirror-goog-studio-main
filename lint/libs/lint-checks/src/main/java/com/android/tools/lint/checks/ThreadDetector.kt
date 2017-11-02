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
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastLintUtils.containsAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import java.util.ArrayList

class ThreadDetector : AbstractAnnotationDetector(), Detector.UastScanner {
    override fun applicableAnnotations(): List<String> = listOf(
            UI_THREAD_ANNOTATION,
            MAIN_THREAD_ANNOTATION,
            BINDER_THREAD_ANNOTATION,
            WORKER_THREAD_ANNOTATION,
            ANY_THREAD_ANNOTATION
    )

    override fun visitAnnotationUsage(
            context: JavaContext,
            argument: UElement,
            annotation: UAnnotation,
            qualifiedName: String,
            method: PsiMethod?,
            annotations: List<UAnnotation>,
            allMemberAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>,
            allPackageAnnotations: List<UAnnotation>) {
        if (method != null) {
            checkThreading(context, argument, method, qualifiedName, annotation,
                    allMemberAnnotations,
                    allClassAnnotations)
        }
    }

    private fun checkThreading(
            context: JavaContext,
            node: UElement,
            method: PsiMethod,
            signature: String,
            annotation: UAnnotation,
            allMethodAnnotations: List<UAnnotation>,
            allClassAnnotations: List<UAnnotation>) {
        val threadContext = getThreadContext(context, node)
        if (threadContext != null && !isCompatibleThread(threadContext, signature)) {
            // If the annotation is specified on the class, ignore this requirement
            // if there is another annotation specified on the method.
            if (containsAnnotation(allClassAnnotations, annotation)) {
                if (containsThreadingAnnotation(allMethodAnnotations)) {
                    return
                }
                // Make sure ALL the other context annotations are acceptable!
            } else {
                assert(containsAnnotation(allMethodAnnotations, annotation))
                // See if any of the *other* annotations are compatible.
                var isFirst: Boolean? = null
                for (other in allMethodAnnotations) {
                    if (other === annotation) {
                        if (isFirst == null) {
                            isFirst = true
                        }
                        continue
                    } else if (!isThreadingAnnotation(other)) {
                        continue
                    }
                    if (isFirst == null) {
                        // We'll be called for each annotation on the method.
                        // For each one we're checking *all* annotations on the target.
                        // Therefore, when we're seeing the second, third, etc annotation
                        // on the method we've already checked them, so return here.
                        return
                    }
                    val s = other.qualifiedName
                    if (s != null && isCompatibleThread(threadContext, s)) {
                        return
                    }
                }
            }

            val name = method.name
            if (name.startsWith("post") && context.evaluator.isMemberInClass(method, CLASS_VIEW)) {
                // The post()/postDelayed() methods are (currently) missing
                // metadata (@AnyThread); they're on a class marked @UiThread
                // but these specific methods are not @UiThread.
                return
            }

            var targetThreads = getThreads(context, method)
            if (targetThreads == null) {
                targetThreads = listOf(signature)
            }

            val message = String.format(
                    "%1\$s %2\$s must be called from the %3\$s thread, currently inferred thread is %4\$s thread",
                    if (method.isConstructor) "Constructor" else "Method",
                    method.name, describeThreads(targetThreads, true),
                    describeThreads(threadContext, false))
            val location = context.getLocation(node)
            report(context, THREAD, node, location, message)
        }
    }

    private fun containsThreadingAnnotation(array: List<UAnnotation>): Boolean {
        for (annotation in array) {
            if (isThreadingAnnotation(annotation)) {
                return true
            }
        }

        return false
    }

    private fun isThreadingAnnotation(annotation: UAnnotation): Boolean {
        val signature = annotation.qualifiedName
        return (signature != null
                && signature.endsWith(THREAD_SUFFIX)
                && signature.startsWith(SUPPORT_ANNOTATIONS_PREFIX))
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

    private fun describeThread(annotation: String): String {
        return when (annotation) {
            UI_THREAD_ANNOTATION -> "UI"
            MAIN_THREAD_ANNOTATION -> "main"
            BINDER_THREAD_ANNOTATION -> "binder"
            WORKER_THREAD_ANNOTATION -> "worker"
            ANY_THREAD_ANNOTATION -> "any"
            else -> "other"
        }
    }

    /** returns true if the two threads are compatible  */
    private fun isCompatibleThread(callers: List<String>, callee: String): Boolean {
        // ALL calling contexts must be valid
        assert(!callers.isEmpty())
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

        if (callee == ANY_THREAD_ANNOTATION) {
            return true
        }

        // Allow @UiThread and @MainThread to be combined
        if (callee == UI_THREAD_ANNOTATION) {
            if (caller == MAIN_THREAD_ANNOTATION) {
                return true
            }
        } else if (callee == MAIN_THREAD_ANNOTATION) {
            if (caller == UI_THREAD_ANNOTATION) {
                return true
            }
        }

        return false
    }

    /** Attempts to infer the current thread context at the site of the given method call  */
    private fun getThreadContext(context: JavaContext, methodCall: UElement): List<String>? {

        val method = methodCall.getParentOfType<UElement>(UMethod::class.java, true,
                UAnonymousClass::class.java, ULambdaExpression::class.java) as? PsiMethod
        return getThreads(context, method)
    }

    /** Attempts to infer the current thread context at the site of the given method call  */
    private fun getThreads(context: JavaContext, originalMethod: PsiMethod?): List<String>? {
        var method = originalMethod
        if (method != null) {
            var result: MutableList<String>? = null
            var cls = method.containingClass

            while (method != null) {
                for (annotation in method.modifierList.annotations) {
                    val name = annotation.qualifiedName
                    if (name != null && name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                            && name.endsWith(THREAD_SUFFIX)) {
                        if (result == null) {
                            result = ArrayList(4)
                        }
                        result.add(name)
                    }
                }
                if (result != null) {
                    // We don't accumulate up the chain: one method replaces the requirements
                    // of its super methods.
                    return result
                }
                method = context.evaluator.getSuperMethod(method)
            }

            // See if we're extending a class with a known threading context
            while (cls != null) {
                val modifierList = cls.modifierList
                if (modifierList != null) {
                    for (annotation in modifierList.annotations) {
                        val name = annotation.qualifiedName
                        if (name != null && name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                                && name.endsWith(THREAD_SUFFIX)) {
                            if (result == null) {
                                result = ArrayList(4)
                            }
                            result.add(name)
                        }
                    }
                    if (result != null) {
                        // We don't accumulate up the chain: one class replaces the requirements
                        // of its super classes.
                        return result
                    }
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

    companion object {
        private val IMPLEMENTATION = Implementation(ThreadDetector::class.java,
                Scope.JAVA_FILE_SCOPE)

        /** Calling methods on the wrong thread  */
        @JvmField
        val THREAD = Issue.create(
            "WrongThread",
            "Wrong Thread",

            "Ensures that a method which expects to be called on a specific thread, is actually " +
            "called from that thread. For example, calls on methods in widgets should always " +
            "be made on the UI thread.",

            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "http://developer.android.com/guide/components/processes-and-threads.html#Threads")
    }
}