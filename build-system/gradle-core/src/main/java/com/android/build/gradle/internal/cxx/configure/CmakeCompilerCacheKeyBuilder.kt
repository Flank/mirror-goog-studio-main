/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperties.*
import java.io.File

/**
 * Convert a CMake command-line into a compiler hash key. Removes black-listed
 * flags and keeps unrecognized flags in case they are relevant to compiler
 * settings.
 */
fun makeCmakeCompilerCacheKey(commandLine : List<CommandLineArgument>) : CmakeCompilerCacheKey {
    return commandLine
        .removeBlackListedProperties()
        .findAndroidNdk()
        .expandSourceProperties()
        .replaceAndroidNdkInProperties()
}

/**
 * Remove properties and flags that shouldn't affect the outcome of the compiler settings.
 */
private fun List<CommandLineArgument>.removeBlackListedProperties() : List<CommandLineArgument> {
    return asSequence()
        .filter { argument ->
            when (argument) {
                is GeneratorName -> false
                is BinaryOutputPath -> false
                is CmakeListsPath -> false
                is DefineProperty -> {
                    when {
                        CMAKE_COMPILER_CHECK_CACHE_KEY_BLACKLIST_STRINGS
                            .contains(argument.propertyName) -> false
                        else -> true
                    }
                }
                else -> true
            }
        }
        .toList()
}

/**
 * Find the ANDROID_NDK property and record it in the key.
 * Remove the property from args.
 */
private fun List<CommandLineArgument>.findAndroidNdk(): CmakeCompilerCacheKey {
    var androidNdkFolder: File? = null
    val args = asSequence()
            .filter { argument ->
                when (argument) {
                    is DefineProperty -> {
                        if (argument.propertyName == ANDROID_NDK.name) {
                            androidNdkFolder = File(argument.propertyValue)
                            false
                        } else {
                            true
                        }
                    }
                    else -> true
                }
            }
            .map { it.sourceArgument }.toList()
    return CmakeCompilerCacheKey(
        ndkInstallationFolder = androidNdkFolder,
        ndkSourceProperties = null,
        args = args
    )
}

/**
 * Try to find the NDK's source.properties file, read it, and record the settings as part of
 * the key.
 */
private fun CmakeCompilerCacheKey.expandSourceProperties(): CmakeCompilerCacheKey {
    if (ndkInstallationFolder == null) {
        return this
    }
    val sourceProperties = File(ndkInstallationFolder, "source.properties")
    if (!sourceProperties.isFile) {
        warn("ANDROID_NDK location ($ndkInstallationFolder) had no source.properties")
        return this
    }
    return copy(ndkSourceProperties = SdkSourceProperties.fromInstallFolder(ndkInstallationFolder))
}

/**
 * Replace literal cases of the NDK path with ${ANDROID_NDK} so that the physical location of the
 * NDK is only determined by the ndkInstallationFolder portion of the key.
 */
private fun CmakeCompilerCacheKey.replaceAndroidNdkInProperties(): CmakeCompilerCacheKey {
    if (ndkInstallationFolder == null) {
        return this
    }
    return copy(args = args.map { arg ->
        arg.replace(ndkInstallationFolder.path, "\${$ANDROID_NDK}")
    })
}
