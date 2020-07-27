/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintOptionsConfiguration
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions
import org.junit.Assert
import java.io.File

class TestLintOptionsConfiguration(
    private val task: TestLintTask,
    client: LintClient,
    lintFile: File?,
    dir: File,
    lintOptions: LintModelLintOptions,
    fatalOnly: Boolean
) : LintOptionsConfiguration(
    client,
    lintFile ?: File(dir, CONFIG_FILE_NAME),
    dir,
    lintOptions,
    fatalOnly
) {

    override fun getDefaultSeverity(issue: Issue): Severity {
        // In unit tests, include issues that are ignored by default
        val severity = super.getDefaultSeverity(issue)
        if (severity == Severity.IGNORE) {
            if (issue.defaultSeverity != Severity.IGNORE) {
                return issue.defaultSeverity
            }
            return Severity.WARNING
        }
        return severity
    }

    override fun isEnabled(issue: Issue): Boolean {
        when {
            issue == IssueRegistry.LINT_ERROR ->
                return task.allowSystemErrors ||
                    !task.allowCompilationErrors
            issue == IssueRegistry.PARSER_ERROR -> return !task.allowSystemErrors
            issue == IssueRegistry.OBSOLETE_LINT_CHECK -> return !task.allowObsoleteLintChecks
            task.issueIds != null -> for (id in task.issueIds!!) {
                if (issue.id == id) {
                    return true
                }
            }
        }

        return task.checkedIssues.contains(issue)
    }

    override fun ignore(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    ) = Assert.fail("Not supported in tests.")

    override fun setSeverity(issue: Issue, severity: Severity?) =
        Assert.fail("Not supported in tests.")
}
