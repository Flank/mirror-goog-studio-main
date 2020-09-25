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

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.abiCxxBuildFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantJsonFolder
import com.android.build.gradle.internal.cxx.settings.CMakeSettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.createBuildSettingsFromFile
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join

/**
 * Construct a [CxxAbiModel], careful to be lazy with module level fields.
 */
fun createCxxAbiModel(
    sdkComponents: SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel,
    variant: CxxVariantModel,
    abi: Abi
) : CxxAbiModel {
    return CxxAbiModel(
        variant = variant,
        abi = abi,
        info = variant.module.ndkMetaAbiList.single { it.abi == abi },
        originalCxxBuildFolder = configurationModel.abiCxxBuildFolder(abi),
        cxxBuildFolder = configurationModel.abiCxxBuildFolder(abi),
        abiPlatformVersion =
            sdkComponents
                .ndkHandler
                .ndkPlatform
                .getOrThrow()
                .ndkInfo
                .findSuitablePlatformVersion(abi.tag, configurationModel.minSdkVersion),
        cmake =
            if (variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                val cmakeArtifactsBaseFolder = join(configurationModel.variantJsonFolder, abi.tag)
                CxxCmakeAbiModel(
                    cmakeServerLogFile = join(cmakeArtifactsBaseFolder, "cmake_server_log.txt"),
                    effectiveConfiguration = CMakeSettingsConfiguration(),
                    cmakeWrappingBaseFolder = join(variant.gradleBuildOutputFolder, abi.tag),
                    cmakeArtifactsBaseFolder = cmakeArtifactsBaseFolder
                )
            } else {
                null
            },
        buildSettings = createBuildSettingsFromFile(variant.module.buildSettingsFile),
        prefabFolder = variant.prefabDirectory.resolve(abi.tag)
    )
}
