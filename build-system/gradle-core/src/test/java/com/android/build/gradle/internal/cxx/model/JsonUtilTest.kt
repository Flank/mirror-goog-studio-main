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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.repository.Revision
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class JsonUtilTest {
    private val module : CxxModuleModel
    private val variant  : CxxVariantModel
    private val cmake  : CxxCmakeAbiModel
    private val abi  : CxxAbiModel
    init {
        val module = object : CxxModuleModel {
            override val isNativeCompilerSettingsCacheEnabled = false
            override val sdkFolder = File("soFolder")
            override val isBuildOnlyTargetAbiEnabled = true
            override val isSideBySideCmakeEnabled = false
            override val ideBuildTargetAbi = "ideBuildTargetAbi"
            override val isGeneratePureSplitsEnabled = true
            override val isUniversalApkEnabled = true
            override val splitsAbiFilters = setOf("ABI")
            override val intermediatesFolder = File("intermediates")
            override val gradleModulePathName = ":app"
            override val moduleRootFolder = File("moduleRootFolder")
            override val buildFolder = File("buildFolder")
            override val makeFile = File("makeFile")
            override val buildSystem = NativeBuildSystem.CMAKE
            override val cmakeVersion = "cmakeVersion "
            override val ndkSymlinkFolder = File("ndkSymlinkFolder")
            override val compilerSettingsCacheFolder = File("compilerSettingsCacheFolder")
            override val cxxFolder = File("cxxFolder")
            override val ndkFolder = File("ndkFolder")
            override val ndkVersion = Revision.parseRevision("1.2.3")
        }

        val variant = object : CxxVariantModel {
            override val buildSystemArgumentList = listOf("buildSystemArgumentList")
            override val cFlagList = listOf("cFlagList")
            override val cppFlagsList = listOf("cppFlagList")
            override val variantName = "variantName"
            override val soFolder = File("soFolder")
            override val objFolder = File("objFolder")
            override val jsonFolder = File("jsonFolder")
            override val gradleBuildOutputFolder = File("gradleBuildOutputFolder")
            override val isDebuggable = false
            override val validAbiList = listOf(Abi.ARMEABI_V7A)
            override val module = module
        }
        val cmake = object : CxxCmakeAbiModel {
            override val cmakeListsWrapperFile = File("cmakeListsWrapperFile")
            override val toolchainWrapperFile = File("toolchainWrapperFile")
            override val buildGenerationStateFile = File("buildGenerationStateFile")
            override val cacheKeyFile = File("cacheKeyFile")
            override val compilerCacheUseFile = File("compilerCacheUseFile")
            override val compilerCacheWriteFile = File("compilerCacheWriteFile")
            override val toolchainSettingsFromCacheFile = File("toolchainSettingsFromCacheFile")
        }
        val abi = object : CxxAbiModel {
            override val abi = Abi.X86
            override val abiPlatformVersion = 28
            override val cxxBuildFolder = File("cxxBuildFolder")
            override val jsonFile = File("jsonFile")
            override val gradleBuildOutputFolder = File("gradleBuildOutputFolder")
            override val objFolder = File("objFolder")
            override val buildCommandFile = File("buildCommandFile")
            override val buildOutputFile = File("buildOutputFile")
            override val modelOutputFile = File("modelOutputFile")
            override val cmake = cmake
            override val variant = variant
        }

        this.module = module
        this.variant = variant
        this.cmake = cmake
        this.abi = abi
    }

    @Test
    fun `round trip`() {
        val json = abi.toJsonString()
        val writtenBackAbi = createCxxAbiModelFromJson(json)
        val writtenBackJson = writtenBackAbi.toJsonString()
        assertThat(json).isEqualTo(writtenBackJson)
        assertThat(writtenBackAbi.variant.module.buildFolder.path).isEqualTo("buildFolder")
        assertThat(writtenBackAbi.variant.module.ndkVersion.toString()).isEqualTo("1.2.3")
    }
}