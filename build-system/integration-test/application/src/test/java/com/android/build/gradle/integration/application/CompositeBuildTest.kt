/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.EmptyAndroidTestApp
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.ZipFileSubject
import com.google.common.collect.ImmutableMap
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

/** Integration test for composite build.  */
class CompositeBuildTest {
    @get:Rule
    val app = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withName("app")
        .create()

    @get:Rule
    val lib = builder()
        .fromTestApp(EmptyAndroidTestApp())
        .withName("lib")
        .create()

    @get:Rule
    val androidLib = builder()
        .fromTestApp(
            MultiModuleTestProject(
                ImmutableMap.of(
                    "androidLib1",
                    EmptyAndroidTestApp("com.example.androidLib1"),
                    "androidLib2",
                    EmptyAndroidTestApp("com.example.androidLib2")
                )
            )
        )
        .withName("androidLib")
        .withDependencyChecker(false)
        .create()

    @Before
    fun setUp() {
        app.file("settings.gradle").writeText(
            """
includeBuild('../lib') {
    dependencySubstitution {
        substitute module('com.example:lib') with project(':')
    }
}
includeBuild('../androidLib') {
    dependencySubstitution {
        substitute module('com.example:androidLib1') with project(':androidLib1')
        substitute module('com.example:androidLib2') with project(':androidLib2')
    }
}
"""
        )

        TestFileUtils.appendToFile(
            app.buildFile,
            """
android {
    buildTypes.debug.testCoverageEnabled true
}
dependencies {
    api 'com.example:lib'
    api 'com.example:androidLib1'
    api 'com.example:androidLib2'
}
"""
        )

        // lib is just an empty project.
        lib.file("settings.gradle").createNewFile()
        TestFileUtils.appendToFile(lib.buildFile, "apply plugin: 'java'\n")

        // b/62428620 - Add a fake file to the classpath to cause androidLib project to be loaded
        // in a different classloader.  This used to cause problem in composite build in older
        // Gradle plugin.
        TestFileUtils.searchAndReplace(
            androidLib.buildFile,
            "buildscript { apply from: \"../commonBuildScript.gradle\" }",
            """
buildscript {    apply from: "../commonBuildScript.gradle"
    dependencies {
        classpath files('fake')
    }
}
"""
        )

        // androidLib1 and androidLib2 are empty aar project.
        androidLib.file("settings.gradle").writeText(
            """
include 'androidLib1'
include 'androidLib2'
"""
        )

        val androidLibBuildGradle = """
apply plugin: 'com.android.library'

android {
    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
}
"""
        TestFileUtils.appendToFile(
            androidLib.getSubproject(":androidLib1").buildFile, androidLibBuildGradle
        )
        TestFileUtils.appendToFile(
            androidLib.getSubproject(":androidLib2").buildFile, androidLibBuildGradle
        )
    }

    @Test
    fun assembleDebug() {
        app.execute(":assembleDebug")
        ZipFileSubject.assertThat(
            app.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        ) { it.exists() }
    }

    @Test
    fun assembleDebugWithConfigureOnDemand() {
        app.executor().withArgument("--configure-on-demand").run(":assembleDebug")
        ZipFileSubject.assertThat(
            app.getApkAsFile(GradleTestProject.ApkType.DEBUG)
        ) {  it.exists() }
    }

    @Ignore("b/195109976")
    @Test
    fun checkDifferentPluginVersionsCauseFailure() {
        TestFileUtils.appendToFile(
            androidLib.buildFile,
            """
buildscript {
  dependencies {
    classpath('com.android.tools.build:gradle:3.5.0') { force=true }
  }
}
"""
        )
        val result = app.executor().withFailOnWarning(false).expectFailure().run("help")
        result.stderr.use { scanner ->
            assertThat(scanner)
                .contains(
                    "   > Using multiple versions of the Android Gradle plugin in the same build is not allowed."
                )
        }
    }

    @Test
    fun testFetchingAndroidModel() {
        app.model().fetchAndroidProjects()
        androidLib.model().fetchAndroidProjects()
    }
}
