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
import com.google.common.collect.Lists
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiResourceVariable
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getOutermostQualified
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks for missing `recycle` calls on resources that encourage it, and for missing `commit`
 * calls on FragmentTransactions, etc.
 */
class CleanupDetector : Detector(), SourceCodeScanner {

    // ---- implements SourceCodeScanner ----

    override fun getApplicableMethodNames(): List<String>? {
        return Arrays.asList(
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
            EDIT
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(SURFACE_TEXTURE_CLS, SURFACE_CLS)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val name = method.name
        when {
            BEGIN_TRANSACTION == name -> checkTransactionCommits(context, node, method)
            EDIT == name -> checkEditorApplied(context, node, method)
            else -> checkResourceRecycled(context, node, method)
        }
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val type = constructor.containingClass?.qualifiedName ?: return
        checkRecycled(context, node, type, RELEASE)
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
        if ((OBTAIN == name || OBTAIN_NO_HISTORY == name) && evaluator.extendsClass(
                containingClass,
                MOTION_EVENT_CLS,
                false
            )
        ) {
            checkRecycled(context, node, MOTION_EVENT_CLS, RECYCLE)
        } else if (OBTAIN == name && evaluator.extendsClass(containingClass, PARCEL_CLS, false)) {
            checkRecycled(context, node, PARCEL_CLS, RECYCLE)
        } else if (OBTAIN == name && evaluator.extendsClass(
                containingClass,
                VELOCITY_TRACKER_CLS,
                false
            )
        ) {
            checkRecycled(context, node, VELOCITY_TRACKER_CLS, RECYCLE)
        } else if ((OBTAIN_STYLED_ATTRIBUTES == name ||
                    OBTAIN_ATTRIBUTES == name ||
                    OBTAIN_TYPED_ARRAY == name) && (evaluator.extendsClass(
                containingClass,
                CLASS_CONTEXT,
                false
            ) || evaluator.extendsClass(
                containingClass, SdkConstants.CLASS_RESOURCES, false
            ))
        ) {
            val returnType = method.returnType
            if (returnType is PsiClassType) {
                val cls = returnType.resolve()
                if (cls != null && SdkConstants.CLS_TYPED_ARRAY == cls.qualifiedName) {
                    checkRecycled(context, node, SdkConstants.CLS_TYPED_ARRAY, RECYCLE)
                }
            }
        } else if (ACQUIRE_CPC == name && evaluator.extendsClass(
                containingClass,
                CONTENT_RESOLVER_CLS,
                false
            )
        ) {
            checkRecycled(context, node, CONTENT_PROVIDER_CLIENT_CLS, RELEASE)
        } else if ((QUERY == name ||
                    RAW_QUERY == name ||
                    QUERY_WITH_FACTORY == name ||
                    RAW_QUERY_WITH_FACTORY == name) && (evaluator.extendsClass(
                containingClass,
                SQLITE_DATABASE_CLS,
                false
            ) ||
                    evaluator.extendsClass(containingClass, CONTENT_RESOLVER_CLS, false) ||
                    evaluator.extendsClass(containingClass, CLASS_CONTENTPROVIDER, false) ||
                    evaluator.extendsClass(
                containingClass, CONTENT_PROVIDER_CLIENT_CLS, false
            ))
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
                    if (PsiTreeUtil.getParentOfType(psi, PsiResourceVariable::class.java) != null) {
                        return
                    }
                    break
                }
                curr = curr.uastParent
            }

            checkRecycled(context, node, CURSOR_CLS, CLOSE)
        }
    }

    private fun checkRecycled(
        context: JavaContext,
        node: UCallExpression,
        recycleType: String,
        recycleName: String
    ) {
        val method = node.getParentOfType<UMethod>(UMethod::class.java) ?: return
        val recycled = AtomicBoolean(false)
        val escapes = AtomicBoolean(false)
        val visitor = object : DataFlowAnalyzer(setOf(node), emptyList()) {
            override fun receiver(call: UCallExpression) {
                if (isCleanup(call)) {
                    recycled.set(true)
                }
                super.receiver(call)
            }

            private fun isCleanup(call: UCallExpression): Boolean {
                val methodName = getMethodName(call)
                if ("use" == methodName && CLOSE == recycleName) {
                    // Kotlin: "use" calls close; see issue 62377185
                    // Can't call call.resolve() to check it's the runtime because
                    // resolve returns null on these usages.
                    // Now make sure we're calling it on the right variable
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
                escapes.set(true)
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

                escapes.set(true)
            }

            override fun returns(expression: UReturnExpression) {
                escapes.set(true)
            }
        }
        method.accept(visitor)

        if (!recycled.get() && !escapes.get()) {
            val className = recycleType.substring(recycleType.lastIndexOf('.') + 1)
            val message: String
            message = if (RECYCLE == recycleName) {
                String.format(
                    "This `%1\$s` should be recycled after use with `#recycle()`",
                    className
                )
            } else {
                String.format(
                    "This `%1\$s` should be freed up after use with `#%2\$s()`",
                    className, recycleName
                )
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
        // TODO: Switch to new DataFlowAnalyzer
        if (isBeginTransaction(context, calledMethod)) {
            val boundVariable = getVariableElement(node, true, true)
            if (isCommittedInChainedCalls(context, node)) {
                return
            }

            if (boundVariable != null) {
                val method = node.getParentOfType<UMethod>(UMethod::class.java) ?: return

                val commitVisitor = object : FinishVisitor(context, boundVariable) {
                    override fun isCleanupCall(call: UCallExpression): Boolean {
                        if (isTransactionCommitMethodCall(this.context, call)) {
                            val chain = call.getOutermostQualified().getQualifiedChain()
                            if (chain.isEmpty()) {
                                return false
                            }

                            var operand: UExpression? = chain[0]
                            if (operand != null) {
                                var resolved = operand.tryResolve()

                                if (resolved != null && variables.contains(resolved)) {
                                    return true
                                } else if (resolved is PsiMethod &&
                                    operand is UCallExpression &&
                                    isCommittedInChainedCalls(
                                    this.context, operand
                                    )
                                ) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand is UCallExpression) {
                                        operand = operand.receiver
                                    }
                                    if (operand is UResolvable) {
                                        resolved = operand.resolve()
                                        if (resolved != null && variables.contains(resolved)) {
                                            return true
                                        }
                                    }
                                }
                            }
                        } else if (isShowFragmentMethodCall(this.context, call)) {
                            val arguments = call.valueArguments
                            if (arguments.size == 2) {
                                val first = arguments[0]
                                val resolved = first.tryResolve()

                                if (resolved != null && variables.contains(resolved)) {
                                    return true
                                }
                            }
                        }
                        return false
                    }
                }

                method.accept(commitVisitor)
                if (commitVisitor.isCleanedUp || commitVisitor.variableEscapes()) {
                    return
                }
            }

            val message = "This transaction should be completed with a `commit()` call"
            context.report(COMMIT_FRAGMENT, node, context.getNameLocation(node), message)
        }
    }

    private fun isCommittedInChainedCalls(
        context: JavaContext,
        node: UCallExpression
    ): Boolean {
        // Look for chained calls since the FragmentManager methods all return "this"
        // to allow constructor chaining, e.g.
        //    getFragmentManager().beginTransaction().addToBackStack("test")
        //            .disallowAddToBackStack().hide(mFragment2).setBreadCrumbShortTitle("test")
        //            .show(mFragment2).setCustomAnimations(0, 0).commit();
        val checker: (JavaContext, UCallExpression) -> Boolean = { c, call ->
            isTransactionCommitMethodCall(c, call) || isShowFragmentMethodCall(c, call)
        }
        return isCommittedInChainedCalls(context, node, checker)
    }

    private fun isTransactionCommitMethodCall(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {

        val methodName = getMethodName(call)
        return (COMMIT == methodName ||
                COMMIT_ALLOWING_LOSS == methodName ||
                COMMIT_NOW_ALLOWING_LOSS == methodName ||
                COMMIT_NOW == methodName) && isMethodOnFragmentClass(
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
        val method = call.resolve()
        return if (method != null) {
            val containingClass = method.containingClass
            val evaluator = context.evaluator
            evaluator.extendsClass(
                containingClass,
                fragmentClass,
                false
            ) || evaluator.extendsClass(containingClass, v4FragmentClass, false)
        } else {
            // If we *can't* resolve the method call, caller can decide
            // whether to consider the method called or not
            returnForUnresolved
        }
    }

    private fun checkEditorApplied(
        context: JavaContext,
        node: UCallExpression,
        calledMethod: PsiMethod
    ) {
        // TODO: Switch to new DataFlowAnalyzer
        if (isSharedEditorCreation(context, calledMethod)) {
            if (!node.valueArguments.isEmpty()) {
                // Passing parameters to edit(); that's not the built-in edit method
                // on SharedPreferences; it's probably the Android KTX extension method which
                // handles cleanup
                return
            }

            val boundVariable = getVariableElement(node, true, true)
            if (isEditorCommittedInChainedCalls(context, node)) {
                return
            }

            if (boundVariable != null) {
                val method = node.getParentOfType<UMethod>(UMethod::class.java) ?: return

                val commitVisitor = object : FinishVisitor(context, boundVariable) {
                    override fun isCleanupCall(call: UCallExpression): Boolean {
                        if (isEditorApplyMethodCall(this.context, call) || isEditorCommitMethodCall(
                            this.context,
                                call
                            )
                        ) {
                            val chain = call.getOutermostQualified().getQualifiedChain()
                            if (chain.isEmpty()) {
                                return false
                            }

                            var operand: UExpression? = chain[0]
                            if (operand != null) {
                                var resolved = operand.tryResolve()

                                if (resolved != null && variables.contains(resolved)) {
                                    return true
                                } else if (resolved is PsiMethod &&
                                    operand is UCallExpression &&
                                    isEditorCommittedInChainedCalls(
                                    this.context, operand
                                    )
                                ) {
                                    // Check that the target of the committed chains is the
                                    // right variable!
                                    while (operand is UCallExpression) {
                                        operand = operand.receiver
                                    }
                                    if (operand is UResolvable) {
                                        resolved = operand.resolve()
                                        if (resolved != null && variables.contains(resolved)) {
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                        return false
                    }
                }

                method.accept(commitVisitor)
                if (commitVisitor.isCleanedUp || commitVisitor.variableEscapes()) {
                    return
                }
            } else if (node.getParentOfType<UElement>(UReturnExpression::class.java) != null) {
                // Allocation is in a return statement
                return
            }

            val message =
                "`SharedPreferences.edit()` without a corresponding `commit()` or " + "`apply()` call"
            context.report(SHARED_PREF, node, context.getLocation(node), message)
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
            return (evaluator.implementsInterface(
                containingClass, ANDROID_CONTENT_SHARED_PREFERENCES, false
            ) && evaluator.typeMatches(type, ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR))
        }

        return false
    }

    private fun isEditorCommittedInChainedCalls(
        context: JavaContext,
        node: UCallExpression
    ): Boolean {
        val checker: (JavaContext, UCallExpression) -> Boolean = { c, call ->
            isEditorCommitMethodCall(c, call) || isEditorApplyMethodCall(c, call)
        }
        return isCommittedInChainedCalls(context, node, checker)
    }

    private fun isCommittedInChainedCalls(
        context: JavaContext,
        node: UCallExpression,
        isCommitCall: (JavaContext, UCallExpression) -> Boolean
    ): Boolean {
        val chain = node.getOutermostQualified().getQualifiedChain()
        if (!chain.isEmpty()) {
            val lastExpression = chain[chain.size - 1]
            if (lastExpression is UCallExpression) {
                if (isCommitCall(context, lastExpression)) {
                    return true
                }

                // with, run, let, apply, etc chained to the end of this call
                if (lastArgCallsCommit(context, lastExpression, isCommitCall)) {
                    return true
                }
            }

            // Check preceding calls too (but not for chained lambdas)
            for (i in chain.size - 2 downTo 1) {
                val call = chain[i]
                if (call === node || call !is UCallExpression) {
                    break
                } else if (isCommitCall(context, call)) {
                    return true
                }
            }
        }

        // Surrounding with-call?
        val parentCall = node.getParentOfType<UCallExpression>(UCallExpression::class.java)
        if (parentCall != null) {
            val methodName = getMethodName(parentCall)
            if ("with" == methodName) {
                val args = parentCall.valueArguments
                return args.size == 2 && lastArgCallsCommit(
                    context,
                    parentCall,
                    isCommitCall
                )
            }
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
        if (context.project.minSdkVersion.apiLevel >= 9) {
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
                val message = ("Consider using `apply()` instead; `commit` writes " +
                        "its data to persistent storage immediately, whereas " +
                        "`apply` will handle it in the background")
                val location = context.getLocation(node)
                val fix = LintFix.create()
                    .name("Replace commit() with apply()")
                    .replace()
                    .pattern("(commit)\\s*\\(")
                    .with("apply")
                    .build()
                context.report(APPLY_SHARED_PREF, node, location, message, fix)
            }
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

    /**
     * Visitor which checks whether an operation is "finished"; in the case of a FragmentTransaction
     * we're looking for a "commit" call; in the case of a TypedArray we're looking for a "recycle",
     * call, in the case of a database cursor we're looking for a "close" call, etc.
     */
    private abstract class FinishVisitor(
        protected val context: JavaContext,
        private val originalVariableNode: PsiVariable
    ) : AbstractUastVisitor() {
        protected val variables: MutableList<PsiVariable>

        var isCleanedUp: Boolean = false
            private set

        private var escapes: Boolean = false

        init {
            variables = Lists.newArrayList(originalVariableNode)
        }

        fun variableEscapes(): Boolean {
            return escapes
        }

        override fun visitElement(node: UElement): Boolean {
            return isCleanedUp || super.visitElement(node)
        }

        protected abstract fun isCleanupCall(call: UCallExpression): Boolean

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.kind === UastCallKind.METHOD_CALL) {
                visitMethodCallExpression(node)
            }
            return super.visitCallExpression(node)
        }

        private fun visitMethodCallExpression(call: UCallExpression) {
            if (isCleanedUp) {
                return
            }

            // Look for escapes
            if (!escapes) {
                for (expression in call.valueArguments) {
                    if (expression is UResolvable) {
                        val resolved = expression.resolve()
                        if (resolved != null && variables.contains(resolved)) {
                            val wasEscaped = escapes
                            escapes = true

                            // Special case: MotionEvent.obtain(MotionEvent): passing in an
                            // event here does not recycle the event, and we also know it
                            // doesn't escape
                            if (OBTAIN == getMethodName(call)) {
                                val method = call.resolve()
                                if (context.evaluator
                                        .isMemberInClass(method, MOTION_EVENT_CLS)
                                ) {
                                    escapes = wasEscaped
                                }
                            }
                        }
                    }
                }
            }

            if (isCleanupCall(call)) {
                isCleanedUp = true
            }
        }

        override fun visitVariable(node: UVariable): Boolean {
            if (node is ULocalVariable) {
                val initializer = node.uastInitializer
                if (initializer is UResolvable) {
                    val resolved = initializer.resolve()
                    if (resolved != null && variables.contains(resolved)) {
                        val psi = node.sourcePsi as? PsiVariable
                        psi?.let { variables.add(it) }
                    }
                }
            }

            return super.visitVariable(node)
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (!node.isAssignment()) {
                return super.visitBinaryExpression(node)
            }

            // TEMPORARILY DISABLED; see testDatabaseCursorReassignment
            // This can result in some false positives right now. Play it
            // safe instead.
            var clearLhs = false

            val rhs = node.rightOperand
            if (rhs is UResolvable) {
                val resolved = rhs.resolve()
                if (resolved != null && variables.contains(resolved)) {
                    clearLhs = false
                    val lhs = node.leftOperand.tryResolve()
                    if (lhs is PsiLocalVariable) {
                        variables.add(lhs)
                    } else if (lhs is PsiField) {
                        escapes = true
                    }
                }
            }

            if (clearLhs) {
                // If we reassign one of the variables, clear it out
                val lhs = node.leftOperand.tryResolve()
                if (lhs != null && lhs != originalVariableNode && variables.contains(lhs)) {
                    variables.remove(lhs)
                }
            }

            return super.visitBinaryExpression(node)
        }

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val returnValue = node.returnExpression
            if (returnValue is UResolvable) {
                val resolved = returnValue.resolve()
                if (resolved != null && variables.contains(resolved)) {
                    escapes = true
                }
            }

            return super.visitReturnExpression(node)
        }
    }

    private fun lastArgCallsCommit(
        context: JavaContext,
        methodInvocation: UCallExpression,
        checker: (JavaContext, UCallExpression) -> Boolean
    ): Boolean {
        val args = methodInvocation.valueArguments
        if (!args.isEmpty()) {
            val last = args[args.size - 1]
            if (last is ULambdaExpression) {
                val body = last.body
                return callsCommit(context, body, checker)
            }
        }

        return false
    }

    private fun callsCommit(
        context: JavaContext,
        node: UElement,
        checker: (JavaContext, UCallExpression) -> Boolean
    ): Boolean {
        val visitor = CommitCallVisitor(context, checker)
        node.accept(visitor)
        return visitor.isFound
    }

    private class CommitCallVisitor(
        private val context: JavaContext,
        private val isCommitCall: (JavaContext, UCallExpression) -> Boolean
    ) : AbstractUastVisitor() {
        var isFound: Boolean = false
            private set

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (isCommitCall(context, node)) {
                isFound = true
            }
            return super.visitCallExpression(node)
        }
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(CleanupDetector::class.java, Scope.JAVA_FILE_SCOPE)

        /** Problems with missing recycle calls */
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

        /** Failing to commit a shared preference */
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

        /** Using commit instead of apply on a shared preference */
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

        const val SURFACE_CLS = "android.view.Surface"
        const val SURFACE_TEXTURE_CLS = "android.graphics.SurfaceTexture"
        const val CONTENT_PROVIDER_CLIENT_CLS = "android.content.ContentProviderClient"
        const val CONTENT_RESOLVER_CLS = "android.content.ContentResolver"
        const val SQLITE_DATABASE_CLS = "android.database.sqlite.SQLiteDatabase"
        const val CURSOR_CLS = "android.database.Cursor"
        const val ANDROID_CONTENT_SHARED_PREFERENCES = "android.content.SharedPreferences"
        private const val ANDROID_CONTENT_SHARED_PREFERENCES_EDITOR =
            "android.content.SharedPreferences.Editor"

        /** Returns the variable the expression is assigned to, if any */
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
