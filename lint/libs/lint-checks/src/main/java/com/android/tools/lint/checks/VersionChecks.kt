package com.android.tools.lint.checks

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.checks.ApiConstraint.Companion.above
import com.android.tools.lint.checks.ApiConstraint.Companion.atLeast
import com.android.tools.lint.checks.ApiConstraint.Companion.atMost
import com.android.tools.lint.checks.ApiConstraint.Companion.below
import com.android.tools.lint.checks.ApiConstraint.Companion.range
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.skipParentheses
import com.android.utils.SdkUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.visitor.AbstractUastVisitor

private typealias ApiLevelLookup = (UElement) -> Int

/**
 * Helper for checking whether a given element is surrounded (or
 * preceded!) by an API check using SDK_INT (or other version checking
 * utilities such as BuildCompat#isAtLeastN)
 */
class VersionChecks(
    private val client: LintClient,
    private val evaluator: JavaEvaluator,
    private val project: Project?,
    private val lowerBound: Boolean = true
) {
    companion object {
        const val SDK_INT = "SDK_INT"
        const val CHECKS_SDK_INT_AT_LEAST_ANNOTATION = "androidx.annotation.ChecksSdkIntAtLeast"

        /** SDK int method used by the data binding compiler. */
        private const val GET_BUILD_SDK_INT = "getBuildSdkInt"

        @JvmStatic
        fun codeNameToApi(text: String): Int {
            val dotIndex = text.lastIndexOf('.')
            val buildCode =
                if (dotIndex != -1) {
                    text.substring(dotIndex + 1)
                } else {
                    text
                }
            return SdkVersionInfo.getApiByBuildCode(buildCode, true)
        }

        @JvmStatic
        @JvmOverloads
        fun isWithinVersionCheckConditional(
            context: JavaContext,
            element: UElement,
            api: Int,
            lowerBound: Boolean = true
        ): Boolean {
            val client = context.client
            val evaluator = context.evaluator
            val project = context.project
            val check = VersionChecks(client, evaluator, project, lowerBound)
            val constraint = check.getWithinVersionCheckConditional(
                evaluator = evaluator, element = element, apiLookup = null
            ) ?: return false
            return constraint.matches(api)
        }

        @JvmStatic
        @JvmOverloads
        fun isWithinVersionCheckConditional(
            client: LintClient,
            evaluator: JavaEvaluator,
            element: UElement,
            api: Int,
            lowerBound: Boolean = true
        ): Boolean {
            val check = VersionChecks(client, evaluator, null, lowerBound)
            val constraint = check.getWithinVersionCheckConditional(
                evaluator = evaluator, element = element, apiLookup = null
            ) ?: return false
            return constraint.matches(api)
        }

        @JvmStatic
        fun isPrecededByVersionCheckExit(
            context: JavaContext,
            element: UElement,
            api: Int
        ): Boolean {
            val client = context.client
            val evaluator = context.evaluator
            val project = context.project
            // TODO: Switch to constraints!
            return isPrecededByVersionCheckExit(client, evaluator, element, api, project)
        }

        @JvmStatic
        fun isPrecededByVersionCheckExit(
            client: LintClient,
            evaluator: JavaEvaluator,
            element: UElement,
            api: Int,
            project: Project? = null
        ): Boolean {
            val check = VersionChecks(client, evaluator, project)
            var prev = element
            var current: UExpression? = prev.getParentOfType(
                UExpression::class.java,
                true,
                UMethod::class.java,
                UClass::class.java
            )
            while (current != null) {
                val visitor = check.VersionCheckWithExitFinder(prev, api)
                current.accept(visitor)
                if (visitor.found()) {
                    return true
                }
                prev = current
                current = current.getParentOfType(
                    UExpression::class.java,
                    true,
                    UMethod::class.java,
                    UClass::class.java
                )
                // TODO: what about lambdas?
            }
            return false
        }

        /**
         * Returns the actual API constraint enforced by the given
         * SDK_INT comparison.
         */
        @JvmStatic
        fun getVersionCheckConditional(binary: UBinaryExpression): ApiConstraint? {
            val tokenType = binary.operator
            if (tokenType === UastBinaryOperator.GREATER ||
                tokenType === UastBinaryOperator.GREATER_OR_EQUALS ||
                tokenType === UastBinaryOperator.LESS_OR_EQUALS ||
                tokenType === UastBinaryOperator.LESS ||
                tokenType === UastBinaryOperator.EQUALS ||
                tokenType === UastBinaryOperator.IDENTITY_EQUALS
            ) {
                val left = binary.leftOperand
                if (left is UReferenceExpression) {
                    if (SDK_INT == left.resolvedName) {
                        val right = binary.rightOperand
                        var level = -1
                        if (right is UReferenceExpression) {
                            val codeName = right.resolvedName ?: return null
                            level = SdkVersionInfo.getApiByBuildCode(codeName, true)
                        } else if (right is ULiteralExpression) {
                            val value = right.value
                            if (value is Int) {
                                level = value
                            }
                        }
                        if (level != -1) {
                            if (tokenType === UastBinaryOperator.GREATER_OR_EQUALS) {
                                // SDK_INT >= ICE_CREAM_SANDWICH
                                return atLeast(level)
                            } else if (tokenType === UastBinaryOperator.GREATER) {
                                // SDK_INT > ICE_CREAM_SANDWICH
                                return above(level)
                            } else if (tokenType === UastBinaryOperator.LESS_OR_EQUALS) {
                                return atMost(level)
                            } else if (tokenType === UastBinaryOperator.LESS) {
                                // SDK_INT < ICE_CREAM_SANDWICH
                                return below(level)
                            } else if (tokenType === UastBinaryOperator.EQUALS ||
                                tokenType === UastBinaryOperator.IDENTITY_EQUALS
                            ) {
                                return range(level, level + 1)
                            }
                        }
                    }
                }
            }
            return null
        }

        private val VERSION_METHOD_NAME_PREFIXES = arrayOf(
            "isAtLeast", "isRunning", "is", "runningOn", "running", "has"
        )

        private val VERSION_METHOD_NAME_SUFFIXES = arrayOf(
            "OrLater", "OrAbove", "OrHigher", "OrNewer", "Sdk"
        )

        @VisibleForTesting
        fun getMinSdkVersionFromMethodName(name: String): Int {
            val prefix =
                VERSION_METHOD_NAME_PREFIXES.firstOrNull { name.startsWith(it) } ?: return -1
            val suffix =
                VERSION_METHOD_NAME_SUFFIXES.firstOrNull { SdkUtils.endsWithIgnoreCase(name, it) }
                    ?: if (prefix != "is") "" else null ?: return -1
            val codeName = name.substring(prefix.length, name.length - suffix.length)
            var version = SdkVersionInfo.getApiByPreviewName(codeName, false)
            if (version == -1) {
                version = SdkVersionInfo.getApiByBuildCode(codeName, false)
                if (version == -1 && codeName.length == 1 && Character.isUpperCase(codeName[0])
                ) {
                    // Some future API level
                    version = SdkVersionInfo.HIGHEST_KNOWN_API + 1
                } else if (SdkUtils.startsWithIgnoreCase(codeName, "api")) {
                    val length = codeName.length
                    var begin = 3 // "api".length
                    if (begin < length && codeName[begin] == '_') {
                        begin++
                    }
                    var end = begin
                    while (end < length) {
                        if (!Character.isDigit(codeName[end])) {
                            break
                        }
                        end++
                    }
                    if (begin < end) {
                        version = Integer.decode(codeName.substring(begin, end))
                    }
                }
            }
            return version
        }
    }

    private fun isUnconditionalReturn(statement: UExpression): Boolean {
        if (statement is UBlockExpression) {
            val expressions = statement.expressions
            val statements = expressions.size
            if (statements > 0) {
                val last = expressions[statements - 1]
                if (last is UReturnExpression || last is UThrowExpression) {
                    return true
                } else if (last is UCallExpression) {
                    val methodName = getMethodName(last)
                    // Look for Kotlin runtime library methods that unconditionally exit
                    if ("error" == methodName || "TODO" == methodName) {
                        return true
                    }
                }
            }
        }
        return statement is UReturnExpression
    }

    private fun getWithinVersionCheckConditional(
        evaluator: JavaEvaluator,
        element: UElement,
        apiLookup: ApiLevelLookup?
    ): ApiConstraint? {
        var current = skipParentheses(element.uastParent)
        var prev = element
        while (current != null) {

            if (current is UPolyadicExpression) {
                val anded = getAndedWithConditional(current, prev)
                if (anded != null) {
                    return anded
                }
                val ored = getOredWithConditional(current, prev)
                if (ored != null) {
                    return ored
                }
            }

            if (current is UIfExpression) {
                val ifStatement = current
                val condition = ifStatement.condition
                if (prev !== condition) {
                    val fromThen = prev == ifStatement.thenExpression
                    getVersionCheckConditional(
                        element = condition,
                        and = fromThen,
                        prev = prev,
                        apiLookup = apiLookup
                    )?.let { return it }
                }
            } else if (current is USwitchClauseExpressionWithBody) {
                for (condition in current.caseValues) {
                    getVersionCheckConditional(
                        element = condition,
                        and = true,
                        prev = prev,
                        apiLookup = apiLookup
                    )?.let { return it }
                }
            } else if (current is UCallExpression && prev is ULambdaExpression) {
                // If the API violation is in a lambda that is passed to a method,
                // see if the lambda parameter is invoked inside that method, wrapped within
                // a suitable version conditional.
                //
                // Optionally also see if we're passing in the API level as a parameter
                // to the function.
                //
                // Algorithm:
                //  (1) Figure out which parameter we're mapping the lambda argument to.
                //  (2) Find that parameter invoked within the function
                //  (3) From the invocation see if it's a suitable version conditional
                //
                val call = current
                val method = call.resolve()
                if (method != null) {
                    val annotation = SdkIntAnnotation.get(method)
                    if (annotation != null) {
                        val value = annotation.getApiLevel(evaluator, method, call)
                        if (value != null) {
                            return atMost(value)
                        } // else: lambda
                    }

                    val mapping = evaluator.computeArgumentMapping(call, method)
                    val parameter = mapping[prev]
                    if (parameter != null) {
                        val lambdaInvocation = getLambdaInvocation(parameter, method)
                        if (lambdaInvocation != null) {
                            val constraint = getWithinVersionCheckConditional(
                                evaluator = evaluator,
                                element = lambdaInvocation,
                                apiLookup = getReferenceApiLookup(call)
                            )
                            if (constraint != null) {
                                return constraint
                            }
                        }
                    }
                }
            } else if (current is UCallExpression && prev is UObjectLiteralExpression) {
                val method = current.resolve()
                if (method != null) {
                    val annotation = SdkIntAnnotation.get(method)
                    if (annotation != null) {
                        val value = annotation.getApiLevel(evaluator, method, current)
                        if (value != null) {
                            return atMost(value)
                        } // else: lambda
                    }

                    val mapping = evaluator.computeArgumentMapping(current, method)
                    val parameter = mapping[prev]
                    if (parameter != null) {
                        val lambdaInvocation = getLambdaInvocation(parameter, method)
                        if (lambdaInvocation != null) {
                            val constraint = getWithinVersionCheckConditional(
                                evaluator = evaluator,
                                element = lambdaInvocation,
                                apiLookup = getReferenceApiLookup(current)
                            )
                            if (constraint != null) {
                                return constraint
                            }
                        }
                    }
                }
            } else if (current is UMethod) {
                if (current.uastParent !is UAnonymousClass) return null
            } else if (current is PsiFile) {
                return null
            }
            prev = current
            current = skipParentheses(current.uastParent)
        }
        return null
    }

    private fun getReferenceApiLookup(call: UCallExpression): (UElement) -> Int {
        return { reference ->
            var apiLevel = -1
            if (reference is UReferenceExpression) {
                val resolved = reference.resolve()
                if (resolved is PsiParameter) {
                    val parameterList =
                        PsiTreeUtil.getParentOfType(
                            resolved, PsiParameterList::class.java
                        )
                    if (parameterList != null) {
                        val index = parameterList.getParameterIndex(resolved)
                        val arguments = call.valueArguments
                        if (index != -1 && index < arguments.size) {
                            apiLevel = getApiLevel(arguments[index], null)
                        }
                    }
                }
            }
            apiLevel
        }
    }

    private fun getLambdaInvocation(
        parameter: PsiParameter,
        method: PsiMethod
    ): UCallExpression? {
        if (method is PsiCompiledElement) {
            return null
        }
        val uMethod = UastFacade.convertElementWithParent(
            method,
            UMethod::class.java
        ) as UMethod? ?: return null

        val match = Ref<UCallExpression>()
        val parameterName = parameter.name
        uMethod.accept(
            object : AbstractUastVisitor() {
                override fun visitCallExpression(
                    node: UCallExpression
                ): Boolean {
                    val receiver = node.receiver
                    if (receiver is USimpleNameReferenceExpression) {
                        val name = receiver.identifier
                        if (name == parameterName) {
                            match.set(node)
                        }
                    } else if (receiver is UReferenceExpression) {
                        val name = receiver.resolvedName
                        if (name == parameterName) {
                            match.set(node)
                        }
                    }

                    val callName = getMethodName(node)
                    if (callName == parameterName) {
                        // Potentially not correct due to scopes, but these lambda
                        // utility methods tend to be short and for lambda function
                        // calls, resolve on call returns null
                        match.set(node)
                    }

                    return super.visitCallExpression(node)
                }
            })

        return match.get()
    }

    private fun getVersionCheckConditional(
        element: UElement,
        and: Boolean,
        prev: UElement? = null,
        apiLookup: ApiLevelLookup? = null
    ): ApiConstraint? {
        if (element is UPolyadicExpression) {
            if (element is UBinaryExpression) {
                getVersionCheckConditional(
                    fromThen = and,
                    binary = element,
                    apiLevelLookup = apiLookup
                )?.let { return it }
            }
            val tokenType = element.operator
            if (and && tokenType === UastBinaryOperator.LOGICAL_AND) {
                val constraint = getAndedWithConditional(element, prev)
                if (constraint != null) {
                    return constraint
                }
            } else if (!and && tokenType === UastBinaryOperator.LOGICAL_OR) {
                val constraint = getOredWithConditional(element, prev)
                if (constraint != null) {
                    return constraint
                }
            }
        } else if (element is UCallExpression) {
            return getValidVersionCall(and, element)
        } else if (element is UReferenceExpression) {
            // Constant expression for an SDK version check?
            val resolved = element.resolve()
            if (resolved is PsiField) {
                @Suppress("UnnecessaryVariable")
                val field = resolved

                val validFromAnnotation = getValidFromAnnotation(field)
                if (validFromAnnotation != null) {
                    return validFromAnnotation
                }
                val validFromInferredAnnotation = getValidFromInferredAnnotation(field)
                if (validFromInferredAnnotation != null) {
                    return validFromInferredAnnotation
                }
                val modifierList = field.modifierList
                if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                    val initializer = UastFacade.getInitializerBody(field)
                    if (initializer != null) {
                        val constraint = getVersionCheckConditional(
                            element = initializer,
                            and = and
                        )
                        if (constraint != null) {
                            return constraint
                        }
                    }
                }
            } else if (resolved is PsiMethod &&
                element is UQualifiedReferenceExpression &&
                element.selector is UCallExpression
            ) {
                val call = element.selector as UCallExpression
                return getValidVersionCall(and, call)
            } else if (resolved is PsiMethod) {
                // Method call via Kotlin property syntax
                return getValidVersionCall(
                    and = and,
                    call = element,
                    method = resolved
                )
            } else if (resolved == null && element is UQualifiedReferenceExpression) {
                val selector = element.selector
                if (selector is UCallExpression) {
                    return getValidVersionCall(
                        and = and,
                        call = selector
                    )
                }
            }
        } else if (element is UUnaryExpression) {
            if (element.operator === UastPrefixOperator.LOGICAL_NOT) {
                val operand = element.operand
                getVersionCheckConditional(element = operand, and = !and)?.let { return it }
            }
        }
        return null
    }

    private fun getValidFromAnnotation(
        owner: PsiModifierListOwner,
        call: UCallExpression? = null
    ): ApiConstraint? {
        val annotation = AnnotationUtil.findAnnotation(
            owner, true, CHECKS_SDK_INT_AT_LEAST_ANNOTATION
        ) ?: return null
        val value = SdkIntAnnotation(annotation).getApiLevel(evaluator, owner, call) ?: return null
        return atMost(value)
    }

    /**
     * When we come across SDK_INT comparisons in library, we'll store
     * that as an implied @ChecksSdkIntAtLeast annotation (to match the
     * existing support for actual @ChecksSdkIntAtLeast annotations).
     * Here, when looking up version checks we'll check the given method
     * or field and see if we've stashed any implied version checks when
     * analyzing the dependencies.
     */
    private fun getValidFromInferredAnnotation(
        owner: PsiModifierListOwner,
        call: UCallExpression? = null
    ): ApiConstraint? {
        if (!client.supportsPartialAnalysis()) {
            return null
        }
        if (project == null || owner !is PsiCompiledElement) {
            return null
        }
        val annotation = when (owner) {
            is PsiMethod ->
                SdkIntDetector.findSdkIntAnnotation(client, evaluator, project, owner)
                    ?: return null
            is PsiField ->
                SdkIntDetector.findSdkIntAnnotation(client, evaluator, project, owner)
                    ?: return null
            else -> return null
        }
        val value = annotation.getApiLevel(evaluator, owner, call) ?: return null
        return atMost(value)
    }

    private fun getValidVersionCall(
        and: Boolean,
        call: UCallExpression
    ): ApiConstraint? {
        val method = call.resolve()
        if (method == null) {
            // Fallback when we can't resolve call: Try to guess just based on the method name
            val identifier = call.methodIdentifier
            if (identifier != null) {
                val name = identifier.name
                val version = getMinSdkVersionFromMethodName(name)
                if (version != -1) {
                    return (if (and) ::atMost else ::above)(version)
                }
            }
            return null
        }
        return getValidVersionCall(and, call, method)
    }

    private fun getValidVersionCall(
        and: Boolean,
        call: UElement,
        method: PsiMethod
    ): ApiConstraint? {
        val callExpression = call as? UCallExpression
        val validFromAnnotation = getValidFromAnnotation(method, callExpression)
        if (validFromAnnotation != null) {
            return validFromAnnotation
        }

        val validFromInferredAnnotation = getValidFromInferredAnnotation(method, callExpression)
        if (validFromInferredAnnotation != null) {
            return validFromInferredAnnotation
        }

        val name = method.name
        if (name.startsWith("isAtLeast")) {
            val containingClass = method.containingClass
            if (containingClass != null &&
                // android.support.v4.os.BuildCompat,
                // androidx.core.os.BuildCompat
                "BuildCompat" == containingClass.name
            ) {
                when {
                    name == "isAtLeastN" -> return atMost(24)
                    name == "isAtLeastNMR1" -> return atMost(25)
                    name == "isAtLeastO" -> return atMost(26)
                    name.startsWith("isAtLeastP") -> return atMost(28)
                    name.startsWith("isAtLeastQ") -> return atMost(29)
                    // Try to guess future API levels before they're announced
                    name.startsWith("isAtLeast") &&
                        name.length == 10 && Character.isUpperCase(name[9])
                        && name[9] > 'Q' ->
                        return atMost(SdkVersionInfo.HIGHEST_KNOWN_API + 1)
                }
            }
        }
        val version = getMinSdkVersionFromMethodName(name)
        if (version != -1) {
            return atMost(version)
        }

        // Unconditional version utility method? If so just attempt to call it
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            val body = UastFacade.getMethodBody(method) ?: return null
            val expressions: List<UExpression> = if (body is UBlockExpression) {
                body.expressions
            } else {
                listOf(body)
            }
            if (expressions.size == 1) {
                val statement = expressions[0]
                val returnValue: UExpression? = if (statement is UReturnExpression) {
                    statement.returnExpression
                } else {
                    // Kotlin: may not have an explicit return statement
                    statement
                }
                if (returnValue != null) {
                    val arguments =
                        if (call is UCallExpression) call.valueArguments else emptyList()
                    if (arguments.isEmpty()) {
                        if (returnValue is UPolyadicExpression ||
                            returnValue is UCallExpression ||
                            returnValue is UQualifiedReferenceExpression
                        ) {
                            val constraint =
                                getVersionCheckConditional(element = returnValue, and = and)
                            if (constraint != null) {
                                return constraint
                            }
                        }
                    } else if (arguments.size == 1) {
                        // See if we're passing in a value to the version utility method
                        val lookup: (UElement) -> Int = { reference ->
                            var apiLevel = -1
                            if (reference is UReferenceExpression) {
                                val resolved = reference.resolve()
                                if (resolved is PsiParameter) {
                                    val parameterList =
                                        PsiTreeUtil.getParentOfType(
                                            resolved, PsiParameterList::class.java
                                        )
                                    if (parameterList != null) {
                                        val index = parameterList.getParameterIndex(resolved)
                                        if (index != -1 && index < arguments.size) {
                                            apiLevel = getApiLevel(arguments[index], null)
                                        }
                                    }
                                }
                            }
                            apiLevel
                        }
                        val constraint = getVersionCheckConditional(
                            element = returnValue,
                            and = and,
                            apiLookup = lookup
                        )
                        if (constraint != null) {
                            return constraint
                        }
                    }
                }
            }
        }

        return null
    }

    private fun isSdkInt(element: PsiElement): Boolean {
        if (element is PsiReferenceExpression) {
            if (SDK_INT == element.referenceName) {
                return true
            }
            val resolved = element.resolve()
            if (resolved is PsiVariable) {
                val initializer = resolved.initializer
                if (initializer != null) {
                    return isSdkInt(initializer)
                }
            }
        } else if (element is PsiMethodCallExpression) {
            if (GET_BUILD_SDK_INT == element.methodExpression.referenceName) {
                return true
            } // else look inside the body?
        }
        return false
    }

    private fun isSdkInt(element: UElement): Boolean {
        if (element is UReferenceExpression) {
            if (SDK_INT == element.resolvedName) {
                return true
            }
            val resolved = element.resolve()
            if (resolved is ULocalVariable) {
                val initializer = resolved.uastInitializer
                if (initializer != null) {
                    return isSdkInt(initializer)
                }
            } else if (resolved is PsiVariable) {
                val initializer = resolved.initializer
                if (initializer != null) {
                    return isSdkInt(initializer)
                }
            }
        } else if (element is UCallExpression) {
            if (GET_BUILD_SDK_INT == getMethodName(element)) {
                return true
            } // else look inside the body?
        }
        return false
    }

    /**
     * For a given SDK_INT check, this method returns the API
     * constraints for **safe** API level usages within the then-body of
     * this if-check. If [fromThen] is false, the code is in the else
     * clause instead.
     *
     * For example, this code:
     *
     *     if (SDK_INT >= 28) {
     *         requiresN()
     *
     * is safe for values of N from 1 up through 28, so it will return
     *
     * an API constraint of API <= 28, whereas the
     * [getVersionCheckConditional] method returns API >= 28.
     */
    private fun getVersionCheckConditional(
        binary: UBinaryExpression,
        fromThen: Boolean,
        apiLevelLookup: ApiLevelLookup? = null
    ): ApiConstraint? {
        @Suppress("NAME_SHADOWING")
        var fromThen = fromThen
        val tokenType = binary.operator
        if (tokenType === UastBinaryOperator.GREATER ||
            tokenType === UastBinaryOperator.GREATER_OR_EQUALS ||
            tokenType === UastBinaryOperator.LESS_OR_EQUALS ||
            tokenType === UastBinaryOperator.LESS ||
            tokenType === UastBinaryOperator.EQUALS ||
            tokenType === UastBinaryOperator.IDENTITY_EQUALS ||
            tokenType === UastBinaryOperator.NOT_EQUALS ||
            tokenType === UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            val left = binary.leftOperand
            val level: Int
            val right: UExpression
            if (!isSdkInt(left)) {
                right = binary.rightOperand
                if (isSdkInt(right)) {
                    fromThen = !fromThen
                    level = getApiLevel(left, apiLevelLookup)
                } else {
                    return null
                }
            } else {
                right = binary.rightOperand
                level = getApiLevel(right, apiLevelLookup)
            }

            if (level != -1) {
                if (tokenType === UastBinaryOperator.GREATER_OR_EQUALS) {
                    return if (lowerBound) {
                        // Here we we know that we're on ice cream sandwich or later.
                        // That means it's safe to use call from all older versions
                        // up to and including ice cream sandwich itself.
                        if (fromThen) atMost(level) else null
                    } else {
                        // if (SDK_INT >= ICE_CREAM_SANDWICH) {  } else { <here> }
                        // so in <here>, SDK_INT < ICE_CREAM_SANDWICH
                        if (!fromThen) atLeast(level - 2) else null
                    }
                } else if (tokenType === UastBinaryOperator.GREATER) {
                    return if (lowerBound) {
                        if (fromThen) atMost(level + 1) else null
                    } else {
                        if (!fromThen) atLeast(level - 1) else null
                    }
                } else if (tokenType === UastBinaryOperator.LESS_OR_EQUALS) {
                    return if (lowerBound) {
                        if (!fromThen) atMost(level + 1) else null
                    } else {
                        if (fromThen) atLeast(level - 2) else null
                    }
                } else if (tokenType === UastBinaryOperator.LESS) {
                    return if (lowerBound) {
                        if (!fromThen) atMost(level) else null
                    } else {
                        if (fromThen) atLeast(level - 3) else null
                    }
                } else if (tokenType === UastBinaryOperator.EQUALS ||
                    tokenType === UastBinaryOperator.IDENTITY_EQUALS
                ) {
                    // if (SDK_INT == ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return if (lowerBound) {
                        if (fromThen) atMost(level) else null
                    } else {
                        if (fromThen) atLeast(level) else null
                    }
                } else if (tokenType === UastBinaryOperator.NOT_EQUALS ||
                    tokenType === UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    // if (SDK_INT != ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return if (!fromThen) range(level, level + 1) else null
                } else {
                    assert(false) { tokenType }
                }
            }
        }

        return null
    }

    private fun getApiLevel(
        element: UExpression?,
        apiLevelLookup: ApiLevelLookup?
    ): Int {
        var level = -1
        if (element is UReferenceExpression) {
            val codeName = element.resolvedName
            if (codeName != null) {
                level = SdkVersionInfo.getApiByBuildCode(codeName, false)
            }
            if (level == -1) {
                val constant = ConstantEvaluator.evaluate(null, element)
                if (constant is Number) {
                    level = constant.toInt()
                }
            }
        } else if (element is ULiteralExpression) {
            val value = element.value
            if (value is Int) {
                level = value
            }
        }
        if (level == -1 && apiLevelLookup != null && element != null) {
            level = apiLevelLookup(element)
        }
        return level
    }

    @Suppress("SpellCheckingInspection")
    private fun getOredWithConditional(
        element: UElement,
        before: UElement?
    ): ApiConstraint? {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                val left = element.leftOperand
                if (before !== left) {
                    getVersionCheckConditional(element = left, and = false)?.let { return it }
                    val right = element.rightOperand
                    getVersionCheckConditional(element = right, and = false)?.let { return it }
                }
            }
            getVersionCheckConditional(fromThen = false, binary = element)?.let { return it }
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else {
                        val constraint = getOredWithConditional(
                            element = operand,
                            before = before
                        )
                        if (constraint != null) {
                            return constraint
                        }
                    }
                }
            }
        }
        return null
    }

    @Suppress("SpellCheckingInspection")
    private fun getAndedWithConditional(
        element: UElement,
        before: UElement?
    ): ApiConstraint? {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                val left = element.leftOperand
                if (before !== left) {
                    getVersionCheckConditional(element = left, and = true)?.let { return it }
                    val right = element.rightOperand
                    getVersionCheckConditional(element = right, and = true)?.let { return it }
                }
            }
            getVersionCheckConditional(fromThen = true, binary = element)?.let { return it }
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else {
                        val constraint = getVersionCheckConditional(operand, and = true)
                        if (constraint != null) {
                            return constraint
                        }
                    }
                }
            }
        }
        return null
    }

    private inner class VersionCheckWithExitFinder(
        private val endElement: UElement,
        private val api: Int,
    ) : AbstractUastVisitor() {
        private var found = false
        private var done = false
        override fun visitElement(node: UElement): Boolean {
            if (done) {
                return true
            }
            if (node === endElement) {
                done = true
            }
            return done
        }

        override fun visitIfExpression(node: UIfExpression): Boolean {
            super.visitIfExpression(node)
            if (done) {
                return true
            }
            if (endElement.isUastChildOf(node, true)) {
                // Even if there is an unconditional exit, endElement will occur before it!
                done = true
                return true
            }
            val thenBranch = node.thenExpression
            val elseBranch = node.elseExpression
            if (thenBranch != null) {
                val constraint = getVersionCheckConditional(
                    element = node.condition,
                    and = false
                )
                if (constraint?.matches(api) == true) {
                    // See if the body does an immediate return
                    if (isUnconditionalReturn(thenBranch)) {
                        found = true
                        done = true
                    }
                }
            }
            if (elseBranch != null) {
                val constraint = getVersionCheckConditional(
                    element = node.condition,
                    and = true
                )
                if (constraint?.matches(api) == true) {
                    if (isUnconditionalReturn(elseBranch)) {
                        found = true
                        done = true
                    }
                }
            }
            return true
        }

        fun found(): Boolean {
            return found
        }
    }
}

fun PsiAnnotation.getAnnotationIntValue(
    attribute: String,
    defaultValue: Int = -1
): Int {
    val psiValue = findAttributeValue(attribute) ?: return defaultValue
    val value = ConstantEvaluator.evaluate(null, psiValue)
    if (value is Number) {
        return value.toInt()
    }

    return defaultValue
}

fun PsiAnnotation.getAnnotationStringValue(
    attribute: String,
    defaultValue: String = ""
): String {
    val psiValue = findAttributeValue(attribute) ?: return defaultValue
    return ConstantEvaluator.evaluateString(null, psiValue, false)
        ?: defaultValue
}
