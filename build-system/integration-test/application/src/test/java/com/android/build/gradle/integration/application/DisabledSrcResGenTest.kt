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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * tests disabling build features that are normally on by default
 */
class DisabledSrcResGenTest {
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
    fun `test disabling AIDL via gradle-properties`() {
        checkViaGradleProperties(BooleanOption.BUILD_FEATURE_AIDL, "compileDebugAidl")
    }

    @Test
    fun `test disabling AIDL via build-gradle`() {
        checkViaBuildFile("aidl", "compileDebugAidl")
    }

    @Test
    fun `test disabling Renderscript via gradle-properties`() {
        checkViaGradleProperties(BooleanOption.BUILD_FEATURE_RENDERSCRIPT, "compileDebugRenderscript")
    }

    @Test
    fun `test disabling Renderscript via build-gradle`() {
        checkViaBuildFile("renderScript", "compileDebugRenderscript")
    }

    @Test
    fun `test disabling Res Values via gradle-properties`() {
        checkViaGradleProperties(BooleanOption.BUILD_FEATURE_RESVALUES, "generateDebugResValues")
    }

    @Test
    fun `test disabling Res Values via build-gradle`() {
        checkViaBuildFile("resValues", "generateDebugResValues")
    }

    @Test
    fun `test disabling shaders via gradle-properties`() {
        checkViaGradleProperties(BooleanOption.BUILD_FEATURE_SHADERS, "compileDebugShaders")
    }

    @Test
    fun `test disabling shaders via build-gradle`() {
        checkViaBuildFile("shaders", "compileDebugShaders")
    }

    @Test
    fun `check disabling Res Values triggers validation errors`() {
        appProject.buildFile.appendText("""
            android {
                buildFeatures.resValues = false
                defaultConfig {
                   resValue "string", "foo", "foo"
                }
            }
        """.trimIndent())

        val failure = rootProject.executor().expectFailure().run("app:assembleDebug")
        failure.stderr.use {
            ScannerSubject.assertThat(it)
                .contains("defaultConfig contains custom resource values, but the feature is disabled.")
        }
    }

    private fun checkViaGradleProperties(
        booleanOption: BooleanOption,
        taskName: String
    ) {
        // first do a build without the disabling to check the task exist in this case
        // build both apk and aar
        var result = rootProject.executor().run("assembleDebug")
        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNotNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNotNull()

        // then change the project and run again
        rootProject.gradlePropertiesFile
            .appendText(
                """
    ${booleanOption.propertyName}=false"""
            )

        result = rootProject.executor().run("assembleDebug")

        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNull()
    }

    private fun checkViaBuildFile(
        propertyName: String,
        taskName: String
    ) {
        // first do a build without the disabling to check the task exist in this case
        // build both apk and aar
        var result = rootProject.executor().run("assembleDebug")
        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNotNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNotNull()

        // then change the project and run again
        appProject.buildFile.appendText("android.buildFeatures.$propertyName = false")
        libProject.buildFile.appendText("android.buildFeatures.$propertyName = false")

        result = rootProject.executor().run("assembleDebug")

        Truth.assertThat(result.findTask(":app:$taskName")).named(":app:$taskName").isNull()
        Truth.assertThat(result.findTask(":lib:$taskName")).named(":lib:$taskName").isNull()
    }
}
