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

package com.android.build.gradle.integration.nativebuild

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.model.createCxxAbiModelFromJson
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.EnvironmentVariable
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class CMakeBuildSettingsTest(private val cmakeVersionInDsl: String) {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build()
        )
        .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    companion object {
        @Parameterized.Parameters(name = "model = {0}")
        @JvmStatic
        fun data() = arrayOf(
            arrayOf("3.6.0"),
            arrayOf("3.10.2")
        )
    }

    @Before
    fun setUp() {
        PathSubject.assertThat(project.buildFile).isNotNull()
        PathSubject.assertThat(project.buildFile).isFile()

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    ndkVersion "${GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION}"
                    defaultConfig {
                      externalNativeBuild {
                          cmake {
                            abiFilters.addAll("armeabi-v7a", "x86_64");
                            targets.addAll("hello-jni")
                            // TODO enable this once configuration has been added to DSL
                            // configuration "my-test-configuration"
                          }
                      }
                    }
                    externalNativeBuild {
                      cmake {
                        path "CMakeLists.txt"
                        version "$cmakeVersionInDsl"
                      }
                    }
                    buildTypes {
                        debug {}
                        release {}
                        minSizeRel {}
                        relWithDebInfo {}
                    }
                }

            """.trimIndent()
        )
        setupTestLauncher()
    }

    private fun setupTestLauncher() {
        // Launcher that prints ${TEST_ENV} to launcher_output.txt then runs the Ninja build
        val wrapper = if(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS){
            setupWindowsLauncher()
        } else {
            setupLinuxLauncher()
        }
        wrapper.setReadable(true)
        wrapper.setExecutable(true)

        TestFileUtils.appendToFile(
            FileUtils.join(project.buildFile.parentFile, "CMakeLists.txt"),
            "set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE \"${wrapper.path.replace("\\", "\\\\")}\")"
        )
    }

    private fun setupLinuxLauncher(): File {
        val wrapper = FileUtils.join(project.buildFile.parentFile, "wrapper.sh")
        TestFileUtils.appendToFile(
            wrapper,
            """
                #!/bin/bash
                echo "${'$'}{TEST_ENV}" > ${FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")}
                $*
            """.trimIndent()
        )
        return wrapper
    }

    private fun setupWindowsLauncher(): File {
        val wrapper = FileUtils.join(project.buildFile.parentFile, "wrapper.cmd")
        TestFileUtils.appendToFile(
            wrapper,
            """
                echo %TEST_ENV% > ${FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")}
                %*
            """.trimIndent()
        )
        return wrapper
    }

    @Test
    fun `uses empty BuildSettingsConfiguration if JSON file does not exist`() {
        project.execute("clean", "assembleDebug")

        // No BuildSettings.json, should have empty BuildSettingsConfiguration
        debugBuildModelFiles()
            .map { createCxxAbiModelFromJson(it.readText()).buildSettings }
            .forEach {
                assertThat(it).isEqualTo(BuildSettingsConfiguration())
            }
    }

    @Test
    fun `uses BuildSettings environment variables during the build`() {
        TestFileUtils.appendToFile(
            FileUtils.join(project.buildFile.parentFile, "BuildSettings.json"),
            """
            {
                "environmentVariables": [
                    {
                      "name": "TEST_ENV",
                      "value": "value for TEST_ENV"
                    },
                    {
                      "name": "abi",
                      "value": "${'$'}{ndk.abi}"
                    }
                ]
            }""".trimIndent()
        )

        project.execute("clean", "assembleDebug")


        // Verify that environment variables is set in BuildSettings
        debugBuildModelFiles()
            .map { createCxxAbiModelFromJson(it.readText()) }
            .forEach {
                assertThat(it.buildSettings).isEqualTo(
                    BuildSettingsConfiguration(
                        environmentVariables = listOf(
                            EnvironmentVariable(name = "TEST_ENV", value = "value for TEST_ENV"),
                            EnvironmentVariable(name = "abi", value = it.abi.tag)
                        )
                    )
                )
            }

        // Verify the environment variable was used by the launcher
        val launcherOutput = FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")
        assertThat(launcherOutput.readText().trim()).isEqualTo("value for TEST_ENV")
    }

    private fun debugBuildModelFiles(): List<File> {
        val x86ModelDebug = FileUtils.join(
            project.testDir, ".cxx", "cmake", "debug", "x86_64", "build_model.json"
        )
        val armModelDebug = FileUtils.join(
            project.testDir, ".cxx", "cmake", "debug", "armeabi-v7a", "build_model.json"
        )

        return listOf(x86ModelDebug, armModelDebug)
    }
}
