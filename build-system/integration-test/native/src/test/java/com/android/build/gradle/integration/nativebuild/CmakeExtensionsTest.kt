/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.NativeAndroidProject
import org.junit.Rule
import org.junit.Test
import com.android.build.gradle.integration.common.truth.NativeAndroidProjectSubject.assertThat
import org.junit.Before
import java.io.File
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CXX_LOCAL_PROPERTIES_CACHE_DIR
import com.android.build.gradle.internal.cxx.configure.CmakeBuildGenerationState
import com.android.build.gradle.internal.cxx.configure.CmakeCompilerCacheKey
import com.android.build.gradle.internal.cxx.configure.CmakeCompilerCacheUse
import com.android.build.gradle.internal.cxx.configure.CmakeCompilerCacheWrite
import com.android.build.gradle.internal.cxx.configure.CmakeCompilerSettingsCache
import com.android.build.gradle.internal.cxx.configure.CmakePropertyValue
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.*
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.buildGenerationStateFile
import com.android.build.gradle.internal.cxx.model.cacheKeyFile
import com.android.build.gradle.internal.cxx.model.cmakeListsWrapperFile
import com.android.build.gradle.internal.cxx.model.compilerCacheUseFile
import com.android.build.gradle.internal.cxx.model.compilerCacheWriteFile
import com.android.build.gradle.internal.cxx.model.createCxxAbiModelFromJson
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.toolchainWrapperFile
import com.android.build.gradle.options.BooleanOption.*
import com.android.utils.FileUtils.join
import org.junit.Ignore

/**
 * Parameterized JUnit test for CMake compiler settings cache.
 * The parameters are:
 * cmakeVersion - the CMake version to test with. This CMake must be installed in prebuilts.
 * enableCaching - true or false means user set ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED.
 *   null means the user didn't set it.
 * alternateCacheFolder - when not null it means the user set local.settings property cxx.cache.dir.
 * errorInCmakeLists - when true, the user's CMakeLists.txt has an error in it. In this case
 *   compiler settings may not be reliable and shouldn't be cached.
 */
@RunWith(Parameterized::class)
@Ignore("Re-enable at the end of CMake cohabitation")
class CmakeExtensionsTest(
    private val cmakeVersion : String,
    private val enableCaching : Boolean?,
    private val alternateCacheFolder : String?,
    private val errorInCmakeLists : Boolean) {

    companion object {
        @Parameterized.Parameters(name = "model = {0} {1} {2} {3}")
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            return arrayOf(
                arrayOf("3.10.2", true, null, true),
                arrayOf<Any?>("3.6.0", true, ".cxx-alternate", true),
                arrayOf("3.10.2", false, null, false),
                arrayOf<Any?>("3.10.2", false, ".cxx-alternate", false),
                arrayOf("3.6.0", false, null, false),
                arrayOf("3.10.2", null, ".cxx-alternate", false),
                arrayOf("3.6.0", null, null, false)
            )
        }
    }

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder()
                .withNativeDir("cpp")
                .useCppSource(true)
                .build()
        )
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.buildFile, """
                apply plugin: 'com.android.application'
                android.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                android.externalNativeBuild.cmake.path "src/main/cpp/CMakeLists.txt"
                android.externalNativeBuild.cmake.version "$cmakeVersion"
                android.defaultConfig.ndk.abiFilters "x86_64"
                """.trimIndent()
        )
        // any backslash in the path must be escaped.
        val ninja = GradleTestProject.getPreferredNinja().absolutePath.replace("\\", "\\\\")
        when (enableCaching) {
            null -> TestFileUtils.appendToFile(
                project.buildFile,
                "android.defaultConfig.externalNativeBuild.cmake.arguments " +
                        "\"-DCMAKE_MAKE_PROGRAM=$ninja\", " +
                        "\"-DX=x==y\"")
            else -> TestFileUtils.appendToFile(
                project.buildFile,
                "android.defaultConfig.externalNativeBuild.cmake.arguments " +
                        "\"-DANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED=$enableCaching\", " +
                        "\"-DCMAKE_MAKE_PROGRAM=$ninja\", " +
                        "\"-DX=x==y\"")
        }
        val cmakeLists = File(project.buildFile.parent, "src/main/cpp/CMakeLists.txt")
        TestFileUtils.appendToFile(
            cmakeLists, """
                cmake_minimum_required(VERSION 3.4.1)
                add_library(native-lib SHARED hello-jni.cpp extra-header.hpp)
                find_library(log-lib log)
                target_link_libraries(native-lib ${'$'}{log-lib})
                """.trimIndent()
        )
        if (errorInCmakeLists) {
            TestFileUtils.appendToFile(
                cmakeLists,
               """"message(FATAL_ERROR "fatal error in compiler settings cache test")""")
        }
        val extraHeader = File(project.buildFile.parent, "src/main/cpp/extra-header.hpp")
        TestFileUtils.appendToFile(
            extraHeader,
            """
                // Extra header file that is referenced in CMakeLists.txt
                """.trimIndent()
        )

        if (alternateCacheFolder != null) {
            project.localProp.appendText("$CXX_LOCAL_PROPERTIES_CACHE_DIR=" +
                    "${File(project.testDir, alternateCacheFolder).absolutePath.replace("\\", "\\\\")}\n")
        }
    }

    private fun getAbiConfiguration() : CxxAbiModel {
        val modelFile = join(project.testDir, ".cxx", "cmake", "debug", "x86_64", "build_model.json")
        return createCxxAbiModelFromJson(modelFile.readText())
    }

    @Test
    fun basicTest() {
        val syncIssue = project.model().fetchAndroidProjects().onlyModelSyncIssues
        assertThat(syncIssue).hasSize(0)
        project.model()
            .with(ENABLE_NATIVE_COMPILER_SETTINGS_CACHE, true)
            .fetch(NativeAndroidProject::class.java)

        val config = getAbiConfiguration()
        assertThat(config.jsonFile.isFile).named(config.jsonFile.toString())
            .isEqualTo(!errorInCmakeLists)

        when(errorInCmakeLists) {
            true -> checkNonCaching(config)
            false -> when (enableCaching) {
                null, true -> checkCaching(config)
                false -> checkNonCaching(config)
            }
        }
    }

    /**
     * This function checks that the cache was written as expected.
     *
     * Then, it simulates the user manually deleting the .externalNativeBuild folder and resyncing.
     * This time, the cache should be used (and so cache results not written back). This test
     * checks that both of those things are true.
     *
     * At the end, it also checks CMake properties before and after using the the cache to see
     * if build properties were the same (up to some properties that are known to not matter).
     */
    private fun checkCaching(config: CxxAbiModel) {
        val cmake = config.cmake!!
        assertThat(cmake.buildGenerationStateFile.isFile)
            .named(cmake.buildGenerationStateFile.toString()).isTrue()
        assertThat(cmake.cmakeListsWrapperFile.isFile)
            .named(cmake.cmakeListsWrapperFile.toString()).isTrue()
        assertThat(cmake.toolchainWrapperFile.isFile)
            .named(cmake.toolchainWrapperFile.toString()).isFalse()
        assertThat(cmake.cacheKeyFile.isFile)
            .named(cmake.cacheKeyFile.toString()).isTrue()
        assertThat(cmake.buildGenerationStateFile.isFile)
            .named(cmake.buildGenerationStateFile.toString()).isTrue()
        assertThat(cmake.compilerCacheWriteFile.isFile)
            .named(cmake.compilerCacheWriteFile.toString()).isTrue()

        val buildGenerationState =
            CmakeBuildGenerationState.fromFile(cmake.buildGenerationStateFile)

        // The first time, the cache should not be used.
        val cacheFolder = if (alternateCacheFolder != null) {
            File(project.testDir, alternateCacheFolder)
        } else {
            File(project.testDir, CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
        }
        assertThat(cacheFolder.isDirectory)
            .named(cacheFolder.toString())
            .isTrue()
        val cache = CmakeCompilerSettingsCache(cacheFolder)

        assertThat(cmake.compilerCacheUseFile.isFile).isTrue()
        val cacheUse = CmakeCompilerCacheUse.fromFile(cmake.compilerCacheUseFile)
        assertThat(cacheUse.isCacheUsed).isFalse()

        val cacheWrite = CmakeCompilerCacheWrite.fromFile(cmake.compilerCacheWriteFile)
        assertThat(cacheWrite.cacheWritten).isTrue()

        // Delete the .externalNativeBuild folder and sync
        File(project.testDir, "$CXX_DEFAULT_CONFIGURATION_SUBFOLDER/cmake").deleteRecursively()
        File(project.testDir, "$CXX_DEFAULT_CONFIGURATION_SUBFOLDER/gradle").deleteRecursively()
        val nativeProject2 = project.model()
            .with(ENABLE_NATIVE_COMPILER_SETTINGS_CACHE, true)
            .fetch(NativeAndroidProject::class.java)

        assertThat(nativeProject2).hasArtifactGroupsNamed("debug", "release")
        assertThat(nativeProject2.artifacts.first()!!.sourceFiles.size).isEqualTo(1)

        val compilerCacheUse = CmakeCompilerCacheUse.fromFile(cmake.compilerCacheUseFile)

        assertThat(compilerCacheUse.isCacheUsed).isTrue()
        val cacheKey = CmakeCompilerCacheKey.fromFile(cmake.cacheKeyFile)
        assertThat(cacheKey.ndkSourceProperties.getValue(SDK_PKG_DESC))
            .named(cacheKey.toJsonString())
            .isEqualTo("Android NDK")
        val cacheValueInCache = cache.tryGetValue(cacheKey)!!
        assertThat(cacheValueInCache)
            .isNotNull()

        val cacheKey2 = CmakeCompilerCacheKey.fromFile(cmake.cacheKeyFile)
        assertThat(cacheKey2).isEqualTo(cacheKey)

        val compilerCacheUse2 = CmakeCompilerCacheUse.fromFile(cmake.compilerCacheUseFile)
        assertThat(compilerCacheUse2.isCacheUsed).isTrue()
        val buildGenerationState2 =
            CmakeBuildGenerationState.fromFile(cmake.buildGenerationStateFile)

        val cacheWrite2 = CmakeCompilerCacheWrite.fromFile(cmake.compilerCacheWriteFile)
        assertThat(cacheWrite2.cacheWritten).isEqualTo(false)

        // Now we have before cache use and after cache use generation state. They should
        // be the same
        (removeIrrelevantProperties(buildGenerationState.properties) zip
                removeIrrelevantProperties(buildGenerationState2.properties)).onEach { (before, after) ->
            assertThat(after.name).isEqualTo(before.name)
            assertThat(after.value).named(after.name).isEqualTo(before.value)
        }
    }

    /**
     * The user disabled caching. Make sure it worked.
     */
    private fun checkNonCaching(config: CxxAbiModel) {
        assertThat(config.cmake!!.compilerCacheWriteFile.exists())
            .named(config.cmake!!.compilerCacheWriteFile.toString())
            .isFalse()
    }

    /**
     * Helper function removes properties that don't matter wit respect to whether the cache was
     * accurate or not
     */
    private fun removeIrrelevantProperties(properties: List<CmakePropertyValue>)
            : List<CmakePropertyValue> {
        return properties.filter {
            when {
                it.name.startsWith("INCLUDE_CMAKE_TOOLCHAIN_FILE_IF_REQUIRED") -> false
                it.name.startsWith("_INCLUDED_TOOLCHAIN_FILE") -> false
                it.name.startsWith("CMAKE_TOOLCHAIN_FILE") -> false
                it.name.startsWith("ANDROID_GRADLE_BUILD") -> false
                it.name.startsWith("CMAKE_INSTALL_PREFIX") -> false
                it.name.startsWith("CMAKE_SYSTEM_PREFIX_PATH") -> false
                it.name.endsWith("_FORCED") -> false
                it.name.startsWith("CMAKE_MATCH_") -> false
                it.name.startsWith("__") -> false
                else -> true
            }
        }
    }
}