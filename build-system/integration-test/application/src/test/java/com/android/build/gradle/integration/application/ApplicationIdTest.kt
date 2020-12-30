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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test setting applicationId and applicationIdSuffix.  */
class ApplicationIdTest {

    @get:Rule
    var project = GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |apply plugin: "com.android.application"
            |
            |android {
            |    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
            |    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
            |
            |    defaultConfig {
            |        applicationId "com.example.applicationidtest"
            |        applicationIdSuffix "default"
            |    }
            |
            |    buildTypes {
            |        debug {
            |            applicationIdSuffix ".debug"
            |        }
            |    }
            |
            |    flavorDimensions 'foo'
            |    productFlavors {
            |        f1 {
            |            applicationIdSuffix "f1"
            |        }
            |    }
            |}
            |""".trimMargin("|")
        )
    }

    @Test
    fun checkApplicationIdDebug() {
        project.execute("assembleF1Debug")
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1"))
            .hasApplicationId("com.example.applicationidtest.default.f1.debug")

        TestFileUtils.searchAndReplace(
            project.buildFile,
            "applicationIdSuffix \".debug\"",
            "applicationIdSuffix \".foo\""
        )

        project.execute("assembleF1Debug")

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "f1"))
            .hasApplicationId("com.example.applicationidtest.default.f1.foo")
    }

    @Test
    fun checkApplicationIdRelease() {
        project.executor()
            // http://b/149978740 and http://b/146208910
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("assembleF1Release")
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1"))
            .hasApplicationId("com.example.applicationidtest.default.f1")

        project.executor()
            // http://b/149978740 and http://b/146208910
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("assembleF1Release")
        assertThat(project.getApk(GradleTestProject.ApkType.RELEASE, "f1"))
            .hasApplicationId("com.example.applicationidtest.default.f1")
    }
}

