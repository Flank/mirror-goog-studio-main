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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.options.BooleanOption

enum class LintInvocationType {
    REFLECTIVE_LINT_RUNNER {
        override fun addGradleProperties(projectBuilder: GradleTestProjectBuilder, maxProblems: Int): GradleTestProjectBuilder =
            projectBuilder
                .addGradleProperties(BooleanOption.USE_NEW_LINT_MODEL.propertyName + "=false")
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
    },
    NEW_LINT_MODEL {
        override fun addGradleProperties(projectBuilder: GradleTestProjectBuilder, maxProblems: Int): GradleTestProjectBuilder =
            projectBuilder
                .withConfigurationCaching(
                    if (maxProblems == 0)
                        BaseGradleExecutor.ConfigurationCaching.ON else BaseGradleExecutor.ConfigurationCaching.WARN
                )
                .addGradleProperties("org.gradle.unsafe.configuration-cache.max-problems=$maxProblems")
    },
    ;

    protected abstract fun addGradleProperties(
        projectBuilder: GradleTestProjectBuilder,
        maxProblems: Int
    ): GradleTestProjectBuilder

    @JvmOverloads
    fun testProjectBuilder(maxProblems: Int = 0) =
        addGradleProperties(GradleTestProject.builder(), maxProblems)

}
