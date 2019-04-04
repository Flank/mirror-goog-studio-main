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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.logging.RecordingLoggingEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule

import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CmakeAndroidGradleBuildExtensionsKtTest {
    @Rule
    @JvmField
    val projectParentFolder = TemporaryFolder()
    private val logger = RecordingLoggingEnvironment()
    private val abi = createCmakeProjectCxxAbiForTest(projectParentFolder)
    private val cmakeListsFolder = abi.variant.module.makeFile
    private val cmakeToolchainFile = abi.variant.module.cmake!!.cmakeToolchainFile
    private val moduleRootFolder = abi.variant.module.moduleRootFolder
    private val ndkFolder = abi.variant.module.ndkFolder

    @After
    fun after() {
        logger.close()
    }

    @Test
    fun androidNdkWasNotDefined() {
        val executionContext = wrapCmakeListsForCompilerSettingsCaching(
            abi,
            listOf(
                "-H${cmakeListsFolder.path}",
                "-DX=Y")
        )
        assertStateNotWrapped()
        assertThat(executionContext.cmakeListsFolder.path).isEqualTo(
            "$moduleRootFolder${File.separatorChar}src")
        assertThat(executionContext.args).containsExactly(
            "-H${cmakeListsFolder.path}",
            "-DX=Y")
    }

    @Test
    fun prepareCmakeToWriteSettingsToCache() {
        val executionState = wrapCmakeListsForCompilerSettingsCaching(
            abi,
            listOf(
                "-H${cmakeListsFolder.path}",
                "-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchainFile",
                "-D$ANDROID_NDK=$ndkFolder",
                "-DX=Y")
        )
        assertStateReadyToBuildCache(executionState)
    }

    @Test
    fun writeBackToCacheAfterCmakeBuild() {
        val executionState = wrapCmakeListsForCompilerSettingsCaching(
            abi,
            listOf(
                "-H${cmakeListsFolder.path}",
                "-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchainFile",
                "-D$ANDROID_NDK=$ndkFolder",
                "-DX=Y")
        )
        assertStateReadyToBuildCache(executionState)

        simulateCmakeProjectGeneration()

        writeCompilerSettingsToCache(abi)

        assertStateWroteCache()
    }

    @Test
    fun cmakeRunThatUsesCacheSettings() {
        fun wrap() {
            wrapCmakeListsForCompilerSettingsCaching(
                abi,
                listOf(
                    "-H${cmakeListsFolder.path}",
                    "-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchainFile",
                    "-D$ANDROID_NDK=$ndkFolder",
                    "-DX=Y"
                )
            )
        }
        wrap()
        simulateCmakeProjectGeneration()
        writeCompilerSettingsToCache(abi)

        // Above is setup, this is the test
        wrap()
        assertStateUsedCacheToWrap()
        simulateCmakeProjectGeneration()
        assertAfterCmakeSuccessfullyUsedCache()
    }

    @Test
    fun cmakeRunWithDeletedCacheFolder() {
        val deleteableRootFolder = abi.variant.module.compilerSettingsCacheFolder
        fun wrap() {
            wrapCmakeListsForCompilerSettingsCaching(
                abi,
                listOf(
                    "-H${cmakeListsFolder.path}",
                    "-D$CMAKE_TOOLCHAIN_FILE=$cmakeToolchainFile",
                    "-D$ANDROID_NDK=$ndkFolder",
                    "-DX=Y"
                )
            )
        }
        wrap()
        simulateCmakeProjectGeneration()
        writeCompilerSettingsToCache(abi)
        wrap()
        simulateCmakeProjectGeneration()

        // Above is setup, this is the test
        deleteableRootFolder.deleteRecursively()
        writeCompilerSettingsToCache(abi)
        assertStateWroteCache(false)
    }

    @Test
    fun noCmakeLists() {
        val executionContext = wrapCmakeListsForCompilerSettingsCaching(
            abi,
            listOf("-DX=Y")
        )
        assertThat(executionContext.cmakeListsFolder.path).isEqualTo(
            "$moduleRootFolder${File.separatorChar}src")
        assertThat(executionContext.args).containsExactly("-DX=Y")
        assertStateNotWrapped()
    }

    @Test
    fun wrapDisabled() {
        val executionContext = wrapCmakeListsForCompilerSettingsCaching(
            abi,
            listOf(
                "-D$ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED=0",
                "-DX=Y")
        )
        assertThat(executionContext.cmakeListsFolder.path).isEqualTo(
            "$moduleRootFolder${File.separatorChar}src")
        assertThat(executionContext.args).containsExactly(
            "-DX=Y")
        assertStateNotWrapped()
    }

    /**
     * Pretends to be CMake by looking at the current state and acting like CMake would in that
     * situation.
     */
    private fun simulateCmakeProjectGeneration() {
        // Simulate a CMake execution
        val buildGenerationState = CmakeBuildGenerationState(listOf(
            CmakePropertyValue.from(CMAKE_C_COMPILER_ABI, "1"),
            CmakePropertyValue.from(CMAKE_CXX_COMPILER_ABI, "1"),
            CmakePropertyValue.from(C_TEST_WAS_RUN, "1"),
            CmakePropertyValue.from(CXX_TEST_WAS_RUN, "1"),
            CmakePropertyValue.from(CMAKE_C_ABI_COMPILED, "1"),
            CmakePropertyValue.from(CMAKE_CXX_ABI_COMPILED, "1"),
            CmakePropertyValue.from(CMAKE_C_SIZEOF_DATA_PTR, "1"),
            CmakePropertyValue.from(CMAKE_CXX_SIZEOF_DATA_PTR, "1"),
            CmakePropertyValue.from(CMAKE_C_SIZEOF_DATA_PTR, "1"),
            CmakePropertyValue.from(CMAKE_CXX_SIZEOF_DATA_PTR, "1"),
            CmakePropertyValue.from(CMAKE_SIZEOF_VOID_P, "1")
        ))
        buildGenerationState.toFile(abi.cmake!!.buildGenerationStateFile)
        val isCacheUsed = abi.cmake!!.toolchainSettingsFromCacheFile.isFile
        val cacheUsed = CmakeCompilerCacheUse(isCacheUsed)
        cacheUsed.toFile(abi.cmake!!.compilerCacheUseFile)
    }

    /**
     * This is the state when neither cmakeToolchainFile nor CMakeLists.txt has been wrapped.
     */
    private fun assertStateNotWrapped() {
        with(abi.cmake!!) {
            assertThat(abi.gradleBuildOutputFolder.isDirectory).isFalse()
            assertThat(cacheKeyFile.isFile).isFalse()

            assertThat(buildGenerationStateFile.isFile).isFalse()
            assertThat(compilerCacheUseFile.isFile).isFalse()
            assertThat(compilerCacheWriteFile.isFile).isFalse()
            assertThat(toolchainSettingsFromCacheFile.isFile).isFalse()
            assertThat(toolchainWrapperFile.isFile).isFalse()
            assertThat(cmakeListsWrapperFile.isFile).isFalse()
        }
    }

    /**
     * This is the state when wrapping CMakeLists.txt has been wrapped but not the cmakeToolchainFile.
     * CMakeLists.txt has been wrapped so it will produce buildGenerationStateFile when executed.
     * Toolchain has *not* been wrapped because there was no compiler settings cached yet.
     */
    private fun assertStateReadyToBuildCache(executionState : CmakeExecutionConfiguration) {
        with(abi.cmake!!) {
            // Things that exist on disk
            assertThat(abi.gradleBuildOutputFolder.isDirectory).isTrue()
            assertThat(cacheKeyFile.isFile).isTrue()
            assertThat(cmakeListsWrapperFile.isFile).isTrue()
            // Things that don't exist on disk
            assertThat(buildGenerationStateFile.isFile).isFalse()
            assertThat(compilerCacheUseFile.isFile).isFalse()
            assertThat(compilerCacheWriteFile.isFile).isFalse()
            assertThat(toolchainSettingsFromCacheFile.isFile).isFalse()
            assertThat(toolchainWrapperFile.isFile).isFalse()

            assertThat(executionState.cmakeListsFolder)
                .named("CMakeLists.txt was not wrapped in the expected location")
                .isEqualTo(abi.gradleBuildOutputFolder)

            val parsed = parseCmakeArguments(executionState.args)
            val cmakeListsFromArgs = parsed.getCmakeListsPathValue()

            assertThat(File(cmakeListsFromArgs))
                .named("CMakeLists.txt was not wrapped in the expected location")
                .isEqualTo(abi.gradleBuildOutputFolder)

            val cmakeToolChainFile = parsed.getCmakeProperty(CMAKE_TOOLCHAIN_FILE)
            assertThat(File(cmakeToolChainFile))
                .named("Toolchain was wrapped but it wasn't expected")
                .isEqualTo(cmakeToolchainFile)

            val androidNdk = parsed.getCmakeProperty(ANDROID_NDK)
            assertThat(File(androidNdk))
                .named("Expected to find $ANDROID_NDK argument")
                .isEqualTo(ndkFolder)
        }
    }

    /**
     * In this state, CMake has executed and gathered some settings that were then cached for later
     * use.
     * When cache root folder has been deleted manually, there will be some leftover cached settings
     * from previous cache generation. However as long as the compilerCacheUseFile content indicates
     * cache was not used, it should satisfy our check.
     */
    private fun assertStateWroteCache(ensureClean: Boolean = true) {
        with(abi.cmake!!) {
            // Things that exist on disk
            assertThat(abi.gradleBuildOutputFolder.isDirectory).isTrue()
            assertThat(cacheKeyFile.isFile).isTrue()
            assertThat(cmakeListsWrapperFile.isFile).isTrue()
            assertThat(buildGenerationStateFile.isFile).isTrue()
            assertThat(compilerCacheWriteFile.isFile).isTrue()
            assertThat(compilerCacheUseFile.isFile).isTrue()
            if (ensureClean) {
                // Things that don't exist on disk
                assertThat(toolchainSettingsFromCacheFile.isFile).isFalse()
                assertThat(toolchainWrapperFile.isFile).isFalse()
            }

            val compilerCacheUse = CmakeCompilerCacheUse.fromFile(compilerCacheUseFile)
            assertThat(compilerCacheUse.isCacheUsed)
                .named("cache was used by CMake build so we shouldn't write it back")
                .isFalse()

            val compilerCacheWrite = CmakeCompilerCacheWrite.fromFile(compilerCacheWriteFile)
            assertThat(compilerCacheWrite.status)
                .named(compilerCacheWrite.status)
                .isEmpty()
        }
    }

    /**
     * In this state, we're wrapping cmakeToolchainFile because the compiler settings have been found.
     * This is *before* CMake is executed to actually use the cache.
     */
    private fun assertStateUsedCacheToWrap() {
        with(abi.cmake!!) {
            assertThat(abi.gradleBuildOutputFolder.isDirectory).isTrue()
            assertThat(cacheKeyFile.isFile).isTrue()
            assertThat(buildGenerationStateFile.isFile).isTrue()
            assertThat(compilerCacheUseFile.isFile).isTrue()
            assertThat(compilerCacheWriteFile.isFile).isTrue()
            assertThat(toolchainSettingsFromCacheFile.isFile).isTrue()
            assertThat(toolchainWrapperFile.isFile).isTrue()
            assertThat(cmakeListsWrapperFile.isFile).isTrue()
        }
    }

    private fun assertAfterCmakeSuccessfullyUsedCache() {
        with(abi.cmake!!) {
            assertThat(abi.gradleBuildOutputFolder.isDirectory).isTrue()
            assertThat(cacheKeyFile.isFile).isTrue()
            assertThat(buildGenerationStateFile.isFile).isTrue()
            assertThat(compilerCacheUseFile.isFile).isTrue()
            assertThat(compilerCacheWriteFile.isFile).isTrue()
            assertThat(toolchainSettingsFromCacheFile.isFile).isTrue()
            assertThat(toolchainWrapperFile.isFile).isTrue()
            assertThat(cmakeListsWrapperFile.isFile).isTrue()

            val compilerCacheUse = CmakeCompilerCacheUse.fromFile(compilerCacheUseFile)
            assertThat(compilerCacheUse.isCacheUsed)
                .named("expected CMake to use cache")
                .isTrue()
        }
    }
}