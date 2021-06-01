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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

class BasicModelV2Test: ModelComparator() {
    @get:Rule
    val project = builder()
        .fromTestProject("basic")
        // http://b/149978740 and http://b/146208910
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareAndroidProject(
            modelAction = { container.singleAndroidProject },
            goldenFile = "testProject"
        )

        with(result).compareAndroidDsl(
            modelAction = { container.singleAndroidDsl },
            goldenFile = "AndroidDsl"
        )

        with(result).compareVariantDependencies(
            modelAction = { container.singleVariantDependencies },
            goldenFile = "testDep"
        )
    }
}
