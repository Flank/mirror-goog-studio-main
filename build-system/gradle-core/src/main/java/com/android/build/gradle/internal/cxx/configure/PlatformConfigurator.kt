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

import com.android.build.gradle.internal.core.Abi
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import org.gradle.api.logging.Logging
import java.io.File
import java.io.FileFilter
import java.io.FileReader

class PlatformConfigurator(private val ndkRoot: File) {
    private val sensibleDefaultPlatformApiVersionForErrorCase = AndroidVersion.MIN_RECOMMENDED_API
    private val veryHighPlatformApiVersion = Int.MAX_VALUE

    /**
     * This is a hard-coded version of meta/platforms.json aliases. It does not need to be updated
     * in sync with the release of new platforms in the NDK because more recent NDKs will get this
     * information from the JSON file.
     */
    private val platformNameAliases: Map<String, Int> = mapOf(
        "20" to 19,
        "25" to 24,
        "J" to 16,
        "J-MR1" to 17,
        "J-MR2" to 18,
        "K" to 19,
        "L" to 21,
        "L-MR1" to 22,
        "M" to 23,
        "N" to 24,
        "N-MR1" to 24,
        "O" to 26,
        "O-MR1" to 27,
        "P" to 28
    )

    /**
     * <pre>
     *
     * Find suitable platform for the given ABI.
     *
     * (a-1) If codeName is known then it is converted to API level.
     *   For example, "P" is converted to 28.
     * (a-2) If no codeName then use minSdkVersion as API level.
     * (a-3) If neither is specified then use the lowest available version as API level.
     *
     * For NDKs that have meta/platforms.json file:
     *
     *   (b-1) Clamp API level to be between min and max from platforms.json and return.
     *
     * For NDKs that don't have meta/platforms.json file:
     *
     *   (c-1) If there exists folder platforms/android-{API level}/arch-{ABI}, then {API level} is
     *     returned.
     *   (c-2) Find min and max {platform} in platforms/android-{platform}/arch-{ABI} and clamp
     *     API level to be in that range.
     *
     * Error cases:
     *
     *   If codeName exists but isn't recognized then an error is issued and the highest available
     *     API level is returned.
     *
     *   If both codeName and minSdkVersion are specified by the user and they disagree then an
     *     error is issued and minSdkVersion is chosen for further processing. If they agree then
     *     a warning is issued instead.
     *
     *   During clamping if the API level is too high then an error is issued and the max value is
     *     returned.
     *
     *   Other errors return a sensible default platform so that processing can continue.
     *
     * </pre>
     */
    fun findSuitablePlatformVersionLogged(
        abiName: String,
        androidVersionOrNull: AndroidVersion?,
        ndkMetaPlatforms: NdkMetaPlatforms?,
        error: (String) -> Unit = { message ->
            Logging.getLogger("findSuitablePlatformVersionLogged").error(message) },
        warn: (String) -> Unit = { message ->
            Logging.getLogger("findSuitablePlatformVersionLogged").warn(message) },
        info: (String) -> Unit = { message ->
            Logging.getLogger("findSuitablePlatformVersionLogged").info(message) } ): Int {

        val abi = Abi.getByName(abiName) ?: run {
            error("Specified abi='$abiName' is not recognized.")
            // Fall back so that processing can continue after the error
            return sensibleDefaultPlatformApiVersionForErrorCase
        }

        // This is a fallback/legacy case for supporting pre-r17 NDKs which don't have
        // platforms.json. As of the time this code is written there is no policy for deprecating
        // use of older NDKs in Android Studio. If a policy is established and everything up to
        // r17 is deprecated then we can remove this code path.
        if (ndkMetaPlatforms == null) {
            // If platforms/android-[min sdk]/arch-[ABI] exists, then use the min sdk as platform
            // for that ABI
            val platformDir = FileUtils.join(ndkRoot, "platforms")
            if (!platformDir.isDirectory) {
                error("NDK folder '$ndkRoot' specified does not contain 'platforms'.")
                // Fall back so that processing can continue after the error
                return sensibleDefaultPlatformApiVersionForErrorCase
            }

            val minSdkVersion = computeMinSdkVersion(
                abiName,
                androidVersionOrNull,
                platformNameAliases,
                error,
                info,
                warn
            )
            return findPlatformConfiguratorLegacy(abi, minSdkVersion, platformDir, error, info)
        }

        /**
         * Algorithm used when meta/platforms.json exists (likely r17+). See "b" comments at the
         * top of this class.
         */
        val minSdkVersion = computeMinSdkVersion(
            abiName,
            androidVersionOrNull,
            ndkMetaPlatforms.aliases,
            error,
            info,
            warn
        )
        return clamp(minSdkVersion, ndkMetaPlatforms.min, ndkMetaPlatforms.max, error)
    }

    /**
     * This function combines the minSdkVersion and codeName fields from build.gradle into a
     * recognized minSdkVersion. Along the way it issues errors, warnings, and infos about how
     * the decisions were made and what problems were seen.
     */
    private fun computeMinSdkVersion(
        abiName: String,
        androidVersionOrNull: AndroidVersion?,
        platformNameAliases: Map<String, Int>,
        error: (String) -> Unit,
        info: (String) -> Unit,
        warn: (String) -> Unit): Int {

        val androidVersion = androidVersionOrNull ?: AndroidVersion(0)
        val minSdkVersionOrZero = androidVersion.apiLevel
        val codeNameOrNull = androidVersion.codename

        // Convert from codeName like "P" to SDK platform version like 28
        val minSdkVersionFromCodeName = when {
            codeNameOrNull == null || codeNameOrNull.isEmpty() ->
                null
            else -> {
                val lookup = platformNameAliases[codeNameOrNull]
                when {
                    lookup != null -> lookup
                    else -> {
                        // This version is not yet known (or is an error), choose a very high
                        // version number which will then be lowered to the maximum version
                        // actually installed in the referenced NDK.
                        error("API codeName '$codeNameOrNull' is not recognized.")
                        veryHighPlatformApiVersion
                    }
                }
            }
        }

        // Check minSdkVersion for errors and use codeName where appropriate
        val minSdkVersion = when {
            minSdkVersionOrZero == 0 && minSdkVersionFromCodeName == null -> {
                info(
                    "Neither codeName nor minSdkVersion specified. Using minimum platform " +
                            "version for '$abiName'."
                )
                0
            }
            minSdkVersionOrZero != 0 && minSdkVersionFromCodeName != null ->
                when (minSdkVersionOrZero) {
                    minSdkVersionFromCodeName -> {
                        warn(
                            "Both codeName and minSdkVersion specified. " +
                                    "They agree but only one should be specified."
                        )
                        minSdkVersionOrZero
                    }
                    else -> {
                        error(
                            "Disagreement between codeName='$codeNameOrNull' " +
                                    "and minSdkVersion='$minSdkVersionOrZero'. Only one " +
                                    "should be specified."
                        )
                        minSdkVersionOrZero
                    }
                }
            minSdkVersionOrZero == 0 -> minSdkVersionFromCodeName
            else -> {
                // In case this is a number-valued alias then it may have arrived from build.gradle
                // in the minSdkVersion field. Try to look up the alias now.
                val lookup = platformNameAliases[minSdkVersionOrZero.toString()]
                if (lookup != null) {
                    info("Version minSdkVersion='$minSdkVersionOrZero' is mapped to '$lookup'.")
                    return lookup
                } else {
                    minSdkVersionOrZero
                }
            }
        }
        return minSdkVersion!!
    }

    /**
     * Algorithm used in the pre-meta/platform ABIs era (likely < r17) to find an existing and
     * appropriate platform. See the "c" steps in the comment at the top of this class.
     */
    private fun findPlatformConfiguratorLegacy(
        abi: Abi,
        minSdkVersion: Int,
        platformDir: File,
        error: (String) -> Unit,
        info: (String) -> Unit): Int {

        val linkerSysrootPath = getLinkerSysrootPath(
            ndkRoot, abi,
            "android-$minSdkVersion"
        )
        if (File(linkerSysrootPath).isDirectory) {
            return minSdkVersion
        }

        // Find the minimum and maximum platform folders and clamp the specified minSdkVersion
        // into that range.
        val platformSubDirs = platformDir.listFiles(FileFilter { it.isDirectory }).orEmpty()
        val versions =  platformSubDirs
            .filter { it.name.startsWith("android-") }
            .filter { FileUtils.join(it, "arch-" + abi.architecture).isDirectory }
            .mapNotNull {
                val version = it.name.substring("android-".length).toIntOrNull()
                if (version == null) {
                    info("Found non-numeric platform folder '${it.name}'. Ignoring.")
                }
                version
            }
        if (versions.isEmpty()) {
            error("Abi '$abi' is not recognized in '$ndkRoot'.")
            // This should be impossible but fall back to a sensible default
            return sensibleDefaultPlatformApiVersionForErrorCase
        }
        val min = versions.min()!!
        val max = versions.max()!!
        return clamp(minSdkVersion, min, max, error)
    }

    /**
     * Clamp minSdkVersion into min/max range. Issue an error in the too-high case.
     */
    private fun clamp(
        minSdkVersion: Int,
        min: Int,
        max: Int,
        error: (String) -> Unit): Int {
        return when {
            minSdkVersion < min -> min
            minSdkVersion > max -> {
                if (minSdkVersion < veryHighPlatformApiVersion) {
                    error("Platform version '$minSdkVersion' is too high.")
                }
                max
            }
            else -> minSdkVersion
        }
    }

    fun findSuitablePlatformVersion(
        abiName: String,
        androidVersion: AndroidVersion?): Int {
        val ndkMetaPlatformsFile = NdkMetaPlatforms.jsonFile(ndkRoot)
        val ndkMetaPlatforms = if (ndkMetaPlatformsFile.isFile) {
            NdkMetaPlatforms.fromReader(FileReader(ndkMetaPlatformsFile))
        } else {
            null
        }

        return findSuitablePlatformVersionLogged(abiName, androidVersion, ndkMetaPlatforms)
    }

    private fun getLinkerSysrootPath(
        ndkRoot: File,
        abi: Abi,
        platformVersion: String): String {
        return FileUtils.join(
            ndkRoot.path, "platforms", platformVersion, "arch-" + abi.architecture
        )
    }
}
