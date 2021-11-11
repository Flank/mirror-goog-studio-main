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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.getParentOfType

/**
 * Test mode which converts if statements in Kotlin files to when
 * statements. In the future we could also try to convert Java if
 * statements into switches if the comparisons are eligible (e.g.
 * constant expressions).
 */
class IfToWhenTestMode : UastSourceTransformationTestMode(
    description = "Converting if/else to when/switch",
    "TestMode.IF_TO_WHEN",
    "if_to_when"
) {
    override val diffExplanation: String =
        // first line shorter: expecting to prefix that line with
        // "org.junit.ComparisonFailure: "
        """
        You can often rewrite a series of
        if-then-else expressions into a single when statement (or in some
        cases, a switch statement in Java). These have a different structure
        in the AST. This test mode will convert some if-then-else statements
        into when or switch blocks to make sure that the detector is properly
        handling both forms, e.g. handling both `UIfExpression` and
        `USwitchExpression`.

        In the unlikely event that your lint check is actually doing something
        specific to if/else expressions, you can turn off this test mode using
        `.skipTestModes($fieldName)`.
        """.trimIndent()

    override fun isRelevantFile(file: TestFile): Boolean {
        // Currently only migrating to when statements, not Java switches,
        // so this check should only run in Kotlin files
        return file.targetRelativePath.endsWith(SdkConstants.DOT_KT)
    }

    override fun transform(
        source: String,
        context: JavaContext,
        root: UFile,
        clientData: MutableMap<String, Any>
    ): MutableList<Edit> {
        if (!isKotlin(root.sourcePsi)) {
            return mutableListOf()
        }
        val seen = LinkedHashSet<PsiElement>()
        val edits = mutableListOf<Edit>()
        root.accept(object : EditVisitor() {
            override fun visitIfExpression(node: UIfExpression): Boolean {
                if (node.getParentOfType<UIfExpression>() == null) {
                    rewriteIfElse(node)
                }
                return super.visitIfExpression(node)
            }

            private fun rewriteIfElse(ifExpression: UIfExpression) {
                // When if statements are used in properties they can appear multiple
                // times in the generated AST, so make sure we only process them once
                val sourcePsi = ifExpression.sourcePsi ?: return
                if (!seen.add(sourcePsi)) {
                    return
                }
                val cases = mutableListOf<Pair<UExpression?, UExpression?>>()
                var curr = ifExpression
                while (true) {
                    cases.add(Pair(curr.condition, curr.thenExpression))
                    val next = curr.elseExpression
                    if (next is UIfExpression) {
                        curr = next
                    } else {
                        if (next != null) {
                            cases.add(Pair(null, next)) // null condition: final else
                        }
                        break
                    }
                }

                val startOffset = sourcePsi.startOffset
                val endOffset = sourcePsi.endOffset
                val sb = StringBuilder()

                // Get the indent of the opening line. We'll use this
                // to indent the opening when statement and the closing
                // bracket, as well as each clause condition. We do not
                // attempt to indent the statements within each body;
                // that's risky since we'd have to do lexical analysis
                // or risk breaking things like raw strings that
                // span multiple lines. Readability of the converted
                // code isn't a high priority anyway; the low effort
                // indentation here just helps makes reading unit test
                // diffs a bit easier.
                val indent = getIndent(source, startOffset)

                sb.append("when {\n")
                for ((condition, body) in cases) {
                    sb.append(indent)
                    if (condition == null) {
                        sb.append("else -> ")
                    } else {
                        sb.append(condition.sourcePsi?.text).append(" -> ")
                    }
                    if (body != null) {
                        sb.append(body.sourcePsi?.text)
                    }
                    sb.append("\n")
                }
                if (sb.endsWith("\n")) {
                    sb.setLength(sb.length - 1)
                }
                sb.append("\n$indent}")
                source.substring(startOffset, endOffset)
                edits.add(replace(startOffset, endOffset, sb.toString()))
            }
        })

        return edits
    }

    /**
     * Computes the indent string for the line containing the given
     * offset.
     */
    private fun getIndent(source: String, offset: Int): String {
        val sb = StringBuilder()
        var curr = offset - 1
        while (curr >= 0) {
            val c = source[curr]
            if (c == '\n') {
                break
            } else if (c.isWhitespace()) {
                sb.append(c)
            } else {
                sb.clear()
            }
            curr--
        }
        return sb.toString()
    }
}
