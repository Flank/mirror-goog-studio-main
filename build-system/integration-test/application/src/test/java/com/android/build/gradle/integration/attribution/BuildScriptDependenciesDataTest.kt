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

package com.android.build.gradle.integration.attribution

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.StringOption
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildScriptDependenciesDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .withPluginManagementBlock(true)
            .create()

    @Test
    fun testKotlinPluginDependencyNotDetectedWhenNotAdded() {
        val attributionFileLocation = temporaryFolder.newFolder()

        project.executor()
                .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION,
                        attributionFileLocation.absolutePath)
                .run(":compileDebugJavaWithJavac")

        val originalAttributionData =
                AndroidGradlePluginAttributionData.load(attributionFileLocation)!!
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo).isNotEmpty()
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo)
                .doesNotContain("org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
    }

    @Test
    fun testKotlinPluginDependencyDetectedInBuildscriptDependencies() {
        val attributionFileLocation = temporaryFolder.newFolder()

        TestFileUtils.appendToFile(project.buildFile, """
buildscript {
    dependencies {
        // Provides the 'android-kotlin' build plugin for the app
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}"
    }
}
        """.trimIndent())

        project.executor()
                .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION,
                        attributionFileLocation.absolutePath)
                .run(":compileDebugJavaWithJavac")

        val originalAttributionData =
                AndroidGradlePluginAttributionData.load(attributionFileLocation)!!
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo).isNotEmpty()
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo)
                .contains("org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
    }

    @Test
    fun testKotlinPluginDependencyDetectedAppliedWithPluginsDsl() {
        val attributionFileLocation = temporaryFolder.newFolder()

        TestFileUtils.searchAndReplace(project.file("settings.gradle"),
                "eachPlugin {",
                """
eachPlugin {
            if(requested.id.namespace == "org.jetbrains.kotlin") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
            }
                """.trimIndent())
        project.file("build.gradle").delete()
        project.file("build.gradle").writeText("""

plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
apply from: "../commonHeader.gradle"
apply from: "../commonLocalRepo.gradle"
apply from: "../commonVersions.gradle", to: rootProject.ext

// Treat javac warnings as errors
tasks.withType(JavaCompile) {
    options.compilerArgs << "-Werror"
}

android {
    defaultConfig.minSdkVersion 14
    compileSdkVersion 30
    lintOptions.checkReleaseBuilds = false
    defaultConfig {
        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
    }
}
dependencies {
    androidTestImplementation "com.android.support.test:runner:${"$"}{project.testSupportLibVersion}"
    androidTestImplementation "com.android.support.test:rules:${"$"}{project.testSupportLibVersion}"
}

        """.trimIndent())

        project.executor()
                .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION,
                        attributionFileLocation.absolutePath)
                .run(":compileDebugJavaWithJavac")

        val originalAttributionData =
                AndroidGradlePluginAttributionData.load(attributionFileLocation)!!
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo).isNotEmpty()
        Truth.assertThat(originalAttributionData.buildscriptDependenciesInfo)
                .contains("org.jetbrains.kotlin:kotlin-gradle-plugin:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
    }

//TODO(b/181326671): check kotlin-gradle-plugin are detected when in buildSrc
}
