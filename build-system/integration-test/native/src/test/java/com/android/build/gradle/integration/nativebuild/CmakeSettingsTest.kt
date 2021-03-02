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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.readAsFileIndex
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_C_FLAGS
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.OFF_STAGE_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons.getNativeBuildMiniConfig
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_WORKSPACE_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_NAME
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils.join
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CmakeSettingsTest(private val cmakeVersionInDsl: String) {

    @Rule
    @JvmField
    var project = GradleTestProject.builder()
      .fromTestApp(
        HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build()
      )
      // TODO(159233213) Turn to ON when release configuration is cacheable
      // TODO(159998570) Figure out flakiness before turning to WARN or ON
      .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()


    companion object {
        @Parameterized.Parameters(name = "version={0}")
        @JvmStatic
        fun data() = arrayOf(
          // CMakeSettings.json doesn't work with fork CMake version 3.6.0
          arrayOf(DEFAULT_CMAKE_VERSION),
          arrayOf(OFF_STAGE_CMAKE_VERSION)
        )
    }

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            join(project.buildFile.parentFile, "CMakeSettings.json"),
            """
            {
                "configurations": [{
                    "name": "android-gradle-plugin-predetermined-name",
                    "description": "Configuration generated by Android Gradle Plugin",
                    "inheritEnvironments": ["ndk"],
                    "buildCommandArgs": "",
                    "buildRoot": "${ENV_WORKSPACE_ROOT.ref}/cmake/android/${NDK_VARIANT_NAME.ref}/${NDK_ABI.ref}",
                    "variables": [
                        {"name": "$CMAKE_C_FLAGS", "value": "-DTEST_C_FLAG -DTEST_C_FLAG_2"},
                        {"name": "$CMAKE_CXX_FLAGS", "value": "-DTEST_CPP_FLAG"},
                    ]
                }]
            }""".trimIndent())

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    ndkVersion "$DEFAULT_NDK_SIDE_BY_SIDE_VERSION"
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
    }

    @Test
    fun checkBuildFoldersRedirected() {
        project.execute("clean", "assemble")
        val abis = 2
        val buildTypes = 4
        val model = project.modelV2().fetchNativeModules(emptyList(), emptyList())
        val allBuildOutputs = model.container.singleModel.variants.flatMap { variant ->
            variant.abis.flatMap { abi ->
                abi.symbolFolderIndexFile.readAsFileIndex().flatMap {
                    it.list()!!.toList()
                }
            }
        }
        Truth.assertThat(allBuildOutputs).hasSize(abis * buildTypes)
        Truth.assertThat(allBuildOutputs.toSet()).containsExactly("libhello-jni.so")
        val projectRoot = project.buildFile.parentFile
        assertThat(join(projectRoot, "cmake/android/debug")).isDirectory()
        assertThat(join(projectRoot, "cmake/android/release")).isDirectory()
        assertThat(join(projectRoot, "cmake/android/minSizeRel")).isDirectory()
        assertThat(join(projectRoot, "cmake/android/relWithDebInfo")).isDirectory()
    }

    @Test
    fun checkJsonRegeneratedForDifferentBuildCommands() {
        project.execute("clean", "assemble")
        val miniConfigs = getMiniConfigs()

        assertThat(miniConfigs.size).isEqualTo(2)
        for(miniConfig in miniConfigs){
            val buildCommand = miniConfig.buildTargetsCommandComponents?.joinToString(" ")
            assertThat(buildCommand).doesNotContain("-j 100")
        }

        TestFileUtils.searchAndReplace(
            join(project.buildFile.parentFile, "CMakeSettings.json"),
            "\"buildCommandArgs\": \"\",",
            "\"buildCommandArgs\": \"-j 100\",")

        project.execute("clean", "assemble")
        val miniConfigsWithBuildCommandArgs = getMiniConfigs()

        assertThat(miniConfigs.size).isEqualTo(2)
        for(miniConfig in miniConfigsWithBuildCommandArgs){
            val buildCommand = miniConfig.buildTargetsCommandComponents?.joinToString(" ")
            assertThat(buildCommand).contains("-j 100")
        }
    }

    private fun getMiniConfigs() : List<NativeBuildConfigValueMini> {
        return project.recoverExistingCxxAbiModels()
                .filter { it.abi == Abi.X86_64 || it.abi == Abi.ARMEABI_V7A}
                .filter { it.variant.variantName == "debug" }
                .map { getNativeBuildMiniConfig(it, null) }
    }
}
