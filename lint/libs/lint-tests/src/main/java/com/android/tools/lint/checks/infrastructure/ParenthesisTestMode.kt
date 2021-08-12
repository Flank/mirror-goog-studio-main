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
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.source.tree.TreeElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.java.JavaUAssertExpression
import org.jetbrains.uast.java.JavaUTernaryIfExpression
import org.jetbrains.uast.java.expressions.JavaUAnnotationCallExpression
import org.jetbrains.uast.kotlin.KotlinStringTemplateUPolyadicExpression
import org.jetbrains.uast.kotlin.KotlinStringULiteralExpression
import org.jetbrains.uast.kotlin.KotlinUSafeQualifiedExpression
import org.jetbrains.uast.util.isArrayInitializer

/**
 * Test mode which inserts unnecessary parentheses in various places
 * to make sure AST analysis properly calls `skipParenthesizedExprUp`
 * and `skipParenthesizedExprDown` to navigate through
 * UParenthesizedExpression nodes
 */
class ParenthesisTestMode(private val includeUnlikely: Boolean = false) : SourceTransformationTestMode(
    description = "Extra parentheses added",
    "TestMode.PARENTHESIZED",
    "parentheses"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        The user is allowed to add extra or
        unnecessary parentheses in their code, and when they do, these show up
        as `UParenthesizedExpression` nodes in the abstract syntax tree. For
        this reason, you shouldn't check something like `if (node.uastParent is
        UCallExpression)` because it's possible that the parent is a
        parenthesized expression and you have to look at its parent instead.
        And in theory the code could even include multiple repeated, redundant
        parentheses. Therefore, whenever you look at the parent, make sure you
        surround the call with `skipParenthesizedExprUp(UExpression)`.

        Conversely, if you are looking at a child node, you may also need to be
        prepared to look inside parentheses; for that, use the method
        `skipParenthesizedExprDown`, an extension method on UExpression (and
        from Java import it from UastUtils).

        To help catch these bugs, lint has a special test mode where it inserts
        various redundant parentheses in your test code, and then makes sure
        that the same errors are reported. The error output will of course
        potentially vary slightly (since the source code snippets shown will
        contain extra parentheses), but the test will ignore these differences
        and only fail if it sees new errors reported or expected errors not
        reported.

        In the unlikely event that your lint check is actually doing something
        parenthesis specific, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        val edits = mutableListOf<Edit>()
        root.accept(object : EditVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                parenthesize(node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCall(node)
                return super.visitCallExpression(node)
            }

            private fun checkCall(node: UCallExpression) {
                if (node.sourcePsi is KtSuperTypeCallEntry || node.sourcePsi is KtConstructorDelegationCall) {
                    // Super calls shouldn't be parenthesized
                    return
                }
                if (node is JavaUAssertExpression || node is JavaUAnnotationCallExpression) {
                    return
                }
                if (node.isArrayInitializer()) {
                    return
                }
                val receiver = node.receiver
                if (receiver != null) {
                    if (receiver is UCallExpression ||
                        receiver is UParenthesizedExpression ||
                        receiver is KotlinUSafeQualifiedExpression ||
                        includeUnlikely && (receiver is UThisExpression || receiver is USuperExpression) ||
                        receiver is UQualifiedReferenceExpression && receiver.selector !is USimpleNameReferenceExpression ||
                        receiver is USimpleNameReferenceExpression && receiver.resolve() is PsiVariable
                    ) {
                        parenthesize(receiver)
                    }
                } else {
                    if (node is UThisExpression || node is USuperExpression) {
                        return
                    }
                    val name = node.methodName
                    if (name == "this" || name == "super") {
                        return
                    }
                    parenthesize(node)
                }
            }

            override fun visitIfExpression(node: UIfExpression): Boolean {
                if (node is JavaUTernaryIfExpression) {
                    parenthesize(node.condition)
                    parenthesize(node.thenExpression)
                    parenthesize(node.elseExpression)
                }
                return super.visitIfExpression(node)
            }

            override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
                if (includeUnlikely) {
                    if (node is KotlinStringULiteralExpression &&
                        node.sourcePsi is KtLiteralStringTemplateEntry &&
                        (node.sourcePsi.nextSibling as? TreeElement)?.elementType != KtTokens.CLOSING_QUOTE
                    ) {
                        // Offsets in template strings aren't quite right, so skip these
                        return super.visitLiteralExpression(node)
                    }

                    parenthesize(node)
                }
                return super.visitLiteralExpression(node)
            }

            override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
                parenthesize(node)
                return super.visitBinaryExpressionWithType(node)
            }

            override fun visitPolyadicExpression(node: UPolyadicExpression): Boolean {
                if (node is KotlinStringTemplateUPolyadicExpression) {
                    // Offsets are all wrong here so don't attempt to insert parentheses
                    return super.visitPolyadicExpression(node)
                }
                for (child in node.operands) {
                    parenthesize(node, child)
                }
                return super.visitPolyadicExpression(node)
            }

            override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
                if (node.operator == UastPrefixOperator.LOGICAL_NOT) {
                    parenthesize(node.operand)
                }
                return super.visitPrefixExpression(node)
            }

            private fun parenthesize(node: UExpression) {
                if (node.uastParent is USwitchClauseExpression) {
                    return
                }
                parenthesize(node, node)
            }

            private fun parenthesize(beginNode: UExpression, endNode: UExpression) {
                if (beginNode === endNode && beginNode is UParenthesizedExpression && !includeUnlikely) {
                    return
                }
                edits.surround(beginNode, endNode, "(", ")")
            }
        })

        return edits
    }

    override fun transformMessage(message: String): String {
        // Drop parentheses when comparing error messages
        return message.replace("(", "").replace(")", "")
    }
}
