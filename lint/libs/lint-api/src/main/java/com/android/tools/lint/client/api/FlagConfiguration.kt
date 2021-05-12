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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.guessGradleLocationForFile
import java.io.File

/**
 * Consult the lint.xml file, but override with the --enable
 * and --disable flags supplied on the command line (as well as any
 * other applicable flags)
 */
open class FlagConfiguration(configurations: ConfigurationHierarchy) : Configuration(configurations) {
    var associatedLocation: Location? = null

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
    open fun severityOverrides(): Set<String> = emptySet()

    override fun getDefinedSeverity(issue: Issue, source: Configuration, visibleDefault: Severity): Severity? {
        if (issue.suppressNames != null) {
            return getDefaultSeverity(issue, visibleDefault)
        }
        var severity = computeSeverity(issue, source, visibleDefault)
        if (fatalOnly()) {
            if (severity == null) {
                val configuredSeverity =
                    client.configurations.getDefinedSeverityWithoutOverride(source, issue, visibleDefault)
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
        if (isWarningsAsErrors() || isIgnoreWarnings()) {
            // If the severity is defined in this configuration, use it, otherwise
            // if we're in an override configuration, use the fallback value,
            // and finally if severity is not configured by either use the
            // default. This ensures that for example we set warningsAsErrors
            // in lintOptions, this will not turn issues ignored in a lint.xml
            // file back on, and similarly it ensures that issues not mentioned
            // in lint.xml will use the default.
            val impliedSeverity = severity
                ?: configurations.getDefinedSeverityWithoutOverride(source, issue, visibleDefault)
                ?: getDefaultSeverity(issue, visibleDefault)
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

    override fun getDefaultSeverity(issue: Issue, visibleDefault: Severity): Severity {
        return if (isCheckAllWarnings()) {
            if (neverEnabledImplicitly(issue)) {
                super.getDefaultSeverity(issue, visibleDefault)
            } else visibleDefault
        } else super.getDefaultSeverity(issue, visibleDefault)
    }

    private fun neverEnabledImplicitly(issue: Issue): Boolean {
        // Exclude the inter-procedural check from the "enable all warnings" flag;
        // it's much slower and still triggers various bugs in UAST that can affect
        // other checks.
        @Suppress("SpellCheckingInspection")
        return issue.id == "WrongThreadInterprocedural"
    }

    private fun computeSeverity(issue: Issue, source: Configuration, visibleDefault: Severity): Severity? {
        if (issue.suppressNames != null && !allowSuppress()) {
            return getDefaultSeverity(issue, visibleDefault)
        }

        val severity = parent?.getDefinedSeverity(issue, source, visibleDefault)

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
                return getVisibleSeverity(issue, severity, source, visibleDefault)
            } else if (category !== Category.LINT) {
                return Severity.IGNORE
            }
        }
        if (exactCategories != null) {
            if (exactCategories.contains(category) ||
                category.parent != null && exactCategories.contains(category.parent)
            ) {
                return getVisibleSeverity(issue, severity, source, visibleDefault)
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
            severity != Severity.IGNORE && isCheckAllWarnings() && !neverEnabledImplicitly(issue)
        ) {
            return getVisibleSeverity(issue, severity, source, visibleDefault)
        }

        return severity
    }

    /**
     * Returns the given severity, but if not visible, use the default.
     * If an override [severity] is configured (from an inherited
     * override configuration) use it, otherwise try to compute the
     * severity from the [source] configuration (without applying
     * overrides), and finally use default severity of the issue.
     */
    private fun getVisibleSeverity(
        issue: Issue,
        severity: Severity?,
        source: Configuration,
        visibleDefault: Severity
    ): Severity {
        val configuredSeverity = client.configurations.getDefinedSeverityWithoutOverride(source, issue, visibleDefault)
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
            visibleSeverity = visibleDefault
            if (visibleSeverity === Severity.IGNORE) {
                visibleSeverity = if (isWarningsAsErrors()) {
                    Severity.ERROR
                } else {
                    Severity.WARNING
                }
            }
        }
        return visibleSeverity
    }

    /**
     * Already validated this issue? We can encounter the same
     * configuration multiple times when searching up the parent tree.
     * (We can't skip calling the parent because the parent references
     * can change over time.)
     */
    private var validated = false

    override fun validateIssueIds(
        client: LintClient,
        driver: LintDriver,
        project: Project?,
        registry: IssueRegistry
    ) {
        parent?.validateIssueIds(client, driver, project, registry)
        if (validated) {
            return
        }
        validated = true

        validateIssueIds(client, driver, project, registry, disabledIds())
        validateIssueIds(client, driver, project, registry, enabledIds())
        validateIssueIds(client, driver, project, registry, severityOverrides())
        exactCheckedIds()?.let { validateIssueIds(client, driver, project, registry, it) }
    }

    protected fun validateIssueIds(
        client: LintClient,
        driver: LintDriver,
        project: Project?,
        registry: IssueRegistry,
        ids: Collection<String>
    ) {
        for (id in ids) {
            if (id == SdkConstants.SUPPRESS_ALL) {
                // builtin special "id" which means all id's
                continue
            }
            if (registry.getIssue(id) == null) {
                // You can also configure issues by categories; don't flag these
                if (registry.isCategoryName(id)) {
                    continue
                }
                reportNonExistingIssueId(client, driver, registry, project, id)
            }
        }
        parent?.validateIssueIds(client, driver, project, registry)
    }

    override fun addConfiguredIssues(
        targetMap: MutableMap<String, Severity>,
        registry: IssueRegistry,
        specificOnly: Boolean
    ) {
        parent?.addConfiguredIssues(targetMap, registry, specificOnly)

        val suppress = disabledIds()
        val disabledCategories: Set<Category>? = disabledCategories()
        val enabled = enabledIds()
        val exact = exactCheckedIds()
        val enabledCategories = enabledCategories()
        val exactCategories = exactCategories()

        for (issue in registry.issues) {
            if (issue.suppressNames != null && !allowSuppress()) {
                continue
            }

            // Issue not allowed to be suppressed?
            val id = issue.id
            if (suppress.contains(id)) {
                targetMap[issue.id] = Severity.IGNORE
                continue
            }

            if (disabledCategories != null) {
                val category = issue.category
                if (disabledCategories.contains(category) ||
                    category.parent != null && disabledCategories.contains(category.parent)
                ) {
                    targetMap[issue.id] = Severity.IGNORE
                    continue
                }
            }

            val manual = severityOverride(issue)
            if (manual != null) {
                targetMap[issue.id] = manual
                continue
            }

            val category = issue.category
            if (exact != null) {
                if (exact.contains(id)) {
                    targetMap[issue.id] = issue.defaultSeverity
                    continue
                } else if (category !== Category.LINT) {
                    targetMap[issue.id] = Severity.IGNORE
                    continue
                }
            }
            if (exactCategories != null) {
                if (exactCategories.contains(category) ||
                    category.parent != null && exactCategories.contains(category.parent)
                ) {
                    targetMap[issue.id] = issue.defaultSeverity
                    continue
                } else if (category !== Category.LINT ||
                    disabledCategories()?.contains(Category.LINT) == true
                ) {
                    targetMap[issue.id] = Severity.IGNORE
                    continue
                }
            }
            if (enabled.contains(id) ||
                enabledCategories != null && (
                    enabledCategories.contains(category) ||
                        category.parent != null && enabledCategories.contains(category.parent)
                    )
            ) {
                targetMap[issue.id] = issue.defaultSeverity
            }

            overrides?.addConfiguredIssues(targetMap, registry, specificOnly)
        }
    }

    override fun getLocalIssueConfigLocation(
        issue: String,
        specificOnly: Boolean,
        severityOnly: Boolean,
        source: Configuration
    ): Location? {
        if (associatedLocation != null) {
            val file = associatedLocation?.file
            if (file != null) {
                return guessGradleLocationForFile(configurations.client, file, issue)
            }
        }

        return parent?.getLocalIssueConfigLocation(issue, specificOnly, severityOnly, source)
            ?: associatedLocation
    }
}
