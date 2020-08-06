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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Makes sure that extensions for indexing in the IDE do not
 * break indexing
 */
class ShortNameCacheDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        @Suppress("LintImplUnexpectedDomain")
        @JvmField
        val ISSUE = Issue.create(
            id = "ShortNamesCache",
            briefDescription = "PsiShortNamesCaches which abort processing",
            explanation =
                """
                The various `process` methods in PsiShortNamesCache take a boolean
                return value. If you return "false" from this method, you're saying
                that cache processing should not continue. This will break other name caches,
                which for example happened with http://b/152432842.
            """,
            category = Category.CORRECTNESS,
            priority = 1,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = Implementation(
                ShortNameCacheDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf("com.intellij.psi.search.PsiShortNamesCache")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name.startsWith("process") && method.findSuperMethods().any {
                it.containingClass?.qualifiedName == "com.intellij.psi.search.PsiShortNamesCache"
            }
            ) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        method.accept(object : AbstractUastVisitor() {
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
                val expression = node.returnExpression ?: return true
                val value = ConstantEvaluator.evaluate(context, expression)
                if (value == false) {
                    val ifParent: UIfExpression? = expression.getParentOfType(strict = true)
                    if (ifParent != null) {
                        // Surrounding if check; conditionally handling this return; probably
                        // a reasonable usage
                        return true
                    }

                    context.report(
                        ISSUE, node, context.getLocation(node),
                        "Do **not** return `false`; this will mark processing as " +
                            "consumed for this element and other cache processors will not " +
                            "run. This can lead to bugs like b/152432842."
                    )
                }
                return true
            }
        })
    }
}
