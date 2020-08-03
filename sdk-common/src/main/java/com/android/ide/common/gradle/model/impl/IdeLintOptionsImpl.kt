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
package com.android.ide.common.gradle.model.impl

import com.android.builder.model.LintOptions
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.repository.GradleVersion
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.Serializable

data class IdeLintOptionsImpl(
  override val baselineFile: File? = null,
  override val lintConfig: File? = null,
  override val severityOverrides: Map<String, Int>? = null,
  override val isCheckTestSources: Boolean = false,
  override val isCheckDependencies: Boolean = false,
  override val disable: Set<String> = mutableSetOf(), // instead of emptySet, because of ModelSerializationTest
  override val enable: Set<String> = mutableSetOf(),
  override val check: Set<String>? = null,
  override val isAbortOnError: Boolean = true,
  override val isAbsolutePaths: Boolean = true,
  override val isNoLines: Boolean = false,
  override val isQuiet: Boolean = false,
  override val isCheckAllWarnings: Boolean = false,
  override val isIgnoreWarnings: Boolean = false,
  override val isWarningsAsErrors: Boolean = false,
  override val isIgnoreTestSources: Boolean = false,
  override val isCheckGeneratedSources: Boolean = false,
  override val isCheckReleaseBuilds: Boolean = true,
  override val isExplainIssues: Boolean = true,
  override val isShowAll: Boolean = false,
  override val textReport: Boolean = false,
  override val textOutput: File? = null,
  override val htmlReport: Boolean = true,
  override val htmlOutput: File? = null,
  override val xmlReport: Boolean = true,
  override val xmlOutput: File? = null
) : Serializable, IdeLintOptions {

    companion object {
        private const val serialVersionUID = 2L

        @JvmStatic
        fun createFrom(
          options: LintOptions,
          modelVersion: GradleVersion?
        ): IdeLintOptionsImpl = IdeLintOptionsImpl(
          baselineFile = if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true))
              options.baselineFile
          else
              null,
          lintConfig = IdeModel.copyNewProperty<File>({ options.lintConfig }, null),
          severityOverrides = options.severityOverrides?.let { ImmutableMap.copyOf(it) },
          isCheckTestSources = modelVersion != null &&
                               modelVersion.isAtLeast(2, 4, 0) &&
                               options.isCheckTestSources,
          isCheckDependencies = IdeModel.copyNewProperty({ options.isCheckDependencies }, false)!!,
          disable = IdeModel.copy(options.disable)!!,
          enable = IdeModel.copy(options.enable)!!,
          check = options.check?.let { ImmutableSet.copyOf(it) },
          isAbortOnError = IdeModel.copyNewProperty({ options.isAbortOnError }, true)!!,
          isAbsolutePaths = IdeModel.copyNewProperty({ options.isAbsolutePaths }, true)!!,
          isNoLines = IdeModel.copyNewProperty({ options.isNoLines }, false)!!,
          isQuiet = IdeModel.copyNewProperty({ options.isQuiet }, false)!!,
          isCheckAllWarnings = IdeModel.copyNewProperty({ options.isCheckAllWarnings }, false)!!,
          isIgnoreWarnings = IdeModel.copyNewProperty({ options.isIgnoreWarnings }, false)!!,
          isWarningsAsErrors = IdeModel.copyNewProperty({ options.isWarningsAsErrors }, false)!!,
          isIgnoreTestSources = IdeModel.copyNewProperty({ options.isIgnoreTestSources }, false)!!,
          isCheckGeneratedSources = IdeModel.copyNewProperty({ options.isCheckGeneratedSources }, false)!!,
          isExplainIssues = IdeModel.copyNewProperty({ options.isExplainIssues }, true)!!,
          isShowAll = IdeModel.copyNewProperty({ options.isShowAll }, false)!!,
          textReport = IdeModel.copyNewProperty({ options.textReport }, false)!!,
          textOutput = IdeModel.copyNewProperty({ options.textOutput }, null),
          htmlReport = IdeModel.copyNewProperty({ options.htmlReport }, true)!!,
          htmlOutput = IdeModel.copyNewProperty({ options.htmlOutput }, null),
          xmlReport = IdeModel.copyNewProperty({ options.xmlReport }, true)!!,
          xmlOutput = IdeModel.copyNewProperty({ options.xmlOutput }, null),
          isCheckReleaseBuilds = IdeModel.copyNewProperty({ options.isCheckReleaseBuilds }, true)!!
        )
    }
}