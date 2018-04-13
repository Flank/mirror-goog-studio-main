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

import com.android.tools.lint.detector.api.skipParentheses
import com.google.common.collect.Sets
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Helper class for analyzing data flow */
abstract class DataFlowAnalyzer(
    val initial: Collection<UElement>
) : AbstractUastVisitor() {

    /** The instance being tracked is the receiver for a method call */
    open fun receiver(call: UCallExpression) {}

    /** The instance being tracked is being returned from this block */
    open fun returns(expression: UReturnExpression) {}

    /** The instance being tracked is being stored into a field */
    open fun field(field: UElement) {}

    /** The instance being tracked is being passed in a method call */
    open fun argument(
        call: UCallExpression,
        reference: UElement
    ) {
    }

    protected val references: MutableSet<PsiVariable> = mutableSetOf()
    protected val instances: MutableSet<UElement> = Sets.newIdentityHashSet<UElement>()

    init {
        if (instances.isEmpty()) {
            instances.addAll(initial)
            for (element in initial) {
                var parent = element.uastParent
                var prev = element
                while (parent != null) {
                    if (parent is UParenthesizedExpression) {
                        parent = parent.uastParent // don't reset prev
                        continue
                    }
                    if (parent is UQualifiedReferenceExpression && parent.receiver === prev) {
                        val selector = parent.selector
                        if (selector is UCallExpression && returnsSelf(selector)) {
                            instances.add(parent)
                            instances.add(selector)
                        } else {
                            break
                        }
                    } else if (parent is UQualifiedReferenceExpression && parent.selector == prev) {
                        instances.add(parent)
                    } else if (parent is ULocalVariable) {
                        if (prev === parent.uastInitializer) {
                            (parent.sourcePsi as? PsiVariable)?.let { references.add(it) }
                        } else {
                            break
                        }
                    } else {
                        if (element is UCallExpression) {
                            val boundVariable = getVariableElement(element)
                            if (boundVariable != null) {
                                references.add(boundVariable)
                            }
                        }

                        break
                    }
                    prev = parent
                    parent = parent.uastParent
                }
            }
        }
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
        node.receiver?.let { receiver ->
            if (instances.contains(node)) {
                if (!initial.contains(node)) {
                    receiver(node)
                }
            } else {
                receiver.tryResolve().let { resolved ->
                    if (references.contains(resolved)) {
                        if (!initial.contains(node)) {
                            receiver(node)
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

    override fun visitVariable(node: UVariable): Boolean {
        if (node is ULocalVariable) {
            val variable = node.sourcePsi as? PsiLocalVariable ?: node
            val initializer = node.uastInitializer
            if (initializer != null) {
                if (instances.contains(initializer)) {
                    // Instance is stored in a variable
                    references.add(variable)
                } else if (initializer is UReferenceExpression) {
                    val resolved = initializer.resolve()
                    if (resolved != null && references.contains(resolved)) {
                        references.add(variable)
                    }
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
        if (instances.contains(rhs)) {
            val lhs = node.leftOperand.tryResolve()
            if (lhs is PsiLocalVariable) {
                references.add(lhs)
            } else if (lhs is PsiField) {
                field(rhs)
            }
        } else if (rhs is UReferenceExpression) {
            val resolved = rhs.resolve()
            if (resolved != null && references.contains(resolved)) {
                clearLhs = false
                val lhs = node.leftOperand.tryResolve()
                if (lhs is PsiLocalVariable) {
                    references.add(lhs)
                } else if (lhs is PsiField) {
                    field(rhs)
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

        return super.visitBinaryExpression(node)
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
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

        return super.visitReturnExpression(node)
    }

    companion object {
        /** Returns the variable the expression is assigned to, if any  */
        fun getVariableElement(rhs: UCallExpression): PsiVariable? {
            return getVariableElement(rhs, false, false)
        }

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
                    parent.uastParent!!.uastParent is UExpressionList) {
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

        /**
         * Tries to guess whether the given method call returns self.
         * This is intended to be able to tell that in a constructor
         * call chain foo().bar().baz() is still invoking methods on the
         * foo instance.
         */
        fun returnsSelf(call: UCallExpression): Boolean {
            // Heuristic: the return method is the same type as the class
            return (call.returnType as? PsiClassType)?.resolve() == call.resolve()?.containingClass
        }
    }
}