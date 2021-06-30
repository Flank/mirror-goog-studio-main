/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.CLASS_CONTENTPROVIDER
import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_RESOURCES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.skipParentheses
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiResourceVariable
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isConstructorCall

/**
 * Checks for missing `recycle` calls on resources that encourage it,
 * and for missing `commit` calls on FragmentTransactions, etc.
 */
class CleanupDetector : Detector(), SourceCodeScanner {

    // ---- implements SourceCodeScanner ----

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            // FragmentManager commit check
            BEGIN_TRANSACTION,

            // Recycle check
            OBTAIN,
            OBTAIN_NO_HISTORY,
            OBTAIN_STYLED_ATTRIBUTES,
            OBTAIN_ATTRIBUTES,
            OBTAIN_TYPED_ARRAY,

            // Release check
            ACQUIRE_CPC,

            // Cursor close check
            QUERY,
            RAW_QUERY,
            QUERY_WITH_FACTORY,
            RAW_QUERY_WITH_FACTORY,

            // SharedPreferences check
            EDIT,

            // Animation
            OF_INT,
            OF_ARGB,
            OF_FLOAT,
            OF_OBJECT,
            OF_PROPERTY_VALUES_HOLDER
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            SURFACE_TEXTURE_CLS,
            SURFACE_CLS,
            VALUE_ANIMATOR_CLS,
            OBJECT_ANIMATOR_CLS,
            ANIMATOR_SET_CLS
        )
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        when (method.name) {
            BEGIN_TRANSACTION -> checkTransactionCommits(context, node, method)
            EDIT -> checkEditorApplied(context, node, method)
            else -> checkResourceRecycled(context, node, method)
        }
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val type = constructor.containingClass?.qualifiedName ?: return
        if (isSuperConstructorCall(node)) return
        if (type == SURFACE_TEXTURE_CLS || type == SURFACE_CLS) {
            checkRecycled(context, node, type, RELEASE)
        } else {
            checkRecycled(context, node, type, START)
        }
    }

    private fun checkResourceRecycled(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val name = method.name

        // Recycle detector
        val containingClass = method.containingClass ?: return
        val evaluator = context.evaluator

        when (name) {
            OBTAIN, OBTAIN_NO_HISTORY ->
                when {
                    evaluator.extendsClass(containingClass, MOTION_EVENT_CLS, false) ->
                        checkRecycled(context, node, MOTION_EVENT_CLS, RECYCLE)
                    evaluator.extendsClass(containingClass, PARCEL_CLS, false) ->
                        checkRecycled(context, node, PARCEL_CLS, RECYCLE)
                    evaluator.extendsClass(containingClass, VELOCITY_TRACKER_CLS, false) ->
                        checkRecycled(context, node, VELOCITY_TRACKER_CLS, RECYCLE)
                }

            OBTAIN_STYLED_ATTRIBUTES,
            OBTAIN_ATTRIBUTES,
            OBTAIN_TYPED_ARRAY ->
                if (evaluator.extendsClass(containingClass, CLASS_CONTEXT, false) ||
                    evaluator.extendsClass(containingClass, CLASS_RESOURCES, false)
                ) {
                    val returnType = method.returnType
                    if (returnType is PsiClassType) {
                        val cls = returnType.resolve()
                        if (cls != null && SdkConstants.CLS_TYPED_ARRAY == cls.qualifiedName) {
                            checkRecycled(context, node, SdkConstants.CLS_TYPED_ARRAY, RECYCLE)
                        }
                    }
                }
            ACQUIRE_CPC ->
                if (evaluator.extendsClass(containingClass, CONTENT_RESOLVER_CLS, false))
                    checkRecycled(context, node, CONTENT_PROVIDER_CLIENT_CLS, RELEASE)
            QUERY, RAW_QUERY, QUERY_WITH_FACTORY, RAW_QUERY_WITH_FACTORY ->
                if (evaluator.extendsClass(containingClass, SQLITE_DATABASE_CLS, false) ||
                    evaluator.extendsClass(containingClass, CONTENT_RESOLVER_CLS, false) ||
                    evaluator.extendsClass(containingClass, CLASS_CONTENTPROVIDER, false) ||
                    evaluator.extendsClass(
                            containingClass, CONTENT_PROVIDER_CLIENT_CLS, false
                        )
                ) {
                    // Other potential cursors-returning methods that should be tracked:
                    //    android.app.DownloadManager#query
                    //    android.content.ContentProviderClient#query
                    //    android.content.ContentResolver#query
                    //    android.database.sqlite.SQLiteQueryBuilder#query
                    //    android.provider.Browser#getAllBookmarks
                    //    android.provider.Browser#getAllVisitedUrls
                    //    android.provider.DocumentsProvider#queryChildDocuments
                    //    android.provider.DocumentsProvider#qqueryDocument
                    //    android.provider.DocumentsProvider#queryRecentDocuments
                    //    android.provider.DocumentsProvider#queryRoots
                    //    android.provider.DocumentsProvider#querySearchDocuments
                    //    android.provider.MediaStore$Images$Media#query
                    //    android.widget.FilterQueryProvider#runQuery

                    // If it's in a try-with-resources clause, don't flag it: these
                    // will be cleaned up automatically
                    var curr: UElement? = node
                    while (curr != null) {
                        val psi = curr.sourcePsi
                        if (psi != null) {
                            if (getParentOfType(psi, PsiResourceVariable::class.java) != null) {
                                return
                            }
                            break
                        }
                        curr = curr.uastParent
                    }

                    checkRecycled(context, node, CURSOR_CLS, CLOSE)
                }

            OF_INT, OF_ARGB, OF_FLOAT, OF_OBJECT, OF_PROPERTY_VALUES_HOLDER -> {
                // ObjectAnimator, ValueAnimator, AnimatorSet
                val type = containingClass.qualifiedName ?: return
                checkRecycled(context, node, type, START)
            }
        }
    }

    private fun checkRecycled(
        context: JavaContext,
        node: UCallExpression,
        recycleType: String,
        recycleName: String
    ) {
        val method = node.getParentOfType(UMethod::class.java) ?: return
        var recycled = false
        var escapes = false
        val visitor = object : DataFlowAnalyzer(setOf(node), emptyList()) {
            override fun receiver(call: UCallExpression) {
                if (isCleanup(call)) {
                    recycled = true
                }
            }

            private fun isCleanup(call: UCallExpression): Boolean {
                val methodName = getMethodName(call)
                if ("use" == methodName) {
                    // Kotlin: "use" calls close; see issue 62377185
                    // Can't call call.resolve() to check it's the runtime because
                    // resolve returns null on these usages.
                    // (See also ktx use method, issue 140344435, which
                    // handles recycle() calls.)
                    // Now make sure we're calling it on the right variable:
                    val operand: UExpression? = call.receiver
                    if (operand != null && instances.contains(operand)) {
                        return true
                    } else if (operand is UResolvable) {
                        val resolved = operand.resolve()
                        if (resolved != null && references.contains(resolved)) {
                            return true
                        }
                    }
                }

                if (recycleName != methodName) {
                    return false
                }
                val resolved = call.resolve()
                if (resolved != null) {
                    val containingClass = resolved.containingClass
                    return context.evaluator
                        .extendsClass(containingClass, recycleType, false)
                }
                return false
            }

            override fun field(field: UElement) {
                escapes = true
            }

            override fun argument(
                call: UCallExpression,
                reference: UElement
            ) {
                // Special case
                if (recycleType == SURFACE_TEXTURE_CLS && call.isConstructorCall()) {
                    val resolved = call.resolve()
                    if (resolved != null && context.evaluator
                        .isMemberInClass(resolved, SURFACE_CLS)
                    ) {
                        return
                    }
                }

                // Special case: MotionEvent.obtain(MotionEvent): passing in an
                // event here does not recycle the event, and we also know it
                // doesn't escape
                if (OBTAIN == getMethodName(call)) {
                    val resolved = call.resolve()
                    if (context.evaluator.isMemberInClass(resolved, MOTION_EVENT_CLS)) {
                        return
                    }
                }

                escapes = true
            }

            override fun returns(expression: UReturnExpression) {
                escapes = true
            }
        }
        method.accept(visitor)

        if (!recycled && !escapes) {
            val className = recycleType.substring(recycleType.lastIndexOf('.') + 1)
            val message = when (recycleName) {
                RECYCLE -> {
                    String.format(
                        "This `%1\$s` should be recycled after use with `#recycle()`",
                        className
                    )
                }
                START -> {
                    "This animation should be started with `#start()`"
                }
                else -> {
                    String.format(
                        "This `%1\$s` should be freed up after use with `#%2\$s()`",
                        className, recycleName
                    )
                }
            }

            var locationNode: UElement? = node.methodIdentifier
            if (locationNode == null) {
                locationNode = node
            }
            val location = context.getLocation(locationNode)
            context.report(RECYCLE_RESOURCE, node, location, message)
        }
    }

    private fun checkTransactionCommits(
        context: JavaContext,
        node: UCallExpression,
        calledMethod: PsiMethod
    ) {
        if (isBeginTransaction(context, calledMethod)) {
            val method = node.getParentOfType(UMethod::class.java) ?: return
            var committed = false
            var escaped = false
            val visitor = object : DataFlowAnalyzer(setOf(node), emptyList()) {
                override fun receiver(call: UCallExpression) {
                    if (isTransactionCommitMethodCall(context, call) ||
                        isShowFragmentMethodCall(context, call)
                    ) {
                        committed = true
                    }
                }

                override fun field(field: UElement) {
                    escaped = true
                }

                override fun argument(
                    call: UCallExpression,
                    reference: UElement
                ) {
                    escaped = true
                }

                override fun returns(expression: UReturnExpression) {
                    escaped = true
                }
            }
            method.accept(visitor)

            if (!committed && !escaped) {
                val message = "This transaction should be completed with a `commit()` call"
                context.report(COMMIT_FRAGMENT, node, context.getNameLocation(node), message)
            }
        }
    }

    private fun isTransactionCommitMethodCall(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val methodName = getMethodName(call)
        return (
            COMMIT == methodName ||
                COMMIT_ALLOWING_LOSS == methodName ||
                COMMIT_NOW_ALLOWING_LOSS == methodName ||
                COMMIT_NOW == methodName
            ) && isMethodOnFragmentClass(
            context, call, FRAGMENT_TRANSACTION_CLS, FRAGMENT_TRANSACTION_V4_CLS, true
        )
    }

    private fun isShowFragmentMethodCall(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val methodName = getMethodName(call)
        return SHOW == methodName && isMethodOnFragmentClass(
            context, call, DIALOG_FRAGMENT, DIALOG_V4_FRAGMENT, true
        )
    }

    private fun isMethodOnFragmentClass(
        context: JavaContext,
        call: UCallExpression,
        fragmentClass: String,
        v4FragmentClass: String,
        returnForUnresolved: Boolean
    ): Boolean {
        // If we *can't* resolve the method call, caller can decide
        // whether to consider the method called or not
        val method = call.resolve() ?: return returnForUnresolved
        val containingClass = method.containingClass
        val evaluator = context.evaluator
        return evaluator.extendsClass(containingClass, fragmentClass, false) ||
            evaluator.extendsClass(containingClass, v4FragmentClass, false)
    }

    private fun isSuperConstructorCall(node: UCallExpression) =
        node.sourcePsi is KtSuperTypeCallEntry || node.methodName == "super"

    private fun checkEditorApplied(
        context: JavaContext,
        node: UCallExpression,
        calledMethod: PsiMethod
    ) {
        if (isSharedEditorCreation(context, calledMethod)) {
            if (node.valueArguments.isNotEmpty()) {
                // Passing parameters to edit(); that's not the built-in edit method
                // on SharedPreferences; it's probably the Android KTX extension method which
                // handles cleanup
                return
            }

            val method = node.getParentOfType(UMethod::class.java) ?: return
            var applied = false
            var escaped = false
            val visitor = object : DataFlowAnalyzer(setOf(node), emptyList()) {
                override fun receiver(call: UCallExpression) {
                    if (isEditorApplyMethodCall(context, call) ||
                        isEditorCommitMethodCall(context, call)
                    ) {
                        applied = true
                    }
                }

                override fun field(field: UElement) {
                    escaped = true
                }

                override fun argument(
                    call: UCallExpression,
                    reference: UElement
                ) {
                    escaped = true
                }

                override fun returns(expression: UReturnExpression) {
                    escaped = true
                }
            }
            method.accept(visitor)

            if (!applied && !escaped) {
                val message = "`SharedPreferences.edit()` without a corresponding `commit()` or `apply()` call"
                context.report(SHARED_PREF, node, context.getLocation(node), message)
            }
        }
    }

    private fun isSharedEditorCreation(
        context: JavaContext,
        method: PsiMethod
    ): Boolean {
        val methodName = method.name
        if (EDIT == methodName) {
            val containingClass = method.containingClass ?: return false
            val type = method.returnType ?: return false
            val evaluator = context.evaluator
            return (
                evaluator.implementsInterface(
                    containingClass, ANDROID_CONTENT_SHARED_PREFERENCES, false
                ) && evaluator.typeMatches(type, ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR)
                )
        }

        return false
    }

    private fun isEditorCommitMethodCall(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val methodName = getMethodName(call)
        if (COMMIT == methodName) {
            val method = call.resolve()
            if (method != null) {
                val containingClass = method.containingClass
                val evaluator = context.evaluator
                if (evaluator.extendsClass(
                        containingClass, ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false
                    )
                ) {
                    suggestApplyIfApplicable(context, call)
                    return true
                }
            } else if (call.valueArgumentCount == 0) {
                // Couldn't find method but it *looks* like an apply call
                return true
            }
        }

        return false
    }

    private fun isEditorApplyMethodCall(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val methodName = getMethodName(call)
        if (APPLY == methodName) {
            val method = call.resolve()
            if (method != null) {
                val containingClass = method.containingClass
                val evaluator = context.evaluator
                return evaluator.extendsClass(
                    containingClass, ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR, false
                )
            } else if (call.valueArgumentCount == 0) {
                // Couldn't find method but it *looks* like an apply call
                return true
            }
        }

        return false
    }

    private fun suggestApplyIfApplicable(
        context: JavaContext,
        node: UCallExpression
    ) {
        // See if the return value is read: can only replace commit with
        // apply if the return value is not considered

        var qualifiedNode: UElement = node
        var parent = skipParentheses(node.uastParent)
        while (parent is UReferenceExpression) {
            qualifiedNode = parent
            parent = skipParentheses(parent.uastParent)
        }
        var returnValueIgnored = true

        if (parent is UCallExpression ||
            parent is UVariable ||
            parent is UPolyadicExpression ||
            parent is UUnaryExpression ||
            parent is UReturnExpression
        ) {
            returnValueIgnored = false
        } else if (parent is UIfExpression) {
            val condition = parent.condition
            returnValueIgnored = condition != qualifiedNode
        } else if (parent is UWhileExpression) {
            val condition = parent.condition
            returnValueIgnored = condition != qualifiedNode
        } else if (parent is UDoWhileExpression) {
            val condition = parent.condition
            returnValueIgnored = condition != qualifiedNode
        }

        if (returnValueIgnored) {
            val message = (
                "Consider using `apply()` instead; `commit` writes " +
                    "its data to persistent storage immediately, whereas " +
                    "`apply` will handle it in the background"
                )
            val location = context.getCallLocation(node, includeReceiver = false, includeArguments = true)
            val fix = LintFix.create()
                .name("Replace commit() with apply()")
                .replace()
                .pattern("(commit)\\s*\\(")
                .with("apply")
                .build()
            context.report(APPLY_SHARED_PREF, node, location, message, fix)
        }
    }

    private fun isBeginTransaction(
        context: JavaContext,
        method: PsiMethod
    ): Boolean {
        val methodName = method.name
        if (BEGIN_TRANSACTION == methodName) {
            val containingClass = method.containingClass
            val evaluator = context.evaluator
            if (evaluator.extendsClass(
                    containingClass,
                    FRAGMENT_MANAGER_CLS,
                    false
                ) || evaluator.extendsClass(containingClass, FRAGMENT_MANAGER_V4_CLS, false)
            ) {
                return true
            }
        }

        return false
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(CleanupDetector::class.java, Scope.JAVA_FILE_SCOPE)

        /** Problems with missing recycle calls. */
        @JvmField
        val RECYCLE_RESOURCE = Issue.create(
            id = "Recycle",
            briefDescription = "Missing `recycle()` calls",
            explanation = """
                Many resources, such as TypedArrays, VelocityTrackers, etc., should be recycled \
                (with a `recycle()` call) after use. This lint check looks for missing \
                `recycle()` calls.""",
            category = Category.PERFORMANCE,
            androidSpecific = true,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Problems with missing commit calls. */
        @JvmField
        val COMMIT_FRAGMENT = Issue.create(
            id = "CommitTransaction",
            briefDescription = "Missing `commit()` calls",
            explanation = """
                After creating a `FragmentTransaction`, you typically need to commit it as well
                """,
            category = Category.CORRECTNESS,
            androidSpecific = true,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        /** Failing to commit a shared preference. */
        @JvmField
        val SHARED_PREF = Issue.create(
            id = "CommitPrefEdits",
            briefDescription = "Missing `commit()` on `SharedPreference` editor",
            explanation = """
                After calling `edit()` on a `SharedPreference`, you must call `commit()` or \
                `apply()` on the editor to save the results.""",
            category = Category.CORRECTNESS,
            androidSpecific = true,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(CleanupDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        /** Using commit instead of apply on a shared preference. */
        @JvmField
        val APPLY_SHARED_PREF = Issue.create(
            id = "ApplySharedPref",
            briefDescription = "Use `apply()` on `SharedPreferences`",
            explanation = """
                Consider using `apply()` instead of `commit` on shared preferences. Whereas \
                `commit` blocks and writes its data to persistent storage immediately, `apply` \
                will handle it in the background.""",
            category = Category.CORRECTNESS,
            androidSpecific = true,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(CleanupDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )

        // Target method names

        private const val OF_INT = "ofInt"
        private const val OF_ARGB = "ofArgb"
        private const val OF_FLOAT = "ofFloat"
        private const val OF_OBJECT = "ofObject"
        private const val OF_PROPERTY_VALUES_HOLDER = "ofPropertyValuesHolder"
        private const val START = "start"

        private const val RECYCLE = "recycle"
        private const val RELEASE = "release"
        private const val OBTAIN = "obtain"
        private const val SHOW = "show"
        private const val ACQUIRE_CPC = "acquireContentProviderClient"
        private const val OBTAIN_NO_HISTORY = "obtainNoHistory"
        private const val OBTAIN_ATTRIBUTES = "obtainAttributes"
        private const val OBTAIN_TYPED_ARRAY = "obtainTypedArray"
        private const val OBTAIN_STYLED_ATTRIBUTES = "obtainStyledAttributes"
        private const val BEGIN_TRANSACTION = "beginTransaction"
        private const val COMMIT = "commit"
        private const val COMMIT_NOW = "commitNow"
        private const val APPLY = "apply"
        private const val COMMIT_ALLOWING_LOSS = "commitAllowingStateLoss"
        private const val COMMIT_NOW_ALLOWING_LOSS = "commitNowAllowingStateLoss"
        private const val QUERY = "query"
        private const val RAW_QUERY = "rawQuery"
        private const val QUERY_WITH_FACTORY = "queryWithFactory"
        private const val RAW_QUERY_WITH_FACTORY = "rawQueryWithFactory"
        private const val CLOSE = "close"
        private const val EDIT = "edit"

        const val MOTION_EVENT_CLS = "android.view.MotionEvent"
        private const val PARCEL_CLS = "android.os.Parcel"
        private const val VELOCITY_TRACKER_CLS = "android.view.VelocityTracker"
        private const val DIALOG_FRAGMENT = "android.app.DialogFragment"
        private const val DIALOG_V4_FRAGMENT = "android.support.v4.app.DialogFragment"
        private const val FRAGMENT_MANAGER_CLS = "android.app.FragmentManager"
        private const val FRAGMENT_MANAGER_V4_CLS = "android.support.v4.app.FragmentManager"
        private const val FRAGMENT_TRANSACTION_CLS = "android.app.FragmentTransaction"
        private const val FRAGMENT_TRANSACTION_V4_CLS = "android.support.v4.app.FragmentTransaction"
        private const val VALUE_ANIMATOR_CLS = "android.animation.ValueAnimator"
        private const val OBJECT_ANIMATOR_CLS = "android.animation.ObjectAnimator"
        private const val ANIMATOR_SET_CLS = "android.animation.AnimatorSet"

        const val SURFACE_CLS = "android.view.Surface"
        const val SURFACE_TEXTURE_CLS = "android.graphics.SurfaceTexture"
        const val CONTENT_PROVIDER_CLIENT_CLS = "android.content.ContentProviderClient"
        const val CONTENT_RESOLVER_CLS = "android.content.ContentResolver"
        const val SQLITE_DATABASE_CLS = "android.database.sqlite.SQLiteDatabase"
        const val CURSOR_CLS = "android.database.Cursor"
        const val ANDROID_CONTENT_SHARED_PREFERENCES = "android.content.SharedPreferences"
        private const val ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR =
            "android.content.SharedPreferences.Editor"

        /**
         * Returns the variable the expression is assigned to, if any.
         */
        @JvmStatic
        @JvmOverloads
        fun getVariableElement(
            rhs: UCallExpression,
            allowChainedCalls: Boolean = false,
            allowFields: Boolean = false
        ): PsiVariable? {
            return DataFlowAnalyzer.getVariableElement(rhs, allowChainedCalls, allowFields)
        }
    }
}
