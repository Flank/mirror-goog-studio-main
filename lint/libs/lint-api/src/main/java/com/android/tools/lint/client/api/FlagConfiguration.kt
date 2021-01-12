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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import java.io.File

/**
 * Consult the lint.xml file, but override with the --enable and --disable flags supplied on the
 * command line (as well as any other applicable flags)
 */
open class FlagConfiguration(configurations: ConfigurationHierarchy) : Configuration(configurations) {

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

    override fun getDefinedSeverity(issue: Issue, source: Configuration): Severity? {
        if (issue.suppressNames != null) {
            return getDefaultSeverity(issue)
        }
        var severity = computeSeverity(issue, source)
        if (fatalOnly()) {
            if (severity == null) {
                val configuredSeverity =
                    client.configurations.getDefinedSeverityWithoutOverride(source, issue)
                if (configuredSeverity != null && configuredSeverity == Severity.FATAL) {
                    return configuredSeverity
                } else if (configuredSeverity != null) {
                    return Severity.IGNORE
                } else if (issue.defaultSeverity !== Severity.FATAL) {
                    return Severity.IGNORE
                }
            } else if (severity !== Severity.FATAL) {
                return Severity.IGNORE
            }
        }

        if (fatalOnly() && (
            severity == null && issue.defaultSeverity !== Severity.FATAL ||
                severity != null && severity !== Severity.FATAL
            )
        ) {
            return Severity.IGNORE
        }
        val impliedSeverity = severity ?: getDefaultSeverity(issue)
        if (isWarningsAsErrors() && impliedSeverity === Severity.WARNING) {
            if (issue === IssueRegistry.BASELINE) {
                // Don't promote the baseline informational issue
                // (number of issues promoted) to error
                return severity
            }
            severity = Severity.ERROR
        }
        if (isIgnoreWarnings() && impliedSeverity === Severity.WARNING) {
            severity = Severity.IGNORE
        }
        return severity
    }

    private fun unsupported(): Nothing {
        error("This method should not be invoked on a synthetic (non XML) configuration")
    }

    override fun ignore(context: Context, issue: Issue, location: Location?, message: String) {
        unsupported()
    }

    override fun ignore(issue: Issue, file: File) {
        unsupported()
    }

    override fun ignore(issueId: String, file: File) {
        unsupported()
    }

    override fun setSeverity(issue: Issue, severity: Severity?) {
        unsupported()
    }

    override var baselineFile: File? = parent?.baselineFile

    override fun getDefaultSeverity(issue: Issue): Severity {
        return if (isCheckAllWarnings()) {
            if (neverEnabledImplicitly(issue)) {
                super.getDefaultSeverity(issue)
            } else issue.defaultSeverity
        } else super.getDefaultSeverity(issue)
    }

    private fun neverEnabledImplicitly(issue: Issue): Boolean {
        // Exclude the inter-procedural check from the "enable all warnings" flag;
        // it's much slower and still triggers various bugs in UAST that can affect
        // other checks.
        @Suppress("SpellCheckingInspection")
        return issue.id == "WrongThreadInterprocedural"
    }

    private fun computeSeverity(issue: Issue, source: Configuration): Severity? {
        if (issue.suppressNames != null && !allowSuppress()) {
            return getDefaultSeverity(issue)
        }

        val severity = parent?.getDefinedSeverity(issue, source)

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
                return getVisibleSeverity(issue, severity, source)
            } else if (category !== Category.LINT) {
                return Severity.IGNORE
            }
        }
        if (exactCategories != null) {
            if (exactCategories.contains(category) ||
                category.parent != null && exactCategories.contains(category.parent)
            ) {
                return getVisibleSeverity(issue, severity, source)
            } else if (category !== Category.LINT ||
                disabledCategories()?.contains(Category.LINT) == true
            ) {
                return Severity.IGNORE
            }
        }
        if (enabled.contains(id) ||
            enabledCategories != null && (
                enabledCategories.contains(category) ||
                    category.parent != null && enabledCategories.contains(category.parent)
                ) ||
            isCheckAllWarnings() && !neverEnabledImplicitly(issue)
        ) {
            return getVisibleSeverity(issue, severity, source)
        }

        return severity
    }

    /**
     * Returns the given severity, but if not visible, use the default. If an override
     * [severity] is configured (from an inherited override configuration) use it, otherwise
     * try to compute the severity from the [source] configuration (without applying overrides),
     * and finally use default severity of the issue.
     */
    private fun getVisibleSeverity(
        issue: Issue,
        severity: Severity?,
        source: Configuration
    ): Severity {
        val configuredSeverity = client.configurations.getDefinedSeverityWithoutOverride(source, issue)
        if (configuredSeverity != null && configuredSeverity != Severity.IGNORE) {
            if (configuredSeverity == Severity.WARNING && isWarningsAsErrors()) {
                return Severity.ERROR
            }
            return configuredSeverity
        }

        // Overriding default
        // Detectors shouldn't be returning ignore as a default severity,
        // but in case they do, force it up to warning here to ensure that
        // it's run
        var visibleSeverity = severity
        if (visibleSeverity == null || visibleSeverity === Severity.IGNORE) {
            visibleSeverity = issue.defaultSeverity
            if (visibleSeverity === Severity.IGNORE) {
                if (isWarningsAsErrors()) {
                    visibleSeverity = Severity.ERROR
                } else {
                    visibleSeverity = Severity.WARNING
                }
            }
        }
        return visibleSeverity
    }
}
