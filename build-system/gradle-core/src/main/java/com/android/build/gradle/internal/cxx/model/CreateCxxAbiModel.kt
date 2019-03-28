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
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils.join
import com.google.common.annotations.VisibleForTesting

/**
 * Construct a [CxxAbiModel], careful to be lazy with module level fields.
 */
fun createCxxAbiModel(
    variant: CxxVariantModel,
    abi: Abi,
    global: GlobalScope,
    baseVariantData: BaseVariantData) : CxxAbiModel {

    val abiPlatformVersion: () -> Int = {
        val minSdkVersion = baseVariantData.variantConfiguration.mergedFlavor.minSdkVersion
        val version = if (minSdkVersion == null) {
            null
        } else{
            AndroidVersion(minSdkVersion.apiLevel, minSdkVersion.codename)
        }
        global
                .sdkComponents
                .ndkHandlerSupplier.get()
                .ndkPlatform
                .ndkInfo
                .findSuitablePlatformVersion(abi.tag, version)
    }
    return createCxxAbiModel(
        variant,
        abi,
        abiPlatformVersion
    )
}

@VisibleForTesting
fun createCxxAbiModel(
    variant: CxxVariantModel,
    abi: Abi,
    abiPlatformVersion: () -> Int) : CxxAbiModel {
    return object : CxxAbiModel {
        private val buildSystemPresentationName by lazy {
            variant.module.buildSystem.tag }
        override val variant = variant
        override val abi = abi
        override val abiPlatformVersion by lazy {
            abiPlatformVersion() }
        override val cxxBuildFolder by lazy {
            join(variant.jsonFolder, abi.tag) }
        override val jsonFile by lazy {
            join(cxxBuildFolder,"android_gradle_build.json") }
        override val gradleBuildOutputFolder by lazy {
            join(variant.gradleBuildOutputFolder, abi.tag) }
        override val objFolder by lazy { join(variant.objFolder, abi.tag) }
        override val buildCommandFile by lazy {
            join(cxxBuildFolder, "${buildSystemPresentationName}_build_command.txt") }
        override val buildOutputFile by lazy {
            join(cxxBuildFolder, "${buildSystemPresentationName}_build_output.txt") }
        override val modelOutputFile by lazy {
            join(cxxBuildFolder,"build-model.yaml") }
        override val cmake by lazy {
            if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                object : CxxCmakeAbiModel {
                    override val cmakeListsWrapperFile by lazy {
                        join(gradleBuildOutputFolder, "CMakeLists.txt") }
                    override val toolchainWrapperFile by lazy {
                        join(gradleBuildOutputFolder,
                            "android_gradle_build.toolchain.cmake") }
                    override val buildGenerationStateFile by lazy {
                        join(gradleBuildOutputFolder, "build_generation_state.json") }
                    override val cacheKeyFile by lazy {
                        join(gradleBuildOutputFolder, "compiler_cache_key.json") }
                    override val compilerCacheUseFile by lazy {
                        join(gradleBuildOutputFolder, "compiler_cache_use.json") }
                    override val compilerCacheWriteFile by lazy {
                        join(gradleBuildOutputFolder, "compiler_cache_write.json") }
                    override val toolchainSettingsFromCacheFile by lazy {
                        join(gradleBuildOutputFolder, "compiler_settings_cache.cmake") }
                }
            } else {
                null
            }
        }
    }
}