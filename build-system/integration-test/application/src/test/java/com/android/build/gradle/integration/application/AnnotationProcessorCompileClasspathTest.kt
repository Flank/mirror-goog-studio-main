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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.errors.DeprecationReporter
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
    fun checkWarningWithIncludeCompileClasspath() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            dependencies {
                compile 'com.jakewharton:butterknife:7.0.1'
            }
            """.trimIndent())
        var result = project.executor().run("help")
        result.stdout.use {
            ScannerSubject.assertThat(it).doesNotContain(
                "annotationProcessorOptions.includeCompileClasspath")
        }

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android.defaultConfig.javaCompileOptions
                .annotationProcessorOptions.includeCompileClasspath = true
            """.trimIndent())
        result = project.executor().run("help")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "DSL element 'annotationProcessorOptions.includeCompileClasspath' is obsolete.\n"
                        + DeprecationReporter.DeprecationTarget.INCLUDE_COMPILE_CLASSPATH.getDeprecationTargetMessage()
            )
        }
    }
}
