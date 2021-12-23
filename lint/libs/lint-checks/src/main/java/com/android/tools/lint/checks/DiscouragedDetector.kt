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

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.EXTENDS
import com.android.tools.lint.detector.api.AnnotationUsageType.FIELD_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_OVERRIDE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_REFERENCE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValue
import org.jetbrains.uast.UElement

class DiscouragedDetector : AbstractAnnotationDetector(), SourceCodeScanner {

    override fun applicableAnnotations(): List<String> = listOf(
        DISCOURAGED_ANNOTATION
    )

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == METHOD_CALL || type == METHOD_REFERENCE || type == CLASS_REFERENCE ||
            type == METHOD_OVERRIDE || type == EXTENDS || type == FIELD_REFERENCE
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        usageInfo.referenced ?: return
        val location = context.getNameLocation(element)

        // androidx.annotation.Discouraged defines the message as an empty string; it is non-null.
        val message = getAnnotationStringValue(
            annotationInfo.annotation, "message"
        )

        // If an explanation is not provided, a generic message will be shown instead.
        if (!message.isNullOrBlank()) {
            report(context, ISSUE, element, location, message)
        } else {
            val defaultMessage = "Use of this API is discouraged"
            report(context, ISSUE, element, location, defaultMessage)
        }
    }

    companion object {
        const val DISCOURAGED_ANNOTATION = "androidx.annotation.Discouraged"

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
