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

import com.android.build.gradle.internal.cxx.configure.AbiConfigurator
import com.android.build.gradle.internal.cxx.configure.createNativeBuildSystemVariantConfig
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import com.google.common.annotations.VisibleForTesting

/**
 * Construct a [CxxVariantModel], careful to be lazy with module-level fields.
 */
fun createCxxVariantModel(
    module: CxxModuleModel,
    baseVariantData: BaseVariantData) : CxxVariantModel {
    val buildSystem by lazy {
        createNativeBuildSystemVariantConfig(
            module.buildSystem,
            baseVariantData.variantConfiguration)
    }
    val buildSystemArgumentList: () -> List<String> = { buildSystem.arguments }
    val buildTargetSet: () -> Set<String> = { buildSystem.targets }
    val cFlags: () -> List<String> = { buildSystem.cFlags }
    val cppFlags: () -> List<String> = { buildSystem.cppFlags }
    val variantName: () -> String = { baseVariantData.name }
    val isDebuggable: () -> Boolean = { baseVariantData.variantConfiguration.buildType.isDebuggable }
    val externalNativeBuildAbiFilters: () -> Set<String> = {
        buildSystem.externalNativeBuildAbiFilters
    }
    val ndkAbiFilters: () -> Set<String> = { buildSystem.ndkAbiFilters }

    return createCxxVariantModel(
        module = module,
        buildSystemArgumentList = buildSystemArgumentList,
        cFlags = cFlags,
        cppFlags = cppFlags,
        variantName = variantName,
        isDebuggable = isDebuggable,
        externalNativeBuildAbiFilters = externalNativeBuildAbiFilters,
        ndkAbiFilters = ndkAbiFilters,
        buildTargetSet = buildTargetSet
    )
}

private val notImpl = { throw RuntimeException("Not Implemented") }

@VisibleForTesting
fun createCxxVariantModel(
    module: CxxModuleModel,
    buildSystemArgumentList: () -> List<String> = notImpl,
    cFlags: () -> List<String> = notImpl,
    cppFlags: () -> List<String> = notImpl,
    variantName: () -> String = notImpl,
    isDebuggable: () -> Boolean = notImpl,
    externalNativeBuildAbiFilters: () -> Set<String> = notImpl,
    ndkAbiFilters: () -> Set<String> = notImpl,
    buildTargetSet: () -> Set<String> = notImpl): CxxVariantModel {
    return object : CxxVariantModel {
        override val buildTargetSet by lazy { buildTargetSet() }
        private val intermediatesFolder by lazy {
            join(module.intermediatesFolder, module.buildSystem.tag, this.variantName) }
        override val module = module
        override val buildSystemArgumentList by lazy { buildSystemArgumentList() }
        override val cFlagList by lazy { cFlags() }
        override val cppFlagsList by lazy { cppFlags() }
        override val variantName by lazy { variantName() }
        override val soFolder by lazy { join(intermediatesFolder, "lib") }
        override val objFolder by lazy {
            if (module.buildSystem == NativeBuildSystem.NDK_BUILD) {
                // ndkPlatform-build create libraries in a "local" subfolder.
                join(intermediatesFolder, "obj", "local")
            } else {
                join(intermediatesFolder, "obj")
            }
        }
        override val gradleBuildOutputFolder by lazy {
            join(module.cxxFolder, "cxx", this.variantName) }
        override val jsonFolder by lazy {
            join(module.cxxFolder, module.buildSystem.tag, this.variantName) }
        override val isDebuggableEnabled by lazy { isDebuggable() }
        override val validAbiList by lazy {
            AbiConfigurator(
                module.ndkSupportedAbiList,
                module.ndkDefaultAbiList,
                externalNativeBuildAbiFilters(),
                ndkAbiFilters(),
                module.splitsAbiFilterSet,
                module.isBuildOnlyTargetAbiEnabled,
                module.ideBuildTargetAbi
            ).validAbis.toList()
        }
    }
}