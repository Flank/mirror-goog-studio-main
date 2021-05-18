/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.skipParentheses
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.java.JavaUIfExpression
import org.jetbrains.uast.kotlin.KotlinPostfixOperators
import org.jetbrains.uast.kotlin.KotlinUSwitchEntry
import org.jetbrains.uast.kotlin.expressions.KotlinUElvisExpression
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Helper class for analyzing data flow. */
abstract class DataFlowAnalyzer(
    val initial: Collection<UElement>,
    initialReferences: Collection<PsiVariable> = emptyList()
) : AbstractUastVisitor() {

    /**
     * The instance being tracked is the receiver for a method call.
     */
    open fun receiver(call: UCallExpression) {}

    /**
     * The instance being tracked is being returned from this block.
     */
    open fun returns(expression: UReturnExpression) {}

    /** The instance being tracked is being stored into a field. */
    open fun field(field: UElement) {}

    /** The instance being tracked is being passed in a method call. */
    open fun argument(
        call: UCallExpression,
        reference: UElement
    ) {
    }

    protected val references: MutableSet<PsiElement> = LinkedHashSet()
    protected val instances: MutableSet<UElement> = LinkedHashSet()

    init {
        if (references.isEmpty()) {
            references.addAll(initialReferences)
        }
        if (instances.isEmpty()) {
            instances.addAll(initial)
            for (element in initial) {
                if (element is UCallExpression) {
                    val parent = element.uastParent
                    if (parent is UQualifiedReferenceExpression && parent.selector == element) {
                        instances.add(parent)
                    }
                }
            }
        }
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
        val receiver = node.receiver
        var matched = false
        if (receiver != null) {
            if (instances.contains(receiver)) {
                matched = true
            } else {
                val resolved = receiver.tryResolve()
                if (resolved == null && receiver is USimpleNameReferenceExpression) {
                    // Work around UAST bug where resolving in a lambda doesn't work; KT-46628.
                    if (receiver.identifier == "it") {
                        var curr: UElement = receiver
                        while (true) {
                            val lambda = curr.getParentOfType(ULambdaExpression::class.java, true) ?: break
                            val valueParameters = lambda.valueParameters
                            if (valueParameters.any { parameter ->
                                val javaPsi = parameter.javaPsi
                                val sourcePsi = parameter.sourcePsi
                                javaPsi != null && references.contains(javaPsi) ||
                                    sourcePsi != null && references.contains(sourcePsi)
                            }
                            ) {
                                // We found the variable
                                matched = true
                                break
                            }

                            curr = lambda
                        }
                    }
                } else if (resolved != null) {
                    if (references.contains(resolved)) {
                        matched = true
                    }
                }
            }
        } else {
            val lambda = node.uastParent as? ULambdaExpression
                ?: node.uastParent?.uastParent as? ULambdaExpression
                // Kotlin 1.3.50 may add another layer UImplicitReturnExpression
                ?: node.uastParent?.uastParent?.uastParent as? ULambdaExpression
            if (lambda != null && lambda.uastParent is UCallExpression &&
                isScopingThis(lambda.uastParent as UCallExpression)
            ) {
                if (instances.contains(node)) {
                    matched = true
                }
            } else if (isScopingThis(node)) {
                val args = node.valueArguments
                if (args.size == 2 && instances.contains(args[0]) &&
                    args[1] is ULambdaExpression
                ) {
                    val body = (args[1] as ULambdaExpression).body
                    instances.add(body)
                    if (body is UBlockExpression) {
                        for (expression in body.expressions) {
                            if (expression is UReturnExpression) {
                                // The with or apply call is returned
                                expression.returnExpression?.let(instances::add)
                            } else {
                                // When we have with(X) { statementList }, and X is a tracked instance, also treat
                                // each of the statements in the body as being invoked on the instance
                                instances.add(expression)
                            }
                        }
                    }
                }
            }
        }

        if (matched) {
            if (!initial.contains(node)) {
                receiver(node)

                if ((node.methodName == "apply" || node.methodName == "also") &&
                    node.uastParent?.uastParent?.isMethodCall() == true
                ) {
                    // The node is being passed as an argument to a method
                    argument(node.uastParent?.uastParent as UCallExpression, node)
                }
            }
            if (returnsSelf(node)) {
                instances.add(node)
                val parent = node.uastParent as? UQualifiedReferenceExpression
                if (parent != null) {
                    instances.add(parent)
                    val parentParent = parent.uastParent as? UQualifiedReferenceExpression
                    val chained = parentParent?.selector
                    if (chained != null) {
                        instances.add(chained)
                    }
                }
            }

            val lambda = node.valueArguments.lastOrNull() as? ULambdaExpression
            if (lambda != null) {
                if (isScopingIt(node)) {
                    // If we have X.let { Y }, and X is a tracked instance, we should now
                    // also track references to the "it" variable (or whatever the lambda
                    // variable is called). Same case for X.also { Y }.
                    if (lambda.valueParameters.size == 1) {
                        val resolved = receiver?.tryResolve()
                        if (resolved != null && references.contains(resolved)) {
                            val lambdaVar = lambda.valueParameters.first()
                            instances.add(lambdaVar)
                            addVariableReference(lambdaVar)
                        }
                    }
                } else if (isScopingThis(node)) {
                    val body = lambda.body
                    if (body is UBlockExpression) {
                        for (expression in body.expressions) {
                            if (expression is UReturnExpression) {
                                expression.returnExpression?.let(instances::add)
                            } else {
                                instances.add(expression)
                            }
                        }
                    }
                }
            }
        }

        for (expression in node.valueArguments) {
            if (instances.contains(expression)) {
                argument(node, expression)
            } else if (expression is UReferenceExpression) {
                val resolved = expression.resolve()

                if (resolved != null && references.contains(resolved)) {
                    argument(node, expression)
                    break
                }
            }
        }

        return super.visitCallExpression(node)
    }

    override fun afterVisitVariable(node: UVariable) {
        if (node is ULocalVariable) {
            val initializer = node.uastInitializer
            if (initializer != null) {
                if (instances.contains(initializer)) {
                    // Instance is stored in a variable
                    addVariableReference(node)
                } else if (initializer is UReferenceExpression) {
                    val resolved = initializer.resolve()
                    if (resolved != null && references.contains(resolved)) {
                        addVariableReference(node)
                    }
                }
            }
        }
        super.afterVisitVariable(node)
    }

    override fun afterVisitPostfixExpression(node: UPostfixExpression) {
        if (node.operator == KotlinPostfixOperators.EXCLEXCL) {
            val element = node.operand
            if (instances.contains(element)) {
                instances.add(node)
            }
        }

        super.afterVisitPostfixExpression(node)
    }

    protected fun addVariableReference(node: UVariable) {
        node.sourcePsi?.let { references.add(it) }
        (node.javaPsi as? PsiVariable)?.let { references.add(it) }
    }

    override fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {
        if (node is KotlinUSwitchEntry) {
            for (expression in node.body.expressions) {
                if (instances.contains(expression)) {
                    val switch = node.getParentOfType<USwitchExpression>()
                    if (switch != null) {
                        instances.add(switch)
                        break
                    }
                }
            }
        }

        super.afterVisitSwitchClauseExpression(node)
    }

    @Suppress("UnstableApiUsage") // yield is still experimental
    override fun afterVisitYieldExpression(node: UYieldExpression) {
        val element: UElement? = node.expression
        if (element != null && instances.contains(element)) {
            instances.add(node)
        }
        super.afterVisitYieldExpression(node)
    }

    override fun afterVisitLabeledExpression(node: ULabeledExpression) {
        if (instances.contains(node.expression)) {
            instances.add(node)
        }
        super.afterVisitLabeledExpression(node)
    }

    override fun afterVisitIfExpression(node: UIfExpression) {
        if (node !is JavaUIfExpression) { // Does not apply to Java
            // Handle Elvis operator
            val parent = node.uastParent
            if (parent is KotlinUElvisExpression) {
                val then = node.thenExpression
                if (then is USimpleNameReferenceExpression) {
                    val variable = then.resolve()
                    if (variable != null) {
                        if (references.contains(variable)) {
                            instances.add(parent)
                        } else if (variable is UVariable) {
                            val psi = variable.javaPsi
                            val sourcePsi = variable.sourcePsi
                            if (psi != null && references.contains(psi) ||
                                sourcePsi != null && references.contains(sourcePsi)
                            ) {
                                instances.add(parent)
                            }
                        }
                    }
                }
            }

            val thenExpression = node.thenExpression
            val elseExpression = node.elseExpression
            if (thenExpression != null && instances.contains(thenExpression)) {
                instances.add(node)
            } else if (elseExpression != null && instances.contains(elseExpression)) {
                instances.add(node)
            } else {
                if (thenExpression is UBlockExpression) {
                    thenExpression.expressions.lastOrNull()?.let {
                        if (instances.contains(it)) {
                            instances.add(node)
                        }
                    }
                }
                if (elseExpression is UBlockExpression) {
                    elseExpression.expressions.lastOrNull()?.let {
                        if (instances.contains(it)) {
                            instances.add(node)
                        }
                    }
                }
            }
        }

        super.afterVisitIfExpression(node)
    }

    override fun afterVisitBinaryExpression(node: UBinaryExpression) {
        if (!node.isAssignment()) {
            super.afterVisitBinaryExpression(node)
            return
        }

        // TEMPORARILY DISABLED; see testDatabaseCursorReassignment
        // This can result in some false positives right now. Play it
        // safe instead.
        var clearLhs = false

        val rhs = node.rightOperand
        if (instances.contains(rhs)) {
            when (val lhs = node.leftOperand.tryResolve()) {
                is UVariable -> addVariableReference(lhs)
                is PsiLocalVariable -> references.add(lhs)
                is PsiField -> field(rhs)
            }
        } else if (rhs is UReferenceExpression) {
            val resolved = rhs.resolve()
            if (resolved != null && references.contains(resolved)) {
                clearLhs = false
                when (val lhs = node.leftOperand.tryResolve()) {
                    is UVariable -> addVariableReference(lhs)
                    is PsiLocalVariable -> references.add(lhs)
                    is PsiField -> field(rhs)
                }
            }
        }

        if (clearLhs) {
            // If we reassign one of the variables, clear it out
            val lhs = node.leftOperand.tryResolve()
            if (lhs != null && lhs != initial && references.contains(lhs)) {
                references.remove(lhs)
            }
        }
        super.afterVisitBinaryExpression(node)
    }

    override fun afterVisitReturnExpression(node: UReturnExpression) {
        val returnValue = node.returnExpression
        if (returnValue != null) {
            if (instances.contains(returnValue)) {
                returns(node)
            } else if (returnValue is UReferenceExpression) {
                val resolved = returnValue.resolve()
                if (resolved != null && references.contains(resolved)) {
                    returns(node)
                }
            }
        }
        super.afterVisitReturnExpression(node)
    }

    /**
     * Tries to guess whether the given method call returns self. This
     * is intended to be able to tell that in a constructor call chain
     * foo().bar().baz() is still invoking methods on the foo instance.
     */
    open fun returnsSelf(call: UCallExpression): Boolean {
        val resolvedCall = call.resolve() ?: return false
        if (call.returnType is PsiPrimitiveType) {
            return false
        }
        val containingClass = resolvedCall.containingClass ?: return false
        val returnTypeClass = (call.returnType as? PsiClassType)?.resolve()
        if (returnTypeClass == containingClass) {
            return true
        }

        // Kotlin stdlib functions also return "this" but for various reasons
        // don't have the right return type
        if (isReturningContext(call)) {
            return true
        }

        // Return a subtype is also likely self; see for example Snackbar
        if (returnTypeClass != null && returnTypeClass.isInheritor(containingClass, true) &&
            containingClass.name != "Object"
        ) {
            return true
        }

        return false
    }

    companion object {
        fun getVariableElement(
            rhs: UCallExpression,
            allowChainedCalls: Boolean,
            allowFields: Boolean
        ): PsiVariable? {
            var parent = skipParentheses(rhs.getQualifiedParentOrThis().uastParent)

            // Handle some types of chained calls; e.g. you might have
            //    var = prefs.edit().put(key,value)
            // and here we want to skip past the put call
            if (allowChainedCalls) {
                while (true) {
                    if (parent is UQualifiedReferenceExpression) {
                        val parentParent = skipParentheses(parent.uastParent)
                        if (parentParent is UQualifiedReferenceExpression) {
                            parent = skipParentheses(parentParent.uastParent)
                        } else if (parentParent is UVariable || parentParent is UPolyadicExpression) {
                            parent = parentParent
                            break
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            }

            if (parent != null && parent.isAssignment()) {
                val assignment = parent as UBinaryExpression
                val lhs = assignment.leftOperand
                if (lhs is UReferenceExpression) {
                    val resolved = lhs.resolve()
                    if (resolved is PsiVariable && (allowFields || resolved !is PsiField)) {
                        // e.g. local variable, parameter - but not a field
                        return resolved
                    }
                }
            } else if (parent is UVariable && (allowFields || parent !is UField)) {
                // Handle elvis operators in Kotlin. A statement like this:
                //   val transaction = f.beginTransaction() ?: return
                // is turned into
                //   var transaction: android.app.FragmentTransaction = elvis {
                //       @org.jetbrains.annotations.NotNull var var8633f9d5: android.app.FragmentTransaction = f.beginTransaction()
                //       if (var8633f9d5 != null) var8633f9d5 else return
                //   }
                // and here we want to record "transaction", not "var8633f9d5", as the variable
                // to track.
                if (parent.uastParent is UDeclarationsExpression &&
                    parent.uastParent!!.uastParent is UExpressionList
                ) {
                    val exp = parent.uastParent!!.uastParent as UExpressionList
                    val kind = exp.kind
                    if (kind.name == "elvis" && exp.uastParent is UVariable) {
                        parent = exp.uastParent
                    }
                }

                return (parent as UVariable).psi
            }

            return null
        }
    }

    /**
     * Returns true if the given call represents a Kotlin
     * scope function where the object reference is this. See
     * https://kotlinlang.org/docs/scope-functions.html#function-selection
     */
    private fun isScopingThis(node: UCallExpression): Boolean {
        val name = getMethodName(node)
        if (name == "run" || name == "with" || name == "apply") {
            return isScopingFunction(node)
        }
        return false
    }

    /**
     * Returns true if the given call represents a Kotlin scope function
     * where the object reference is the lambda variable `it`; see
     * https://kotlinlang.org/docs/scope-functions.html#function-selection
     */
    private fun isScopingIt(node: UCallExpression): Boolean {
        val name = getMethodName(node)
        if (name == "let" || name == "also") {
            return isScopingFunction(node)
        }
        return false
    }

    /**
     * Returns true if the given call represents a Kotlin scope
     * function where the return value is the context object; see
     * https://kotlinlang.org/docs/scope-functions.html#function-selection
     */
    private fun isReturningContext(node: UCallExpression): Boolean {
        val name = getMethodName(node)
        if (name == "apply" || name == "also") {
            return isScopingFunction(node)
        }
        return false
    }

    /**
     * Returns true if the given node appears to be one of the scope
     * functions. Only checks parent class; caller should intend that
     * it's actually one of let, with, apply, etc.
     */
    private fun isScopingFunction(node: UCallExpression): Boolean {
        val called = node.resolve() ?: return true
        // See libraries/stdlib/jvm/build/stdlib-declarations.json
        return called.containingClass?.qualifiedName == "kotlin.StandardKt__StandardKt"
    }
}
