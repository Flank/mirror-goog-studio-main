/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("SyncOptions")
package com.android.tools.lint

import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.detector.api.Category.Companion.getCategory
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelModule
import java.io.File
import com.android.tools.lint.model.LintModelSeverity as ModelSeverity

// Operations related to syncing LintOptions to lint's internal state

fun syncTo(
    project: LintModelModule,
    client: LintCliClient,
    flags: LintCliFlags,
    variantName: String?,
    reportsDir: File?,
    report: Boolean
) {
    val options = project.lintOptions
    val disabled = options.disable
    if (disabled.isNotEmpty()) {
        for (id in disabled) {
            val category = getCategory(id)
            if (category != null) {
                // Disabling a whole category
                flags.addDisabledCategory(category)
            } else {
                flags.suppressedIds.add(id)
            }
        }
    }
    val enabled = options.enable
    if (enabled.isNotEmpty()) {
        for (id in enabled) {
            val category = getCategory(id)
            if (category != null) {
                // Enabling a whole category
                flags.addEnabledCategory(category)
            } else {
                flags.enabledIds.add(id)
            }
        }
    }
    val check = options.check
    if (check != null && check.isNotEmpty()) {
        for (id in check) {
            val category = getCategory(id)
            if (category != null) {
                // Checking a whole category
                flags.addExactCategory(category)
            } else {
                flags.addExactId(id)
            }
        }
    }
    flags.isSetExitCode = options.abortOnError
    flags.isFullPath = options.absolutePaths
    flags.isShowSourceLines = !options.noLines
    flags.isQuiet = options.quiet
    flags.isCheckAllWarnings = options.checkAllWarnings
    flags.isIgnoreWarnings = options.ignoreWarnings
    flags.isWarningsAsErrors = options.warningsAsErrors
    flags.isCheckTestSources = options.checkTestSources
    flags.isIgnoreTestSources = options.ignoreTestSources
    flags.isCheckGeneratedSources = options.checkGeneratedSources
    flags.isCheckDependencies = options.checkDependencies
    flags.isShowEverything = options.showAll
    flags.lintConfig = options.lintConfig
    flags.isExplainIssues = options.explainIssues
    flags.baselineFile = options.baselineFile
    val severityOverrides = options.severityOverrides
    if (severityOverrides != null) {
        val map: MutableMap<String, Severity> = mutableMapOf()
        val registry = BuiltinIssueRegistry()
        for ((id, severityInt) in severityOverrides) {
            val issue = registry.getIssue(id)
            val severity = issue?.let { getSeverity(it, severityInt) } ?: Severity.WARNING
            val category = getCategory(id)
            if (category != null) {
                for (current in registry.issues) {
                    val currentCategory = current.category
                    if (currentCategory === category || currentCategory.parent === category) {
                        map[current.id] = severity
                    }
                }
            } else {
                map[id] = severity
            }
        }
        flags.severityOverrides = map
    } else {
        flags.severityOverrides = emptyMap()
    }
}

private fun getSeverity(
    issue: Issue,
    modelSeverity: ModelSeverity
): Severity {
    return when (modelSeverity) {
        ModelSeverity.FATAL -> Severity.FATAL
        ModelSeverity.ERROR -> Severity.ERROR
        ModelSeverity.WARNING -> Severity.WARNING
        ModelSeverity.INFORMATIONAL -> Severity.INFORMATIONAL
        ModelSeverity.IGNORE -> Severity.IGNORE
        ModelSeverity.DEFAULT_ENABLED -> issue.defaultSeverity
        else -> Severity.WARNING
    }
}
