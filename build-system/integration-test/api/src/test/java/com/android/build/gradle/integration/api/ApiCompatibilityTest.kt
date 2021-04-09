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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import org.junit.Rule
import org.junit.Test

/** Integration test to ensure that plugin binary compatibility of AGP APIs is preserved. */
class ApiCompatibilityTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("apiBinaryCompatibility")
            // Disabled because of https://youtrack.jetbrains.com/issue/KT-43605
            // and https://github.com/gradle/gradle/issues/15900
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .create()

    @Test
    fun binaryCompatibilityTest() {
        val result = project.executor().run(":lib:examplePluginTask")
        assertThat(result.stdout).contains("Custom task ran OK")
    }

}
