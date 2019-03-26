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

import com.android.SdkConstants.NDK_SYMLINK_DIR
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CXX_LOCAL_PROPERTIES_CACHE_DIR
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.model.CoreExternalNativeBuild
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE
import com.android.build.gradle.options.BooleanOption.BUILD_ONLY_TARGET_ABI
import com.android.build.gradle.options.BooleanOption.ENABLE_SIDE_BY_SIDE_CMAKE
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.StringOption.IDE_BUILD_TARGET_ABI
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.internal.cxx.logging.error
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import java.io.File

/**
 * Examine the build.gradle DSL and determine whether the user has requested C/C++.
 * If so, then return a [CxxModuleModel] that describes the build.
 * If not, then return null.
 * This function and the [CxxModuleModel] are intended to be initiated in sync
 * configuration.
 * To make sync faster, as little work as possible is done directly in this
 * function. Instead, most vals are deferred until later with 'by lazy'.
 */
fun tryCreateCxxModuleModel(global : GlobalScope) : CxxModuleModel? {
    val (buildSystem, makeFile, buildStagingDirectory) =
        getProjectPath(global.extension.externalNativeBuild) ?: return null

    fun option(option: BooleanOption) = global.projectOptions.get(option)
    fun option(option: StringOption) = global.projectOptions.get(option)
    val localProperties by lazy { gradleLocalProperties(global.project.rootDir) }
    fun localPropertyFile(property : String) : File? {
        val path = localProperties.getProperty(property) ?: return null
        return File(path)
    }
    val sdkFolder = { global.sdkComponents.getSdkFolder()!! }
    val generatePureSplits = { global.extension.generatePureSplits }
    val isUniversalApk = { global.extension.splits.abi.isUniversalApk }
    val splitsAbiFilters = { global.extension.splits.abiFilters }
    val intermediatesDir = { global.intermediatesDir }
    val projectPath = { global.project.path }
    val projectDir = { global.project.projectDir }
    val buildDir = { global.project.buildDir }
    val cmakeVersion = { global.extension.externalNativeBuild.cmake.version }
    val isNativeCompilerSettingsCacheEnabled = { option(ENABLE_NATIVE_COMPILER_SETTINGS_CACHE) }
    val isBuildOnlyTargetAbiEnabled = { option(BUILD_ONLY_TARGET_ABI) }
    val isSideBySideCmakeEnabled = { option(ENABLE_SIDE_BY_SIDE_CMAKE) }
    val ideBuildTargetAbi = { option(IDE_BUILD_TARGET_ABI) }
    val rootDir = { global.project.rootDir }
    val ndkSymLinkFolder = { localPropertyFile(NDK_SYMLINK_DIR) }
    val compilerSettingsCacheFolder = { localPropertyFile(CXX_LOCAL_PROPERTIES_CACHE_DIR) }

    return createCxxModuleModel(
        makeFile,
        buildSystem,
        sdkFolder,
        intermediatesDir,
        projectPath,
        projectDir,
        buildDir,
        rootDir,
        isNativeCompilerSettingsCacheEnabled,
        isBuildOnlyTargetAbiEnabled,
        isSideBySideCmakeEnabled,
        ideBuildTargetAbi,
        generatePureSplits,
        isUniversalApk,
        splitsAbiFilters,
        cmakeVersion,
        ndkSymLinkFolder,
        compilerSettingsCacheFolder,
        buildStagingDirectory
    )
}

private val notImpl = { throw RuntimeException("Not Implemented") }

@VisibleForTesting
internal fun createCxxModuleModel(
    makeFile: File,
    buildSystem: NativeBuildSystem,
    sdkFolder: () -> File = notImpl,
    intermediatesDir: () -> File = notImpl,
    projectPath: () -> String = notImpl,
    projectDir: () -> File = notImpl,
    buildDir: () -> File = notImpl,
    rootDir: () -> File = notImpl,
    isNativeCompilerSettingsCacheEnabled: () -> Boolean = notImpl,
    isBuildOnlyTargetAbiEnabled: () -> Boolean = notImpl,
    isSideBySideCmakeEnabled: () -> Boolean = notImpl,
    ideBuildTargetAbi: () -> String? = notImpl,
    generatePureSplits: () -> Boolean = notImpl,
    isUniversalApk: () -> Boolean = notImpl,
    splitsAbiFilters: () -> Set<String> = notImpl,
    cmakeVersion: () -> String? = notImpl,
    symLinkFolder: () -> File? = notImpl,
    compilerSettingsCacheFolder: () -> File? = notImpl,
    buildStagingDirectory: File? = null
): CxxModuleModel {
    return object : CxxModuleModel {
        // NDK fields can be rebound after an NDK has been installed. There may be no NDK before
        // this so the fields start out as null.
        override val ndkFolder = null
        override val ndkVersion = null
        override val makeFile = makeFile
        override val buildSystem = buildSystem
        override val sdkFolder by lazy { sdkFolder() }
        override val isNativeCompilerSettingsCacheEnabled by lazy {
            isNativeCompilerSettingsCacheEnabled()
        }
        override val isBuildOnlyTargetAbiEnabled by lazy { isBuildOnlyTargetAbiEnabled() }
        override val isSideBySideCmakeEnabled by lazy { isSideBySideCmakeEnabled() }
        override val ideBuildTargetAbi by lazy { ideBuildTargetAbi() }
        override val isGeneratePureSplitsEnabled by lazy { generatePureSplits() }
        override val isUniversalApkEnabled by lazy { isUniversalApk() }
        override val splitsAbiFilters: Set<String> by lazy { splitsAbiFilters() }
        override val intermediatesFolder by lazy { intermediatesDir() }
        override val gradleModulePathName: String by lazy { projectPath() }
        override val moduleRootFolder: File by lazy { projectDir() }
        override val buildFolder: File by lazy { buildDir() }
        override val cmakeVersion: String? by lazy { cmakeVersion() }
        override val ndkSymlinkFolder: File? by lazy { symLinkFolder() }
        override val compilerSettingsCacheFolder by lazy {
            compilerSettingsCacheFolder() ?: File(rootDir(), CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
        }
        override val cxxFolder by lazy {
            findCxxFolder(moduleRootFolder, buildStagingDirectory, buildFolder)
        }
    }
}

/**
 * Resolve the CMake or ndk-build path and buildStagingDirectory of native build project.
 * - If there is exactly 1 path in the DSL, then use it.
 * - If there are more than 1, then that is an error. The user has specified both cmake and
 *   ndkBuild in the same project.
 */
private fun getProjectPath(config: CoreExternalNativeBuild)
        : Triple<NativeBuildSystem, File, File?>? {
    val externalProjectPaths = listOfNotNull(
        config.cmake.path?.let { Triple(CMAKE, it, config.cmake.buildStagingDirectory)},
        config.ndkBuild.path?.let { Triple(NDK_BUILD, it, config.ndkBuild.buildStagingDirectory) })

    return when {
        externalProjectPaths.size > 1 -> {
            error("More than one externalNativeBuild path specified")
            null
        }
        externalProjectPaths.isEmpty() -> {
            // No external projects present.
            null
        }
        else -> externalProjectPaths[0]
    }
}

/**
 * Finds the location of the build-system output folder. For example, .cxx/cmake/debug/x86/
 *
 * If user specific externalNativeBuild.cmake.buildStagingFolder = 'xyz' then that folder
 * will be used instead of the default of moduleRoot/.cxx.
 *
 * If the resulting build output folder would be inside of moduleRoot/build then issue an error
 * because moduleRoot/build will be deleted when the user does clean and that will lead to
 * undefined behavior.
 */
private fun findCxxFolder(
    moduleRootFolder : File,
    buildStagingDirectory: File?,
    buildFolder: File): File {
    val defaultCxxFolder =
        FileUtils.join(
            moduleRootFolder,
            CXX_DEFAULT_CONFIGURATION_SUBFOLDER
        )
    return when {
        buildStagingDirectory == null -> defaultCxxFolder
        FileUtils.isFileInDirectory(buildStagingDirectory, buildFolder) -> {
            error("""
            The build staging directory you specified ('${buildStagingDirectory.absolutePath}')
            is a subdirectory of your project's temporary build directory (
            '${buildFolder.absolutePath}'). Files in this directory do not persist through clean
            builds. Either use the default build staging directory ('$defaultCxxFolder'), or
            specify a path outside the temporary build directory.""".trimIndent())
            defaultCxxFolder
        }
        else -> buildStagingDirectory
    }
}
