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

import com.android.tools.lint.checks.AssertDetector
import com.android.tools.lint.checks.CheckResultDetector
import com.android.tools.lint.checks.CommentDetector
import com.android.tools.lint.checks.DateFormatDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.LintDetectorDetector
import com.android.tools.lint.checks.SamDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.detector.api.CURRENT_API

class StudioIssueRegistry : IssueRegistry() {

    override val api = CURRENT_API

    init {
        // Turn on some checks that are off by default but which we want run in Studio:
        LintDetectorDetector.UNEXPECTED_DOMAIN.setEnabledByDefault(true)
        if (isStudio) {
            LintDetectorDetector.PSI_COMPARE.setEnabledByDefault(true)
        }

        // A few other standard lint checks disabled by default which we want enforced
        // in our codebase
        SamDetector.ISSUE.setEnabledByDefault(true)
        CommentDetector.EASTER_EGG.setEnabledByDefault(true)
        CommentDetector.STOP_SHIP.setEnabledByDefault(true)
        DateFormatDetector.WEEK_YEAR.setEnabledByDefault(true)
        if (isStudio) { // not enforced in PSQ but give guidance in the IDE
            AssertDetector.EXPENSIVE.setEnabledByDefault(true)
            InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS.setEnabledByDefault(true)
            InteroperabilityDetector.LAMBDA_LAST.setEnabledByDefault(true)
            InteroperabilityDetector.KOTLIN_PROPERTY.setEnabledByDefault(true)
        }
    }

    override val issues = listOf(
        CheckResultDetector.CHECK_RESULT,
        ExternalAnnotationsDetector.ISSUE,
        FileComparisonDetector.ISSUE,
        ForkJoinPoolDetector.COMMON_FJ_POOL,
        ForkJoinPoolDetector.NEW_FJ_POOL,
        ImplicitExecutorDetector.ISSUE,
        IntellijThreadDetector.ISSUE,
        RegexpPathDetector.ISSUE,
        SwingUtilitiesDetector.ISSUE,
        SwingWorkerDetector.ISSUE,
        GradleApiUsageDetector.ISSUE,
        HdpiDetector.ISSUE,
        LintDetectorDetector.ID,
        LintDetectorDetector.PSI_COMPARE,
        LintDetectorDetector.CHECK_URL,
        LintDetectorDetector.DOLLAR_STRINGS,
        LintDetectorDetector.TEXT_FORMAT,
        LintDetectorDetector.TRIM_INDENT,
        LintDetectorDetector.UNEXPECTED_DOMAIN,
        LintDetectorDetector.USE_KOTLIN,
        LintDetectorDetector.USE_UAST,
        ShortNameCacheDetector.ISSUE,
        HtmlPaneDetector.ISSUE,
        ForbiddenStudioCallDetector.INTERN,
        TerminologyDetector.ISSUE
    )

    // TODO other checks:
    // TODO: Creating file reader or writer without UTF-8!
}
