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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.goldenBuildProducts
import com.android.build.gradle.integration.common.fixture.model.goldenConfigurationFlags
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.EnvironmentVariable
import com.android.testutils.AssumeUtil
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

class NdkBuildBuildSettingsTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
      .fromTestApp(HelloWorldJniApp.builder().build())
      .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
      .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
      .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            apply plugin: 'com.android.application'
                android {
                    compileSdkVersion 30
                    ndkPath "${project.ndkPath}"
                    defaultConfig {
                      externalNativeBuild {
                          ndkBuild {
                            abiFilters.addAll("armeabi-v7a", "arm64-v8a")
                          }
                      }
                    }
                    externalNativeBuild {
                      ndkBuild {
                        path "src/main/jni/Android.mk"
                      }
                    }
                }

                android.packagingOptions {
                    doNotStrip "*/armeabi-v7a/libhello-jni.so"
                }
            android {
                applicationVariants.all { variant ->
                    assert !variant.getExternalNativeBuildTasks().isEmpty()
                    for (def task : variant.getExternalNativeBuildTasks()) {
                        assert task.getName() == "externalNativeBuild" + variant.getName().capitalize()
                    }
                }
            }
           """.trimIndent()
        )
    }

    @Test
    fun `uses empty BuildSettingsConfiguration if JSON file does not exist`() {
        project.execute("clean", "assembleDebug")

        // No BuildSettings.json, should have empty BuildSettingsConfiguration
        project.recoverExistingCxxAbiModels()
            .map { it.buildSettings }
            .forEach {
                assertThat(it).isEqualTo(BuildSettingsConfiguration())
            }
    }

    @Test
    fun `uses BuildSettings environment variables during the build`() {
        AssumeUtil.assumeNotWindows()
        val launcher = setupTestLauncher()

        // NDK_CCACHE sets a launcher for ndk-build
        TestFileUtils.appendToFile(
            join(
                project.buildFile.parentFile,
                "src",
                "main",
                "jni",
                "BuildSettings.json"
            ),
            """
            {
                "environmentVariables": [
                    {
                      "name": "NDK_CCACHE",
                      "value": "${launcher.path}"
                    }
                ]
            }""".trimIndent()
        )
        project.execute("clean", "assembleDebug")

        // Verify that environment variables should be set in BuildSettings
        project.recoverExistingCxxAbiModels()
            .map { it.buildSettings }
            .forEach {
                assertThat(it.environmentVariables).isEqualTo(
                    listOf(
                        EnvironmentVariable("NDK_CCACHE", launcher.path)
                    )
                )
            }

        // Verify that environment variables was used during the build to set the launcher
        val launcherOutput = FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")
        assertThat(launcherOutput.readText().trim()).isEqualTo("output to launcher_output.txt")
    }

    @Test
    fun `build product golden locations`() {
        project.execute("assembleDebug")
        val golden = project.goldenBuildProducts()
        assertThat(golden).isEqualTo("""
            {PROJECT}/build/intermediates/merged_native_libs/debug/out/lib/arm64-v8a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/merged_native_libs/debug/out/lib/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/ndkBuild/debug/obj/local/arm64-v8a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/stripped_native_libs/debug/out/lib/arm64-v8a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/stripped_native_libs/debug/out/lib/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/local/arm64-v8a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/local/arm64-v8a/objs-debug/hello-jni/hello-jni.o{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/local/armeabi-v7a/libhello-jni.so{F}
            {PROJECT}/build/intermediates/{DEBUG}/obj/local/armeabi-v7a/objs-debug/hello-jni/hello-jni.o{F}
        """.trimIndent())
    }

    @Test
    fun `configuration build command golden flags`() {
        val golden = project.goldenConfigurationFlags(Abi.ARMEABI_V7A)
        println(golden)
        assertThat(golden).isEqualTo("""
            -B
            -n
            APP_ABI=armeabi-v7a
            APP_BUILD_SCRIPT={PROJECT}/src/main/jni/Android.mk
            APP_PLATFORM=android-16
            APP_SHORT_COMMANDS=false
            LOCAL_SHORT_COMMANDS=false
            NDK_ALL_ABIS=armeabi-v7a
            NDK_DEBUG=1
            NDK_LIBS_OUT={PROJECT}/build/intermediates/{DEBUG}/lib
            NDK_OUT={PROJECT}/build/intermediates/{DEBUG}/obj
            NDK_PROJECT_PATH=null
        """.trimIndent())
    }

    private fun setupTestLauncher(): File {
        // Launcher that prints output to launcher_output.txt to launcher_output.txt then runs ndk-build
        val wrapper = if(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS){
            setupWindowsLauncher()
        } else {
            setupLinuxLauncher()
        }
        wrapper.setReadable(true)
        wrapper.setExecutable(true)
        return wrapper
    }

    private fun setupLinuxLauncher(): File {
        val wrapper = FileUtils.join(project.buildFile.parentFile, "wrapper.sh")
        TestFileUtils.appendToFile(
            wrapper,
            """
                #!/bin/bash
                echo "output to launcher_output.txt" > ${FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")}
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
                echo "output to launcher_output.txt" > ${FileUtils.join(project.buildFile.parentFile, "launcher_output.txt")}
                %*
            """.trimIndent()
        )
        return wrapper
    }
}
