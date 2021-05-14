/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.internal.dsl.LintOptions
import java.io.File

class LintOptionsImpl: LintOptions {
    constructor(
        disable: Set<String>,
        enable: Set<String> ,
        checkOnly: Set<String>,
        lintConfig: File?,
        textReport: Boolean,
        textOutput: File?,
        htmlReport: Boolean,
        htmlOutput: File?,
        xmlReport: Boolean,
        xmlOutput: File?,
        sarifReport: Boolean,
        sarifOutput: File?,
        abortOnError: Boolean,
        absolutePaths: Boolean,
        noLines: Boolean,
        quiet: Boolean,
        checkAllWarnings: Boolean,
        ignoreWarnings: Boolean,
        warningsAsErrors: Boolean,
        showAll: Boolean,
        explainIssues: Boolean,
        checkReleaseBuilds: Boolean,
        checkTestSources: Boolean,
        ignoreTestSources: Boolean,
        checkGeneratedSources: Boolean,
        checkDependencies: Boolean,
        baselineFile: File?,
        severityOverrides: Map<String, Int>?
    ): super(null) {
        this.disable.addAll(disable)
        this.enable.addAll(enable)
        this.checkOnly.addAll(checkOnly)
        this.lintConfig = lintConfig
        this.textReport = textReport
        this.textOutput = textOutput
        this.htmlReport = htmlReport
        this.htmlOutput = htmlOutput
        this.xmlReport = xmlReport
        this.xmlOutput = xmlOutput
        this.sarifReport = sarifReport
        this.sarifOutput = sarifOutput
        this.isAbortOnError = abortOnError
        this.isAbsolutePaths = absolutePaths
        this.isNoLines = noLines
        this.isQuiet = quiet
        this.isCheckAllWarnings = checkAllWarnings
        this.isIgnoreWarnings = ignoreWarnings
        this.isWarningsAsErrors = warningsAsErrors
        this.isShowAll = showAll
        this.isExplainIssues = explainIssues
        this.isCheckReleaseBuilds = checkReleaseBuilds
        this.isCheckTestSources = checkTestSources
        this.isIgnoreTestSources = ignoreTestSources
        this.isCheckGeneratedSources = checkGeneratedSources
        this.isCheckDependencies = checkDependencies
        this.baselineFile = baselineFile

        severityOverrides?.let {
            severities.putAll(it)
        }
    }
}
