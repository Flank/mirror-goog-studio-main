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

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Forbid SwingUtilities usage
 */
class SwingUtilitiesDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            SwingUtilitiesDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WrongInvokeLater",
            briefDescription = "Using SwingUtilities.invokeLater",
            explanation =
                """
                Do not use `SwingUtilities#invokeLater`; use `Application#invokeLater` \
                instead to properly handle modality.

                For more, see `go/do-not-freeze`.
            """,
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("invokeLater")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "javax.swing.SwingUtilities")) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = true, includeArguments = false),
                "Do not use `SwingUtilities.invokeLater`; use `Application.invokeLater` instead. See `go/do-not-freeze`."
            )
        }
    }
}
