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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.isKotlinHardKeyword
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.elements.KtLightMemberImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile

/**
 * Test mode which reorders the argument order in Kotlin files to make
 * sure that detectors are properly computing the argument map.
 */
class ArgumentReorderingTestMode : UastSourceTransformationTestMode(
    description = "Reordered Named Arguments",
    "TestMode.REORDER_ARGUMENTS",
    "reorder-arguments"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        In Kotlin, with named parameters,
        callers are free to supply the arguments in a different order than
        the parameter order in the method. Lint has support for looking up
        the argument mapping; you should not just line the arguments and
        parameters up one by one in order.

        This test mode will arbitrarily reorder arguments (from Kotlin to
        Kotlin, where named parameters are available) and make sure that
        the test results are unaffected.

        In the unlikely event that your lint check is actually doing something
        argument order specific, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun isRelevantFile(file: TestFile): Boolean {
        // Only applies to Kotlin files, not Java
        return file.targetRelativePath.endsWith(DOT_KT)
    }

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        val edits = mutableListOf<Edit>()
        root.acceptSourceFile(object : EditVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val resolved = node.resolve()
                if (resolved is PsiMethod && node.valueArguments.size > 1) {
                    checkCall(node, resolved)
                }
                return super.visitCallExpression(node)
            }

            private fun checkCall(call: UCallExpression, method: PsiMethod) {
                if (method is PsiCompiledElement) {
                    return
                }
                if (method is KtLightMemberImpl<*>) {
                    val argumentMapping = context.evaluator.computeArgumentMapping(call, method)
                    val arguments = mutableListOf<Triple<UExpression, KtValueArgument, KtParameter>>()
                    for (argument in call.valueArguments) {
                        val psi = argument.sourcePsi
                        val ktArgument = psi?.getParentOfType<KtValueArgument>(false) ?: return
                        val parameter = argumentMapping[argument]
                        val ktParameter = try {
                            parameter?.javaClass?.getDeclaredMethod("getKotlinOrigin")
                                ?.invoke(parameter) as? KtParameter
                                // We can't just reorder some and not all so if any are not found, skip this call
                                ?: return
                        } catch (ignore: Throwable) {
                            return
                        }
                        if (ktParameter.isVarArg) {
                            // Currently, we're not trying to reorder varags. This is a bit more complicated
                            // since the parameter mapping we're getting has a number of repeats and we
                            // have to figure out how to construct an array from the individual elements -- e.g.
                            // if we have foo(true, 1,2,3) this would need to turn into
                            //  foo(name=true, arrays=intArrayOf(1,2,3)), and son.
                            return
                        }
                        arguments.add(Triple(argument, ktArgument, ktParameter))
                    }

                    // Shift arguments
                    val argumentCount = arguments.size
                    for (i in 0 until argumentCount) {
                        val j = (i + 1) % argumentCount
                        val current = arguments[i]
                        val next = arguments[j]
                        // Replace the argument at i with the argument from j, optionally adding in
                        // the name if necessary
                        val argument = next.second
                        val nextText = argument.text.let {
                            if (argument.isNamed()) it else {
                                val parameterName = next.third.name
                                if (parameterName == null) {
                                    return
                                } else if (escapeName(parameterName)) {
                                    "`$parameterName` = $it"
                                } else {
                                    "$parameterName = $it"
                                }
                            }
                        }
                        val currentRange = current.second.textRange

                        if (i == argumentCount - 2) {
                            // Check to see if there is a closing parenthesis between the second
                            // to last and last argument; this is where you've placed a lambda
                            // after the call. When we reorder we need to pull it back inside the
                            // parentheses.
                            var sibling = current.second.nextSibling
                            while (sibling != null && sibling != next.second) {
                                if (sibling is LeafPsiElement && sibling.elementType == KtTokens.RPAR) {
                                    val offset = sibling.startOffset
                                    val after = arguments[argumentCount - 1].second.textRange.endOffset
                                    edits.add(replace(offset, offset + 1, ","))
                                    edits.add(insert(after, ")"))
                                    break
                                }
                                sibling = sibling.nextSibling
                            }
                        }

                        edits.add(replace(currentRange.startOffset, currentRange.endOffset, nextText))
                    }
                }
            }
        })

        return edits
    }

    private fun escapeName(name: String): Boolean {
        if (name.any { !it.isJavaIdentifierPart() } || !name.first().isJavaIdentifierStart()) {
            return true
        }

        return isKotlinHardKeyword(name)
    }
}
