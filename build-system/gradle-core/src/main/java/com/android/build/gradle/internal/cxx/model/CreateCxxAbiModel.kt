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
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.settings.SettingsConfiguration
import com.android.build.gradle.internal.cxx.settings.createBuildSettingsFromFile
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Construct a [CxxAbiModel].
 */
fun createCxxAbiModel(
    sdkComponents: SdkComponentsBuildService,
    configurationParameters: CxxConfigurationParameters,
    variant: CxxVariantModel,
    abi: Abi
) : CxxAbiModel {
    val cxxBuildFolder = join(
        configurationParameters.cxxFolder,
        configurationParameters.buildSystem.tag,
        configurationParameters.variantName,
        abi.tag)
    with(variant) {
        return CxxAbiModel(
                variant = this,
                abi = abi,
                info = module.ndkMetaAbiList.single { it.abi == abi },
                originalCxxBuildFolder = cxxBuildFolder,
                cxxBuildFolder = cxxBuildFolder,
                abiPlatformVersion =
                sdkComponents
                        .ndkHandler
                        .ndkPlatform
                        .getOrThrow()
                        .ndkInfo
                        .findSuitablePlatformVersion(abi.tag,
                                configurationParameters.minSdkVersion),
                cmake =
                if (module.buildSystem == NativeBuildSystem.CMAKE) {
                    CxxCmakeAbiModel(
                            cmakeServerLogFile = join(cxxBuildFolder, "cmake_server_log.txt"),
                            effectiveConfiguration = SettingsConfiguration(),
                            cmakeArtifactsBaseFolder = cxxBuildFolder
                    )
                } else {
                    null
                },
                buildSettings = createBuildSettingsFromFile(module.buildSettingsFile),
                isActiveAbi = validAbiList.contains(abi),
                prefabFolder = prefabDirectory.resolve(abi.tag),
                stlLibraryFile =
                    Stl.fromArgumentName(stlType)
                            ?.let { module.stlSharedObjectMap.getValue(it)[abi]?.toString() }
                            ?.let { File(it) }
        )
    }
}
