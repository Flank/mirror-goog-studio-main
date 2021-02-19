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

import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.ConfigurationHierarchy
import com.android.tools.lint.client.api.LintOptionsConfiguration
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions
import org.junit.Assert

class TestLintOptionsConfiguration(
    private val task: TestLintTask,
    private val project: Project,
    configurations: ConfigurationHierarchy,
    lintOptions: LintModelLintOptions,
    fatalOnly: Boolean
) : LintOptionsConfiguration(
    configurations,
    lintOptions,
    fatalOnly
) {
    init {
        associatedLocation = Location.create(project.dir)
    }

    override fun getDefinedSeverity(issue: Issue, source: Configuration, visibleDefault: Severity): Severity {
        val override = overrideSeverity(task, issue, visibleDefault)
        if (override != null) {
            return override
        }
        val severity = super.getDefinedSeverity(issue, source, visibleDefault)
        if (severity != null) {
            return severity
        }

        val parentSeverity = parent?.getDefinedSeverity(issue, source, visibleDefault)
        if (parentSeverity != null) {
            return parentSeverity
        }

        // In unit tests, include issues that are ignored by default
        for (id in task.issueIds ?: emptyArray()) {
            if (issue.id == id) {
                return getNonIgnoredSeverity(visibleDefault, issue)
            }
        }

        return if (task.checkedIssues.contains(issue))
            getNonIgnoredSeverity(visibleDefault, issue)
        else
            Severity.IGNORE
    }

    override fun ignore(
        context: Context,
        issue: Issue,
        location: Location?,
        message: String
    ) = Assert.fail("Not supported in tests.")

    override fun setSeverity(issue: Issue, severity: Severity?) =
        Assert.fail("Not supported in tests.")

    override fun toString(): String {
        return this.javaClass.simpleName + " for " + project.dir
    }
}
