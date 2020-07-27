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
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelLintOptions
import com.android.tools.lint.model.LintModelSeverity
import java.io.File

/**
 * Consult the lint.xml file, but override with the suitable flags configured via
 * [LintModelLintOptions].
 */
open class LintOptionsConfiguration(
    client: LintClient,
    lintFile: File,
    dir: File,
    private val lintOptions: LintModelLintOptions,
    private val fatalOnly: Boolean = false
) : FlagConfiguration(client, lintFile, dir) {

    private var disabledIds: Set<String>
    private var disabledCategories: Set<Category>
    private var enabledIds: Set<String>
    private var enabledCategories: Set<Category>
    private var exactIds: Set<String>?
    private var exactCategories: Set<Category>?
    init {
        val disable = lintOptions.disable
        if (disable.isEmpty()) {
            disabledIds = emptySet()
            disabledCategories = emptySet()
        } else {
            disabledIds = mutableSetOf()
            disabledCategories = mutableSetOf()
            partition(
                disable, disabledIds as MutableSet<String>,
                disabledCategories as MutableSet<Category>
            )
        }
        val enable = lintOptions.enable
        if (enable.isEmpty()) {
            enabledIds = emptySet()
            enabledCategories = emptySet()
        } else {
            enabledIds = mutableSetOf()
            enabledCategories = mutableSetOf()
            partition(
                enable, enabledIds as MutableSet<String>,
                enabledCategories as MutableSet<Category>
            )
        }
        val check = lintOptions.check
        if (check == null || check.isEmpty()) {
            exactIds = null
            exactCategories = null
        } else {
            exactIds = mutableSetOf()
            exactCategories = mutableSetOf()
            partition(
                check, exactIds as MutableSet<String>,
                exactCategories as MutableSet<Category>
            )
        }
    }

    /** Split a series of strings into categories and issue ids */
    private fun partition(
        candidates: Collection<String>,
        ids: MutableSet<String>,
        categories: MutableSet<Category>
    ) {
        for (id in candidates) {
            val category = Category.getCategory(id)
            if (category != null) {
                categories.add(category)
            } else {
                ids.add(id)
            }
        }
    }

    override fun fatalOnly(): Boolean = fatalOnly
    override fun isWarningsAsErrors(): Boolean = lintOptions.warningsAsErrors
    override fun isIgnoreWarnings(): Boolean = lintOptions.ignoreWarnings
    override fun isCheckAllWarnings(): Boolean = lintOptions.checkAllWarnings
    override fun disabledIds(): Set<String> = disabledIds
    override fun enabledIds(): Set<String> = enabledIds
    override fun exactCheckedIds(): Set<String>? = exactIds
    override fun disabledCategories(): Set<Category>? = disabledCategories
    override fun enabledCategories(): Set<Category>? = enabledCategories
    override fun exactCategories(): Set<Category>? = exactCategories
    override fun severityOverride(issue: Issue): Severity? {
        return when (lintOptions.severityOverrides?.get(issue.id)) {
            LintModelSeverity.FATAL -> Severity.FATAL
            LintModelSeverity.ERROR -> Severity.ERROR
            LintModelSeverity.WARNING -> Severity.WARNING
            LintModelSeverity.INFORMATIONAL -> Severity.INFORMATIONAL
            LintModelSeverity.IGNORE -> Severity.IGNORE
            LintModelSeverity.DEFAULT_ENABLED -> issue.defaultSeverity
            else -> null
        }
    }
    // Not currently settable via LintOptions
    override fun allowSuppress(): Boolean = false
}
