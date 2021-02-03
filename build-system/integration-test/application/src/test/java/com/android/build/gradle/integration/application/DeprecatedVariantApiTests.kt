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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class DeprecatedVariantApiTests {
    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()
    @Before
    fun setUp() {
        project.gradlePropertiesFile
            .appendText("\n${BooleanOption.BUILD_FEATURE_BUILDCONFIG.propertyName}=true\n")
    }
    @Test
    fun `ensure buildConfig is generated when tasks are created eagerly`() {
        project.buildFile.appendText("""
        android {
            // force task configuration which will happen before the old variant API is called.
            tasks.whenTaskAdded {
                System.out.println(it.name)
            }
            applicationVariants.all {
                resValue("string", "res_name", "SomeResValue")
                buildConfigField("String", "CUSTOM_VERSION_NAME", "\"buildVersionName\"")
            }
        }""".trimIndent())
        project.execute("assembleDebug")
        val debugApk = project.getApk(GradleTestProject.ApkType.DEBUG)
        ApkSubject.assertThat(debugApk)
            .containsClass("Lcom/example/helloworld/BuildConfig;")
        val generatedBuildConfig = File(project.buildDir,
            "generated/source/buildConfig/debug/com/example/helloworld/BuildConfig.java")
        Truth.assertThat(generatedBuildConfig.exists()).isTrue()
        Truth.assertThat(generatedBuildConfig.readText()).contains("CUSTOM_VERSION_NAME")
    }
}
