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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import java.io.File

/**
 * Consult the lint.xml file, but override with the --enable and --disable flags supplied on the
 * command line (as well as any other applicable flags)
 */
open class FlagConfiguration : LintXmlConfiguration {
    constructor(
        client: LintClient,
        project: Project
    ) : super(client, project)

    constructor(
        client: LintClient,
        lintFile: File,
        dir: File
    ) : super(client, lintFile, dir, true)

    open fun fatalOnly(): Boolean = false
    open fun isWarningsAsErrors(): Boolean = false
    open fun isIgnoreWarnings(): Boolean = false
    open fun isCheckAllWarnings(): Boolean = false
    open fun allowSuppress(): Boolean = true
    open fun disabledIds(): Set<String> = emptySet()
    open fun enabledIds(): Set<String> = emptySet()
    open fun exactCheckedIds(): Set<String>? = null
    open fun disabledCategories(): Set<Category>? = null
    open fun enabledCategories(): Set<Category>? = null
    open fun exactCategories(): Set<Category>? = null
    open fun severityOverride(issue: Issue): Severity? = null

    override fun getSeverity(issue: Issue): Severity {
        if (issue.suppressNames != null) {
            return getDefaultSeverity(issue)
        }
        var severity = computeSeverity(issue)
        if (fatalOnly() && severity !== Severity.FATAL) {
            return Severity.IGNORE
        }
        if (isWarningsAsErrors() && severity === Severity.WARNING) {
            if (issue === IssueRegistry.BASELINE) {
                // Don't promote the baseline informational issue
                // (number of issues promoted) to error
                return severity
            }
            severity = Severity.ERROR
        }
        if (isIgnoreWarnings() && severity === Severity.WARNING) {
            severity = Severity.IGNORE
        }
        return severity
    }

    override fun getDefaultSeverity(issue: Issue): Severity {
        return if (isCheckAllWarnings()) {
            // Exclude the inter-procedural check from the "enable all warnings" flag;
            // it's much slower and still triggers various bugs in UAST that can affect
            // other checks.
            @Suppress("SpellCheckingInspection")
            if (issue.id == "WrongThreadInterprocedural") {
                super.getDefaultSeverity(issue)
            } else issue.defaultSeverity
        } else super.getDefaultSeverity(issue)
    }

    private fun computeSeverity(issue: Issue): Severity {
        if (issue.suppressNames != null && !allowSuppress()) {
            return getDefaultSeverity(issue)
        }
        val severity = super.getSeverity(issue)
        // Issue not allowed to be suppressed?
        val id = issue.id
        val suppress = disabledIds()
        if (suppress.contains(id)) {
            return Severity.IGNORE
        }
        val disabledCategories: Set<Category>? = disabledCategories()
        if (disabledCategories != null) {
            val category = issue.category
            if (disabledCategories.contains(category) ||
                category.parent != null && disabledCategories.contains(category.parent)
            ) {
                return Severity.IGNORE
            }
        }
        val manual = severityOverride(issue)
        if (manual != null) {
            return manual
        }
        val enabled = enabledIds()
        val exact = exactCheckedIds()
        val enabledCategories = enabledCategories()
        val exactCategories = exactCategories()
        val category = issue.category
        if (exact != null) {
            if (exact.contains(id)) {
                return getVisibleSeverity(issue, severity)
            } else if (category !== Category.LINT) {
                return Severity.IGNORE
            }
        }
        if (exactCategories != null) {
            if (exactCategories.contains(category) ||
                category.parent != null && exactCategories.contains(category.parent)
            ) {
                return getVisibleSeverity(issue, severity)
            } else if (category !== Category.LINT ||
                disabledCategories()?.contains(Category.LINT) == true
            ) {
                return Severity.IGNORE
            }
        }
        return if (enabled.contains(id) ||
            enabledCategories != null && (
                enabledCategories.contains(category) ||
                    category.parent != null && enabledCategories.contains(category.parent)
                )
        ) {
            getVisibleSeverity(issue, severity)
        } else severity
    }

    /** Returns the given severity, but if not visible, use the default  */
    private fun getVisibleSeverity(issue: Issue, severity: Severity): Severity {
        // Overriding default
        // Detectors shouldn't be returning ignore as a default severity,
        // but in case they do, force it up to warning here to ensure that
        // it's run
        var visibleSeverity = severity
        if (visibleSeverity === Severity.IGNORE) {
            visibleSeverity = issue.defaultSeverity
            if (visibleSeverity === Severity.IGNORE) {
                visibleSeverity = Severity.WARNING
            }
        }
        return visibleSeverity
    }
}
