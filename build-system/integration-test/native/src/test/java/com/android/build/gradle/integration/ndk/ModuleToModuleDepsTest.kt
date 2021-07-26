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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.model.cartesianOf
import com.android.build.gradle.integration.common.fixture.model.enableCxxStructuredLogging
import com.android.build.gradle.integration.common.fixture.model.minimizeUsingTupleCoverage
import com.android.build.gradle.integration.common.fixture.model.readStructuredLogs
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.ndk.ModuleToModuleDepsTest.BuildSystemConfig.CMake
import com.android.build.gradle.integration.ndk.ModuleToModuleDepsTest.BuildSystemConfig.NdkBuild
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.OFF_STAGE_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.decodeLoggingMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * CMake lib<-app project where lib is published as Prefab
 */
@RunWith(Parameterized::class)
class ModuleToModuleDepsTest(
    private val appBuildSystem: BuildSystemConfig,
    private val libBuildSystem: BuildSystemConfig,
    private val appUsesPrefab: Boolean,
    private val libUsesPrefabPublish: Boolean,
    private val libExtension: String
) {
    sealed class BuildSystemConfig {
        abstract val build : String
        data class CMake(val version : String) : BuildSystemConfig() {
            override val build = "CMake"
        }

        object NdkBuild : BuildSystemConfig() {
            override val build = "NdkBuild"
            override fun toString() = "ndk-build"
        }
    }
    private val app = MinimalSubProject.app()
    private val lib = MinimalSubProject.lib()
    private val multiModule = MultiModuleTestProject.builder()
        .subproject(":app", app)
        .subproject(":lib", lib)
        .dependency(app, lib)
        .build()

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(multiModule)
            .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    companion object {
        @Parameterized.Parameters(
            name = "app={0} lib={1} appUsesPrefab={2} libUsesPrefabPublish={3} libExtension={4}")
        @JvmStatic
        fun data() =
            cartesianOf(
                    arrayOf(NdkBuild, CMake(OFF_STAGE_CMAKE_VERSION), CMake(DEFAULT_CMAKE_VERSION)),
                    arrayOf(NdkBuild, CMake(OFF_STAGE_CMAKE_VERSION), CMake(DEFAULT_CMAKE_VERSION)),
                    arrayOf(true, false),
                    arrayOf(true, false),
                    arrayOf(".a", ".so")
                )
                .minimizeUsingTupleCoverage(4)
    }

    @Before
    fun setUp() {
        val staticOrShared = if (libExtension == ".a") "STATIC" else "SHARED"
        val stl = if (libExtension == ".a") "c++_static" else "c++_shared"
        val appStanza = when(appBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${appBuildSystem.version}"
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$stl")
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$stl")
                """.trimIndent()
        }

        project.getSubproject(":app").buildFile.appendText(
            """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $DEFAULT_COMPILE_SDK_VERSION
                ndkPath "${project.ndkPath}"
                defaultConfig {
                    ndk {
                        abiFilters "x86"
                    }
                }

                buildFeatures {
                    prefab $appUsesPrefab
                }
            }

            dependencies {
                implementation project(":lib")
            }

            $appStanza
            """.trimIndent())
        val libStanza = when(libBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${libBuildSystem.version}"
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$stl")
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$stl")
                """.trimIndent()
        }

        project.getSubproject(":lib").buildFile.appendText(
            """
            android {
                buildFeatures {
                    prefabPublishing $libUsesPrefabPublish
                }
                prefab {
                    foo {
                        headers "src/main/cpp/include"
                        libraryName "libfoo"
                    }
                }
            }
            $libStanza
            """)

        val header = project.getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/include/foo.h")
        header.parentFile.mkdirs()
        header.writeText("int foo();")

        val libSource = project.getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/foo.cpp")
        libSource.parentFile.mkdirs()
        libSource.writeText("int foo() { return 5; }")

        when(libBuildSystem) {
            is CMake -> {
                val libCMakeLists =
                    project.getSubproject(":lib").buildFile.resolveSibling("CMakeLists.txt")
                libCMakeLists.writeText(
                    """
                    cmake_minimum_required(VERSION 3.4.1)
                    file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)
                    message("${'$'}{SRC}")
                    set(CMAKE_VERBOSE_MAKEFILE ON)
                    add_library(foo $staticOrShared ${'$'}{SRC})
                    """.trimIndent()
                )
            }
            is NdkBuild -> {
                val libAndroidMk =
                    project.getSubproject(":lib").buildFile.resolveSibling("Android.mk")
                libAndroidMk.writeText("""
                    LOCAL_PATH := $(call my-dir)

                    include $(CLEAR_VARS)
                    LOCAL_MODULE := foo
                    LOCAL_MODULE_FILENAME := libfoo
                    LOCAL_SRC_FILES := src/main/cpp/foo.cpp
                    LOCAL_C_INCLUDES := src/main/cpp/include
                    LOCAL_EXPORT_C_INCLUDES := src/main/cpp/include
                    include $(BUILD_${staticOrShared}_LIBRARY)
                    """.trimIndent())
            }
        }

        when(appBuildSystem) {
            is CMake -> {
                val appCMakeLists = project.getSubproject(":app").buildFile.resolveSibling("CMakeLists.txt")
                appCMakeLists.writeText(
                    """
                    cmake_minimum_required(VERSION 3.4.1)
                    file(GLOB_RECURSE SRC src/*.c src/*.cpp src/*.cc src/*.cxx src/*.c++ src/*.C)
                    message("${'$'}{SRC}")
                    set(CMAKE_VERBOSE_MAKEFILE ON)
                    add_library(hello-jni SHARED ${'$'}{SRC})
                    find_package(lib REQUIRED CONFIG)
                    target_link_libraries(hello-jni log lib::foo)
                    """.trimIndent())
            }
            is NdkBuild -> {
                val appAndroidMk =
                    project.getSubproject(":app").buildFile.resolveSibling("Android.mk")
                appAndroidMk.writeText("""
                    LOCAL_PATH := $(call my-dir)
                    $(call import-module,prefab/lib)

                    include $(CLEAR_VARS)
                    LOCAL_MODULE := hello-jni
                    LOCAL_MODULE_FILENAME := libhello-jni
                    LOCAL_SRC_FILES := src/main/cpp/call_foo.cpp
                    LOCAL_C_INCLUDES := src/main/cpp/include
                    LOCAL_SHARED_LIBRARIES := libfoo
                    include $(BUILD_SHARED_LIBRARY)
                    """.trimIndent())
            }
        }


        val appSource = project.getSubproject(":app").buildFile.resolveSibling("src/main/cpp/call_foo.cpp")
        appSource.parentFile.mkdirs()
        appSource.writeText("""
            #include <foo.h>
            int callFoo() { return foo(); }
        """.trimIndent())

        enableCxxStructuredLogging(project)
    }

    /**
     * Returns true if this configuration is expected to fail.
     */
    private fun expectGradleError() : Boolean {
        // If app side doesn't set android.buildFeatures.prefab=true then we
        // expect a build failure indicating that 'lib' can't be found.
        if (!appUsesPrefab || !libUsesPrefabPublish) {
            if (appBuildSystem == NdkBuild) {
                // Error when this test was written:
                //   Are you sure your NDK_MODULE_PATH variable is properly defined
                return true
            }
            // Error when this test was written:
            //   Add the installation prefix of "lib" to CMAKE_PREFIX_PATH
            return true
        }

        // ndk-build can't consume consume configuration-only packages until a future NDK
        // update and corresponding prefab update.
        if (appBuildSystem == NdkBuild) {
            // Error when this test was written:
            //   Android.mk:foo: LOCAL_SRC_FILES points to a missing file
            return true
        }

        return false
    }

    @Test
    fun `app configure`() {
        var executor = project.executor()

        // Expect failure for cases when configuration failure is expected.
        if (expectGradleError()) {
            executor.expectFailure()
        }

        executor.run(":app:configure${appBuildSystem.build}Debug")

        // Check for expected Gradle error message (if any)
        if (expectGradleError()) {
            return
        }

        // Check that the output is known but does not yet exist on disk.
        val libAbi = project.getSubproject("lib")
            .recoverExistingCxxAbiModels().single { it.abi == Abi.X86_64 }
        val libConfig = AndroidBuildGradleJsons.getNativeBuildMiniConfig(libAbi, null)
        println(libConfig)
        val libOutput = libConfig.libraries.values.single().output!!
        assertThat(libOutput.toString().endsWith(libExtension))
            .named("$libOutput")
            .isTrue()
        assertThat(libOutput.isFile)
            .named("$libOutput")
            .isFalse()
    }

    @Test
    fun `app build`() {
        // There is no point in testing build in the cases where configuration is expected to fail.
        // Those cases will be covered by other tests
        if (expectGradleError()) return

        project.executor()
            .run(":app:build${appBuildSystem.build}Debug")
    }
}
