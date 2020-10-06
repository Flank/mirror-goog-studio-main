/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import org.junit.Assert.fail
import java.io.File

class TestConfiguration(
    private val task: TestLintTask,
    configurations: ConfigurationHierarchy
) : Configuration(configurations) {

    override fun getDefinedSeverity(issue: Issue, source: Configuration): Severity? {
        val override = overrideSeverity(task, issue)
        if (override != null) {
            return override
        }
        val severity = super.getDefinedSeverity(issue, source)
        if (severity != null) {
            return severity
        }

        // In unit tests, include issues that are ignored by default
        for (id in task.issueIds ?: emptyArray()) {
            if (issue.id == id) {
                return getNonIgnoredSeverity(issue.defaultSeverity, issue)
            }
        }

        return if (task.checkedIssues.contains(issue))
            getNonIgnoredSeverity(issue.defaultSeverity, issue)
        else
            Severity.IGNORE
    }

    override fun ignore(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    ) = fail("Not supported in tests.")

    override var baselineFile: File?
        get() = null
        set(value) {
            fail("Not supported in tests.")
        }

    override fun setSeverity(issue: Issue, severity: Severity?) = fail("Not supported in tests.")

    override fun ignore(issue: Issue, file: File) {
        fail("Not supported in tests.")
    }

    override fun ignore(issueId: String, file: File) {
        fail("Not supported in tests.")
    }
}

fun overrideSeverity(task: TestLintTask, issue: Issue): Severity? {
    val enabled = when (issue) {
        IssueRegistry.LINT_ERROR, IssueRegistry.LINT_WARNING ->
            task.allowSystemErrors || !task.allowCompilationErrors
        IssueRegistry.PARSER_ERROR -> !task.allowSystemErrors
        IssueRegistry.OBSOLETE_LINT_CHECK -> !task.allowObsoleteLintChecks
        IssueRegistry.UNKNOWN_ISSUE_ID -> true
        else -> null
    }
    return if (enabled != null) {
        if (enabled) issue.defaultSeverity else Severity.IGNORE
    } else {
        null
    }
}

fun getNonIgnoredSeverity(severity: Severity, issue: Issue): Severity {
    return if (severity === Severity.IGNORE) {
        if (issue.defaultSeverity !== Severity.IGNORE) {
            issue.defaultSeverity
        } else {
            Severity.WARNING
        }
    } else {
        severity
    }
}
