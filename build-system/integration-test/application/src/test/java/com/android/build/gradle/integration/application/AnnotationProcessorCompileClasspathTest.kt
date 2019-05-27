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

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test various scenarios with AnnotationProcessorOptions.includeCompileClasspath  */
class AnnotationProcessorCompileClasspathTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("butterknife").create()

    @Before
    fun setUp() {
        // Remove dependencies block from build file.
        TestFileUtils.searchRegexAndReplace(
            project.buildFile, "(?s)dependencies \\{.*\\}", ""
        )
    }

    @Test
    fun failWhenClasspathHasProcessor() {

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            dependencies {
                compile 'com.jakewharton:butterknife:7.0.1'
            }
            android.defaultConfig.javaCompileOptions
                .annotationProcessorOptions.includeCompileClasspath null""".trimIndent())

        val result = project.executor().expectFailure().run("assembleDebug")
        assertThat(result.failureMessage)
            .contains("Annotation processors must be explicitly declared now")
        assertThat(result.failureMessage)
            .contains("- butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)")
    }

    @Test
    fun failForAndroidTest() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            dependencies {
                compile 'com.jakewharton:butterknife:7.0.1'
                annotationProcessor 'com.jakewharton:butterknife:7.0.1'
            }
            android.defaultConfig.javaCompileOptions
                .annotationProcessorOptions.includeCompileClasspath null""".trimIndent()
        )

        var result = project.executor().run("assembleDebugAndroidTest")
        result.stdout.use { stdout ->
            ScannerSubject.assertThat(stdout)
                .contains("""
                          Annotation processors must be explicitly declared now
                          androidTestAnnotationProcessor
                          butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)
                          """.trimIndent())
        }
        result = project.executor().run("assembleDebugUnitTest")
        result.stdout.use { stdout ->
            ScannerSubject.assertThat(stdout)
                .contains("""
                          Annotation processors must be explicitly declared
                          testAnnotationProcessor
                          - butterknife-7.0.1.jar (com.jakewharton:butterknife:7.0.1)
                          """.trimIndent()
                )
        }

    }

    @Test
    fun checkSuccessWithIncludeCompileClasspath() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android.defaultConfig.javaCompileOptions
                .annotationProcessorOptions.includeCompileClasspath = true
            dependencies {
                compile 'com.jakewharton:butterknife:7.0.1'
            }
            """.trimIndent())
        project.executor()
            .run("assembleDebug", "assembleDebugAndroidTest", "assembleDebugUnitTest")
    }

    @Test
    fun checkSuccessWhenProcessorIsSpecified() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android.defaultConfig.javaCompileOptions
                .annotationProcessorOptions.includeCompileClasspath null
            dependencies {
                compile 'com.jakewharton:butterknife:7.0.1'
                annotationProcessor 'com.jakewharton:butterknife:7.0.1'
                testAnnotationProcessor 'com.jakewharton:butterknife:7.0.1'
                androidTestAnnotationProcessor 'com.jakewharton:butterknife:7.0.1'
            }
            """.trimIndent())
        project.executor().run("assembleDebug", "assembleDebugAndroidTest", "testDebug")
    }
}
