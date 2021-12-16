/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.CHECK_RESULT_ANNOTATION
import com.android.tools.lint.checks.CheckResultDetector.Companion.expectsSideEffect
import com.android.tools.lint.checks.CheckResultDetector.Companion.isExpressionValueUnused
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator

class NoOpDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            NoOpDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NoOp",
            briefDescription = "NoOp Code",
            explanation = """
                This check looks for code which looks like it's a no-op -- usually \
                left over expressions from interactive debugging.
                """,
            category = CORRECTNESS,
            severity = Severity.WARNING,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        ).setAliases(listOf("ResultOfMethodCallIgnored"))
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UQualifiedReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector
                if (selector is UCallExpression) {
                    checkCall(selector, node)
                } else if (selector is USimpleNameReferenceExpression) {
                    val resolved = selector.resolve()
                    if (resolved is PsiMethod) {
                        checkCall(null, node, selector.identifier, resolved)
                    } else {
                        if (isExpressionValueUnused(selector)) {
                            report(context, selector, selector.identifier)
                        }
                    }
                }
            }

            private fun checkCall(
                call: UCallExpression,
                node: UQualifiedReferenceExpression
            ) {
                if (call.valueArguments.isNotEmpty()) return
                val method = call.resolve() ?: return
                val methodName = method.name
                checkCall(call, node, methodName, method)
            }

            private fun checkCall(
                call: UCallExpression?,
                node: UQualifiedReferenceExpression,
                methodName: String,
                method: PsiMethod
            ) {
                if (methodName == "getText" || methodName == "toString") {
                    if (isExpressionValueUnused(node)) {
                        report(context, call ?: node, methodName)
                    }
                } else if (methodName == "getPreferredSize" ||
                    methodName == "getInstance" ||
                    methodName == "getResourceResolver" ||
                    methodName == "getComponent" &&
                    context.evaluator.isMemberInClass(method, "com.intellij.execution.ui.RunnerLayoutUi")
                ) {
                    // Typically has deliberate side effects despite name
                } else if (call == null || isGetterName(methodName) && isGetter(method)) {
                    // (When call is null, it's a Kotlin property access of a method)

                    // Calls to static methods called get tend to be used for intentional
                    // initialization -- for example, getInstance() etc.
                    if (context.evaluator.isStatic(method)) return
                    if (isExpressionValueUnused(node)) {
                        if (isAnnotatedCheckResult(context, method)) return
                        if (isBufferMethod(context, method)) return
                        if (call != null && expectsSideEffect(context, call)) return
                        report(context, node.selector, methodName)
                    }
                }
            }
        }
    }

    override fun applicableAnnotations(): List<String> = listOf("org.jetbrains.annotations.Contract")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_CALL
    }

    override fun inheritAnnotation(annotation: String): Boolean = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val pure = UastLintUtils.getAnnotationBooleanValue(annotationInfo.annotation, "pure", false)
        if (!pure) {
            return
        }
        if (isExpressionValueUnused(element)) {
            report(context, element, (usageInfo.referenced as? PsiNamedElement)?.name ?: "?")
        }
    }

    private fun isAnnotatedCheckResult(
        context: JavaContext,
        method: PsiMethod
    ): Boolean {
        return context.evaluator.getAnnotation(
            method,
            CHECK_RESULT_ANNOTATION.oldName(),
            CHECK_RESULT_ANNOTATION.newName()
        ) != null
    }

    private fun isGetter(psiMethod: PsiMethod): Boolean {
        if (psiMethod.isConstructor) {
            return false
        }
        if (psiMethod.returnType == PsiType.VOID) {
            return false
        }
        val body = UastFacade.getMethodBody(psiMethod) ?: return psiMethod is PsiCompiledElement
        return isGetterBody(body)
    }

    private fun isGetterBody(node: UExpression?): Boolean {
        node ?: return true

        when (node) {
            is UReferenceExpression, is ULiteralExpression -> {
                return true
            }
            is UBinaryExpression -> {
                return node.operator !is UastBinaryOperator.AssignOperator
            }
            is UPrefixExpression -> {
                return node.operator != UastPrefixOperator.DEC && node.operator != UastPrefixOperator.INC
            }
            is UBlockExpression -> {
                val expressions = node.expressions
                if (expressions.size == 1) {
                    val expression = expressions[0]
                    if (expression is UReturnExpression) {
                        return isGetterBody(expression.returnExpression)
                    }
                }
            }
        }

        return false
    }

    /**
     * Is the given [name] a likely getter name, such as `getFoo` or
     * `isBar` ?
     *
     * We don't consider "get" by itself to be a getter name; it needs
     * to be a prefix for a named property.
     */
    private fun isGetterName(name: String): Boolean {
        val length = name.length
        return name.startsWith("get") && length > 3 && name[3].isUpperCase() ||
            name.startsWith("is") && length > 2 && name[2].isUpperCase()
    }

    /**
     * Is the given [method] a method from [java.nio.Buffer] ? These use
     * getter-naming but have side effects.
     */
    private fun isBufferMethod(context: JavaContext, method: PsiMethod) =
        context.evaluator.isMemberInSubClassOf(method, "java.nio.Buffer")

    private fun report(context: JavaContext, expression: UElement, name: String) {
        val message = "This ${if (expression is UCallExpression) "call result" else "reference"} is unused: $name"
        context.report(ISSUE, expression, context.getLocation(expression), message)
    }
}
