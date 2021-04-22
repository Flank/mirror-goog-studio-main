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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.getSoFolderFor
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class CmakeVariantApiTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build())
            // TODO(b/159233213) Turn to ON when release configuration is cacheable
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
            .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    @Test
    fun testAbiFilter() {

        TestFileUtils.appendToFile(project.buildFile, """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                ndkPath "${project.ndkPath}"
                defaultConfig {
                    externalNativeBuild {
                      cmake {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.externalNativeBuild.abiFilters.empty()
                    it.externalNativeBuild.abiFilters.add("x86_64")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.7)

            add_library(foo SHARED foo.cpp)

            target_link_libraries(foo ${'$'}{log-lib})
            """.trimIndent())

        project.execute("assembleDebug")

        assertThat(project.getSoFolderFor(Abi.ARM64_V8A)).isNull()
        assertThat(project.getSoFolderFor(Abi.X86)).isNull()
        assertThat(project.getSoFolderFor(Abi.ARMEABI_V7A)).isNull()
        assertThat(project.getSoFolderFor(Abi.X86_64)).exists()

        project.recoverExistingCxxAbiModels().forEach { abi ->
            val buildCommandFile = abi.metadataGenerationCommandFile
            assertThat(buildCommandFile).exists()
            val buildCommand = buildCommandFile.readText()

            Truth.assertThat(buildCommand).contains("-DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG")
            Truth.assertThat(buildCommand).contains("-DCMAKE_C_FLAGS=-DTEST_C_FLAG")
            Truth.assertThat(buildCommand).contains("-DANDROID_ABI=x86_64")
        }
    }

    @Test
    fun testFlags() {

        TestFileUtils.appendToFile(project.buildFile, """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                ndkPath "${project.ndkPath}"
                defaultConfig {
                    externalNativeBuild {
                      cmake {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.externalNativeBuild.getCFlags().add("-DTEST_C_FLAG2")
                    it.externalNativeBuild.getCppFlags().add("-DTEST_CPP_FLAG2")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.7)

            add_library(foo SHARED foo.cpp)

            target_link_libraries(foo ${'$'}{log-lib})
            """.trimIndent())

        project.execute("assembleDebug")

        assertThat(project.getSoFolderFor(Abi.ARM64_V8A)).isNull()
        assertThat(project.getSoFolderFor(Abi.X86)).isNull()
        assertThat(project.getSoFolderFor(Abi.ARMEABI_V7A)).exists()
        assertThat(project.getSoFolderFor(Abi.X86_64)).exists()

        project.recoverExistingCxxAbiModels().forEach { abi ->
            val buildCommandFile = abi.metadataGenerationCommandFile
            assertThat(buildCommandFile).exists()
            val buildCommand = buildCommandFile.readText()

            Truth.assertThat(buildCommand)
                    .contains("-DCMAKE_CXX_FLAGS=-DTEST_CPP_FLAG -DTEST_CPP_FLAG2")
            Truth.assertThat(buildCommand).contains("-DCMAKE_C_FLAGS=-DTEST_C_FLAG -DTEST_C_FLAG2")
        }
    }

    @Test
    fun testArguments() {

        TestFileUtils.appendToFile(project.buildFile, """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                ndkPath "${project.ndkPath}"
                defaultConfig {
                    externalNativeBuild {
                      cmake {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    cmake {
                        path "CMakeLists.txt"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.externalNativeBuild.arguments.add("-DANDROID_ARM_NEON=TRUE")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        val cmakeLists = project.buildFile.resolveSibling("CMakeLists.txt")
        assertThat(cmakeLists).isFile()
        cmakeLists.writeText("""
            cmake_minimum_required(VERSION 3.7)

            add_library(foo SHARED foo.cpp)

            target_link_libraries(foo ${'$'}{log-lib})
            """.trimIndent())

        project.execute("assembleDebug")

        assertThat(project.getSoFolderFor(Abi.ARM64_V8A)).isNull()
        assertThat(project.getSoFolderFor(Abi.X86)).isNull()
        assertThat(project.getSoFolderFor(Abi.ARMEABI_V7A)).exists()
        assertThat(project.getSoFolderFor(Abi.X86_64)).exists()

        project.recoverExistingCxxAbiModels().forEach { abi ->
            val buildCommandFile = abi.metadataGenerationCommandFile
            assertThat(buildCommandFile).exists()
            val buildCommand = buildCommandFile.readText()

            Truth.assertThat(buildCommand).contains("-DANDROID_ARM_NEON=TRUE")
        }
    }
}

