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
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.ZipFileSubject
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/** Integration test for composite build.  */
class CompositeBuildTest {

    @get:Rule
    val app = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                buildTypes {
                    named("debug") {
                        testCoverageEnabled = true
                    }
                }
            }
            dependencies {
                api("com.example:lib:1.0")
                api("com.example:androidLib1:1.0")
                api("com.example:androidLib2:1.0")
            }
        }
        includedBuild("lib") {
            rootProject {
                group = "com.example"
                version = "1.0"
                plugins.add(PluginType.JAVA_LIBRARY)
            }
        }
        includedBuild("androidLib") {
            subProject(":androidLib1") {
                plugins.add(PluginType.ANDROID_LIB)
                group = "com.example"
                version = "1.0"
                android {
                    defaultCompileSdk()
                }
            }

            subProject(":androidLib2") {
                plugins.add(PluginType.ANDROID_LIB)
                group = "com.example"
                version = "1.0"
                android {
                    defaultCompileSdk()
                }
            }
        }
    }

    @Before
    fun setUp() {
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
        // This is not quite correct but for now this will do
        val androidLib = app.getSubproject("androidLib")

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
}
