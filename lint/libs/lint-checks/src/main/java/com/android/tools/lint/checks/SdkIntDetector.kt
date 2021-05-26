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

package com.android.tools.lint.checks

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.checks.VersionChecks.Companion.CHECKS_SDK_INT_AT_LEAST_ANNOTATION
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.tryResolve

/** Looks for SDK_INT checks and suggests annotating these. */
class SdkIntDetector : Detector(), SourceCodeScanner {
    override fun getApplicableReferenceNames(): List<String> = listOf("SDK_INT")
    override fun getApplicableMethodNames(): List<String> = listOf("getBuildSdkInt")

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        // Make sure it's android.os.Build.VERSION.SDK_INT, though that's highly likely
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(referenced as? PsiField, "android.os.Build.VERSION")) {
            checkAnnotation(context, reference)
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        checkAnnotation(context, node)
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Nothing to do here; the partial results are consumed in the VersionChecks lookup
        // along the way
    }

    companion object {
        fun isLambdaType(context: JavaContext, type: PsiType?): Boolean {
            val rawType = (type as? PsiClassType)?.rawType() ?: return false
            val fqn = rawType.canonicalText
            if (fqn == "java.lang.Runnable" ||
                fqn == "java.util.function.Function" ||
                fqn.startsWith("kotlin.jvm.functions.Function")
            ) {
                return true
            }
            val clz = rawType.resolve() ?: return false
            val evaluator = context.evaluator
            return evaluator.implementsInterface(clz, "kotlin.Function", false) ||
                evaluator.implementsInterface(clz, "java.util.function.Function", false) ||
                evaluator.findAnnotation(clz, "java.lang.FunctionalInterface") != null
        }

        private val IMPLEMENTATION = Implementation(
            SdkIntDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        /** SDK_INT without @ChecksSdkIntAtLeast. */
        @JvmField
        val ISSUE = Issue.create(
            id = "AnnotateVersionCheck",
            briefDescription = "Annotate SDK_INT checks",
            explanation = """
                Methods which perform `SDK_INT` version checks (or field constants which reflect \
                the result of a version check) in libraries should be annotated with \
                `@ChecksSdkIntAtLeast`. This makes it possible for lint to correctly \
                check calls into the library later to correctly understand that problematic \
                code which is wrapped within a call into this library is safe after all.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        fun checkAnnotation(context: JavaContext, sdkInt: UElement) {
            // In app module analysis we always have source access to the
            // check method bodies; don't nag users to annotate these.
            val project = context.project
            if (!project.isLibrary || !project.isAndroidProject) {
                return
            }
            val comparison = sdkInt.getParentOfType(UBinaryExpression::class.java, true)
                ?: return

            val tokenType = comparison.operator
            if (tokenType !== UastBinaryOperator.GREATER &&
                tokenType !== UastBinaryOperator.GREATER_OR_EQUALS
            ) {
                return
            }
            val isGreaterOrEquals = tokenType === UastBinaryOperator.GREATER_OR_EQUALS
            val parent = comparison.uastParent
            if (parent is UField) {
                checkField(comparison, context, isGreaterOrEquals, parent)
                return
            } else if (parent is UReturnExpression) {
                val parentParent = parent.uastParent
                if (parentParent is UBlockExpression && parentParent.uastParent is UMethod) {
                    val size = parentParent.expressions.size
                    if (size == 1) {
                        val method = parentParent.uastParent as UMethod
                        checkMethod(comparison, context, isGreaterOrEquals, method)
                    }
                }
            } else if (parent is UIfExpression) {
                val then = (parent.thenExpression as? UBlockExpression)?.expressions?.firstOrNull()
                    ?: parent.thenExpression
                    ?: return
                val receiver = when (then) {
                    is UQualifiedReferenceExpression -> then.receiver
                    is UCallExpression -> then.receiver ?: return
                    else -> return
                }

                val method: UMethod = if (parent.uastParent is UReturnExpression) {
                    parent.uastParent?.uastParent as? UMethod
                        ?: parent.uastParent?.uastParent?.uastParent as? UMethod
                        ?: return
                } else if (parent.uastParent is UBlockExpression &&
                    parent.uastParent?.uastParent is UMethod
                ) {
                    val block = parent.uastParent as UBlockExpression
                    val expressions = block.expressions
                    if (expressions.size == 1 ||
                        expressions.size == 2 && expressions[1] is UReturnExpression
                    ) {
                        parent.uastParent?.uastParent as? UMethod
                            ?: parent.uastParent?.uastParent?.uastParent as? UMethod
                            ?: return
                    } else {
                        return
                    }
                } else {
                    return
                }

                checkMethod(context, method, receiver, comparison, isGreaterOrEquals)
            }
        }

        private fun checkMethod(
            context: JavaContext,
            method: UMethod,
            receiver: UExpression,
            comparison: UBinaryExpression,
            isGreaterOrEquals: Boolean
        ) {
            val parameter = receiver.tryResolve() as? PsiParameter ?: return
            val index = getParameterIndex(parameter)
            if (index != -1 && isLambdaType(context, parameter.type)) {
                checkMethod(comparison, context, isGreaterOrEquals, method, index)
            }
        }

        private fun getParameterIndex(parameter: PsiParameter): Int {
            val parameterList = parameter.parent as? PsiParameterList ?: return -1
            return parameterList.getParameterIndex(parameter)
        }

        private fun checkMethod(
            comparison: UBinaryExpression,
            context: JavaContext,
            isGreaterOrEquals: Boolean,
            method: UMethod,
            lambda: Int = -1
        ) {
            val apiOperand = comparison.rightOperand
            val apiValue = apiOperand.evaluate() ?: ConstantEvaluator.evaluate(context, apiOperand)
            val api = apiValue as? Int
            if (api != null) {
                val apiAtLeast = if (isGreaterOrEquals) api else api + 1
                if (!annotated(context, method, apiAtLeast)) {
                    val buildCode = getBuildCode(
                        apiAtLeast,
                        if (isGreaterOrEquals) apiOperand else null
                    )
                    val location = context.getNameLocation(method)
                    val args = "api=$buildCode${if (lambda != -1) ", lambda=$lambda" else ""}"
                    val message =
                        "This method should be annotated with `@ChecksSdkIntAtLeast($args)`"
                    val fix = createAnnotationFix(context, args, context.getLocation(method))
                    context.report(ISSUE, method, location, message, fix)

                    if (!context.isGlobalAnalysis()) {
                        // Store data for VersionChecks used by for example ApiDetector
                        val methodDesc = getMethodKey(context.evaluator, method)
                        val map = context.getPartialResults(ISSUE).map()
                        // See VersionChecks#isKnownVersionCheck
                        map.put(
                            methodDesc,
                            "api=$apiAtLeast${if (lambda != -1) ",lambda=$lambda" else ""}"
                        )
                    }
                }
            } else if (apiOperand is UReferenceExpression) {
                val parameter = apiOperand.resolve()
                if (parameter is PsiParameter) {
                    val index = getParameterIndex(parameter)
                    if (index != -1 && !annotated(context, method, -1)) {
                        val args = "parameter=$index${if (lambda != -1) ", lambda=$lambda" else ""}"
                        val message =
                            "This method should be annotated with `@ChecksSdkIntAtLeast($args)`"
                        val location = context.getNameLocation(method)
                        val fix = createAnnotationFix(context, args, context.getLocation(method))
                        context.report(ISSUE, method, location, message, fix)

                        if (!context.isGlobalAnalysis()) {
                            // Store data for the Version Check, used from ApiDetector etc
                            val methodDesc = getMethodKey(context.evaluator, method)
                            val map = context.getPartialResults(ISSUE).map()
                            map.put(
                                methodDesc,
                                "parameter=$index${if (lambda != -1) ",lambda=$lambda" else ""}"
                            )
                        }
                    }
                }
            }
        }

        private fun createAnnotationFix(
            context: JavaContext,
            args: String,
            location: Location
        ): LintFix? {
            // if not on classpath (older annotation library) don't suggest annotating
            if (context.evaluator.findClass(CHECKS_SDK_INT_AT_LEAST_ANNOTATION) == null) return null

            return LintFix.create()
                .annotate("$CHECKS_SDK_INT_AT_LEAST_ANNOTATION($args)")
                .range(location)
                .build()
        }

        private fun getBuildCode(api: Int, constant: UElement?): String {
            val text = (constant as? UReferenceExpression)?.sourcePsi?.text
            if (text != null) {
                return text
            }
            val buildCode = SdkVersionInfo.getBuildCode(api) ?: return api.toString()
            return "android.os.Build.VERSION_CODES.$buildCode"
        }

        private fun checkField(
            comparison: UBinaryExpression,
            context: JavaContext,
            isGreaterOrEquals: Boolean,
            field: UField
        ) {
            val apiOperand = comparison.rightOperand
            val value = apiOperand.evaluate()
                ?: ConstantEvaluator.evaluate(context, apiOperand)
            val api = value as? Int ?: return
            val atLeast = if (isGreaterOrEquals) api else api + 1
            if (!annotated(context, field, atLeast)) {
                val buildCode = getBuildCode(atLeast, if (isGreaterOrEquals) apiOperand else null)
                val args = "api=$buildCode"
                val message = "This field should be annotated with `ChecksSdkIntAtLeast($args)`"
                val location = context.getLocation(field)
                val fix = createAnnotationFix(context, args, location)
                context.report(ISSUE, field, location, message, fix)

                if (!context.isGlobalAnalysis()) {
                    // Store data for VersionChecks used by for example ApiDetector
                    val fieldDesc = getFieldKey(context.evaluator, field)
                    val map = context.getPartialResults(ISSUE).map()
                    map.put(fieldDesc, "api=$atLeast")
                }
            }
        }

        private fun annotated(context: JavaContext, annotated: UAnnotated, api: Int): Boolean {
            // TODO: If annotated, warn if it's not set to the correct API level
            return context.evaluator.getAllAnnotations(annotated, false).any {
                it.qualifiedName == CHECKS_SDK_INT_AT_LEAST_ANNOTATION
            }
        }

        private fun getMethodKey(
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

        private fun getFieldKey(
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
            val map = client.getPartialResults(project, ISSUE).map()
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
