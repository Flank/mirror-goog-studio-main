package com.android.tools.lint.checks

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.skipParentheses
import com.android.utils.SdkUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiAnnotation
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
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
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
 * Helper for checking whether a given element is surrounded (or preceded!) by an API check
 * using SDK_INT (or other version checking utilities such as BuildCompat#isAtLeastN)
 */
class VersionChecks(private val evaluator: JavaEvaluator) {
    companion object {
        const val SDK_INT = "SDK_INT"
        const val CHECKS_SDK_INT_AT_LEAST_ANNOTATION = "androidx.annotation.ChecksSdkIntAtLeast"

        /** SDK int method used by the data binding compiler */
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
        fun isWithinVersionCheckConditional(
            evaluator: JavaEvaluator,
            element: UElement,
            api: Int,
            isLowerBound: Boolean
        ): Boolean {
            return VersionChecks(evaluator).isWithinVersionCheckConditional(
                evaluator = evaluator,
                element = element,
                api = api,
                isLowerBound = isLowerBound,
                apiLookup = null
            )
        }

        @JvmStatic
        fun isPrecededByVersionCheckExit(
            evaluator: JavaEvaluator,
            element: UElement,
            api: Int,
            isLowerBound: Boolean
        ): Boolean {
            val check = VersionChecks(evaluator)
            var prev = element
            var current: UExpression? = prev.getParentOfType(
                UExpression::class.java,
                true,
                UMethod::class.java,
                UClass::class.java
            )
            while (current != null) {
                val visitor = check.VersionCheckWithExitFinder(prev, api, isLowerBound)
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

        // TODO: Merge with the other isVersionCheckConditional
        @JvmStatic
        fun isVersionCheckConditional(api: Int, binary: UBinaryExpression): Boolean? {
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
                            val codeName = right.resolvedName ?: return false
                            level = SdkVersionInfo.getApiByBuildCode(codeName, true)
                        } else if (right is ULiteralExpression) {
                            val value = right.value
                            if (value is Int) {
                                level = value
                            }
                        }
                        if (level != -1) {
                            if (tokenType === UastBinaryOperator.GREATER_OR_EQUALS && level < api) {
                                // SDK_INT >= ICE_CREAM_SANDWICH
                                return true
                            } else if (tokenType === UastBinaryOperator.GREATER && level <= api - 1) {
                                // SDK_INT > ICE_CREAM_SANDWICH
                                return true
                            } else if (tokenType === UastBinaryOperator.LESS_OR_EQUALS && level < api) {
                                return false
                            } else if (tokenType === UastBinaryOperator.LESS && level <= api) {
                                // SDK_INT < ICE_CREAM_SANDWICH
                                return false
                            } else if ((
                                tokenType === UastBinaryOperator.EQUALS ||
                                    tokenType === UastBinaryOperator.IDENTITY_EQUALS
                                ) &&
                                level < api
                            ) {
                                return false
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

    private fun isWithinVersionCheckConditional(
        evaluator: JavaEvaluator,
        element: UElement,
        api: Int,
        isLowerBound: Boolean,
        apiLookup: ApiLevelLookup?
    ): Boolean {
        var current = skipParentheses(element.uastParent)
        var prev = element
        while (current != null) {
            if (current is UIfExpression) {
                val ifStatement = current
                val condition = ifStatement.condition
                if (prev !== condition) {
                    val fromThen = prev == ifStatement.thenExpression
                    val ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = condition,
                        and = fromThen,
                        prev = prev,
                        apiLookup = apiLookup
                    )
                    if (ok != null && ok) {
                        return true
                    }
                }
            } else if (current is UPolyadicExpression &&
                (
                    isAndedWithConditional(current, api, isLowerBound, prev) ||
                        isOredWithConditional(current, api, isLowerBound, prev)
                    )
            ) {
                return true
            } else if (current is USwitchClauseExpressionWithBody) {
                for (condition in current.caseValues) {
                    val ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = condition,
                        and = true,
                        prev = prev,
                        apiLookup = apiLookup
                    )
                    if (ok != null && ok) {
                        return true
                    }
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
                    val validFromAnnotation = isValidFromAnnotation(api, method, call)
                    if (validFromAnnotation != null) {
                        return validFromAnnotation
                    }

                    val mapping =
                        evaluator.computeArgumentMapping(call, method)
                    val parameter = mapping[prev]
                    if (parameter != null) {
                        val uMethod = UastFacade.convertElementWithParent(
                            method,
                            UMethod::class.java
                        ) as UMethod? ?: return false
                        val match = Ref<UCallExpression>()
                        val parameterName = parameter.name
                        uMethod.accept(
                            object : AbstractUastVisitor() {
                                override fun visitCallExpression(
                                    node: UCallExpression
                                ): Boolean {
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
                        val lambdaInvocation = match.get()
                        val newApiLookup: (UElement) -> Int = { reference ->
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
                        if (lambdaInvocation != null && isWithinVersionCheckConditional(
                            evaluator = evaluator,
                            element = lambdaInvocation,
                            api = api,
                            isLowerBound = isLowerBound,
                            apiLookup = newApiLookup
                        )
                        ) {
                            return true
                        }
                    }
                }
            } else if (current is UMethod) {
                if (current.uastParent !is UAnonymousClass) return false
            } else if (current is PsiFile) {
                return false
            }
            prev = current
            current = skipParentheses(current.uastParent)
        }
        return false
    }

    private fun isVersionCheckConditional(
        api: Int,
        isLowerBound: Boolean,
        element: UElement,
        and: Boolean,
        prev: UElement? = null,
        apiLookup: ApiLevelLookup? = null
    ): Boolean? {
        if (element is UPolyadicExpression) {
            if (element is UBinaryExpression) {
                val ok = isVersionCheckConditional(
                    api = api,
                    isLowerBound = isLowerBound,
                    fromThen = and,
                    binary = element,
                    apiLevelLookup = apiLookup
                )
                if (ok != null) {
                    return ok
                }
            }
            val tokenType = element.operator
            if (and && tokenType === UastBinaryOperator.LOGICAL_AND) {
                if (isAndedWithConditional(element, api, isLowerBound, prev)) {
                    return true
                }
            } else if (!and && tokenType === UastBinaryOperator.LOGICAL_OR) {
                if (isOredWithConditional(element, api, isLowerBound, prev)) {
                    return true
                }
            }
        } else if (element is UCallExpression) {
            return isValidVersionCall(api, isLowerBound, and, element)
        } else if (element is UReferenceExpression) {
            // Constant expression for an SDK version check?
            val resolved = element.resolve()
            if (resolved is PsiField) {
                @Suppress("UnnecessaryVariable")
                val field = resolved

                val validFromAnnotation = isValidFromAnnotation(api, field)
                if (validFromAnnotation != null) {
                    return validFromAnnotation
                }

                val modifierList = field.modifierList
                if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                    val initializer = UastFacade.getInitializerBody(field)
                    if (initializer != null) {
                        val ok = isVersionCheckConditional(
                            api = api,
                            isLowerBound = isLowerBound,
                            element = initializer,
                            and = and
                        )
                        if (ok != null) {
                            return ok
                        }
                    }
                }
            } else if (resolved is PsiMethod &&
                element is UQualifiedReferenceExpression &&
                element.selector is UCallExpression
            ) {
                val call = element.selector as UCallExpression
                return isValidVersionCall(api, isLowerBound, and, call)
            } else if (resolved is PsiMethod) {
                // Method call via Kotlin property syntax
                return isValidVersionCall(
                    api = api,
                    isLowerBound = isLowerBound,
                    and = and,
                    call = element,
                    method = resolved
                )
            } else if (resolved == null && element is UQualifiedReferenceExpression) {
                val selector = element.selector
                if (selector is UCallExpression) {
                    return isValidVersionCall(
                        api = api,
                        isLowerBound = isLowerBound,
                        and = and,
                        call = selector
                    )
                }
            }
        } else if (element is UUnaryExpression) {
            if (element.operator === UastPrefixOperator.LOGICAL_NOT) {
                val operand = element.operand
                val ok = isVersionCheckConditional(
                    api = api,
                    isLowerBound = isLowerBound,
                    element = operand,
                    and = !and
                )
                if (ok != null) {
                    return ok
                }
            }
        }
        return null
    }

    private fun isValidFromAnnotation(
        api: Int,
        owner: PsiModifierListOwner,
        call: UCallExpression? = null
    ): Boolean? {
        val annotation = AnnotationUtil.findAnnotation(
            owner, true, CHECKS_SDK_INT_AT_LEAST_ANNOTATION
        ) ?: return null
        val value = getApiLevel(annotation, owner, call)
        return if (value != -1) api <= value else null
    }

    private fun getApiLevel(
        annotation: PsiAnnotation,
        owner: PsiModifierListOwner,
        call: UCallExpression?
    ): Int {
        val api = annotation.getAnnotationIntValue("api")
        if (api != -1) {
            return api
        }
        val codename = annotation.getAnnotationStringValue("codename")
        if (codename.isNotEmpty()) { // empty string is the default
            return SdkVersionInfo.getApiByPreviewName(codename, true)
        }

        val parameterIndex = annotation.getAnnotationIntValue("parameter")
        if (owner is PsiMethod && call != null) {
            val parameters = owner.parameterList.parameters
            if (parameterIndex >= 0 && parameterIndex < parameters.size) {
                val target = parameters[parameterIndex]
                val mapping = evaluator.computeArgumentMapping(call, owner)
                for ((key, value1) in mapping) {
                    if (value1 === target || value1.isEquivalentTo(target)) {
                        val v = ConstantEvaluator.evaluate(null, key)
                        return (v as? Number)?.toInt() ?: -1
                    }
                }
            }
        }

        return -1
    }

    private fun isValidVersionCall(
        api: Int,
        isLowerBound: Boolean,
        and: Boolean,
        call: UCallExpression
    ): Boolean? {
        val method = call.resolve()
        if (method == null) {
            // Fallback when we can't resolve call: Try to guess just based on the method name
            val identifier = call.methodIdentifier
            if (identifier != null) {
                val name = identifier.name
                val version = getMinSdkVersionFromMethodName(name)
                if (version != -1 && isLowerBound) {
                    return api <= version
                }
            }
            return null
        }
        return isValidVersionCall(api, isLowerBound, and, call, method)
    }

    private fun isValidVersionCall(
        api: Int,
        isLowerBound: Boolean,
        and: Boolean,
        call: UElement,
        method: PsiMethod
    ): Boolean? {
        val validFromAnnotation = isValidFromAnnotation(api, method, call as? UCallExpression)
        if (validFromAnnotation != null) {
            return validFromAnnotation
        }

        val name = method.name
        if (name.startsWith("isAtLeast") && isLowerBound) {
            val containingClass = method.containingClass
            if (containingClass != null &&
                // android.support.v4.os.BuildCompat,
                // androidx.core.os.BuildCompat
                "BuildCompat" == containingClass.name
            ) {
                when {
                    name == "isAtLeastN" -> return api <= 24
                    name == "isAtLeastNMR1" -> return api <= 25
                    name == "isAtLeastO" -> return api <= 26
                    name.startsWith("isAtLeastP") -> return api <= 28
                    name.startsWith("isAtLeastQ") -> return api <= 29
                    // Try to guess future API levels before they're announced
                    name.startsWith("isAtLeast") &&
                        name.length == 10 && Character.isUpperCase(name[9])
                        && name[9] > 'Q' ->
                        return api <= SdkVersionInfo.HIGHEST_KNOWN_API + 1
                }
            }
        }
        val version = getMinSdkVersionFromMethodName(name)
        if (version != -1 && isLowerBound) {
            return api <= version
        }

        // Unconditional version utility method? If so just attempt to call it
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            val body = UastFacade.getMethodBody(method) ?: return null
            val expressions: List<UExpression>
            expressions = if (body is UBlockExpression) {
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
                            val isConditional =
                                isVersionCheckConditional(
                                    api = api,
                                    isLowerBound = isLowerBound,
                                    element = returnValue,
                                    and = and
                                )
                            if (isConditional != null) {
                                return isConditional
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
                        val ok = isVersionCheckConditional(
                            api = api,
                            isLowerBound = isLowerBound,
                            element = returnValue,
                            and = and,
                            apiLookup = lookup
                        )
                        if (ok != null) {
                            return ok
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

    private fun isVersionCheckConditional(
        api: Int,
        isLowerBound: Boolean,
        fromThen: Boolean,
        binary: UBinaryExpression,
        apiLevelLookup: ApiLevelLookup? = null
    ): Boolean? {
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
                    return if (isLowerBound) {
                        // if (SDK_INT >= ICE_CREAM_SANDWICH) { <call> } else { ... }
                        level >= api && fromThen
                    } else {
                        // if (SDK_INT >= ICE_CREAM_SANDWICH) { ... } else { <call> }
                        level - 1 <= api && !fromThen
                    }
                } else if (tokenType === UastBinaryOperator.GREATER) {
                    return if (isLowerBound) {
                        // if (SDK_INT > ICE_CREAM_SANDWICH) { <call> } else { ... }
                        level >= api - 1 && fromThen
                    } else {
                        // if (SDK_INT > ICE_CREAM_SANDWICH) { ... } else { <call> }
                        level <= api && !fromThen
                    }
                } else if (tokenType === UastBinaryOperator.LESS_OR_EQUALS) {
                    return if (isLowerBound) {
                        // if (SDK_INT <= ICE_CREAM_SANDWICH) { ... } else { <call> }
                        level >= api - 1 && !fromThen
                    } else {
                        // if (SDK_INT <= ICE_CREAM_SANDWICH) { <call> } else { ... }
                        level <= api && fromThen
                    }
                } else if (tokenType === UastBinaryOperator.LESS) {
                    return if (isLowerBound) {
                        // if (SDK_INT < ICE_CREAM_SANDWICH) { ... } else { <call> }
                        level >= api && !fromThen
                    } else {
                        // if (SDK_INT < ICE_CREAM_SANDWICH) { <call> } else { ... }
                        level - 1 <= api && fromThen
                    }
                } else if (tokenType === UastBinaryOperator.EQUALS ||
                    tokenType === UastBinaryOperator.IDENTITY_EQUALS
                ) {
                    // if (SDK_INT == ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return if (isLowerBound) {
                        level >= api && fromThen
                    } else {
                        level <= api && fromThen
                    }
                } else if (tokenType === UastBinaryOperator.NOT_EQUALS ||
                    tokenType === UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    // if (SDK_INT != ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return level == api && !fromThen
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

    private fun isOredWithConditional(
        element: UElement,
        api: Int,
        isLowerBound: Boolean,
        before: UElement?
    ): Boolean {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                val left = element.leftOperand
                if (before !== left) {
                    var ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = left,
                        and = false
                    )
                    if (ok != null) {
                        return ok
                    }
                    val right = element.rightOperand
                    ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = right,
                        and = false
                    )
                    if (ok != null) {
                        return ok
                    }
                }
            }
            val value =
                isVersionCheckConditional(
                    api = api,
                    isLowerBound = isLowerBound,
                    fromThen = false,
                    binary = element
                )
            return value != null && value
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else if (isOredWithConditional(
                        element = operand,
                        api = api,
                        isLowerBound = isLowerBound,
                        before = before
                    )
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isAndedWithConditional(
        element: UElement,
        api: Int,
        isLowerBound: Boolean,
        before: UElement?
    ): Boolean {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                val left = element.leftOperand
                if (before !== left) {
                    var ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = left,
                        and = true
                    )
                    if (ok != null) {
                        return ok
                    }
                    val right = element.rightOperand
                    ok = isVersionCheckConditional(
                        api = api,
                        isLowerBound = isLowerBound,
                        element = right,
                        and = true
                    )
                    if (ok != null) {
                        return ok
                    }
                }
            }
            val value = isVersionCheckConditional(
                api = api,
                isLowerBound = isLowerBound,
                fromThen = true,
                binary = element
            )
            return value != null && value
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else if (isAndedWithConditional(
                        operand,
                        api,
                        isLowerBound,
                        before
                    )
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private inner class VersionCheckWithExitFinder(
        private val endElement: UElement,
        private val api: Int,
        private val isLowerBound: Boolean
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
                val level = isVersionCheckConditional(
                    api = api,
                    isLowerBound = isLowerBound,
                    element = node.condition,
                    and = false
                )
                if (level != null && level) {
                    // See if the body does an immediate return
                    if (isUnconditionalReturn(thenBranch)) {
                        found = true
                        done = true
                    }
                }
            }
            if (elseBranch != null) {
                val level = isVersionCheckConditional(
                    api = api,
                    isLowerBound = isLowerBound,
                    element = node.condition,
                    and = true
                )
                if (level != null && level) {
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

private fun PsiAnnotation.getAnnotationIntValue(
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

private fun PsiAnnotation.getAnnotationStringValue(
    attribute: String,
    defaultValue: String = ""
): String {
    val psiValue = findAttributeValue(attribute) ?: return defaultValue
    return ConstantEvaluator.evaluateString(null, psiValue, false)
        ?: defaultValue
}
