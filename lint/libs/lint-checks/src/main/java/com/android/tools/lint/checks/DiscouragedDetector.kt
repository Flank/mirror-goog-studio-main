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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement

class DiscouragedDetector : AbstractAnnotationDetector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> = listOf(
        "androidx.annotation.Discouraged"
    )

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        referenced: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        referenced ?: return

        val location = context.getNameLocation(usage)

        // androidx.annotation.Discouraged defines the message as an empty string; it is non-null.
        val message = getAnnotationStringValue(
            annotation, "message"
        )

        // If an explanation is not provided, a generic message will be shown instead.
        if (!message.isNullOrBlank()) {
            report(context, ISSUE, usage, location, message)
        } else {
            val defaultMessage = "Use of this API is discouraged"
            report(context, ISSUE, usage, location, defaultMessage)
        }
    }

    companion object {

        private val IMPLEMENTATION = Implementation(
            DiscouragedDetector::class.java,
            JAVA_FILE_SCOPE
        )

        /** Usage of elements that are discouraged against. */
        @JvmField
        val ISSUE = create(
            id = "DiscouragedApi",
            briefDescription = "Using discouraged APIs",
            explanation = """
                Discouraged APIs are allowed and are not deprecated, but they may be unfit for \
                common use (e.g. due to slow performance or subtle behavior).
                """,
            category = Category.CORRECTNESS,
            priority = 2,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}
