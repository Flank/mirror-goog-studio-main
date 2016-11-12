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

package com.android.tools.lint.checks.infrastructure;

import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;

public class TestConfiguration extends DefaultConfiguration {

    private final TestLintTask task;

    protected TestConfiguration(
            @NonNull TestLintTask task,
            @NonNull LintClient client,
            @NonNull Project project,
            @Nullable Configuration parent) {
        super(client, project, parent);
        this.task = task;
    }

    @Override
    @NonNull
    protected Severity getDefaultSeverity(@NonNull Issue issue) {
        // In unit tests, include issues that are ignored by default
        Severity severity = super.getDefaultSeverity(issue);
        if (severity == Severity.IGNORE) {
            if (issue.getDefaultSeverity() != Severity.IGNORE) {
                return issue.getDefaultSeverity();
            }
            return Severity.WARNING;
        }
        return severity;
    }

    @Override
    public boolean isEnabled(@NonNull Issue issue) {
        if (issue == IssueRegistry.LINT_ERROR) {
            return !task.allowCompilationErrors;
        } else if (issue == IssueRegistry.PARSER_ERROR) {
            return !task.allowSystemErrors;
        }

        if (task.issueIds != null) {
            for (String id : task.issueIds) {
                if (issue.getId().equals(id)) {
                    return true;
                }
            }
        }

        return task.getCheckedIssues().contains(issue);
    }

    @Override
    public void ignore(@NonNull Context context, @NonNull Issue issue,
            @Nullable Location location, @NonNull String message) {
        fail("Not supported in tests.");
    }

    @Override
    public void setSeverity(@NonNull Issue issue, @Nullable Severity severity) {
        fail("Not supported in tests.");
    }
}