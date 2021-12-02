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
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UFile

/**
 * Test mode which inserts unnecessary whitespace characters into the
 * source code
 */
class WhitespaceTestMode : UastSourceTransformationTestMode(
    description = "Extra whitespace added",
    "TestMode.WHITESPACE",
    "whitespace"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        The user is allowed to add extra or
        unnecessary whitespace through their code. This test mode adds a lot of
        unnecessary whitespace to catch bugs where detectors are incorrectly
        making guesses about offsets assuming "normal" spacing, as well as
        to catch issues where quickfixes are incorrectly handling space such
        as making assumptions like element.asSourceString() being equal to
        the underlying source code.

        In the unlikely event that your lint check is actually doing something
        whitespace specific, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        var ordinal = 0

        val editMap = mutableMapOf<Int, Edit>()

        fun insert(offset: Int) {
            editMap[offset] = Edit(offset, offset, " ", true, ordinal++)
        }

        root.sourcePsi.accept(
            object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    val next = element.nextSibling
                    if (element is KtContainerNode && element.node.elementType == KtNodeTypes.LABEL_QUALIFIER ||
                        next is KtContainerNode && next.node.elementType == KtNodeTypes.LABEL_QUALIFIER
                    ) {
                        // This covers the two adjacent parts of a label reference expression, which
                        // (for example) consists of "this" and "@label" or "continue" and "@label" etc. We
                        // want to make sure we don't add in a space between these.
                        return
                    }

                    checkElement(element)

                    when (element) {
                        is PsiComment,
                        is PsiWhiteSpace,
                        // Don't put spaces inside string literals:
                        is KtStringTemplateExpression,
                        // We cannot separate the "@" from the annotation name:
                        is KtAnnotationEntry,
                        is KtAnnotation,
                        is PsiAnnotation,
                        // Weirdly if there are spaces between tokens in the import list, PSI/UAST does not
                        // work properly:
                        is PsiImportList,
                        is KtImportList,
                        // And ditto for package names:
                        is PsiPackageStatement,
                        is KtPackageDirective
                        -> return
                    }

                    super.visitElement(element)
                }

                private fun checkElement(element: PsiElement) {
                    val range = element.textRange
                    insert(range.startOffset)
                    insert(range.endOffset)
                }
            }
        )

        return editMap.values.sorted().toMutableList()
    }

    override fun transformMessage(message: String): String {
        // Drop whitespace when comparing error messages
        return message.replace(Regex("""\s+"""), "")
    }
}
