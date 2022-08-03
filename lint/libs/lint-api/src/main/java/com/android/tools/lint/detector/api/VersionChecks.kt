/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.sdklib.SdkVersionInfo
import com.android.support.AndroidxName
import com.android.tools.lint.client.api.AndroidPlatformAnnotations
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint.Companion.above
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atMost
import com.android.tools.lint.detector.api.ApiConstraint.Companion.below
import com.android.tools.lint.detector.api.ApiConstraint.Companion.exactly
import com.android.tools.lint.detector.api.ApiConstraint.Companion.not
import com.android.tools.lint.detector.api.ApiConstraint.Companion.range
import com.android.tools.lint.detector.api.VersionChecks.SdkIntAnnotation.Companion.findSdkIntAnnotation
import com.android.utils.SdkUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.skipParenthesizedExprDown
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
    private val project: Project?
) {
    companion object {
        /**
         * The `SdkIntDetector` analyzes methods and looks for SDK_INT
         * checks inside method bodies. If it recognizes that something
         * is a version check, it will record this as partial analysis
         * data. This mechanism needs an associated issue to tie the
         * data to. We want to peek at this data from the version
         * checking utility (such that if we see "if (someFunction())"
         * is actually checking an SDK_INT result), but we cannot
         * reference that detector's issue from here since it's in a
         * downstream dependency, lint-checks rather than lint-api.
         * So instead, we've created a special marker issue here (the
         * "_" prefix in the issue id is a special prefix recognized
         * by lint as meaning it's not a real issue, also used by
         * various tests), and the sdk int detector will store its
         * data using this issue id instead of its reporting issue.
         */
        @JvmField
        val SDK_INT_VERSION_DATA = Issue.create(
            id = "_ChecksSdkIntAtLeast",
            briefDescription = "Version Storage",
            explanation = "_",
            category = Category.LINT,
            severity = Severity.INFORMATIONAL,
            androidSpecific = true,
            // Not a real implementation
            implementation = Implementation(Detector::class.java, Scope.EMPTY)
        )

        const val SDK_INT = "SDK_INT"
        const val CHECKS_SDK_INT_AT_LEAST_ANNOTATION = "androidx.annotation.ChecksSdkIntAtLeast"
        private const val SDK_SUPPRESS_ANNOTATION = "android.support.test.filters.SdkSuppress"
        private const val ANDROIDX_SDK_SUPPRESS_ANNOTATION = "androidx.test.filters.SdkSuppress"
        private const val ROBO_ELECTRIC_CONFIG_ANNOTATION = "org.robolectric.annotation.Config"

        @JvmField
        val REQUIRES_API_ANNOTATION = AndroidxName.of(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX, "RequiresApi")

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

        @JvmOverloads
        @JvmStatic
        fun getTargetApiAnnotation(
            evaluator: JavaEvaluator,
            scope: UElement?,
            isApiLevelAnnotation: (String) -> Boolean = Companion::isTargetAnnotation
        ): Pair<UAnnotation?, Int> {
            var current = scope
            while (current != null) {
                if (current is UAnnotated) {
                    //noinspection AndroidLintExternalAnnotations
                    for (annotation in current.uAnnotations) {
                        val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
                        if (target != -1) {
                            return annotation to target
                        }
                    }
                }
                if (current is UFile) {
                    // Also consult any package annotations
                    val pkg = evaluator.getPackage(current.javaPsi ?: current.sourcePsi)
                    if (pkg != null) {
                        for (psiAnnotation in pkg.annotations) {
                            val annotation = UastFacade.convertElement(psiAnnotation, null) as? UAnnotation ?: continue
                            val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
                            if (target != -1) {
                                return annotation to target
                            }
                        }
                    }

                    break
                }
                current = current.uastParent
            }

            return NO_ANNOTATION_FOUND
        }

        /**
         * Return value for no annotation found from
         * [getTargetApiAnnotation]
         */
        private val NO_ANNOTATION_FOUND: Pair<UAnnotation?, Int> = null to -1

        fun isTargetAnnotation(fqcn: String): Boolean {
            return fqcn == SdkConstants.FQCN_TARGET_API ||
                isRequiresApiAnnotation(fqcn) ||
                fqcn == SDK_SUPPRESS_ANNOTATION ||
                fqcn == ANDROIDX_SDK_SUPPRESS_ANNOTATION ||
                fqcn == ROBO_ELECTRIC_CONFIG_ANNOTATION ||
                fqcn == SdkConstants.TARGET_API || // with missing imports
                fqcn.startsWith(AndroidPlatformAnnotations.PLATFORM_ANNOTATIONS_PREFIX) &&
                isTargetAnnotation(AndroidPlatformAnnotations.toAndroidxAnnotation(fqcn))
        }

        fun isRequiresApiAnnotation(fqcn: String): Boolean {
            return REQUIRES_API_ANNOTATION.isEquals(fqcn) ||
                fqcn == "RequiresApi" || // With missing imports
                fqcn.startsWith(AndroidPlatformAnnotations.PLATFORM_ANNOTATIONS_PREFIX) &&
                isRequiresApiAnnotation(AndroidPlatformAnnotations.toAndroidxAnnotation(fqcn))
        }

        fun getTargetApiForAnnotation(annotation: UAnnotation, isApiLevelAnnotation: (String) -> Boolean): Int {
            val fqcn = annotation.qualifiedName
            if (fqcn != null && isApiLevelAnnotation(fqcn)) {
                val attributeList = annotation.attributeValues
                for (attribute in attributeList) {
                    if (fqcn == ROBO_ELECTRIC_CONFIG_ANNOTATION ||
                        fqcn == SDK_SUPPRESS_ANNOTATION ||
                        fqcn == ANDROIDX_SDK_SUPPRESS_ANNOTATION
                    ) {
                        val name = attribute.name
                        if (name == null || !(name.startsWith("minSdk") || name == "codeName")) {
                            continue
                        }
                    }
                    val expression = attribute.expression
                    if (expression is ULiteralExpression) {
                        val value = expression.value
                        if (value is Int) {
                            return value
                        } else if (value is String) {
                            return codeNameToApi(value)
                        }
                    } else {
                        val apiLevel = ConstantEvaluator.evaluate(null, expression) as? Int
                        if (apiLevel != null) {
                            return apiLevel
                        } else if (expression is UReferenceExpression) {
                            val name = expression.resolvedName
                            if (name != null) {
                                return codeNameToApi(name)
                            }
                        } else {
                            return codeNameToApi(expression.asSourceString())
                        }
                    }
                }
            } else if (fqcn == null) {
                // Work around bugs in UAST type resolution for file annotations:
                // parse the source string instead.
                val psi = annotation.sourcePsi ?: return -1
                if (psi is PsiCompiledElement) {
                    return -1
                }
                val text = psi.text
                val start = text.indexOf('(')
                if (start == -1) {
                    return -1
                }
                val colon = text.indexOf(':') // skip over @file: etc
                val annotationString = text.substring(if (colon < start) colon + 1 else 0, start)
                if (isApiLevelAnnotation(annotationString)) {
                    val end = text.indexOf(')', start + 1)
                    if (end != -1) {
                        var name = text.substring(start + 1, end)
                        // Strip off attribute name and qualifiers, e.g.
                        //   @RequiresApi(api = Build.VERSION.O) -> O
                        var index = name.indexOf('=')
                        if (index != -1) {
                            name = name.substring(index + 1).trim()
                        }
                        index = name.indexOf('.')
                        if (index != -1) {
                            name = name.substring(index + 1)
                        }
                        if (name.isNotEmpty()) {
                            if (name[0].isDigit()) {
                                val api = Integer.parseInt(name)
                                if (api > 0) {
                                    return api
                                }
                            } else {
                                return codeNameToApi(name)
                            }
                        }
                    }
                }
            }

            return -1
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
            val check = VersionChecks(client, evaluator, project)
            val constraint = check.getWithinVersionCheckConditional(element = element, apiLookup = null) ?: return false
            return if (lowerBound) {
                constraint.notLowerThan(api)
            } else {
                constraint.not().notLowerThan(api)
            }
        }

        @JvmStatic
        fun getOuterVersionCheckConstraint(
            context: JavaContext,
            element: UElement,
        ): ApiConstraint? {
            val client = context.client
            val evaluator = context.evaluator
            val project = context.project
            val check = VersionChecks(client, evaluator, project)
            return check.getWithinVersionCheckConditional(element = element, apiLookup = null)
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
            val check = VersionChecks(client, evaluator, null)
            val constraint = check.getWithinVersionCheckConditional(element = element, apiLookup = null) ?: return false
            return if (lowerBound) {
                constraint.notLowerThan(api)
            } else {
                constraint.not().notLowerThan(api)
            }
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

        private fun isSdkInt(element: UElement?): Boolean {
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
            } else if (element is UParenthesizedExpression) {
                return isSdkInt(element.expression)
            }
            return false
        }

        /**
         * Returns the actual API constraint enforced by the given
         * SDK_INT comparison.
         */
        @JvmStatic
        fun getVersionCheckConditional(
            binary: UBinaryExpression,
            apiLevelLookup: ApiLevelLookup? = null
        ): ApiConstraint? {
            var tokenType = binary.operator
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
                        tokenType = tokenType.flip() ?: tokenType
                        level = getApiLevel(left, apiLevelLookup)
                    } else {
                        return null
                    }
                } else {
                    right = binary.rightOperand
                    level = getApiLevel(right, apiLevelLookup)
                }

                if (level != -1) {
                    when (tokenType) {
                        UastBinaryOperator.GREATER_OR_EQUALS -> return atLeast(level)
                        UastBinaryOperator.GREATER -> return above(level)
                        UastBinaryOperator.LESS_OR_EQUALS -> return atMost(level)
                        UastBinaryOperator.LESS -> return below(level)
                        UastBinaryOperator.EQUALS, UastBinaryOperator.IDENTITY_EQUALS -> return exactly(level)
                        UastBinaryOperator.NOT_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS -> return not(level)
                        else -> assert(false) { tokenType }
                    }
                }
            } else if (tokenType.text == "in") {
                val left = binary.leftOperand
                if (!isSdkInt(left)) {
                    return null
                }
                val right: UExpression = binary.rightOperand.skipParenthesizedExprDown()
                if (right is UBinaryExpression) {
                    return getSdkIntConstraintFromExpression(right, apiLevelLookup)
                }
            }

            return null
        }

        private fun getSdkIntConstraintFromExpression(
            expression: UBinaryExpression,
            apiLevelLookup: ApiLevelLookup? = null
        ): ApiConstraint? {
            if (expression.operands.size == 2) {
                // We assume there's no step, downTo etc -- there's no good reason to do that with API level range checks.
                val from = getApiLevel(expression.operands[0], apiLevelLookup)
                var to = getApiLevel(expression.operands[1], apiLevelLookup)
                if (expression.operator.text == "..") {
                    // If it's a range like "1..10", then the end point is included,
                    // whereas if it's "1 until 10", it's not.
                    to++
                }
                return range(from, to)
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
            } else if (element is UParenthesizedExpression) {
                return getApiLevel(element.expression, apiLevelLookup)
            }
            if (level == -1 && apiLevelLookup != null && element != null) {
                level = apiLevelLookup(element)
            }
            return level
        }

        // From "X op Y" to "Y op X" -- e.g. "a > b" = "b < a" and "a >= b" = "b <= a"
        private fun UastBinaryOperator.flip(): UastBinaryOperator? {
            return when (this) {
                UastBinaryOperator.GREATER -> UastBinaryOperator.LESS
                UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS_OR_EQUALS
                UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER_OR_EQUALS
                UastBinaryOperator.LESS -> UastBinaryOperator.GREATER
                else -> null
            }
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
        @Suppress("UnstableApiUsage") // UYieldExpression not yet stable
        if (statement is UBlockExpression) {
            statement.expressions.lastOrNull()?.let { return isUnconditionalReturn(it) }
        } else if (statement is UExpressionList) {
            statement.expressions.lastOrNull()?.let { return isUnconditionalReturn(it) }
        } else if (statement is UYieldExpression) {
            // (Kotlin when statements will sometimes be represented using yields in the UAST representation)
            val yieldExpression = statement.expression
            if (yieldExpression != null) {
                return isUnconditionalReturn(yieldExpression)
            }
        } else if (statement is UParenthesizedExpression) {
            return isUnconditionalReturn(statement.expression)
        } else if (statement is UIfExpression) {
            val thenExpression = statement.thenExpression
            val elseExpression = statement.elseExpression
            if (thenExpression != null && elseExpression != null) {
                return isUnconditionalReturn(thenExpression) && isUnconditionalReturn(elseExpression)
            }
            return false
        } else if (statement is USwitchExpression) {
            for (case in statement.body.expressions) {
                if (case is USwitchClauseExpressionWithBody) {
                    if (!isUnconditionalReturn(case.body)) {
                        return false
                    }
                }
            }
            return true
        }

        if (statement is UReturnExpression || statement is UThrowExpression) {
            return true
        } else if (statement is UCallExpression) {
            val methodName = getMethodName(statement)
            // Look for Kotlin runtime library methods that unconditionally exit
            if ("error" == methodName || "TODO" == methodName) {
                return true
            }
        }

        return false
    }

    private fun getWithinVersionCheckConditional(
        element: UElement,
        apiLookup: ApiLevelLookup?
    ): ApiConstraint? {
        var current = element.uastParent
        var prev = element
        var constraint: ApiConstraint? = null
        while (current != null) {
            if (current is UMethod) {
                if (current.uastParent !is UAnonymousClass) break
            } else if (current is UFile) {
                break
            } else {
                val currentConstraint = getVersionConditional(current, prev, apiLookup)
                if (currentConstraint != null) {
                    if (constraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                        constraint = constraint and currentConstraint
                    } else {
                        constraint = currentConstraint
                    }
                    // For cases inside switch clauses we'll reach up and scan the when cases
                    // (to see if there are previous cases which implies SDK_INT constraints for
                    // the target case) and in this case we don't want to repeat the analysis on
                    // the clause.
                    if (current.uastParent is USwitchClauseExpression) {
                        prev = current
                        current = current.uastParent ?: break
                    }
                }
            }
            prev = current
            current = current.uastParent
        }
        return constraint
    }

    /**
     * Looks up the version conditional for a specific [current]
     * element, coming up from [prev].
     */
    private fun getVersionConditional(
        current: UElement,
        prev: UElement?,
        apiLookup: ApiLevelLookup?
    ): ApiConstraint? {
        if (current is UPolyadicExpression) {
            val anded = getAndedWithConstraint(current, prev)
            if (anded != null) {
                return anded
            }
            val ored = getOredWithConstraint(current, prev)
            if (ored != null) {
                return ored
            }
        }

        if (current is UIfExpression) {
            val ifStatement = current
            val condition = ifStatement.condition
            if (prev !== condition) {
                val fromThen = prev == ifStatement.thenExpression
                getVersionCheckConstraint(
                    element = condition,
                    prev = prev,
                    apiLookup = apiLookup
                )?.let { return if (fromThen) it else it.not() }
            }
        } else if (current is USwitchClauseExpressionWithBody) {
            for (condition in current.caseValues) {
                getVersionCheckConstraint(
                    element = condition,
                    prev = prev,
                    apiLookup = apiLookup
                )?.let { return it }
            }

            val switch = current.getParentOfType(USwitchExpression::class.java, true)
            val entries = switch?.body?.expressions?.filterIsInstance<USwitchClauseExpression>() ?: emptyList()
            val switchExpression = switch?.expression
            val casesAreApiLevels = switchExpression != null && isSdkInt(switchExpression)
            var currentConstraint: ApiConstraint? = null
            for (entry in entries) {
                val caseConstraint = getCaseConstraint(entry, casesAreApiLevels)

                if (entry === current) {
                    val constraint = if (caseConstraint != null) {
                        if (currentConstraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                            caseConstraint and currentConstraint
                        } else {
                            caseConstraint
                        }
                    } else if (currentConstraint != null) {
                        currentConstraint
                    } else {
                        break
                    }
                    return constraint
                }

                // Invert the constraint and combine it with the current constraint; this is now the currentConstraint for the next case.
                if (caseConstraint != null) {
                    val reverse = caseConstraint.not()
                    if (currentConstraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                        currentConstraint = reverse and currentConstraint
                    } else {
                        currentConstraint = reverse
                    }
                }
            }
        } else if (current is UCallExpression &&
            (prev as? UExpression)?.skipParenthesizedExprDown() is ULambdaExpression
        ) {
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
                        return atLeast(value)
                    } // else: lambda
                }

                val mapping = evaluator.computeArgumentMapping(call, method)
                val parameter = mapping[prev]
                if (parameter != null) {
                    val lambdaInvocation = getLambdaInvocation(parameter, method)
                    if (lambdaInvocation != null) {
                        val constraint = getWithinVersionCheckConditional(
                            element = lambdaInvocation,
                            apiLookup = getReferenceApiLookup(call)
                        )
                        if (constraint != null) {
                            return constraint
                        }
                    }
                }
            }
        } else if (current is UCallExpression &&
            (prev as? UExpression)?.skipParenthesizedExprDown() is UObjectLiteralExpression
        ) {
            val method = current.resolve()
            if (method != null) {
                val annotation = SdkIntAnnotation.get(method)
                if (annotation != null) {
                    val value = annotation.getApiLevel(evaluator, method, current)
                    if (value != null) {
                        return atLeast(value)
                    } // else: lambda
                }

                val mapping = evaluator.computeArgumentMapping(current, method)
                val parameter = mapping[prev]
                if (parameter != null) {
                    val lambdaInvocation = getLambdaInvocation(parameter, method)
                    if (lambdaInvocation != null) {
                        val constraint = getWithinVersionCheckConditional(
                            element = lambdaInvocation,
                            apiLookup = getReferenceApiLookup(current)
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

    /**
     * Given a [USwitchClauseExpression] (a case in a when statement),
     * returns the SDK_INT constraint implied by this case, if any. If
     * [casesAreApiLevels], this means the when-statement is switching
     * on SDK_INT as the subject, so the cases will just be integers or
     * ranges understood to refer to SDK_INT. Otherwise, this must be a
     * subject-less when-statement where each case should include the
     * SDK_INT comparison (or version checking utility calls).
     */
    private fun getCaseConstraint(
        entry: USwitchClauseExpression,
        casesAreApiLevels: Boolean,
        apiLevelLookup: ApiLevelLookup? = null
    ): ApiConstraint? {
        val caseValues = entry.caseValues
        if (caseValues.isEmpty()) {
            // It's the else clause: no constraint here.
            return null
        }

        if (!casesAreApiLevels) {
            // When the when-statement has no subject, there's exactly one case (we've ruled out the else clause above).
            val case = caseValues.first().skipParenthesizedExprDown()
            return getVersionCheckConstraint(element = case)
        }

        // When the SDK_INT is the subject of the when, we can have multiple cases, and combinations of specific
        // integers and range-expressions; iterate through each and combine into a single constraint.
        var constraint: ApiConstraint? = null
        for (case in caseValues) {
            val expression = case.skipParenthesizedExprDown()
            val caseConstraint =
                // We have a when (SDK_INT) so we already know it's an SDK_INT version check, here the case
                // is just the range of API levels
                if (expression is UBinaryExpression && expression.operator.text == "in") {
                    val range = expression.operands.lastOrNull()?.skipParenthesizedExprDown() as? UBinaryExpression
                    if (range != null) {
                        getSdkIntConstraintFromExpression(range)
                    } else {
                        null
                    }
                } else {
                    val level = getApiLevel(expression, apiLevelLookup)
                    if (level != -1) {
                        exactly(level)
                    } else {
                        null
                    }
                }

            caseConstraint ?: continue
            if (constraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                constraint = caseConstraint or constraint
            } else {
                constraint = caseConstraint
            }
        }

        return constraint
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
                        call.resolve()?.let { method ->
                            val mapping = evaluator.computeArgumentMapping(call, method)
                            for ((argument, parameter) in mapping) {
                                if (parameter == resolved) {
                                    apiLevel = getApiLevel(argument, null)
                                    break
                                }
                            }
                        } ?: run {
                            val index = parameterList.getParameterIndex(resolved)
                            val arguments = call.valueArguments
                            if (index != -1 && index < arguments.size) {
                                apiLevel = getApiLevel(arguments[index], null)
                            }
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
                    val receiver = node.receiver?.skipParenthesizedExprDown()
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

    private fun getVersionCheckConstraint(
        element: UElement,
        prev: UElement? = null,
        apiLookup: ApiLevelLookup? = null
    ): ApiConstraint? {
        if (element is UPolyadicExpression) {
            if (element is UBinaryExpression) {
                getVersionCheckConditional(
                    binary = element,
                    apiLevelLookup = apiLookup
                )?.let { return it }
            }
            val tokenType = element.operator
            if (tokenType === UastBinaryOperator.LOGICAL_AND) {
                val constraint = getAndedWithConstraint(element, prev)
                if (constraint != null) {
                    return constraint
                }
            } else if (tokenType === UastBinaryOperator.LOGICAL_OR) {
                val constraint = getOredWithConstraint(element, prev)
                if (constraint != null) {
                    return constraint.not()
                }
            }
        } else if (element is UCallExpression) {
            return getValidVersionCall(element)
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
                    val initializer = UastFacade.getInitializerBody(field)?.skipParenthesizedExprDown()
                    if (initializer != null) {
                        val constraint = getVersionCheckConstraint(element = initializer)
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
                return getValidVersionCall(call)
            } else if (resolved is PsiMethod) {
                // Method call via Kotlin property syntax
                return getValidVersionCall(
                    call = element,
                    method = resolved
                )
            } else if (resolved == null && element is UQualifiedReferenceExpression) {
                val selector = element.selector
                if (selector is UCallExpression) {
                    return getValidVersionCall(
                        call = selector
                    )
                }
            }
        } else if (element is UUnaryExpression) {
            if (element.operator === UastPrefixOperator.LOGICAL_NOT) {
                val operand = element.operand
                getVersionCheckConstraint(element = operand)?.let { return it.not() }
            }
        } else if (element is UParenthesizedExpression) {
            return getVersionCheckConstraint(element.expression, element, apiLookup)
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
        return atLeast(value)
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
            is PsiMethod -> findSdkIntAnnotation(client, evaluator, project, owner) ?: return null
            is PsiField -> findSdkIntAnnotation(client, evaluator, project, owner) ?: return null
            else -> return null
        }
        val value = annotation.getApiLevel(evaluator, owner, call) ?: return null
        return atLeast(value)
    }

    private fun getValidVersionCall(call: UCallExpression): ApiConstraint? {
        val method = call.resolve()
        if (method == null) {
            // Fallback when we can't resolve call: Try to guess just based on the method name
            val identifier = call.methodIdentifier
            if (identifier != null) {
                val name = identifier.name
                val version = getMinSdkVersionFromMethodName(name)
                if (version != -1) {
                    return atLeast(version)
                }
            }
            return null
        }
        return getValidVersionCall(call, method)
    }

    private fun getValidVersionCall(
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
                    name == "isAtLeastN" -> return atLeast(24)
                    name == "isAtLeastNMR1" -> return atLeast(25)
                    name == "isAtLeastO" -> return atLeast(26)
                    name.startsWith("isAtLeastP") -> return atLeast(28)
                    name.startsWith("isAtLeastQ") -> return atLeast(29)
                    // Try to guess future API levels before they're announced
                    name.startsWith("isAtLeast") &&
                        name.length == 10 && Character.isUpperCase(name[9]) &&
                        name[9] > 'Q' ->
                        return atLeast(SdkVersionInfo.HIGHEST_KNOWN_API + 1)
                }
            }
        }
        val version = getMinSdkVersionFromMethodName(name)
        if (version != -1) {
            return atLeast(version)
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
                val statement = expressions[0].skipParenthesizedExprDown()
                val returnValue = if (statement is UReturnExpression) {
                    statement.returnExpression?.skipParenthesizedExprDown()
                } else {
                    // Kotlin: may not have an explicit return statement
                    statement
                } ?: return null
                val arguments = if (call is UCallExpression) call.valueArguments else emptyList()
                if (arguments.isEmpty()) {
                    if (returnValue is UPolyadicExpression ||
                        returnValue is UCallExpression ||
                        returnValue is UQualifiedReferenceExpression
                    ) {
                        val constraint = getVersionCheckConstraint(element = returnValue)
                        if (constraint != null) {
                            return constraint
                        }
                    }
                } else if (arguments.size == 1) {
                    // See if we're passing in a value to the version utility method
                    val constraint = getVersionCheckConstraint(
                        element = returnValue,
                        apiLookup = { reference ->
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
                    )
                    if (constraint != null) {
                        return constraint
                    }
                }
            }
        }

        return null
    }

    @Suppress("SpellCheckingInspection")
    private fun getOredWithConstraint(
        element: UElement,
        before: UElement?
    ): ApiConstraint? {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                val left = element.leftOperand
                if (before !== left) {
                    getVersionCheckConstraint(element = left)?.let { return it.not() }
                    val right = element.rightOperand
                    getVersionCheckConstraint(element = right)?.let { return it.not() }
                }
            }
            getVersionCheckConditional(binary = element)?.let { return it.not() }
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_OR) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else {
                        getOredWithConstraint(element = operand, before = before).let { return it }
                    }
                }
            }
        } else if (element is UParenthesizedExpression) {
            return getOredWithConstraint(element.expression, element)
        }
        return null
    }

    @Suppress("SpellCheckingInspection")
    private fun getAndedWithConstraint(
        element: UElement,
        before: UElement?
    ): ApiConstraint? {
        if (element is UBinaryExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                val left = element.leftOperand
                if (before !== left) {
                    getVersionCheckConstraint(element = left)?.let { return it }
                    val right = element.rightOperand
                    getVersionCheckConstraint(element = right)?.let { return it }
                }
            }
            getVersionCheckConditional(binary = element)?.let { return it }
        } else if (element is UPolyadicExpression) {
            if (element.operator === UastBinaryOperator.LOGICAL_AND) {
                for (operand in element.operands) {
                    if (operand == before) {
                        break
                    } else {
                        getVersionCheckConstraint(operand)?.let { return it }
                    }
                }
            }
        } else if (element is UParenthesizedExpression) {
            return getAndedWithConstraint(element.expression, element)
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
            val exit = super.visitIfExpression(node)
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
            val constraint = getVersionCheckConstraint(element = node.condition)
            if (thenBranch != null) {
                if (constraint?.not()?.notLowerThan(api) == true) {
                    // See if the body does an immediate return
                    if (isUnconditionalReturn(thenBranch)) {
                        found = true
                        done = true
                    }
                }
            }
            if (elseBranch != null) {
                if (constraint?.notLowerThan(api) == true) {
                    if (isUnconditionalReturn(elseBranch)) {
                        found = true
                        done = true
                    }
                }
            }
            return exit
        }

        override fun visitSwitchExpression(node: USwitchExpression): Boolean {
            super.visitSwitchExpression(node)
            if (done) {
                return true
            }
            if (endElement.isUastChildOf(node, true)) {
                // Even if there is an unconditional exit, endElement will occur before it!
                done = true
                return true
            }

            // when { } instead of when (subject) { } ? If so, check if any condition is an SDK_INT check.
            val subject = node.expression
            if (subject != null) {
                // We only check for SDK_INT constraints in subject-less when statements. In theory
                // you could do a range-based SDK_INT check (when (SDK_INT) 1...21 -> ... etc) but that
                // would need to be handled differently.
                if (!isSdkInt(subject)) {
                    return done
                }
            }

            // Known constraint about SDK_INT *after* the switch statement. This is the highest possible SDK_INT.
            var fallthroughConstraint: ApiConstraint? = null

            // Known constraint about SDK_INT before each case.
            // For example, after the when-statement
            //   when {
            //      SDK_INT > 30 -> { something(); return; }
            //      SDK_INT > 20 -> { somethingElse(); }
            //      else -> return;
            //   }
            // here the fallthroughConstraint will be SDK_INT <= 30. The elseConstraint will be
            // SDK_INT <= 30 for the second case, and SDK_INT <= 20 for the third (else) case.
            var currentConstraint: ApiConstraint? = null

            for (entry in node.body.expressions) {
                if (entry is USwitchClauseExpression) {
                    val body = (entry as? USwitchClauseExpressionWithBody)?.body
                    val caseConstraint = getCaseConstraint(entry, subject != null)
                    // If the current element falls through, update the fallthroughConstraint: take the worst-case combination
                    // of currentConstraint and caseConstraint with the current
                    if (body != null && fallsThrough(body)) {
                        fallthroughConstraint = if (caseConstraint != null) {
                            if (fallthroughConstraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                                caseConstraint or /*currentConstraint or*/ fallthroughConstraint
                            } else {
                                caseConstraint
                            }
                        } else if (currentConstraint != null) {
                            if (fallthroughConstraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                                currentConstraint or fallthroughConstraint
                            } else {
                                currentConstraint
                            }
                        } else {
                            fallthroughConstraint
                        }
                    }

                    // Invert the constraint and combine it with the current constraint; this is now the currentConstraint for the next case.
                    if (caseConstraint != null) {
                        val reverse = caseConstraint.not()
                        if (currentConstraint != null) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
                            currentConstraint = reverse and currentConstraint
                        } else {
                            currentConstraint = reverse
                        }
                    }

                    // Handle final else clause; if it returns unconditionally, the constraint for after the switch statement
                    // would be the negation of the current constraint.
                    if (entry.caseValues.isEmpty() && body != null && !fallsThrough(body)) {
                        currentConstraint = currentConstraint?.not()
                        break
                    }
                }
            }

            val constraint = fallthroughConstraint ?: currentConstraint ?: return done
            if (constraint.notLowerThan(api)) {
                // We've had earlier clauses which checked the API level.
                // No, this isn't right; we need to lower the level
                found = true
                done = true
            }

            return done
        }

        private fun fallsThrough(body: UExpression): Boolean {
            return !isUnconditionalReturn(body)
        }

        fun found(): Boolean {
            return found
        }
    }

    /**
     * Unpacked version of `@androidx.annotation.ChecksSdkIntAtLeast`
     */
    class SdkIntAnnotation(
        val api: Int?,
        val codename: String?,
        val parameter: Int?,
        val lambda: Int?
    ) {
        constructor(annotation: PsiAnnotation) : this(
            annotation.getAnnotationIntValue("api"),
            annotation.getAnnotationStringValue("codename"),
            annotation.getAnnotationIntValue("parameter"),
            annotation.getAnnotationIntValue("lambda")
        )

        /**
         * Returns the API level for this annotation in the given
         * context.
         */
        fun getApiLevel(
            evaluator: JavaEvaluator,
            owner: PsiModifierListOwner,
            call: UCallExpression?
        ): Int? {
            val apiLevel = apiLevel()
            if (apiLevel != null) {
                return apiLevel
            }

            val index = parameter ?: lambda ?: return null
            if (owner is PsiMethod && call != null) {
                val argument = findArgumentFor(evaluator, owner, index, call)
                if (argument != null) {
                    val v = ConstantEvaluator.evaluate(null, argument)
                    return (v as? Number)?.toInt()
                }
            }

            return null
        }

        private fun apiLevel(): Int? {
            return if (codename != null && codename.isNotEmpty()) {
                val level = SdkVersionInfo.getApiByBuildCode(codename, false)
                if (level != -1) {
                    level
                } else {
                    SdkVersionInfo.getApiByPreviewName(codename, true)
                }
            } else if (api != -1) {
                api
            } else {
                null
            }
        }

        private fun findArgumentFor(
            evaluator: JavaEvaluator,
            calledMethod: PsiMethod,
            parameterIndex: Int,
            call: UCallExpression
        ): UExpression? {
            val parameters = calledMethod.parameterList.parameters
            if (parameterIndex >= 0 && parameterIndex < parameters.size) {
                val target = parameters[parameterIndex]
                val mapping = evaluator.computeArgumentMapping(call, calledMethod)
                for ((key, value1) in mapping) {
                    if (value1 === target || value1.isEquivalentTo(target)) {
                        return key
                    }
                }
            }

            return null
        }

        companion object {
            /**
             * Looks up the @ChecksSdkIntAtLeast annotation for the
             * given method or field.
             */
            fun get(owner: PsiModifierListOwner): SdkIntAnnotation? {
                val annotation = AnnotationUtil.findAnnotation(
                    owner, true, CHECKS_SDK_INT_AT_LEAST_ANNOTATION
                ) ?: return null
                return SdkIntAnnotation(annotation)
            }

            fun getMethodKey(
                evaluator: JavaEvaluator,
                method: UMethod
            ): String {
                val desc = evaluator.getMethodDescription(
                    method,
                    includeName = false,
                    includeReturn = false
                )
                val cls = method.getContainingUClass()?.let { evaluator.getQualifiedName(it) }
                return "$cls#${method.name}$desc"
            }

            fun getFieldKey(
                evaluator: JavaEvaluator,
                field: UField
            ): String {
                val cls = field.getContainingUClass()?.let { evaluator.getQualifiedName(it) }
                return "$cls#${field.name}"
            }

            private fun getMethodKey(
                evaluator: JavaEvaluator,
                method: PsiMethod
            ): String {
                val desc = evaluator.getMethodDescription(
                    method,
                    includeName = false,
                    includeReturn = false
                )
                val cls = method.containingClass?.let { evaluator.getQualifiedName(it) }
                return "$cls#${method.name}$desc"
            }

            private fun getFieldKey(
                evaluator: JavaEvaluator,
                field: PsiField
            ): String {
                val cls = field.containingClass?.let { evaluator.getQualifiedName(it) }
                return "$cls#${field.name}"
            }

            fun findSdkIntAnnotation(
                client: LintClient,
                evaluator: JavaEvaluator,
                project: Project,
                owner: PsiModifierListOwner
            ): SdkIntAnnotation? {
                val key = when (owner) {
                    is PsiMethod -> getMethodKey(evaluator, owner)
                    is PsiField -> getFieldKey(evaluator, owner)
                    else -> return null
                }
                val map = client.getPartialResults(project, SDK_INT_VERSION_DATA).map()
                val args = map[key] ?: return null
                val api = findAttribute(args, "api")?.toIntOrNull()
                val codename = findAttribute(args, "codename")
                val parameter = findAttribute(args, "parameter")?.toIntOrNull()
                val lambda = findAttribute(args, "lambda")?.toIntOrNull()
                return SdkIntAnnotation(api, codename, parameter, lambda)
            }

            private fun findAttribute(args: String, name: String): String? {
                val key = "$name="
                val index = args.indexOf(key)
                if (index == -1) {
                    return null
                }
                val start = index + key.length
                val end = args.indexOf(',', start).let { if (it == -1) args.length else it }
                return args.substring(start, end)
            }
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
