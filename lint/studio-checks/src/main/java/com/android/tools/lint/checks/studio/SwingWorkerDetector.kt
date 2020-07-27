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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression

/**
 * Forbid SwingWorker usage
 */
class SwingWorkerDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            SwingWorkerDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "SwingWorker",
            briefDescription = "Using SwingWorker",
            explanation =
                """
                Do not use `javax.swing.SwingWorker`; use
                `com.intellij.util.concurrency.SwingWorker` instead.

                For more, see `go/do-not-freeze`.
            """,
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf("javax.swing.SwingWorker")

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val locationNode = declaration.uastSuperTypes.firstOrNull() ?: declaration
        report(context, declaration, locationNode)
    }

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {
        report(context, lambda, lambda)
    }

    private fun report(
        context: JavaContext,
        node: UElement,
        locationNode: UElement
    ) {
        context.report(
            ISSUE, node, context.getNameLocation(locationNode),
            "Do not use `javax.swing.SwingWorker`, use `com.intellij.util.concurrency.SwingWorker` instead. See `go/do-not-freeze`."
        )
    }
}
