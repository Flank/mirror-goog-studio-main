/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment

/**
 * Detector looking for suspicious combinations of intent.setData and intent.setType
 */
class IntentDetector : Detector(), SourceCodeScanner {
    companion object {
        /** The main issue discovered by this detector  */
        @JvmField
        val ISSUE = Issue.create(
            "IntentReset",
            "Suspicious mix of `setType` and `setData`",

            "Intent provides the following APIs: `setData(Uri)` and `setType(String)`. " +
                    "Unfortunately, setting one clears the other. If you want to set both, you " +
                    "should call `setDataAndType(Uri, String)` instead.",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                IntentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val INTENT_CLASS = "android.content.Intent"
        private const val ANDROID_NET_URI = "android.net.Uri"
        private const val SET_DATA = "setData"
        private const val SET_TYPE = "setType"
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(INTENT_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        var constructorStatement: UExpression? = null
        var variable: PsiElement? = null
        var block: UBlockExpression? = null
        var p: UElement? = node.uastParent
        while (p != null) {
            if (p is UVariable) {
                variable = p.psi
            } else if (p.isAssignment()) {
                variable = (p as UBinaryExpression).leftOperand.tryResolve()
            }
            val parent = p.uastParent
            if (parent is UBlockExpression) {
                block = parent
                constructorStatement = p as? UExpression
                break
            }
            p = parent
        }

        variable ?: return
        constructorStatement ?: return
        block ?: return

        val statements = block.expressions
        val start = statements.indexOf(constructorStatement)
        if (start == -1) {
            return
        }

        var seenInConstructor = false
        var seenData: UElement? = null
        var seenType: UElement? = null

        // Did we pass in a non-null Uri? If so record that
        for (argument in node.valueArguments) {
            val type = argument.getExpressionType() ?: continue
            if (type.canonicalText == ANDROID_NET_URI &&
                !(argument is ULiteralExpression && argument.isNull)) {
                seenInConstructor = true
                seenData = argument
                break
            }
        }

        for (index in start + 1 until statements.size) {
            val statement = statements[index]
            if (statement is UQualifiedReferenceExpression) {
                val statementReceiver = statement.receiver.tryResolve()
                if (statementReceiver == variable) {
                    val call = statement.selector
                    if (call is UCallExpression) {
                        val args = call.valueArguments
                        if (args.size != 1) {
                            continue
                        }
                        val arg = args[0]
                        if (arg is ULiteralExpression && arg.isNull) {
                            continue
                        }

                        val name = call.methodName
                        if (name == SET_DATA) {
                            seenData = call
                        } else if (name == SET_TYPE) {
                            seenType = call
                        } else {
                            continue
                        }
                        if (seenData != null && seenType != null) {
                            val prev = if (name == SET_DATA) {
                                seenType
                            } else {
                                seenData
                            }

                            val prevDesc = if (seenInConstructor) {
                                "setting URI in `Intent` constructor"
                            } else {
                                "calling `${(prev as? UCallExpression)?.methodName}`"
                            }
                            val data = if (name == SET_DATA) {
                                "type"
                            } else {
                                "data"
                            }
                            val location = context.getLocation(call)
                                .withSecondary(
                                    context.getLocation(prev),
                                    "Originally set here"
                                )
                            context.report(
                                ISSUE,
                                call,
                                location,
                                "Calling `$name` after $prevDesc will clear " +
                                        "the $data: Call `setDataAndType` instead?",
                                null
                            )
                        }
                    }
                }
            }
        }
    }
}
