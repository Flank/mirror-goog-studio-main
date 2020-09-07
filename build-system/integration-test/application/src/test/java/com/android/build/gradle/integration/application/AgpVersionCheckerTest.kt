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

import com.android.Version
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import org.junit.Rule
import org.junit.Test

/** Test to make sure that build fails if different versions of AGP are applied across project. */
class AgpVersionCheckerTest {
    @JvmField
    @Rule
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
        addDirectClasspath("androidLib2", "3.5.0")
        // we are using AGP 3.5.0, so disable --warning-mode=fail
        val result = project.executor().withFailOnWarning(false).expectFailure().run("help")

        result.stderr.use {
            assertThat(it).contains(
                """
   > Using multiple versions of the Android Gradle plugin in the same build is not allowed.
     - Project `${project.getSubproject("androidLib1").projectDir.canonicalPath}` is using version `${Version.ANDROID_GRADLE_PLUGIN_VERSION}`
     - Project `${project.getSubproject("androidLib2").projectDir.canonicalPath}` is using version `3.5.0`
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
}
