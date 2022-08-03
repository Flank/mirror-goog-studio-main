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

import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.textRange

/**
 * Test mode which converts method bodies to expression bodies if
 * possible (and also inserts { } into if statements or removes them if
 * they're already there).
 */
class BodyRemovalTestMode : UastSourceTransformationTestMode(
    description = "Body Removal",
    "TestMode.BODY_REMOVAL",
    "body-removal"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        Kotlin offers expression bodies
        for methods, where instead of declaring a normal method body with
        a return statement, you simply assign the method to the return
        expression's operand. This makes the AST slightly different, and
        in particular there is no `UReturnExpression`, so detectors which
        are assuming they can just visit return expressions to find the
        exit values would not work correctly.

        This test mode replaces all eligible method bodies with expression
        bodies and makes sure the test results remain the same.

        In the unlikely event that your lint check is actually doing something
        expression body specific, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        val edits = mutableListOf<Edit>()
        val seen = mutableSetOf<PsiElement>()
        root.acceptSourceFile(object : EditVisitor() {
            override fun visitIfExpression(node: UIfExpression): Boolean {
                if (!node.isTernary) {
                    toggleBraces(node.thenExpression)
                    val elseExpression = node.elseExpression?.skipParenthesizedExprDown()
                    if (elseExpression != null && elseExpression !is UIfExpression) {
                        toggleBraces(node.elseExpression)
                    }
                }
                return super.visitIfExpression(node)
            }

            private fun toggleBraces(node: UExpression?) {
                node ?: return
                if (node is UBlockExpression) {
                    val statements = node.expressions
                    if (statements.size == 1) {
                        val statement = statements[0]
                        if (statement is UDeclarationsExpression) return
                        if (statement is UIfExpression && statement.elseExpression == null) return
                        edits.unsurround(node, node, "{", "}", source)
                    }
                    return
                }

                val begin = node.sourcePsi
                var end = begin
                var next = node.sourcePsi?.nextSibling
                if (next is PsiJavaToken && next.tokenType == JavaTokenType.SEMICOLON) {
                    end = next
                } else if (next is PsiWhiteSpace) {
                    next = next.nextSibling
                    if (next is PsiJavaToken && next.tokenType == JavaTokenType.SEMICOLON) {
                        end = next
                    }
                }
                edits.surround(begin, end, "{ ", " }")
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                checkReturnExpression(node)
                return super.visitReturnExpression(node)
            }

            private fun checkReturnExpression(node: UReturnExpression) {
                // With something like @JvmStatic UAST will create two methods from
                // the same source, and we risk attempting to edit the same source region
                // twice, so catch this scenario
                val sourcePsi = node.sourcePsi ?: return
                if (!seen.add(sourcePsi)) {
                    return
                }
                val parent = skipParenthesizedExprUp(node.uastParent)
                if (parent is UBlockExpression && isKotlin(node.sourcePsi)) {
                    val count = parent.expressions.size
                    if (count != 1) return
                    val method = skipParenthesizedExprUp(parent.uastParent) as? UMethod ?: return
                    val type = method.returnType?.canonicalText
                    if (type != null && type != "void") {
                        val blockRange = parent.textRange
                        val returnExpressionRange = node.returnExpression?.textRange
                        if (blockRange != null && returnExpressionRange != null) {
                            val blockStart = blockRange.startOffset
                            val expressionStart = returnExpressionRange.startOffset
                            val expressionEnd = returnExpressionRange.endOffset
                            val blockEnd = blockRange.endOffset
                            val openBrace = source.indexOf('{', blockStart)
                            val returnStart = source.lastIndexOf("return", expressionStart)
                            val closeBrace = source.indexOf('}', expressionEnd)
                            if (openBrace in blockStart until expressionStart &&
                                returnStart >= openBrace &&
                                closeBrace in expressionEnd until blockEnd
                            ) {
                                var returnEnd = returnStart + "return".length
                                if (source[returnEnd] == ' ') returnEnd++
                                edits.add(replace(openBrace, openBrace + 1, "="))
                                edits.add(remove(returnStart, returnEnd))
                                edits.add(remove(closeBrace, closeBrace + 1))
                            }
                        }
                    }
                }
            }
        })

        return edits
    }
}
