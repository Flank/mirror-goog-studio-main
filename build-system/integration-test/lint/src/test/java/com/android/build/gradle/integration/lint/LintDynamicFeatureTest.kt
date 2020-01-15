/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class LintDynamicFeatureTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .create()

    @Test
    fun runLint() {
        project.execute("clean", ":feature1:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        project.execute("clean", ":feature2:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()
    }
}
