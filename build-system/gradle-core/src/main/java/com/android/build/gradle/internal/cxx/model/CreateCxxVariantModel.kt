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

import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.AbiConfigurationKey
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.variantJsonFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantObjFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantSoFolder
import com.android.utils.FileUtils.join

/**
 * Construct a [CxxVariantModel], careful to be lazy with module-level fields.
 */
fun createCxxVariantModel(
    configurationModel: CxxConfigurationModel,
    module: CxxModuleModel) : CxxVariantModel {
    val validAbiList = CachingEnvironment(module.cxxFolder).use {
        AbiConfigurator(
                AbiConfigurationKey(
                        module.ndkSupportedAbiList,
                        module.ndkDefaultAbiList,
                        configurationModel.nativeVariantConfig.externalNativeBuildAbiFilters,
                        configurationModel.nativeVariantConfig.ndkAbiFilters,
                        module.splitsAbiFilterSet,
                        module.project.isBuildOnlyTargetAbiEnabled,
                        module.project.ideBuildTargetAbi
                )
        ).validAbis.toList()
    }
    return CxxVariantModel(
        buildTargetSet = configurationModel.nativeVariantConfig.targets,
        implicitBuildTargetSet = configurationModel.implicitBuildTargetSet,
        module = module,
        buildSystemArgumentList = configurationModel.nativeVariantConfig.arguments,
        cFlagsList = configurationModel.nativeVariantConfig.cFlags,
        cppFlagsList = configurationModel.nativeVariantConfig.cppFlags,
        variantName = configurationModel.variantName,
        // TODO remove this after configuration has been added to DSL
        // If CMakeSettings.json has a configuration with this exact name then
        // it will be used. The point is to delay adding 'configuration' to the
        // DSL.
        cmakeSettingsConfiguration = "android-gradle-plugin-predetermined-name",
        objFolder = configurationModel.variantObjFolder,
        soFolder = configurationModel.variantSoFolder,
        isDebuggableEnabled = configurationModel.isDebuggable,
        validAbiList = validAbiList,
        prefabClassPath = configurationModel.prefabClassPath?.singleFile,
        prefabPackageDirectoryList = configurationModel.prefabPackageDirectoryList?.toList()?:listOf(),
        prefabDirectory = configurationModel.variantJsonFolder.resolve("prefab")
    )
}

/**
 * The gradle build output folder
 *   ex, '$moduleRootFolder/.cxx/cxx/debug'
 */
val CxxVariantModel.gradleBuildOutputFolder
        get() = join(module.cxxFolder, "cxx", variantName)

