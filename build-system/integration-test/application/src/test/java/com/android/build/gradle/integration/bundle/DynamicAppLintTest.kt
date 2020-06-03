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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.FileSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DynamicAppLintTest {
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        // b/158092986
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Test
    fun testUnusedResourcesInFeatureModules() {
        project.execute("clean", "lint")

        val file = project.file("app/lint-results.txt")
        FileSubject.assertThat(file).containsAllOf(
            "The resource R.string.unused_from_feature1 appears to be unused",
            "The resource R.string.feature1_title appears to be unused",
            "The resource R.string.feature2_title appears to be unused",
            "The resource R.string.unused_from_app appears to be unused"
        )

        FileSubject.assertThat(file).doesNotContain(
            "The resource R.string.used_from_app appears to be unused"
        )
        FileSubject.assertThat(file).doesNotContain(
            "The resource R.string.used_from_feature1 appears to be unused"
        )
        FileSubject.assertThat(file).doesNotContain(
            "The resource R.string.used_from_feature2 appears to be unused"
        )
    }
}