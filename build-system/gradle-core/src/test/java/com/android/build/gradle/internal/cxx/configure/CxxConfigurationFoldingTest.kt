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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CxxGradleTaskModel.VariantBuild
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.tasks.NativeBuildSystem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CxxConfigurationFoldingTest {
    @Test
    fun `CMake variants that fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(variantName = "debug")
            val config2 = configurationParameters.copy(variantName = "debug2")
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `Dependency model CMake variants that fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(variantName = "debug")
            val config2 = configurationParameters.copy(variantName = "debug2")
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val result = createFoldedCxxTaskDependencyModel(allAbis)
            val variantBuild = result.tasks["externalNativeBuildDebug"] as VariantBuild
            assertThat(variantBuild.representatives).isNotEmpty()
            variantBuild.representatives.forEach { abi ->
                // Make sure republish folder is different than soFolder
                assertThat(abi.soFolder).isNotEqualTo(abi.soRepublishFolder)
            }
        }
    }

    @Test
    fun `ndk-build variants that fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    buildSystem = NativeBuildSystem.NDK_BUILD)
            val config2 = configurationParameters.copy(variantName = "debug2",
                    buildSystem = NativeBuildSystem.NDK_BUILD)
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildNdkBuildDebug" to "configureNdkBuildDebug"
            )
        }
    }

    @Test
    fun `ndk-build variants that don't fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    buildSystem = NativeBuildSystem.NDK_BUILD)
            val config2 = configurationParameters.copy(variantName = "release",
                    buildSystem = NativeBuildSystem.NDK_BUILD,
                    isDebuggable = false)
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildNdkBuildDebug" to "configureNdkBuildDebug",
                    "buildNdkBuildRelease" to "configureNdkBuildRelease"
            )
        }
    }

    @Test
    fun `CMake variants that don't fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(variantName = "debug")
            val config2 = configurationParameters.copy(variantName = "release")
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug" to "configureCMakeDebug",
                    "buildCMakeRelWithDebInfo" to "configureCMakeRelWithDebInfo"
            )
        }
    }

    @Test
    fun `CMake variants that differ only by build targets`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("target-1")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("target-2")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[target-1]" to "configureCMakeDebug",
                    "buildCMakeDebug[target-2]" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `CMake variants with targets having illegal Gradle characters`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("nested/target-1")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("nested/target-2")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[nested_target-1]" to "configureCMakeDebug",
                    "buildCMakeDebug[nested_target-2]" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `CMake variants that differ only by two build targets each`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("a", "b")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("a", "c")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[a,b]" to "configureCMakeDebug",
                    "buildCMakeDebug[a,c]" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `CMake variants that differ with more than two targets each`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("a", "b", "c")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            targets = setOf("a", "b", "d")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[a,b,etc]" to "configureCMakeDebug",
                    "buildCMakeDebug[a,b,etc]-2" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `CMake equivalent variants with different ABIs fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            ndkAbiFilters = setOf("x86")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            ndkAbiFilters = setOf("x86_64")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug" to "configureCMakeDebug"
            )
        }
    }

    @Test
    fun `CMake equivalent variants with different ABIs that don't fold`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            arguments = listOf("-DCONFIG1"),
                            ndkAbiFilters = setOf("x86")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            arguments = listOf("-DCONFIG2"),
                            ndkAbiFilters = setOf("x86_64")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[x86]" to "configureCMakeDebug[x86]",
                    "buildCMakeDebug[x86_64]" to "configureCMakeDebug[x86_64]",
            )
        }
    }

    @Test
    fun `CMake equivalent variants with different ABIs that would fold except they have different targets`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy()
            val config1 = configurationParameters.copy(
                    variantName = "debug",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            ndkAbiFilters = setOf("x86"),
                            targets = setOf("target-1")
                    )
            )
            val config2 = configurationParameters.copy(
                    variantName = "debug2",
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            ndkAbiFilters = setOf("x86_64"),
                            targets = setOf("target-2")
                    )
            )
            val allAbis = createInitialCxxModel(
                    sdkComponents,
                    androidLocationProvider,
                    listOf(config1, config2))
            val namer = CxxConfigurationFolding(allAbis)
            assertThat(namer.buildConfigureEdges).containsExactly(
                    "buildCMakeDebug[x86][target-1]" to "configureCMakeDebug",
                    "buildCMakeDebug[x86_64][target-2]" to "configureCMakeDebug"
            )
        }
    }
}
