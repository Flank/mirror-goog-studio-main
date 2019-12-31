/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/** Checks for errors related to Date Formats */
class DateFormatDetector : Detector(), SourceCodeScanner {
    // ---- implements SourceCodeScanner ----
    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(SIMPLE_DATE_FORMAT_CLS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!specifiesLocale(constructor)) {
            val location = context.getLocation(node)
            val message = "To get local formatting use `getDateInstance()`, " +
                    "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
                    "`new SimpleDateFormat(String template, Locale locale)` with for " +
                    "example `Locale.US` for ASCII dates."
            context.report(DATE_FORMAT, node, location, message)
        }
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(
                DateFormatDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )

        /** Constructing SimpleDateFormat without an explicit locale */
        @JvmField
        val DATE_FORMAT =
            Issue.create(
                id = "SimpleDateFormat",
                briefDescription = "Implied locale in date format",
                explanation = """
                    Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, \
                    or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat \
                    suitable for the user's locale. The main reason you'd create an instance \
                    this class directly is because you need to format/parse a specific \
                    machine-readable format, in which case you almost certainly want to \
                    explicitly ask for US to ensure that you get ASCII digits (rather than, \
                    say, Arabic digits).

                    Therefore, you should either use the form of the SimpleDateFormat \
                    constructor where you pass in an explicit locale, such as Locale.US, or \
                    use one of the get instance methods, or suppress this error if really know \
                    what you are doing.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                moreInfo = "http://developer.android.com/reference/java/text/SimpleDateFormat.html",
                implementation = IMPLEMENTATION
            )

        const val LOCALE_CLS = "java.util.Locale"
        private const val SIMPLE_DATE_FORMAT_CLS = "java.text.SimpleDateFormat"

        private fun specifiesLocale(method: PsiMethod): Boolean {
            val parameterList = method.parameterList
            val parameters = parameterList.parameters
            for (parameter in parameters) {
                val type = parameter.type
                if (type.canonicalText == LOCALE_CLS) {
                    return true
                }
            }
            return false
        }
    }
}
