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
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class AppAndLibNoBuildConfigTest {
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("applibtest")
        .create()

    @Before
    fun setUp() {
        project.gradlePropertiesFile
            .appendText("\n${BooleanOption.BUILD_FEATURE_BUILDCONFIG.propertyName}=false\n")
    }

    @Test
    fun `ensure buildConfig is not in the APK`() {
        project.execute("app:assembleDebug")

        val debugApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)
        ApkSubject.assertThat(debugApk)
            .doesNotContainClass("Lcom/android/tests/testprojecttest/lib/BuildConfig;")
        ApkSubject.assertThat(debugApk)
            .doesNotContainClass("Lcom/android/tests/testprojecttest/app/BuildConfig;")
    }

    @Test
    fun `ensure buildConfig is not in the AAR`() {
        project.execute("lib:assembleDebug")

        project.getSubproject(":lib").assertThatAar("debug") {
            doesNotContainClass("Lcom/android/tests/testprojecttest/lib/BuildConfig;")
        }
    }

    @Test
    fun `ensure defaultConfig-buildConfigField fails`() {
        project.getSubproject(":app")
            .buildFile.appendText("\nandroid.defaultConfig.buildConfigField(\"boolean\", \"foo\", \"true\")")
        val failure = project.executor().expectFailure().run("project")
        failure.stderr.use {
            ScannerSubject.assertThat(it)
                .contains("defaultConfig contains custom BuildConfig fields, but the feature is disabled.")
        }
    }

    @Test
    fun `ensure buildtypes-buildConfigField fails`() {
        project.getSubproject(":app")
            .buildFile.appendText("\nandroid.buildTypes.debug.buildConfigField(\"boolean\", \"foo\", \"true\")")
        val failure = project.executor().expectFailure().run("project")
        failure.stderr.use {
            ScannerSubject.assertThat(it)
                .contains("Build Type 'debug' contains custom BuildConfig fields, but the feature is disabled.")
        }
    }

    @Test
    fun `ensure no buildConfig won't break dexing when no other java source file exists`() {
        project.getSubproject(":app").buildFile.appendText("""

            android.sourceSets.main.java.exclude "**/*.java"

            android {
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_8
                    targetCompatibility JavaVersion.VERSION_1_8
                }
            }
        """.trimIndent())

        project.execute("app:assembleDebug")
    }
}