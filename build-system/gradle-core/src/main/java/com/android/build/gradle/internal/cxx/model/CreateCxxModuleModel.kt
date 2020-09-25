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

import com.android.SdkConstants.CMAKE_DIR_PROPERTY
import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.NDK_SYMLINK_DIR
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.configure.*
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.cxxFolder
import com.android.build.gradle.internal.cxx.logging.lifecycleln
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.utils.FileUtils.join
import java.io.File
import java.io.FileReader
import java.util.function.Consumer

/**
 * Create module-level C/C++ build module ([CxxModuleModel]).
 */
fun createCxxModuleModel(
    sdkComponents : SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel,
    cmakeLocator: CmakeLocator
) : CxxModuleModel {

    val cxxFolder = configurationModel.cxxFolder
    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(configurationModel.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    val ndk= sdkComponents.ndkHandler.ndkPlatform.getOrThrow()
    val ndkFolder = trySymlinkNdk(
            ndk.ndkDirectory,
            cxxFolder,
            localPropertyFile(NDK_SYMLINK_DIR))

    val ndkMetaPlatformsFile = NdkMetaPlatforms.jsonFile(ndkFolder)
    val ndkMetaPlatforms = if (ndkMetaPlatformsFile.isFile) {
        NdkMetaPlatforms.fromReader(FileReader(ndkMetaPlatformsFile))
    } else {
        null
    }

    return CxxModuleModel(
        moduleBuildFile = configurationModel.buildFile,
        cxxFolder = cxxFolder,
        project = createCxxProjectModel(sdkComponents, configurationModel),
        ndkMetaPlatforms = ndkMetaPlatforms,
        ndkMetaAbiList = NdkAbiFile(ndkMetaAbisFile(ndkFolder)).abiInfoList,
        cmakeToolchainFile = join(ndkFolder, "build", "cmake", "android.toolchain.cmake"),
        originalCmakeToolchainFile = join(ndkFolder, "build", "cmake", "android.toolchain.cmake"),
        cmake =
                if (configurationModel.buildSystem == CMAKE) {
                    val exe = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) ".exe" else ""
                    val cmakeFolder =
                        cmakeLocator.findCmakePath(
                                configurationModel.cmakeVersion,
                                localPropertyFile(CMAKE_DIR_PROPERTY),
                                sdkComponents.sdkDirectoryProvider.get().asFile,
                                Consumer { sdkComponents.installCmake(it) })
                    val cmakeExe =
                            if (cmakeFolder == null) null
                            else join(cmakeFolder, "bin", "cmake$exe")
                    lifecycleln("NINJAX: Locating Ninjas")
                    val ninjaExe =
                            cmakeExe?.parentFile?.resolve("ninja$exe")
                            ?.takeIf { it.exists() }
                    CxxCmakeModuleModel(
                        minimumCmakeVersion =
                            CmakeVersionRequirements(configurationModel.cmakeVersion).effectiveRequestVersion,
                        isValidCmakeAvailable = cmakeFolder != null,
                        cmakeExe = cmakeExe,
                        ninjaExe = ninjaExe,
                        isPreferCmakeFileApiEnabled = configurationModel.isPreferCmakeFileApiEnabled
                    )

                } else {
                    null
                },
        ndkFolder = ndkFolder,
        ndkVersion = ndk.revision,
        ndkSupportedAbiList = ndk.supportedAbis,
        ndkDefaultAbiList = ndk.defaultAbis,
        ndkDefaultStl = ndk.ndkInfo.getDefaultStl(configurationModel.buildSystem),
        makeFile = configurationModel.makeFile,
        buildSystem = configurationModel.buildSystem,
        splitsAbiFilterSet = configurationModel.splitsAbiFilterSet,
        intermediatesFolder = configurationModel.intermediatesFolder,
        gradleModulePathName = configurationModel.gradleModulePathName,
        moduleRootFolder = configurationModel.moduleRootFolder,
        buildStagingFolder = configurationModel.buildStagingFolder,
        stlSharedObjectMap =
            ndk.ndkInfo.supportedStls
                .map { stl ->
                    Pair(
                        stl,
                        ndk.ndkInfo.getStlSharedObjectFiles(stl, ndk.ndkInfo.supportedAbis)
                    )
                }
                .toMap()
    )
}

fun createCxxModuleModel(
    sdkComponents: SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel
) = createCxxModuleModel(
    sdkComponents,
    configurationModel,
    CmakeLocator()
)

