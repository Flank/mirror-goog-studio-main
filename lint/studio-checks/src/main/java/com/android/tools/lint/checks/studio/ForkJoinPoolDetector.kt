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
 * Forbid ForkJoinPool usage
 */
class ForkJoinPoolDetector : Detector(), SourceCodeScanner {

    companion object Issues {
        private val IMPLEMENTATION = Implementation(
            ForkJoinPoolDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val COMMON_FJ_POOL = Issue.create(
            id = "CommonForkJoinPool",
            briefDescription = "Using common Fork Join Pool",
            explanation =
                """
                Using the common ForkJoinPool can lead to freezes because in many cases
                the set of threads is very low.

                For Android Studio, either use the IntelliJ application pool:
                `com.intellij.openapi.application.Application#executeOnPooledThread`.
                Or, for long running operations, prefer the
                `AppExecutorUtil.getAppExecutorService()` executor.

                For the Android Gradle Plugin use
                `com.android.build.gradle.internal.tasks.Workers.preferWorkers` or
                `com.android.build.gradle.internal.tasks.Workers.preferThreads`

                For more, see `go/do-not-freeze`.
            """,
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )

        @JvmField
        val NEW_FJ_POOL = Issue.create(
            id = "NewForkJoinPool",
            briefDescription = "Using Fork Join Pool",
            explanation =
                """
                Using new Fork Join Pools should be limited to very specific use cases.

                For Android Studio, when possible, prefer using the IntelliJ application pool:
                `com.intellij.openapi.application.Application#executeOnPooledThread`.
                Or, for long running operations, prefer the
                `AppExecutorUtil.getAppExecutorService()` executor.

                For the Android Gradle Plugin use
                `com.android.build.gradle.internal.tasks.Workers.preferWorkers` or
                `com.android.build.gradle.internal.tasks.Workers.preferThreads`

                For more, see `go/do-not-freeze`.
            """,
            category = UI_RESPONSIVENESS,
            priority = 6,
            severity = Severity.ERROR,
            platforms = STUDIO_PLATFORMS,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableConstructorTypes() = listOf("java.util.concurrent.ForkJoinPool")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // Called constructor directly
        // TODO: ForkJoinTask
        context.report(
            NEW_FJ_POOL, node, context.getLocation(node),
            "Avoid using new ForkJoinPool instances when possible. Prefer using the IntelliJ application pool via `com.intellij.openapi.application.Application#executeOnPooledThread`, or for the Android Gradle Plugin use `com.android.build.gradle.internal.tasks.Workers`. See `go/do-not-freeze`."
        )
    }

    override fun getApplicableMethodNames() = listOf("commonPool")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        // Make sure it's the right methods
        when (method.name) {
            "commonPool" -> {
                if (!evaluator.isMemberInClass(method, "java.util.concurrent.ForkJoinPool")) {
                    return
                }
            }
            else -> {
                error(method.name)
            }
        }

        context.report(
            COMMON_FJ_POOL, node, context.getNameLocation(node),
            "Avoid using common ForkJoinPool, directly or indirectly (for example via CompletableFuture). It has a limited set of threads on some machines which leads to hangs. See `go/do-not-freeze`."
        )
    }
}
