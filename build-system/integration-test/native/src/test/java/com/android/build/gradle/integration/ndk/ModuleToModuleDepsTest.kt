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
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
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
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.decodeLoggingMessage
import com.android.build.gradle.internal.cxx.logging.text
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * CMake lib<-app project where lib is published as Prefab
 */
@RunWith(Parameterized::class)
class ModuleToModuleDepsTest(
    private val appBuildSystem: BuildSystemConfig,
    private val libBuildSystem: BuildSystemConfig,
    appUsesPrefabTag: String,
    libUsesPrefabPublishTag: String,
    private val libExtension: String,
    appStlTag: String,
    libStlTag: String,
    private val outputStructureType: OutputStructureType,
    private val headerType: HeaderType
) {
    private val appUsesPrefab = appUsesPrefabTag == ""
    private val libUsesPrefabPublish = libUsesPrefabPublishTag == ""
    private val appStl = appStlTag.substringAfter(":")
    private val libStl = libStlTag.substringAfter(":")
    private val effectiveAppStl = effectiveStl(appStl, appBuildSystem)
    private val effectiveLibStl = effectiveStl(libStl, libBuildSystem)
    private val config = "$appBuildSystem:$appStl $libBuildSystem:$libStl:$libExtension"

    sealed class BuildSystemConfig {
        abstract val build : String
        data class CMake(val version : String) : BuildSystemConfig() {
            override val build = "CMake"
            override fun toString() = "cmake$version"
        }

        object NdkBuild : BuildSystemConfig() {
            override val build = "NdkBuild"
            override fun toString() = "ndk-build"
        }
    }
    sealed class OutputStructureType {
        object Normal : OutputStructureType() {
            override fun toString() = ""
        }
        object OutOfTreeBuild : OutputStructureType() {
            override fun toString() = " [out-of-tree]"
        }
    }
    sealed class HeaderType(
        val createHeaderDir : Boolean,
        val createHeaderFile : Boolean
    ) {
        object Normal : HeaderType(true, true) {
            override fun toString() = ""
        }
        object DirectoryButNoFile : HeaderType(true, false) {
            override fun toString() = " [header-dir-only]"
        }
        object None : HeaderType(false, false) {
            override fun toString() = " [no-header]"
        }
    }
    private val app = MinimalSubProject.app()
    private val lib = MinimalSubProject.lib()
    private val multiModule = MultiModuleTestProject.builder()
        .subproject(":app", app)
        .subproject(":lib", lib)
        .dependency(app, lib)
        .build()

    private val libOutputRoot : File get() =
        if (outputStructureType == OutputStructureType.Normal) project.getSubproject("lib").buildDir.parentFile
        else {
            // The output folder for lib is at $PROJECT_ROOT/out/lib
            project.getSubproject("lib").buildDir.parentFile.parentFile.parentFile.resolve("out/lib")
        }

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(multiModule)
            .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
            .create()

    companion object {
        @Parameterized.Parameters(
            name = "app.so={0}{2}{5} lib{4}={1}{3}{6}{7}{8}")
        @JvmStatic
        fun data() : Array<Array<Any?>> {
            val tests = cartesianOf(
                    arrayOf<BuildSystemConfig>(NdkBuild) + CMakeVersion.FOR_TESTING.map { CMake(it.version) }.toTypedArray(),
                    arrayOf<BuildSystemConfig>(NdkBuild) + CMakeVersion.FOR_TESTING.map { CMake(it.version) }.toTypedArray(),
                    arrayOf("", ":no-prefab"),
                    arrayOf("", ":no-prefab-publish"),
                    arrayOf(".a", ".so"),
                    arrayOf("", ":c++_static", ":c++_shared"),
                    arrayOf("", ":c++_static", ":c++_shared"),
                    arrayOf(OutputStructureType.Normal, OutputStructureType.OutOfTreeBuild),
                    arrayOf(HeaderType.Normal, HeaderType.DirectoryButNoFile, HeaderType.None)
                )
                .minimizeUsingTupleCoverage(4)
            println("Test configuration count: ${tests.size}")
            return tests
        }
    }

    @Before
    fun setUp() {
        val staticOrShared = if (libExtension == ".a") "STATIC" else "SHARED"
        val appStanza = when(appBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${appBuildSystem.version}"
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val appStlStanza = when {
            appStl == "" -> ""
            appBuildSystem is CMake -> """
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$appStl")
                """.trimIndent()
            appBuildSystem is NdkBuild -> """
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$appStl")
                """.trimIndent()
            else -> error(appStl)
        }
        val testRoot = project.getSubproject(":app")
            .buildFile
            .parentFile
            .parentFile
            .parentFile
            .absolutePath.replace("\\", "/")
        val appStructureStanza = if (outputStructureType == OutputStructureType.Normal) "" else when(appBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val libStructureStanza = if (outputStructureType== OutputStructureType.Normal) "" else when(libBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.cmake.buildStagingDirectory="$testRoot/out/${'$'}{project.path.replace(":", "/")}/nativeConfigure"
                project.buildDir = new File("$testRoot/out/${'$'}{project.path.replace(":", "/")}/build")
                """.trimIndent()
            else -> error("$appBuildSystem")
        }

        project.getSubproject(":app").buildFile.appendText(
            """
            apply plugin: 'com.android.application'

            android {
                compileSdkVersion $DEFAULT_COMPILE_SDK_VERSION
                ndkPath "${project.ndkPath}"
                defaultConfig {
                    ndk {
                        abiFilters "arm64-v8a"
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
            $appStlStanza
            $appStructureStanza

            """.trimIndent())

        val libStanza = when(libBuildSystem) {
            is CMake -> """
                android.externalNativeBuild.cmake.path="CMakeLists.txt"
                android.externalNativeBuild.cmake.version="${libBuildSystem.version}"
                """.trimIndent()
            NdkBuild -> """
                android.externalNativeBuild.ndkBuild.path="Android.mk"
                """.trimIndent()
            else -> error("$appBuildSystem")
        }
        val libStlStanza = when {
            libStl == "" -> ""
            libBuildSystem is CMake -> """
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=$libStl")
                """.trimIndent()
            libBuildSystem == NdkBuild -> """
                android.defaultConfig.externalNativeBuild.ndkBuild.arguments.add("APP_STL=$libStl")
                """.trimIndent()
            else -> error(libStl)
        }
        val headerStanza = if (headerType.createHeaderDir) "headers \"src/main/cpp/include\"\n" else ""

        project.getSubproject(":lib").buildFile.appendText(
            """
            android {
                buildFeatures {
                    prefabPublishing $libUsesPrefabPublish
                }
                prefab {
                    foo {
                        $headerStanza
                        libraryName "libfoo"
                    }
                }
            }
            $libStanza
            $libStlStanza
            $libStructureStanza
            """)
        val header =
            project.getSubproject(":lib").buildFile.resolveSibling("src/main/cpp/include/foo.h")
        if (headerType.createHeaderDir) header.parentFile.mkdirs()
        if (headerType.createHeaderFile) header.writeText("int foo();")

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
                    project(ProjectName)
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
                    project(ProjectName)
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
        if (headerType.createHeaderFile) {
            appSource.writeText(
                """
                #include <foo.h>
                int callFoo() { return foo(); }
                """.trimIndent())
        } else {
            appSource.writeText(
                """
                int foo();
                int callFoo() { return foo(); }
                """.trimIndent())
        }

        enableCxxStructuredLogging(project)
    }

    /**
     * Returns true if this configuration is expected to fail.
     */
    private fun expectGradleConfigureError() : Boolean {
        if (expectSingleStlViolationError()) return true
        if (!prefabConfiguredCorrectly) return true

        // ndk-build can't consume configuration-only packages until a future NDK
        // update and corresponding prefab update.
        if (appBuildSystem == NdkBuild) {
            // Error when this test was written:
            //   Android.mk:foo: LOCAL_SRC_FILES points to a missing file
            return true
        }

        return false
    }

    /**
     * Returns true if the project has configured prefab correctly.
     *
     * At the time of writing, the following errors will be emitted when building the app if prefab
     * is not configured correctly, either because the app was not configured to consume prefab
     * packages or because the library was not configured to produce them.
     *
     * ndk-build:
     * Are you sure your NDK_MODULE_PATH variable is properly defined
     *
     * CMake:
     * Add the installation prefix of "lib" to CMAKE_PREFIX_PATH
     */
    private val prefabConfiguredCorrectly = appUsesPrefab && libUsesPrefabPublish

    /**
     * Returns true if this configuration is expected to fail at build time.
     */
    private fun expectGradleBuildError() : Boolean {
        // If ndk-build produces no library then it will eventually become a build error
        return expectNdkBuildProducesNoLibrary()
    }

    // https://developer.android.com/ndk/guides/cpp-support#one_stl_per_app
    private fun expectSingleStlViolationError() : Boolean {
        if (expectErrorCXX1211()) return true
        if (expectErrorCXX1212()) return true
        return false
    }

    private fun effectiveStl(stl: String, buildSystem: BuildSystemConfig): String {
        if (stl != "") {
            return stl
        }

        return when (buildSystem) {
            NdkBuild -> ""
            else -> "c++_static"
        }
    }

    /**
     * These configurations produce
     *      [CXX1211] Library is a shared library with a statically linked STL and cannot be used
     *      with any library using the STL
     */
    private fun expectErrorCXX1211() =
        libExtension == ".so" && effectiveLibStl == "c++_static" && effectiveAppStl.isNotEmpty()

    /**
     * These configurations produce
     *      [CXX1212] User is using a static STL but library requires a shared STL
     */
    private fun expectErrorCXX1212() =
        libExtension == ".so" && effectiveAppStl == "c++_static" && effectiveLibStl == "c++_shared"

    /**
     * When ndk-build is configure to produce .a but has shared STL it will silently produce
     * no .a file.
     */
    private fun expectNdkBuildProducesNoLibrary() =
        libBuildSystem == NdkBuild && effectiveLibStl == "c++_shared" && libExtension == ".a"

    @Test
    fun `app configure`() {
        println(config) // Print identifier for this configuration
        val executor = project.executor()

        // Expect failure for cases when configuration failure is expected.
        if (expectGradleConfigureError()) {
            executor.expectFailure()
        }

        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")

        // Check for expected Gradle error message (if any)
        if (expectGradleConfigureError()) {
            return
        }

        // Check that the output is known but does not yet exist on disk.
        val libAbi = recoverExistingCxxAbiModels(libOutputRoot).single { it.abi == Abi.X86 }
        val libConfig = AndroidBuildGradleJsons.getNativeBuildMiniConfig(libAbi, null)
        if (expectNdkBuildProducesNoLibrary()) {
            assertThat(libConfig.libraries.values).isEmpty()
            return
        }
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
        println(config) // Print identifier for this configuration

        // There is no point in testing build in the cases where configuration is expected to fail.
        // Those cases will be covered by other tests
        Assume.assumeFalse(expectGradleConfigureError())

        val executor = project.executor()

        // Expect failure for cases when build failure is expected.
        if (expectGradleBuildError()) {
            executor.expectFailure()
        }

        executor.run(":app:build${appBuildSystem.build}Debug[arm64-v8a]")
    }

    @Test
    fun `check single STL violation CXX1211`() {
        Assume.assumeTrue(prefabConfiguredCorrectly)
        Assume.assumeTrue(expectErrorCXX1211()) // Only run the CXX1211 cases
        val executor = project.executor()
        executor.expectFailure()
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        val errors = project.readStructuredLogs(::decodeLoggingMessage)
            .filter { it.level == LoggingMessage.LoggingLevel.ERROR}
        val error = errors.map { it.diagnosticCode }.single()
        assertThat(error)
            .named(errors.map { it.text() }.single())
            .isEqualTo(1211)
    }

    @Test
    fun `check single STL violation CXX1212`() {
        Assume.assumeTrue(prefabConfiguredCorrectly)
        Assume.assumeTrue(expectErrorCXX1212()) // Only run the CXX1212 cases
        val executor = project.executor()
        executor.expectFailure()
        executor.run(":app:configure${appBuildSystem.build}Debug[arm64-v8a]")
        val errors = project.readStructuredLogs(::decodeLoggingMessage)
            .filter { it.level == LoggingMessage.LoggingLevel.ERROR }
        val error = errors.map { it.diagnosticCode }.single()
        assertThat(error)
            .named(errors.map { it.text() }.single())
            .isEqualTo(1212)
    }

    @Test
    fun `test sync`() {
        Assume.assumeFalse(expectGradleConfigureError())
        // Simulate an IDE sync
        project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING) // CMake cannot detect compiler attributes
            .fetchNativeModules(ModelBuilderV2.NativeModuleParams())
    }
}
