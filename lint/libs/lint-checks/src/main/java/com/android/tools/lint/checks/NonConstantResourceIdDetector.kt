/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.java.JavaUSwitchExpression

/**
 * Warns against using non-constant resource IDs in Java switch
 * statement blocks and annotations. This aims to prevent users from
 * using resource IDs as switch cases and annotations.
 */
class NonConstantResourceIdDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf<Class<out UElement?>>(
            UAnnotation::class.java,
            USwitchExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return ResourceIdVisitor(context)
    }

    class ResourceIdVisitor(val context: JavaContext) : UElementHandler() {
        override fun visitSwitchExpression(node: USwitchExpression) {
            if (node is JavaUSwitchExpression) {
                checkSwitchCasesForRClassReferences(node.body)
            }
        }

        /**
         * Checks switch cases values for non-constant expressions which
         * are R class references.
         */
        private fun checkSwitchCasesForRClassReferences(body: UExpressionList) {
            for (expression in body.expressions) {
                if (expression is USwitchClauseExpression) {
                    val switchCase = expression.caseValues.firstOrNull() ?: continue
                    if (checkExpressionReceiverIsRClass(switchCase)) {
                        val location = context.getLocation(switchCase)
                        context.report(
                            NON_CONSTANT_RESOURCE_ID,
                            switchCase,
                            location,
                            /* Bug 170852493 */
                            "Resource IDs will be non-final by default in Android Gradle Plugin version 8.0, " +
                                "avoid using them in switch case statements"
                        )
                    }
                }
            }
        }

        override fun visitAnnotation(node: UAnnotation) {
            for (attribute in node.attributeValues) {
                val attributeExpression = attribute.expression
                if (checkExpressionReceiverIsRClass(attributeExpression)) {
                    val location = context.getLocation(attributeExpression)
                    context.report(
                        NON_CONSTANT_RESOURCE_ID,
                        attributeExpression,
                        location,
                        /* Bug 170852493 */
                        "Resource IDs will be non-final by default in Android Gradle Plugin version 8.0, " +
                            "avoid using them as annotation attributes"
                    )
                }
            }
        }

        private fun checkExpressionReceiverIsRClass(expression: UExpression): Boolean {
            val evaluatedExpression = ResourceReference.get(expression)
            return evaluatedExpression != null &&
                evaluatedExpression.`package` != SdkConstants.ANDROID_PKG
        }
    }

    companion object {
        @JvmField
        val NON_CONSTANT_RESOURCE_ID = Issue.create(
            id = "NonConstantResourceId",
            briefDescription = "Checks use of resource IDs in places requiring constants",
            explanation = """
                Avoid the usage of resource IDs where constant expressions are required.

                A future version of the Android Gradle Plugin will generate R classes with \
                non-constant IDs in order to improve the performance of incremental compilation.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                NonConstantResourceIdDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
