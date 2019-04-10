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

/**
 * Construct a [CxxVariantModel], careful to be lazy with module-level fields.
 */
fun createCxxVariantModel(
    module: CxxModuleModel,
    baseVariantData: BaseVariantData) : CxxVariantModel {
    return object : CxxVariantModel {
        private val buildSystem by lazy {
            createNativeBuildSystemVariantConfig(
                module.buildSystem,
                baseVariantData.variantConfiguration)
        }
        private val intermediatesFolder by lazy {
            join(module.intermediatesFolder, module.buildSystem.tag, variantName)
        }
        override val buildTargetSet get() = buildSystem.targets
        override val module = module
        override val buildSystemArgumentList get() = buildSystem.arguments
        override val cFlagList get() = buildSystem.cFlags
        override val cppFlagsList get() = buildSystem.cppFlags
        override val variantName get() = baseVariantData.name
        override val objFolder get() =
            if (module.buildSystem == NativeBuildSystem.NDK_BUILD) {
                // ndkPlatform-build create libraries in a "local" subfolder.
                join(intermediatesFolder, "obj", "local")
            } else {
                join(intermediatesFolder, "obj")
            }
        override val isDebuggableEnabled get() =
            baseVariantData.variantConfiguration.buildType.isDebuggable
        override val validAbiList by lazy {
            AbiConfigurator(
                module.ndkSupportedAbiList,
                module.ndkDefaultAbiList,
                buildSystem.externalNativeBuildAbiFilters,
                buildSystem.ndkAbiFilters,
                module.splitsAbiFilterSet,
                module.isBuildOnlyTargetAbiEnabled,
                module.ideBuildTargetAbi
            ).validAbis.toList()
        }
    }
}

