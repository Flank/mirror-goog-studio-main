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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PrefabPublishingTest(
    private val variant: String,
    private val buildSystem: NativeBuildSystem,
    private val cmakeVersion: String,
) {
    private val projectName = "prefabPublishing"
    private val gradleModuleName = "foo"

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject(projectName)
        .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    private val ndkMajor = GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION.split(".").first()

    private val expectedAbis = listOf(Abi.ARMEABI_V7A, Abi.ARM64_V8A, Abi.X86, Abi.X86_64)

    companion object {
        @Parameterized.Parameters(name = "variant = {0}, build system = {1}, cmake = {2}")
        @JvmStatic
        fun data() = listOf(
            arrayOf("debug", NativeBuildSystem.CMAKE, "3.10.2"),
            arrayOf("debug", NativeBuildSystem.CMAKE, "3.18.1"),
            arrayOf("debug", NativeBuildSystem.NDK_BUILD, "N/A"),
            arrayOf("release", NativeBuildSystem.CMAKE, "3.10.2"),
            arrayOf("release", NativeBuildSystem.CMAKE, "3.18.1"),
            arrayOf("release", NativeBuildSystem.NDK_BUILD, "N/A")
        )
    }

    @Before
    fun setUp() {
        val appBuild = project.buildFile.parentFile.resolve("foo/build.gradle")
        if (buildSystem == NativeBuildSystem.NDK_BUILD) {
            appBuild.appendText("""
                android.externalNativeBuild.ndkBuild.path="src/main/cpp/Android.mk"
                """.trimIndent())
        } else {
            appBuild.appendText("""
                android.externalNativeBuild.cmake.path="src/main/cpp/CMakeLists.txt"
                android.externalNativeBuild.cmake.version="$cmakeVersion"
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=c++_shared")
                """.trimIndent())
        }
    }

    private fun verifyModule(
        packageDir: File,
        moduleName: String,
        static: Boolean,
        libraryName: String? = null
    ) {
        val moduleDir = packageDir.resolve("modules/$moduleName")
        val moduleMetadata = moduleDir.resolve("module.json").readText()
        if (libraryName != null) {
            Truth.assertThat(moduleMetadata).isEqualTo(
                """
                {
                  "export_libraries": [],
                  "library_name": "$libraryName",
                  "android": {}
                }
                """.trimIndent()
            )
        } else {
            Truth.assertThat(moduleMetadata).isEqualTo(
                """
                {
                  "export_libraries": [],
                  "android": {}
                }
                """.trimIndent()
            )
        }

        val header = moduleDir.resolve("include/$gradleModuleName/$gradleModuleName.h").readText()
        Truth.assertThat(header).isEqualTo(
            """
            #pragma once

            void $gradleModuleName();

            """.trimIndent()
        )

        for (abi in expectedAbis) {
            val abiDir = moduleDir.resolve("libs/android.${abi.tag}")
            val abiMetadata = abiDir.resolve("abi.json").readText()
            val apiLevel = if (abi.supports64Bits()) {
                21
            } else {
                16
            }

            Truth.assertThat(abiMetadata).isEqualTo(
                """
                {
                  "abi": "${abi.tag}",
                  "api": $apiLevel,
                  "ndk": $ndkMajor,
                  "stl": "c++_shared"
                }
                """.trimIndent()
            )

            val prefix = libraryName ?: "lib$moduleName"
            val suffix = if (static) {
                ".a"
            } else {
                ".so"
            }
            val library = abiDir.resolve("$prefix$suffix")
            assertThat(library).exists()
        }
    }

    @Test
    fun `project builds`() {
        project.execute("clean", "assemble$variant")
    }

    @Test
    fun `prefab package was constructed correctly`() {
        project.execute("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val packageMetadata = packageDir.resolve("prefab.json").readText()
        Truth.assertThat(packageMetadata).isEqualTo(
            """
            {
              "name": "$gradleModuleName",
              "schema_version": 1,
              "dependencies": [],
              "version": "1.0"
            }
            """.trimIndent()
        )

        verifyModule(packageDir, gradleModuleName, static = false)
        verifyModule(packageDir, "${gradleModuleName}_static", static = true)
    }

    @Test
    fun `AAR contains the prefab packages`() {
        project.execute("clean", "assemble$variant")
        project.getSubproject(gradleModuleName).assertThatAar(variant) {
            containsFile("prefab/prefab.json")
            containsFile("prefab/modules/$gradleModuleName/module.json")
            containsFile("prefab/modules/${gradleModuleName}_static/module.json")
        }
    }

    @Test
    fun `adding a new header causes a rebuild`() {
        project.execute("assemble${variant.toLowerCase()}")
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        assertThat(header).doesNotExist()

        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        project.execute("assemble$variant")
        assertThat(header).exists()
    }

    @Test
    fun `removing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
            #pragma once
            void bar();
            """.trimIndent()
        )

        project.execute("assemble$variant")
        assertThat(header).exists()

        headerSrc.delete()
        project.execute("assemble$variant")
        assertThat(header).doesNotExist()
    }

    @Test
    fun `changing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        project.execute("assemble$variant")
        assertThat(header).exists()

        val newHeaderContents = """
                #pragma once
                void bar(int);
                """.trimIndent()

        headerSrc.writeText(newHeaderContents)
        project.execute("assemble$variant")
        Truth.assertThat(header.readText()).isEqualTo(newHeaderContents)
    }

    @Test
    fun `modules with libraryName are constructed correctly`() {
        // The ndk-build importer isn't able to determine the name of a module if its
        // LOCAL_MODULE_FILENAME is altered.
        Assume.assumeTrue(buildSystem != NativeBuildSystem.NDK_BUILD)
        val subproject = project.getSubproject(gradleModuleName)

        subproject.buildFile.writeText(
            """
            plugins {
                id 'com.android.library'
            }

            android {
                compileSdkVersion rootProject.latestCompileSdk
                buildToolsVersion = rootProject.buildToolsVersion

                defaultConfig {
                    minSdkVersion 16
                    targetSdkVersion rootProject.latestCompileSdk

                    externalNativeBuild {
                        if (!project.hasProperty("ndkBuild")) {
                            cmake {
                                arguments "-DANDROID_STL=c++_shared"
                            }
                        }
                    }
                }

                externalNativeBuild {
                    if (project.hasProperty("ndkBuild")) {
                        ndkBuild {
                            path "src/main/cpp/Android.mk"
                        }
                    } else {
                        cmake {
                            path "src/main/cpp/CMakeLists.txt"
                        }
                    }
                }

                buildFeatures {
                    prefabPublishing true
                }

                prefab {
                    foo {
                        headers "src/main/cpp/include"
                        libraryName "libfoo_static"
                    }
                }
            }
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("CMakeLists.txt").writeText(
            """
            cmake_minimum_required(VERSION 3.6)
            project(foo VERSION 1.0.0 LANGUAGES CXX)

            add_library(foo STATIC foo.cpp)
            target_include_directories(foo PUBLIC include)
            set_target_properties(foo PROPERTIES OUTPUT_NAME "foo_static")
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("Android.mk").writeText(
            """
            LOCAL_PATH := $(call my-dir)

            include $(CLEAR_VARS)
            LOCAL_MODULE := foo
            LOCAL_MODULE_FILENAME := libfoo_static
            LOCAL_SRC_FILES := foo.cpp
            LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
            LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
            include $(BUILD_STATIC_LIBRARY)
            """.trimIndent()
        )

        project.execute("assemble$variant")

        project.getSubproject(gradleModuleName).assertThatAar(variant) {
            containsFile("prefab/prefab.json")
            containsFile("prefab/modules/$gradleModuleName/module.json")
        }

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        verifyModule(packageDir, gradleModuleName, true, "libfoo_static")
    }

    @Test
    fun `modules with hyphenated names that are prefixes of other modules match appropriately`() {
        val subproject = project.getSubproject(gradleModuleName)

        subproject.getMainSrcDir("cpp").resolve("CMakeLists.txt").appendText(
            """

            add_library(foo-jni SHARED foo.cpp)
            target_include_directories(foo-jni PUBLIC include)
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("Android.mk").appendText(
            """

            include $(CLEAR_VARS)
            LOCAL_MODULE := foo-jni
            LOCAL_SRC_FILES := foo.cpp
            LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
            LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
            include $(BUILD_SHARED_LIBRARY)
            """.trimIndent()
        )

        project.execute("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")

        verifyModule(packageDir, gradleModuleName, static = false)
    }
}
