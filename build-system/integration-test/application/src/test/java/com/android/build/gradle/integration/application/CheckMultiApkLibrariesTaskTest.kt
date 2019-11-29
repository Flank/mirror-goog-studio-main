/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test checking for error when 2 APKs in multi-APK project package the same library
 */
class CheckMultiApkLibrariesTaskTest {

    private val lib = MinimalSubProject.lib("com.example.lib")

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
            """
                            android {
                                dynamicFeatures = [':otherFeature1', ':otherFeature2']
                                defaultConfig.minSdkVersion 14
                            }
                            """)

    private val otherFeature1 = createFeatureSplit("com.example.otherFeature1")

    private val otherFeature2 = createFeatureSplit("com.example.otherFeature2")

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("android.defaultConfig.minSdkVersion 14")

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":baseModule", baseModule)
            .subproject(":otherFeature1", otherFeature1)
            .subproject(":otherFeature2", otherFeature2)
            .dependency(otherFeature1, lib)
            .dependency(otherFeature2, lib)
            .dependency(otherFeature1, baseModule)
            .dependency(otherFeature2, baseModule)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun `test library collision yields error`() {
        val result = project.executor().expectFailure().run("assembleDebug")

        assertThat(result.failureMessage).contains(
            "[:otherFeature1, :otherFeature2] all package the same library [:lib].")
        assertThat(result.failureMessage).contains(
            "[:otherFeature1, :otherFeature2] all package the same library [com.android.support:support-core-utils].")
        assertThat(result.failureMessage).contains(
            "Multiple APKs packaging the same library can cause runtime errors."
        )
    }

    private fun createFeatureSplit(packageName: String) = MinimalSubProject.dynamicFeature(packageName)
        .appendToBuild(
                """
                    android.defaultConfig.minSdkVersion 14
                    dependencies {
                        implementation 'com.android.support:support-core-utils:' + rootProject.supportLibVersion
                    }
                    """)
}
