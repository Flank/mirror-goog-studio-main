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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class NdkBuildVariantApiTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
            .fromTestApp(HelloWorldJniApp.builder().build())
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .addFile(HelloWorldJniApp.androidMkC("src/main/jni"))
            .addGradleProperties("${BooleanOption.ENABLE_V2_NATIVE_MODEL.propertyName}=true")
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
                      ndkBuild {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/jni/Android.mk"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.ndkBuildNativeBuildOptions.abiFilters.empty()
                    it.ndkBuildNativeBuildOptions.abiFilters.add("x86_64")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        project.execute("assembleDebug")

        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARM64_V8A)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARMEABI_V7A)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86_64)).exists()

        val buildCommandFile = project.file(".cxx/ndkBuild/debug/x86_64/build_command.txt")
        PathSubject.assertThat(buildCommandFile).exists()
        val buildCommand = buildCommandFile.readText()

        Truth.assertThat(buildCommand).contains("APP_CPPFLAGS+=-DTEST_CPP_FLAG")
        Truth.assertThat(buildCommand).contains("APP_CFLAGS+=-DTEST_C_FLAG")
        Truth.assertThat(buildCommand).contains("NDK_ALL_ABIS=x86_64")
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
                      ndkBuild {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/jni/Android.mk"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.ndkBuildNativeBuildOptions.getCFlags().add("-DTEST_C_FLAG2")
                    it.ndkBuildNativeBuildOptions.getCppFlags().add("-DTEST_CPP_FLAG2")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        project.execute("assembleDebug")

        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARM64_V8A)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARMEABI_V7A)).exists()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86_64)).exists()

        val buildCommandFile = project.file(".cxx/ndkBuild/debug/x86_64/build_command.txt")
        PathSubject.assertThat(buildCommandFile).exists()
        val buildCommand = buildCommandFile.readText()

        Truth.assertThat(buildCommand).contains("APP_CPPFLAGS+=-DTEST_CPP_FLAG")
        Truth.assertThat(buildCommand).contains("APP_CPPFLAGS+=-DTEST_CPP_FLAG2")
        Truth.assertThat(buildCommand).contains("APP_CFLAGS+=-DTEST_C_FLAG")
        Truth.assertThat(buildCommand).contains("APP_CFLAGS+=-DTEST_C_FLAG2")
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
                      ndkBuild {
                        abiFilters.addAll("armeabi-v7a", "x86_64");
                        cFlags.addAll("-DTEST_C_FLAG")
                        cppFlags.addAll("-DTEST_CPP_FLAG")
                      }
                    }
                }
                externalNativeBuild {
                    ndkBuild {
                        path "src/main/jni/Android.mk"
                    }
                }
            }

            androidComponents {
                onVariants(selector().all(), {
                    it.ndkBuildNativeBuildOptions.abiFilters.empty()
                    it.ndkBuildNativeBuildOptions.abiFilters.add("x86_64")
                    it.ndkBuildNativeBuildOptions.arguments.add("NDK_MODULE_PATH+=./third_party/modules")
                })
            }
        """.trimIndent())

        project.buildFile.resolveSibling("foo.cpp").writeText("void foo() {}")
        project.execute("assembleDebug")

        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARM64_V8A)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.ARMEABI_V7A)).doesNotExist()
        PathSubject.assertThat(getNdkBuildOutputLib(Abi.X86_64)).exists()

        val buildCommandFile = project.file(".cxx/ndkBuild/debug/x86_64/build_command.txt")
        PathSubject.assertThat(buildCommandFile).exists()
        val buildCommand = buildCommandFile.readText()
        Truth.assertThat(buildCommand).contains("NDK_MODULE_PATH+=./third_party/modules")
    }

    private fun getNdkBuildOutputLib(abi: Abi): File? {
        return project.file(
                "build/intermediates/ndkBuild/debug/obj/local/" + abi.tag + "/libhello-jni.so")
    }
}
