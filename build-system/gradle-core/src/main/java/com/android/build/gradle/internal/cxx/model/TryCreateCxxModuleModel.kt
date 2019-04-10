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
import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CXX_LOCAL_PROPERTIES_CACHE_DIR
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.model.CoreExternalNativeBuild
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.BooleanOption.ENABLE_NATIVE_COMPILER_SETTINGS_CACHE
import com.android.build.gradle.options.BooleanOption.BUILD_ONLY_TARGET_ABI
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.StringOption.IDE_BUILD_TARGET_ABI
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import org.gradle.api.InvalidUserDataException
import java.io.File
import java.io.IOException
import java.util.function.Consumer

/**
 * Examine the build.gradle DSL and determine whether the user has requested C/C++.
 * If so, then return a [CxxModuleModel] that describes the build.
 * If not, then return null.
 * This function and the [CxxModuleModel] are intended to be initiated in sync
 * configuration.
 * To make sync faster, as little work as possible is done directly in this
 * function. Instead, most vals are deferred until later with 'by lazy'.
 *
 * A note about laziness:
 * ----------------------
 * Everything except for information needed to determine whether this is a C/C++
 * module (and so [tryCreateCxxModuleModel] returns null) should be deferred with
 * 'by lazy' or some other means.
 *
 * The primary reason for this is that the configuration phase of sync should be
 * as as fast as possible.
 *
 * Some fields, like global.project.projectDir, would probably be harmless to
 * call immediately. However, it is generally hard to know what is harmless or
 * not. Additionally, fields and function call costs can change later.
 *
 * In addition to the primary reason, there are several side benefits:
 *  - We don't have to reason about whether accessing some global fields are
 *   costly or not.
 * - It insures the fields are immutable (which is a requirement of model
 *   interfaces).
 * - It insures that possibly costly function results are computed once.
 * - Some fields have side effects (like logging errors or warnings). These
 *   shouldn't be logged multiple times.
 * - If all fields that access global are lazy then it is trivial to write a
 *   unittest that verifies that global isn't accessed in [tryCreateCxxModuleModel].
 *
 * Since 'by lazy' is not costly in terms of memory or time it's preferable just
 * to always use it.
 */
fun tryCreateCxxModuleModel(global : GlobalScope, cmakeLocator : CmakeLocator) : CxxModuleModel? {
    val (buildSystem, makeFile, buildStagingDirectory) =
        getProjectPath(global.extension.externalNativeBuild) ?: return null

    fun option(option: BooleanOption) = global.projectOptions.get(option)
    fun option(option: StringOption) = global.projectOptions.get(option)
    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(global.project.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    return object : CxxModuleModel {
        private val ndkHandler by lazy {
            val ndkHandler = global.sdkComponents.ndkHandlerSupplier.get()
            try {
                if (!ndkHandler.ndkPlatform.isConfigured) {
                    global.sdkComponents.installNdk(ndkHandler)
                    if (!ndkHandler.ndkPlatform.isConfigured) {
                        throw InvalidUserDataException("NDK not configured. Download it with SDK manager.")
                    }
                }
            } finally {
                ndkHandler.writeNdkLocatorRecord(join(cxxFolder, "ndk_locator_record.json"))
            }
            ndkHandler
        }
        override val rootBuildGradleFolder
            get() = global.project.rootDir
        override val cmake
            get() =
                if (buildSystem == CMAKE) {
                    val exe = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) ".exe" else ""
                    object: CxxCmakeModuleModel {
                        private val cmakeFolder by lazy {
                            cmakeLocator.findCmakePath(
                                global.extension.externalNativeBuild.cmake.version,
                                localPropertyFile(CMAKE_DIR_PROPERTY),
                                global.sdkComponents.getSdkFolder()!!,
                                Consumer { global.sdkComponents.installCmake(it) })!!
                        }
                        override val cmakeExe by lazy {
                            join(cmakeFolder, "bin", "cmake$exe")
                        }
                        override val foundCmakeVersion by lazy {
                            try {
                                CmakeUtils.getVersion(File(cmakeFolder, "bin"))
                            } catch (e: IOException) {
                                // For pre-ENABLE_SIDE_BY_SIDE_CMAKE case, the text of this message triggers
                                // Android Studio to prompt for download.
                                // Post-ENABLE_SIDE_BY_SIDE different messages may be thrown from
                                // CmakeLocatorKt.findCmakePath to trigger download of particular versions of
                                // CMake from the SDK.
                                throw RuntimeException(
                                    "Unable to get the CMake version located at: " +
                                            File(cmakeFolder, "bin").absolutePath
                                )
                            }
                        }
                        override val ninjaExe by lazy {
                            join(cmakeFolder, "bin", "ninja$exe")
                        }
                    }

                } else {
                    null
                }
        override val ndkFolder by lazy {
            trySymlinkNdk(
                ndkHandler.ndkPlatform.getOrThrow().ndkDirectory,
                cxxFolder,
                localPropertyFile(NDK_SYMLINK_DIR))
        }
        override val ndkVersion by lazy {
            ndkHandler.ndkPlatform.getOrThrow().revision
        }
        override val ndkSupportedAbiList by lazy {
            ndkHandler.ndkPlatform.getOrThrow().supportedAbis
        }
        override val ndkDefaultAbiList by lazy {
            ndkHandler.ndkPlatform.getOrThrow().defaultAbis
        }
        override val makeFile = makeFile
        override val buildSystem = buildSystem
        override val sdkFolder by lazy {
            global.sdkComponents.getSdkFolder()!!
        }
        override val isNativeCompilerSettingsCacheEnabled by lazy {
            option(ENABLE_NATIVE_COMPILER_SETTINGS_CACHE)
        }
        override val isBuildOnlyTargetAbiEnabled by lazy {
            option(BUILD_ONLY_TARGET_ABI)
        }
        override val ideBuildTargetAbi by lazy {
            option(IDE_BUILD_TARGET_ABI)
        }
        override val splitsAbiFilterSet by lazy {
            global.extension.splits.abiFilters
        }
        override val intermediatesFolder by lazy {
            global.intermediatesDir
        }
        override val gradleModulePathName by lazy {
            global.project.path
        }
        override val moduleRootFolder by lazy {
            global.project.projectDir
        }
        override val compilerSettingsCacheFolder by lazy {
            localPropertyFile(CXX_LOCAL_PROPERTIES_CACHE_DIR) ?:
            join(global.project.rootDir, CXX_DEFAULT_CONFIGURATION_SUBFOLDER)
        }
        override val cxxFolder by lazy {
            findCxxFolder(
                moduleRootFolder,
                buildStagingDirectory,
                global.project.buildDir)
        }
    }
}

fun tryCreateCxxModuleModel(global : GlobalScope) = tryCreateCxxModuleModel(global, CmakeLocator())

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
            errorln("More than one externalNativeBuild path specified")
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
            errorln("""
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
