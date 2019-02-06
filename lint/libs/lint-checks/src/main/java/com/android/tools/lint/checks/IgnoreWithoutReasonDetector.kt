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

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import java.util.EnumSet

/**
 * It checks that there is a reason defined when using the @Ignored annotation from JUnit.
 */
class IgnoreWithoutReasonDetector : Detector(), Detector.UastScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "IgnoreWithoutReason",
            briefDescription = "@Ignore without Reason",
            explanation = """
            Ignoring a test without a reason makes it difficult to figure out the problem later.
            Please define an explicit reason why it is ignored, and when it can be resolved.""",
            category = Category.TESTING,
            priority = 2,
            severity = Severity.WARNING,
            implementation = Implementation(
                IgnoreWithoutReasonDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        IgnoreAnnotationVisitor(context)

    internal class IgnoreAnnotationVisitor(private val context: JavaContext) : UElementHandler() {
        override fun visitMethod(node: UMethod) = processAnnotations(node, node)

        override fun visitClass(node: UClass) = processAnnotations(node, node)

        private fun processAnnotations(element: UElement, annotated: UAnnotated) {
            val annotations = context.evaluator.getAllAnnotations(annotated, false)
            val ignoreAnnotation =
                annotations.firstOrNull { it.qualifiedName == "org.junit.Ignore" }

            if (ignoreAnnotation != null) {
                val attribute = ignoreAnnotation.findAttributeValue(ATTR_VALUE)
                val hasDescription =
                    attribute != null &&
                            run {
                                val value =
                                    ConstantEvaluator.evaluate(context, attribute) as? String
                                value != null && value.isNotBlank() && value != "TODO"
                            }
                if (!hasDescription) {
                    val fix =
                        if (attribute == null || ignoreAnnotation.attributeValues.isEmpty()) {
                            LintFix.create()
                                .name("Give reason")
                                .replace()
                                .end()
                                .with("(\"TODO\")")
                                .select("TODO")
                                .build()
                        } else {
                            null
                        }
                    context.report(
                        ISSUE, element, context.getLocation(ignoreAnnotation),
                        "Test is ignored without giving any explanation.", fix
                    )
                }
            }
        }
    }
}
