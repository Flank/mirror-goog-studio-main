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

import com.android.SdkConstants
import com.android.SdkConstants.FD_CMAKE
import com.android.build.gradle.external.cmake.CmakeUtils
import com.android.build.gradle.internal.SdkHandler
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.EvalIssueReporter
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.ILogger
import java.io.File
import java.io.IOException

/**
 * This is the logic for locating CMake needed for gradle build. This logic searches for CMake in
 * a prescribed order and provides diagnostic information, warnings, and errors useful to the user
 * in understanding how CMake was found or why it wasn't found.
 *
 * There are several pieces of information at play here:
 * (1) The optional CMake version specified in build.gradle under externalNativeBuild.cmake.version.
 * (2) The optional path to CMake folder specified in local.properties file. This file is not meant
 *     to be checked in to version control.
 * (3) The SDK versions that are available in the SDK. Some of these may have been downloaded
 *     already.
 * (4) The system $PATH variable.
 * (5) The version numbers of "fork CMake" which is an older forked version of CMake that has
 *     additional functionality to help with emitting metadata.
 *
 * The version numbers of SDK CMakes are special in that there are two distinct versions for each
 * cmake.exe:
 * (1) A version like "3.6.0-rc2" which is the compiled-in version that is emitted by a call to
 *     cmake --version.
 * (2) A version like "3.6.4111459" which is the SDK's version for the CMake package. In this case,
 *     4111459 is the ADRT "bid" number used for deployment withing SDK.
 *
 * The searching algorithm needs to take into account that new SDK versions of CMake will be added
 * in the future. We can't assume we know versions a priori.
 *
 * Only the following CMake versions are supported:
 * (1) A known "fork CMake" from the SDK
 * (2) A version of CMake that supports CMake "server" mode. This mode is supported by CMake 3.7.0
 *     and higher.
 *
 * The search algorithm tries to enforce several invariants:
 * (1) If a cmake is specified in local.properties then that CMake must be used or it's an error.
 * (2) If a CMake version *is* specified in build.gradle externalNativeBuild.cmake.version then that
 *     version must be used or it's an error.
 * (3) If a CMake version *is not* specified in build.gradle externalNativeBuild.cmake.version then
 *     fork CMake must be used. We won't use a random CMake found on the path.
 * (4) Combining (2) and (3) creates the additional invariant that there is always a specific CMake
 *     version prescribed by a build.gradle. In the case there is no concrete version in
 *     build.gradle there is still a concreted Android Gradle Plugin version which, in turn,
 *     prescribes an exact CMake version.
 *
 * Given these invariants, the algorithm looks in the following locations:
 * (1) SDK
 * (2) $PATH
 * (3) cmake.dir from local.properties
 *
 * Error Handling:
 * A lot of the logic in this algorithm is dedicated toward giving good error messages in the case
 * that CMake can't be found.
 *
 * - If some CMake versions are found but they don't match the required version then the user is
 *   told what the versions of those CMakes are and where they were found.
 *
 * - If a requested CMake version looks like an SDK version (because it has a bid number in it) then
 *   a specifically constructed error message is emitted that Android Studio can use to
 *   automatically download that version.
 *
 * The various expected error messages are heavily tested in CmakeLocatorTests.kt.
 *
 * Warning:
 * Right now there is only one warning that may be emitted by this algorithm. This is in the case
 * that a CMake.exe is found but it cannot be executed to get its version number. The reason
 * it's not an error is that a matching CMake version may be found in another location. The reason
 * it's not a diagnostic info  is that it's an unusual circumstance and the user may end up
 * confused when their expected CMake isn't found.
 *
 */

/**
 * This is the default CMake version to use for this Android Gradle Plugin.
 */
private const val forkCmakeSdkVersion = "3.6.4111459"
private val forkCmakeSdkVersionRevision = Revision.parseRevision(forkCmakeSdkVersion)

/**
 *  This is the version that forked CMake (which has SDK version 3.6.4111459) reports
 *  when cmake --version is called. For backward compatibility we locate 3.6.4111459
 *  when this version is requested.
 */
private val forkCmakeReportedVersion = Revision.parseRevision("3.6.0-rc2")


private val newline = System.lineSeparator()

/**
 * Accumulated information about that is common to all search methodologies.
 */
private class CmakeSearchContext(
    val cmakeVersionFromDsl: String?,
    error: (String) -> Unit,
    val warn: (String) -> Unit,
    val info: (String) -> Unit
) {
    var result: File? = null
    var cmakeVersion: Revision? = null
    var firstError: String? = null
    val unsuitableCmakeReasons = mutableListOf<String>()
    var requestDownloadFromAndroidStudio = false

    /**
     * Issue an error. Processing continues so also capture the firstError since this should
     * be the most interesting.
     */
    val error = { message: String ->
        if (firstError == null) {
            firstError = message
        }
        error(message)
    }

    /**
     * Return true if the user specified a version in build.gradle.
     */
    internal fun versionInDsl() = cmakeVersionFromDsl != null

    /**
     * Return true if the CMake version looks like it is from the Android SDK.
     * The difference between Android SDK CMake versions:
     * - CMake versions from kitware.org look like 3.6.2
     * - CMake versions from Android Studio SDK look like 3.6.4111459
     * In the latter case, micros contains the ADRT "bid" number from cmake.yaml
     */
    internal fun versionLooksLikeSdkVersion() = cmakeVersion!!.micro >= 4111459

    /**
     * A CMake was found but it was unsuitable because the version didn't match what the user
     * requested. This function records a message about this to tell the user where CMake is
     * available.
     */
    fun recordUnsuitableCmakeMessage(message: String) {
        info(message)
        unsuitableCmakeReasons += "- $message"
    }

    /**
     * A CMake was found but it was unsuitable because the version didn't match what the user
     * requested. This function constructs a message with version and location to tell the
     * user where to find CMake.
     */
    fun recordUnsuitableCmake(foundVersion: Revision, locationTag: String) {
        recordUnsuitableCmakeMessage(
            "CMake '$foundVersion' found $locationTag was not the " +
                    "requested version '$cmakeVersion'."
        )
    }

    /**
     * If the user hasn't specified a version of CMake in their build.gradle then choose a default
     * version for them.
     */
    internal fun useDefaultCmakeVersionIfNecessary(): CmakeSearchContext {
        cmakeVersion = if (cmakeVersionFromDsl == null) {
            info("No CMake version was specified in build.gradle. Choosing a suitable version. ")
            forkCmakeSdkVersionRevision
        } else {
            try {
                Revision.parseRevision(cmakeVersionFromDsl)
            } catch (e: NumberFormatException) {
                error("CMake version '$cmakeVersionFromDsl' is not formatted correctly.")
                forkCmakeSdkVersionRevision
            }
        }
        return this
    }

    /**
     * If the CMake version is too low then it won't have features we need for processing.
     * In this case issue an error and recover by using forkCmakeSdkVersion.
     */
    internal fun checkForCmakeVersionTooLow(): CmakeSearchContext {
        cmakeVersion =
                if (cmakeVersion!!.major < 3
                    || (cmakeVersion!!.major == 3 && cmakeVersion!!.minor < 6)
                ) {
                    error("CMake version '$cmakeVersion' is too low. Use 3.7.0 or higher.")
                    forkCmakeSdkVersionRevision
                } else {
                    cmakeVersion
                }
        return this
    }

    /**
     * If there is a cmake.dir path in the user's local.properties then use it. If the version number
     * from build.gradle doesn't agree then that is an error.
     */
    internal fun tryPathFromLocalProperties(
        cmakeVersionGetter: (File) -> Revision?,
        pathFromLocalProperties: File?
    ): CmakeSearchContext {
        if (result != null) {
            return this
        }
        if (pathFromLocalProperties == null) {
            return this
        }
        val binDir = File(pathFromLocalProperties, "bin")
        val version = cmakeVersionGetter(binDir)
        if (version == null) {
            error("Could not get version from cmake.dir path '$pathFromLocalProperties'.")
            return this
        }

        if (version == cmakeVersion || cmakeVersionFromDsl == null) {
            info("- Found CMake '$version' via cmake.dir='$pathFromLocalProperties'.")
            result = pathFromLocalProperties
        } else {
            error(
                "CMake '$version' found via cmake.dir='$pathFromLocalProperties' does not match " +
                        "requested version '$cmakeVersion'."
            )
        }

        return this
    }

    /**
     * If the user specifies the reported version from fork cmake (which is 3.6.0-rc2) then
     * translate that into the SDK-style version number so it can be found.
     */
    internal fun translateForkCmakeVersionForBackCompatibility()
            : CmakeSearchContext {
        if (cmakeVersion == forkCmakeReportedVersion) {
            cmakeVersion = forkCmakeSdkVersionRevision
        }
        return this
    }

    /**
     * Search within the already-download SDK packages.
     */
    internal fun tryLocalRepositoryPackages(
        downloader: (String) -> Unit,
        repositoryPackages: () -> List<LocalPackage>
    ): CmakeSearchContext {
        if (result != null) {
            return this
        }
        info("Trying to locate CMake in local SDK repository.")
        val packages = repositoryPackages().associateBy({ it.version }, { it }).toMutableMap()

        if (packages.containsKey(cmakeVersion)) {
            info("- Found CMake '$forkCmakeSdkVersionRevision' in SDK.")
            result = packages[forkCmakeSdkVersionRevision]!!.location
        } else if (cmakeVersion == forkCmakeSdkVersionRevision) {
            // The version is exactly the default version. Download it if possible.
            info("- Downloading '$forkCmakeSdkVersionRevision'. ")
            downloader(forkCmakeSdkVersion)
            repositoryPackages().onEach { pkg ->
                if (cmakeVersion == pkg.version) {
                    result = pkg.location
                }
            }
            if (result == null) {
                requestDownloadFromAndroidStudio = true
                error(
                    "CMake '${cmakeVersion.toString()}' is required but has not yet been " +
                            "downloaded from the SDK."
                )
            }
        }

        if (result == null) {
            // Record all SDK packages as unsuitable.
            packages.keys.onEach { version ->
                recordUnsuitableCmake(version, "in SDK")
            }
        }
        return this
    }

    /**
     * Look in $PATH for CMakes to use. If there is a version number in build.gradle then that
     * version will be used if possible. If there is no version in build.gradle then we take the
     * highest version from the set of CMakes found.
     */
    internal fun tryFindInPath(
        cmakeVersionGetter: (File) -> Revision?,
        environmentPaths: () -> List<File>
    ): CmakeSearchContext {
        if (result != null) {
            return this
        }
        info("Trying to locate CMake in PATH.")
        val versionsFoundOnPath = mutableListOf<Pair<File, Revision>>()
        for (cmakeFolder in environmentPaths()) {
            try {
                val version = cmakeVersionGetter(cmakeFolder) ?: continue
                val cmakeInstallPath = cmakeFolder.parentFile

                if (cmakeVersion == version) {
                    result = cmakeInstallPath
                    return this
                } else if (!versionInDsl()) {
                    versionsFoundOnPath += Pair(cmakeInstallPath, version)
                } else {
                    recordUnsuitableCmakeMessage(
                        "CMake '$version' found in PATH at '$cmakeFolder' " +
                                "was not the required version '$cmakeVersion'."
                    )
                }
                break
            } catch (e: IOException) {
                warn("Could not execute cmake at '$cmakeFolder' to get version. Skipping.")
            }
        }
        if (!versionsFoundOnPath.isEmpty()) {
            val sorted = versionsFoundOnPath.sortedByDescending { (_, version) -> version }

            for ((index, pathVersion) in sorted.withIndex()) {
                val (path, version) = pathVersion
                if (firstError == null) {
                    if (index == 0) {
                        result = path
                        info("Using CMake '$version' found at '$path'.")
                    } else {
                        recordUnsuitableCmakeMessage(
                            "CMake found in PATH at '$path' had version '$version'" +
                                    " which is lower than the CMake found in the path at " +
                                    "'$result'."
                        )
                    }
                } else {
                    recordUnsuitableCmakeMessage(
                        "CMake found in PATH at '$path' had version '$version'."
                    )
                }
            }
        }

        return this
    }

    /**
     * If no suitable CMake was found then issue a diagnostic error. If the requested CMake version
     * looks like an SDK version of CMake then issue a specifically constructed message that Android
     * Studio can recognize and prompt the user to download.
     */
    internal fun issueVersionNotFoundError(): File? {
        if (result != null && !requestDownloadFromAndroidStudio) {
            return result
        }

        val unsuitableCMakes = if (unsuitableCmakeReasons.isEmpty()) ""
        else newline + unsuitableCmakeReasons.joinToString(newline)

        if (firstError != null) {
            // Throw an exception to trigger Android Studio to consider the error message for the
            // purposes of downloading CMake from the SDK.
            throw RuntimeException("$firstError$unsuitableCMakes")
        }


        if (!versionInDsl()) {
            // There was no CMake version specified in the DSL.
            throw RuntimeException(
                "No suitable version of CMake found from SDK, PATH, or " +
                        "cmake.dir property.$unsuitableCMakes"
            )
        } else {
            // There was a CMake version specified in the DSL. Does it look like an SDK version?
            if (versionLooksLikeSdkVersion()) {
                throw RuntimeException(
                    "CMake '${cmakeVersion.toString()}' was not found " +
                            "from SDK, PATH, or by cmake.dir property.$unsuitableCMakes"
                )
            } else {
                throw RuntimeException(
                    "CMake '${cmakeVersion.toString()}' was not found in " +
                            "PATH or by cmake.dir property.$unsuitableCMakes"
                )
            }
        }
    }
}

/**
 * @return array of folders (as Files) retrieved from PATH environment variable and from Sdk
 * cmake folder.
 */
private fun getEnvironmentPaths(): List<File> {
    val envPath = System.getenv("PATH") ?: ""
    val pathSeparator = System.getProperty("path.separator").toRegex()
    return envPath
        .split(pathSeparator)
        .filter { it.isNotEmpty() }
        .map { File(it) }
}

private fun getSdkCmakePackages(
    sdkHandler: SdkHandler,
    logger: ILogger
): List<LocalPackage> {
    val androidSdkHandler = AndroidSdkHandler.getInstance(sdkHandler.sdkFolder)
    val sdkManager = androidSdkHandler.getSdkManager(LoggerProgressIndicatorWrapper(logger))
    val packages = sdkManager.packages
    return packages.getLocalPackagesForPrefix(FD_CMAKE).toList()
}

private fun errorReporter(issueReporter: EvalIssueReporter, message: String, variantName: String) {
    issueReporter.reportError(
        EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
        EvalIssueException(message, variantName)
    )
}

private fun getCmakeRevisionFromExecutable(cmakeFolder: File): Revision? {
    if (!cmakeFolder.exists()) {
        return null
    }
    val cmakeExecutableName = if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
        "cmake.exe"
    } else {
        "cmake"
    }
    val cmakeExecutable = File(cmakeFolder, cmakeExecutableName)
    if (!cmakeExecutable.exists()) {
        return null
    }
    return CmakeUtils.getVersion(cmakeFolder)
}

private fun warningReporter(
    issueReporter: EvalIssueReporter,
    message: String,
    variantName: String
) {
    issueReporter.reportWarning(
        EvalIssueReporter.Type.EXTERNAL_NATIVE_BUILD_CONFIGURATION,
        message,
        variantName
    )
}

/**
 * This is the correct find-path logic that has callback for external dependencies like errors,
 * version of CMake file, and so forth. This is the entry-point for unit testing.
 */
fun findCmakePathLogic(
    cmakeVersionFromDsl: String?,
    cmakePathFromLocalProperties: File?,
    error: (String) -> Unit,
    warn: (String) -> Unit,
    info: (String) -> Unit,
    downloader: (String) -> Unit,
    environmentPaths: () -> List<File>,
    cmakeVersion: (File) -> Revision?,
    repositoryPackages: () -> List<LocalPackage>
): File? {
    return CmakeSearchContext(cmakeVersionFromDsl,
        { message -> error(message) },
        { message -> warn(message) },
        { message -> info(message) })
        .useDefaultCmakeVersionIfNecessary()
        .checkForCmakeVersionTooLow()
        .translateForkCmakeVersionForBackCompatibility()
        .tryPathFromLocalProperties(cmakeVersion, cmakePathFromLocalProperties)
        .tryLocalRepositoryPackages(downloader, repositoryPackages)
        .tryFindInPath(cmakeVersion, environmentPaths)
        .issueVersionNotFoundError()
}

/**
 * Locate CMake cmake path for the given build configuration.
 *
 * cmakeVersionFromDsl is the, possibly null, CMake version from the user's build.gradle.
 *   If it is null then a default version will be chosen.
 */
fun findCmakePath(
    cmakeVersionFromDsl: String?,
    sdkHandler: SdkHandler,
    variantName: String,
    issueReporter: EvalIssueReporter,
    logger: ILogger
): File? {
    return findCmakePathLogic(
        cmakeVersionFromDsl,
        sdkHandler.cmakePathInLocalProp,
        { message -> errorReporter(issueReporter, message, variantName) },
        { message -> warningReporter(issueReporter, message, variantName) },
        { message -> logger.info(message) },
        { version -> sdkHandler.installCMake(version) },
        { getEnvironmentPaths() },
        { folder -> getCmakeRevisionFromExecutable(folder) },
        { getSdkCmakePackages(sdkHandler, logger) })
}