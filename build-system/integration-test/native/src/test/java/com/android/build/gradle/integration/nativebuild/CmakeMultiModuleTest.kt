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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.SimpleNativeLib
import com.android.build.gradle.integration.common.fixture.model.cartesianOf
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.OFF_STAGE_CMAKE_VERSION
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cmake test with multiple modules.
 */
@RunWith(Parameterized::class)
class CmakeMultiModuleTest(
        private val cmakeVersionInDsl: String,
        private val hasFoldableVariants: Boolean
) {

    @get:Rule
    val project =
      GradleTestProject.builder()
        .fromTestApp(
          MultiModuleTestProject(
            mapOf(
              "app" to HelloWorldJniApp.builder().withCmake().build(),
              "lib" to SimpleNativeLib()
            )
          )
        )
        .setCmakeVersion(DEFAULT_CMAKE_SDK_DOWNLOAD_VERSION)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .setWithCmakeDirInLocalProp(true)
        .create()

    companion object {
        @Parameterized.Parameters(name = "version={0} hasFoldableVariants={1} enableConfigurationFolding={2}")
        @JvmStatic
        fun data() =
                cartesianOf(
                        arrayOf("3.6.0", OFF_STAGE_CMAKE_VERSION, DEFAULT_CMAKE_VERSION),
                        arrayOf(true, false)
                )
    }

    @Before
    fun setUp() {
        project.getSubproject(":app").buildFile.appendText(
            """
apply plugin: 'com.android.application'

android {
    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
    ndkPath "${project.ndkPath}"
    defaultConfig {
        ndk {
            abiFilters "x86"
        }
    }

    externalNativeBuild {
        cmake {
            path "CMakeLists.txt"
            version "$cmakeVersionInDsl"
        }
    }
}


dependencies {
    implementation project(":lib")
}

// This make the build task in lib run after the task in app if the task dependency is not set up
// properly.
afterEvaluate {
    tasks.getByPath(":lib:externalNativeBuildDebug")
            .shouldRunAfter(tasks.getByPath(":app:externalNativeBuildDebug"))
}

""")

        // Limit ABI to improve running time.
        project.getSubproject(":lib").buildFile.appendText(
"""
android {
    defaultConfig {
        ndk {
            abiFilters "x86"
        }
    }
}
""")

        if (hasFoldableVariants) {
            project.getSubproject(":app").buildFile.appendText("""
            android {
                buildTypes {
                    secondDebug {}
                }
            }
            """.trimIndent())
            project.getSubproject(":lib").buildFile.appendText("""
            android {
                buildTypes {
                    secondDebug {}
                }
            }
            """.trimIndent())
        }
    }

    @Test
    fun checkTaskExecutionOrder() {
        val result = project.executor().run("clean", ":app:assembleDebug")
        assertThat(result.getTask(":lib:externalNativeBuildDebug")).didWork()
        assertThat(result.getTask(":app:externalNativeBuildDebug"))
                .ranAfter(":lib:externalNativeBuildDebug")
    }
}
