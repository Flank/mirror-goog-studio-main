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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests enabling build features that are normally off by default. Similar to
 * [DisabledSrcResGenTest].
 */
class EnabledSrcResGenTest {
    @get:Rule
    val rootProject = GradleTestProject.builder().fromTestProject("applibtest").create()

    private lateinit var appProject: GradleTestProject
    private lateinit var libProject: GradleTestProject

    @Before
    fun setUp() {
        appProject = rootProject.getSubproject(":app")
        libProject = rootProject.getSubproject(":lib")
    }

    @Test
    fun `test disabling Renderscript via gradle-properties`() {
        checkViaGradleProperties(BooleanOption.BUILD_FEATURE_RENDERSCRIPT, "compileDebugRenderscript")
    }

    @Test
    fun `test disabling Renderscript via build-gradle`() {
        checkViaBuildFile("renderScript", "compileDebugRenderscript")
    }

    private fun checkViaGradleProperties(
        booleanOption: BooleanOption,
        taskName: String
    ) {
        // first do a build without enabling the feature to check the tasks do not exist in this
        // case (build both the APK and AAR).
        var result = rootProject.executor().run("assembleDebug")
        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNull()

        // then change the project and run again
        rootProject.gradlePropertiesFile
            .appendText(
                """
    ${booleanOption.propertyName}=true"""
            )

        result = rootProject.executor().run("assembleDebug")

        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNotNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNotNull()
    }

    private fun checkViaBuildFile(
        propertyName: String,
        taskName: String
    ) {
        // first do a build without enabling the feature to check the tasks do not exist in this
        // case (build both the APK and AAR).
        var result = rootProject.executor().run("assembleDebug")
        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNull()

        // then change the project and run again
        appProject.buildFile.appendText("android.buildFeatures.$propertyName = true")
        libProject.buildFile.appendText("android.buildFeatures.$propertyName = true")

        result = rootProject.executor().run("assembleDebug")

        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNotNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNotNull()
    }
}
