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
import com.intellij.util.containers.MultiMap
import org.jetbrains.uast.UCallExpression

/**
 * Forbid methods related to Futures that run code in an implicitly chosen Executor.
 */
class ImplicitExecutorDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            ImplicitExecutorDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ImplicitExecutor",
            briefDescription = "Using an implicitly chosen Executor",
            explanation =
                """
                Not specifying an Executor for running callbacks is a common source of threading
                issues, resulting in too much work running on the UI thread or the shared
                ForkJoinPool used by the IDE for highlighting.

                In Android Studio, always specify the executor for running callbacks. As explained
                in the documentation for `ListenableFuture.addCallback`, try to avoid
                `directExecutor()`.

                For more, see `go/do-not-freeze`.
            """,
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )

        private const val COMPLETABLE_FUTURE = "java.util.concurrent.CompletableFuture"
        private const val EXECUTOR = "java.util.concurrent.Executor"

        private val knownMethods = MultiMap<String, String>().apply {
            // These got removed in later versions of Guava, see
            // https://github.com/google/guava/commit/87d87f5cac5a540d46a6382683722ead7b72d1b3#diff-3fe13f15fa4a5af9b4a55b21d7db2541
            put(
                "com.google.common.util.concurrent.Futures",
                listOf(
                    "addCallback",
                    "catching",
                    "catchingAsync",
                    "transform",
                    "transformAsync"
                )
            )

            // These got removed in later versions of Guava, see
            // https://github.com/google/guava/commit/87d87f5cac5a540d46a6382683722ead7b72d1b3#diff-3fe13f15fa4a5af9b4a55b21d7db2541
            put(
                "com.google.common.util.concurrent.Futures.FutureCombiner",
                listOf(
                    "call",
                    "callAsync"
                )
            )

            put(
                COMPLETABLE_FUTURE,
                Class.forName(COMPLETABLE_FUTURE)
                    .methods
                    .asSequence()
                    .map { it.name }
                    .filter { it.endsWith("Async") }
                    .toSet()
            )
        }
    }

    override fun getApplicableMethodNames(): List<String>? = knownMethods.values().toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        if (!knownMethods.get(containingClass.qualifiedName).contains(method.name)) return

        val lastParameter = evaluator.getParameterCount(method) - 1
        if (evaluator.parameterHasType(method, lastParameter, EXECUTOR)) return

        // See if this method has an overload that takes an Executor.
        val overloads = containingClass.findMethodsByName(method.name, false)
        if (overloads.size < 2) {
            return
        }

        val parametersWithExecutor =
            method.parameterList.parameters.asSequence()
                .map { it.type.canonicalText }
                .plus(EXECUTOR)
                .toList()
                .toTypedArray()

        val overloadWithExecutor =
            overloads.firstOrNull { evaluator.parametersMatch(it, *parametersWithExecutor) }

        if (overloadWithExecutor != null) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = true, includeArguments = false),
                "Use `${method.name}` overload with an explicit Executor instead. See `go/do-not-freeze`."
            )
        }
    }
}
