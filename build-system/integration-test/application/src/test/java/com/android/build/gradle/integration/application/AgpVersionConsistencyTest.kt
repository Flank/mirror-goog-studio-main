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

package com.android.build.gradle.integration.application

import com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION
import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

/** Test to make sure that build fails if different versions of AGP are applied across project. */
class AgpVersionConsistencyTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject(
                mapOf(
                    "androidLib1" to MinimalSubProject.lib("com.example.androidLib1"),
                    "androidLib2" to MinimalSubProject.lib("com.example.androidLib2")
                )
            )
        ).create()

    @Test
    fun testBuildConfiguration() {
        // do not add any buildscript dependencies, those are added per project
        project.buildFile.writeText(
            """
            apply from: "../commonHeader.gradle"
            apply from: "../commonLocalRepo.gradle"
        """.trimIndent()
        )
        addDirectClasspath("androidLib1", Version.ANDROID_GRADLE_PLUGIN_VERSION)
        addDirectClasspath("androidLib2", DIFFERENT_AGP)

        TestFileUtils.appendToFile(
            project.getSubproject("androidLib1").buildFile,
            """
                dependencies {
                    implementation project(":androidLib2")
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("androidLib2").buildFile,
            """
                android {
                    buildToolsVersion '$CURRENT_BUILD_TOOLS_VERSION'
                }
            """.trimIndent()
        )

        val result =
            project.executor()
                .withFailOnWarning(false)
                .expectFailure()
                .run("androidLib1:mergeDebugAssets")

        result.stderr.use {
            assertThat(it).contains(
                """
                    Using multiple versions of the Android Gradle plugin($DIFFERENT_AGP,
                     ${Version.ANDROID_GRADLE_PLUGIN_VERSION}) in the same build is not allowed.
                """.trimIndent()
            )
        }
    }

    private fun addDirectClasspath(name: String, agpVersion: String) {
        project.getSubproject(name).buildFile.also {
            val currentBuild = it.readText()
            it.writeText(
                """
                |buildscript {
                |  apply from: "../../commonLocalRepo.gradle", to: it
                |  dependencies {
                |    classpath 'com.android.tools.build:gradle:$agpVersion'
                |  }
                |}
                |$currentBuild
            """.trimMargin()
            )
        }
    }

    companion object {
        private const val DIFFERENT_AGP = "7.1.0"
    }
}
