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
package com.android.tools.lint.detector.api.interprocedural

import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Multimap
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.kotlin.KotlinUFunctionCallExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Maps expressions and variables to likely receivers,
 * including classes and lambdas, using static analysis.
 * For example, consider the following code
 * ```
 * Runnable r = Foo::bar();
 * r.run();
 * ```
 * The call to `run` resolves to the base method in `Runnable`,
 * but we want to know that it will actually dispatch to `Foo#bar`.
 * This information is captured by mapping `r` to the method reference `Foo::bar`.
 *
 * Note that call receiver evaluators often compose with and augment each other.
 *
 * Note that the term "dispatch receiver" is used to distinguish between the receiver of a call
 * (i.e., the implicit `this` argument) and the callable object of the call (e.g., a lambda
 * bound to a variable). The Kotlin frontend seems to use similar terminology.
 */
abstract class DispatchReceiverEvaluator(
    // Call receiver evaluators often compose with and augment each other.
    // The [delegate] field holds the dispatch receiver evaluator that this one augments.
    private val delegate: DispatchReceiverEvaluator? = null
) {

    /**
     * Get dispatch receivers for [element].
     * Since evaluators augment each other through delegation, [root] gives a way to
     * recurse back to the topmost evaluator.
     */
    operator fun get(
        element: UElement,
        root: DispatchReceiverEvaluator = this
    ): Collection<DispatchReceiver> {
        val ours = getOwn(element, root)
        val theirs = delegate?.get(element, root) ?: emptyList()
        return ours union theirs
    }

    /** Evaluates potential receivers for `this` separately, since `this` can be implicit. */
    fun getForImplicitThis(): Collection<DispatchReceiver> {
        val ours = getOwnForImplicitThis()
        val theirs = delegate?.getForImplicitThis() ?: emptyList()
        return ours union theirs
    }

    protected abstract fun getOwn(
        element: UElement,
        root: DispatchReceiverEvaluator
    ): Collection<DispatchReceiver>

    protected abstract fun getOwnForImplicitThis(): Collection<DispatchReceiver>
}

/** Represents a potential call handler, such as a class or lambda expression. */
sealed class DispatchReceiver {

    abstract val element: UElement

    data class Class(override val element: UClass) : DispatchReceiver() {
        /**
         * Refines the given method to the overriding method that would appear
         * in the virtual method table of this class.
         */
        fun refineToTarget(method: UMethod) =
            element.javaPsi.findMethodBySignature(method.javaPsi, true)
                ?.navigationElement?.toUElementOfType<UMethod>()
                ?.let { CallTarget.Method(it) }
    }

    sealed class Functional(override val element: UElement) : DispatchReceiver() {

        abstract fun toTarget(): CallTarget?

        data class Lambda(
            override val element: ULambdaExpression,
            val captureContext: ParamContext
        ) : Functional(element) {

            override fun toTarget() = CallTarget.Lambda(element)
        }

        data class Reference(
            override val element: UCallableReferenceExpression,
            val receiver: DispatchReceiver.Class?
        ) : Functional(element) {

            override fun toTarget(): CallTarget.Method? {
                val baseCallee = element.resolve().toUElementOfType<UMethod>()
                    ?: return null
                return if (receiver == null) {
                    CallTarget.Method(baseCallee)
                } else {
                    receiver.refineToTarget(baseCallee)
                }
            }
        }
    }
}

/** Returns a deterministically ordered list of variables captured by this lambda. */
private fun ULambdaExpression.getCaptures(): List<UVariable> {
    val res = LinkedHashSet<UVariable>()

    accept(object : AbstractUastVisitor() {
        override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
        ): Boolean {
            val resolved = node.resolve()?.navigationElement.toUElementOfType<UVariable>()
                ?: return super.visitSimpleNameReferenceExpression(node)
            val isCaptured = generateSequence<UElement>(resolved) { it.uastParent }
                .none { it == this@getCaptures }
            if (isCaptured) {
                res.add(resolved)
            }
            return super.visitSimpleNameReferenceExpression(node)
        }
    })

    return res.toList()
}

/** Tries to map expressions to receivers without relying on interprocedural context. */
class SimpleExpressionDispatchReceiverEvaluator(
    private val cha: ClassHierarchy
) : DispatchReceiverEvaluator() {

    override fun getOwn(
        element: UElement,
        root: DispatchReceiverEvaluator
    ): Collection<DispatchReceiver> = when {
        element is UArrayAccessExpression -> root[element.receiver] // Unwrap.
        element is UUnaryExpression -> root[element.operand] // Unwrap.
        element is ULambdaExpression -> {
            // Pair the lambda with a capture context by taking the Cartesian product of
            // the evidenced dispatch receivers of its captures.
            val captures = element.getCaptures()
            val (capturesWithReceivers, nonEmptyDispatchReceivers) = captures
                .map { Pair(it, root[it].toList()) }
                .filter { (_, dispatchReceivers) -> dispatchReceivers.isNotEmpty() }
                .unzip()
            val cartesianProd = Lists.cartesianProduct(nonEmptyDispatchReceivers)
            val paramContexts = cartesianProd
                .take(GRAPH_EXPANSION_LIMIT) // Cap combinatorial explosions.
                .map { receiverTuple ->
                    val zipped = capturesWithReceivers.zip(receiverTuple)
                    ParamContext(zipped, implicitThis = null)
                }
            paramContexts.map { DispatchReceiver.Functional.Lambda(element, it) }
        }
        element is UCallableReferenceExpression -> {
            val receiverExpr = element.qualifierExpression
            if (receiverExpr == null || receiverExpr is UTypeReferenceExpression) {
                listOf(DispatchReceiver.Functional.Reference(element, receiver = null))
            } else {
                root[receiverExpr].filterIsInstance<DispatchReceiver.Class>().map { receiver ->
                    DispatchReceiver.Functional.Reference(element, receiver)
                }
            }
        }
        element is UObjectLiteralExpression -> {
            listOf(DispatchReceiver.Class(element.declaration))
        }
        element is UCallExpression && element.kind == UastCallKind.CONSTRUCTOR_CALL -> {
            // Constructor calls always return an exact type.
            val instantiatedClass = (element.returnType as? PsiClassType)
                ?.resolve()?.navigationElement.toUElementOfType<UClass>()
                ?.let { DispatchReceiver.Class(it) }
            listOfNotNull(instantiatedClass)
        }
        element is UExpression -> {
            // Use class hierarchy analysis to try to refine a static type to a unique runtime type.
            val classType = element.getExpressionType() as? PsiClassType
            val baseClass = classType?.resolve()?.navigationElement.toUElementOfType<UClass>()
            when {
                baseClass == null -> emptyList() // Unable to resolve class.
                LambdaUtil.isFunctionalClass(baseClass.javaPsi) -> {
                    emptyList() // SAM interfaces often have implicit inheritors (lambdas).
                }
                else -> {
                    fun UClass.isInstantiable() =
                        !isInterface && !javaPsi.hasModifierProperty(PsiModifier.ABSTRACT)

                    val subtypes = cha.allInheritorsOf(baseClass) + baseClass
                    val uniqueReceiverClass = subtypes
                        .filter { it.isInstantiable() }
                        .singleOrNull()
                        ?.let { DispatchReceiver.Class(it) }
                    listOfNotNull(uniqueReceiverClass)
                }
            }
        }
        else -> emptyList()
    }

    override fun getOwnForImplicitThis(): Collection<DispatchReceiver> = emptyList()
}

/** Maps variables and methods to dispatch receivers, based only on local context. */
class IntraproceduralDispatchReceiverEvaluator(
    simpleExprEval: SimpleExpressionDispatchReceiverEvaluator,
    private val varMap: Multimap<UVariable, DispatchReceiver>,
    private val methodMap: Multimap<UMethod, DispatchReceiver>
) : DispatchReceiverEvaluator(simpleExprEval) {

    override fun getOwn(
        element: UElement,
        root: DispatchReceiverEvaluator
    ): Collection<DispatchReceiver> = when (element) {
        is UVariable -> varMap[element]
        is UMethod -> methodMap[element]
        is USimpleNameReferenceExpression, is UCallExpression -> {
            val resolved = (element as UResolvable).resolve()?.navigationElement.toUElement()
            resolved?.let { root[it] } ?: emptyList()
        }
        else -> emptyList()
    }

    override fun getOwnForImplicitThis(): Collection<DispatchReceiver> = emptyList()
}

/**
 * Uses a flow-insensitive UAST traversal to map variables to
 * potential receivers based on local context, building up
 * an intraprocedural receiver evaluator.
 */
class IntraproceduralDispatchReceiverVisitor(cha: ClassHierarchy) : AbstractUastVisitor() {
    private val varMap = HashMultimap.create<UVariable, DispatchReceiver>()
    private val methodMap = HashMultimap.create<UMethod, DispatchReceiver>()
    private val methodsVisited = HashSet<UMethod>()
    val receiverEval =
        IntraproceduralDispatchReceiverEvaluator(
            SimpleExpressionDispatchReceiverEvaluator(cha), varMap, methodMap
        )

    override fun visitMethod(node: UMethod): Boolean {
        if (methodsVisited.contains(node))
            return true // Avoids infinite recursion.
        methodsVisited.add(node)
        return super.visitMethod(node)
    }

    override fun afterVisitReturnExpression(node: UReturnExpression) {
        // Map methods to returned receivers.
        val method = node.getContainingUMethod() ?: return
        node.returnExpression?.let { methodMap.putAll(method, receiverEval[it]) }
    }

    override fun afterVisitCallExpression(node: UCallExpression) {
        // Try to visit the resolved method first in order to get evidenced return values.
        val resolved = node.resolve().toUElementOfType<UMethod>() ?: return
        resolved.accept(this)
    }

    override fun afterVisitVariable(node: UVariable) {
        node.uastInitializer?.let { handleAssign(node, it) }
    }

    override fun afterVisitBinaryExpression(node: UBinaryExpression) {
        if (node.operator == UastBinaryOperator.ASSIGN) {
            handleAssign(node.leftOperand, node.rightOperand)
        }
    }

    private fun handleAssign(lval: UElement, expr: UExpression) {
        when (lval) {
            is UVariable -> varMap.putAll(lval, receiverEval[expr])
            is UArrayAccessExpression -> handleAssign(lval.receiver, expr) // Unwrap.
            is UUnaryExpression -> handleAssign(lval.operand, expr) // Unwrap.
            is UMethod -> methodMap.putAll(lval, receiverEval[expr]) // Handles Kotlin setters.
            is USimpleNameReferenceExpression -> {
                val resolved = lval.resolve()?.navigationElement.toUElement()
                resolved?.let { handleAssign(it, expr) }
            }
        }
    }
}

fun UCallExpression.getDispatchReceivers(
    receiverEval: DispatchReceiverEvaluator
): Collection<DispatchReceiver> {

    // If this function call is really an 'invoke' on a local lambda variable,
    // we resolve to the variable declaration before consulting the dispatch receiver evaluator.
    // TODO(kotlin-uast-cleanup): For now we use heuristics to decide whether this is an
    //  invoke on a local lambda variable; it's not obvious that there's a better way.
    if (this is KotlinUFunctionCallExpression && methodName == "invoke") {
        val decl = receiver?.tryResolve().toUElement() ?: return emptyList()
        return receiverEval[decl]
    }

    // "Normal" method calls.
    return receiver?.let { receiverEval[it] } ?: receiverEval.getForImplicitThis()
}

/** Convert this call expression into a list of likely targets given a call dispatch receivers. */
fun UCallExpression.getTarget(dispatchReceiver: DispatchReceiver): CallTarget? {

    // TODO(kotlin-uast-cleanup): See comment in getDispatchReceivers.
    if (methodName == "invoke" && classReference != null) {
        return when (dispatchReceiver) {
            is DispatchReceiver.Class -> null
            is DispatchReceiver.Functional -> dispatchReceiver.toTarget()
        }
    }

    // "Normal" method calls.
    val method = resolve().toUElementOfType<UMethod>() ?: return null
    if (method.isStatic)
        return CallTarget.Method(method)
    fun isFunctionalCall() = method.javaPsi == LambdaUtil.getFunctionalInterfaceMethod(receiverType)
    return when (dispatchReceiver) {
        is DispatchReceiver.Class -> dispatchReceiver.refineToTarget(method)
        is DispatchReceiver.Functional -> {
            if (isFunctionalCall())
                dispatchReceiver.toTarget()
            else null
        }
    }
}

fun UCallExpression.getTargets(receiverEval: DispatchReceiverEvaluator) =
    getDispatchReceivers(receiverEval).mapNotNull { getTarget(it) }
