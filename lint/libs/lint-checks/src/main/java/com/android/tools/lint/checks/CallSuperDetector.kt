/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.support.AndroidxName
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Makes sure that methods call super when overriding methods.
 */
class CallSuperDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            CallSuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /** Missing call to super  */
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingSuperCall",
            briefDescription = "Missing Super Call",
            explanation = """
            Some methods, such as `View#onDetachedFromWindow`, require that you also call the \
            super implementation as part of your method.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        private val CALL_SUPER_ANNOTATION = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "CallSuper")
        private const val ON_DETACHED_FROM_WINDOW = "onDetachedFromWindow"
        private const val ON_VISIBILITY_CHANGED = "onVisibilityChanged"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val superMethod = getRequiredSuperMethod(context, node) ?: return
                if (!callsSuper(node, superMethod)) {
                    val message = "Overriding method should call `super.${node.name}`"
                    val location = context.getNameLocation(node)
                    val fix = fix().map().put(PsiMethod::class.java, superMethod).build()
                    context.report(ISSUE, node, location, message, fix)
                }
            }
        }

    /**
     * Checks whether the given method overrides a method which requires the super method
     * to be invoked, and if so, returns it (otherwise returns null)
     */
    private fun getRequiredSuperMethod(
        context: JavaContext,
        method: UMethod
    ): PsiMethod? {

        val evaluator = context.evaluator
        val directSuper = evaluator.getSuperMethod(method) ?: return null

        val name = method.name
        if (ON_DETACHED_FROM_WINDOW == name) {
            // No longer annotated on the framework method since it's
            // now handled via onDetachedFromWindowInternal, but overriding
            // is still dangerous if supporting older versions so flag
            // this for now (should make annotation carry metadata like
            // compileSdkVersion >= N).
            if (!evaluator.isMemberInSubClassOf(method, CLASS_VIEW, false)) {
                return null
            }
            return directSuper
        } else if (ON_VISIBILITY_CHANGED == name) {
            // From Android Wear API; doesn't yet have an annotation
            // but we want to enforce this right away until the AAR
            // is updated to supply it once @CallSuper is available in
            // the support library
            if (!evaluator.isMemberInSubClassOf(
                    method,
                    "android.support.wearable.watchface.WatchFaceService.Engine", false
                )
            ) {
                return null
            }
            return directSuper
        }

        val annotations = evaluator.getAllAnnotations(directSuper, true)
        for (annotation in annotations) {
            val signature = annotation.qualifiedName
            if (CALL_SUPER_ANNOTATION.isEquals(signature) || signature != null &&
                (signature.endsWith(".OverrideMustInvoke") ||
                        signature.endsWith(".OverridingMethodsMustInvokeSuper"))
            ) {
                return directSuper
            }
        }

        return null
    }

    private fun callsSuper(
        method: UMethod,
        superMethod: PsiMethod
    ): Boolean {
        val visitor = SuperCallVisitor(superMethod)
        method.accept(visitor)
        return visitor.callsSuper
    }

    /** Visits a method and determines whether the method calls its super method  */
    private class SuperCallVisitor constructor(private val targetMethod: PsiMethod) :
        AbstractUastVisitor() {
        var callsSuper: Boolean = false

        override fun visitSuperExpression(node: USuperExpression): Boolean {
            val parent = com.android.tools.lint.detector.api.skipParentheses(node.uastParent)
            if (parent is UReferenceExpression) {
                val resolved = parent.resolve()
                if (resolved == null || // Avoid false positives for type resolution problems
                    targetMethod.isEquivalentTo(resolved)
                ) {
                    callsSuper = true
                }
            }

            return super.visitSuperExpression(node)
        }
    }
}
