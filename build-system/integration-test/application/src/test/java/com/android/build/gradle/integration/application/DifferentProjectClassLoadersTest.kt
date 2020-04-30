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
import org.junit.Rule
import org.junit.Test

class DifferentProjectClassLoadersTest {
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

    /** Regression test for b/154388196. */
    @Test
    fun testCleanBuild() {
        // do not add any buildscript dependencies, those are added per project
        project.buildFile.writeText(
            """
            apply from: "../commonHeader.gradle"
            apply from: "../commonLocalRepo.gradle"
        """.trimIndent()
        )
        addDirectClasspath("androidLib1")
        addDirectClasspath("androidLib2")
        project.executor().run("assembleDebug")

    }

    private fun addDirectClasspath(name: String) {
        project.getSubproject(name).buildFile.also {
            val currentBuild = it.readText()
            it.writeText(
                """
                |buildscript {
                |  apply from: "../../commonBuildScript.gradle"
                |  dependencies {
                |    classpath 'com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}'
                |    classpath files('non_existent_$name.jar')
                |  }
                |}
                |$currentBuild
            """.trimMargin()
            )
        }
    }
}