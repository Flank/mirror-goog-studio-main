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

package com.android.build.gradle.integration.instantexecution

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GradlePropertiesTest {

    @get:Rule
    val project = GradleTestProject
        .builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        project.projectDir.resolve(".gradle/configuration-cache").deleteRecursively()
    }

    @Test
    fun testAccessingChangedGradlePropertiesAtConfiguration() {
        // AndroidX must be enabled when Jetifier is enabled
        executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
        var result = executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Reusing configuration cache")
        }
        result = executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, false)
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Calculating task graph as configuration cache cannot be reused because " +
                        "Gradle property 'android.enableJetifier' has changed")
        }
    }

    @Test
    fun testCapturingStandardInstrumentationTestRunnerArgs() {
        executor()
            .run("assembleDebug")
        executor().run("clean")
        // Make sure we are able to capture new arguments added in following builds
        var result = executor()
            .withArgument("-Pandroid.testInstrumentationRunnerArguments.size=medium")
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Calculating task graph as configuration cache" +
                    " cannot be reused because Gradle property " +
                    "'android.testInstrumentationRunnerArguments.size' has changed")
        }
        executor().run("clean")
        result = executor()
            .withArgument("-Pandroid.testInstrumentationRunnerArguments.size=medium")
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Reusing configuration cache")
        }
    }

    @Test
    fun testCapturingCustomInstrumentationTestRunnerArgs() {
        var result = executor()
            .withArgument("-Pandroid.testInstrumentationRunnerArguments.foo=origin")
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Passing custom test runner argument" +
                    " android.testInstrumentationRunnerArguments.foo from gradle.properties or " +
                    "command line is not compatible with configuration caching")
        }
        executor().run("clean")
        result = executor()
            .withArgument("-Pandroid.testInstrumentationRunnerArguments.foo=origin")
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Reusing configuration cache")
        }
        // Make sure we are able to capture changes to the arguments exist in the "first" build to
        // make it partially compatible with configuration caching.
        result = executor()
            .withArgument("-Pandroid.testInstrumentationRunnerArguments.foo=changed")
            .run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Calculating task graph as configuration cache" +
                    " cannot be reused because Gradle property " +
                    "'android.testInstrumentationRunnerArguments.foo' has changed")
        }
    }

    private fun executor(): GradleTaskExecutor = project.executor()
}