/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind

/**
 * Detect use of Gradle APIs that should not be used in the Android Gradle plugin. Right now, this
 * detects usage of org.gradle.api.Project.exec, but it may contain more things in the future.
 */
class GradleApiUsageDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            GradleApiUsageDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ProjectExecOperations",
            briefDescription = "Using org.gradle.api.Project.exec",
            explanation =
                """
                Using `org.gradle.api.Project.exec` is not compatible with Gradle instant execution.

                Please inject `org.gradle.process.ExecOperations` into task that needs it. This will
                provide you with ability to start Java and other types of processes.
            """,
            category = CORRECTNESS,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                if (node.callableName != "exec") return
                val psiMethod = node.resolve() as? PsiMethod ?: return
                check(context, node, psiMethod)
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.methodName != "exec") return
                check(context, node, node.resolve() ?: return)
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallableReferenceExpression::class.java, UCallExpression::class.java)

    private fun check(context: JavaContext, node: UExpression, psiMethod: PsiMethod) {
        val containingClass = psiMethod.containingClass ?: return
        if (context.evaluator.implementsInterface(
            containingClass,
            "org.gradle.api.Project",
            false
        )
        ) {
            context.report(
                ISSUE, node, context.getNameLocation(node),
                "Avoid using `org.gradle.api.Project.exec` as it is incompatible with Gradle instant execution."
            )
        }
    }
}
