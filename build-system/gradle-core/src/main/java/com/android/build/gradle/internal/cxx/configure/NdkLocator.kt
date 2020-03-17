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

import com.android.SdkConstants.FD_NDK
import com.android.SdkConstants.FD_NDK_SIDE_BY_SIDE
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.android.build.gradle.internal.SdkLocator
import com.android.build.gradle.internal.cxx.caching.cache
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION
import com.android.build.gradle.internal.cxx.logging.IssueReporterLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.builder.errors.IssueReporter
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileNotFoundException

/**
 * The hard-coded NDK version for this Android Gradle Plugin.
 */
const val ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION = "21.0.6113669"

/**
 * Logic to find the NDK.
 *
 * userSettings - information that comes from the user in some way. Includes android.ndkVersion,
 *   for example.
 * getNdkSourceProperties - given a folder to an NDK, this function returns source.properties
 *   content or null if that file doesn't exist.
 *
 * The high-level behavior of this function is:
 * (1) Use ndk.dir if possible
 * (2) Otherwise, use $SDK/ndk/$ndkVersion if possible
 * (3) Otherwise, use $SDK/ndk-bundle if possible.
 * (4) Otherwise, return null
 *
 * This function uses errorln(...) for extraordinary cases that shouldn't happen in the real world.
 * For example, if NDK source.properties doesn't contain Pkg.Revision then that's an errorln(...).
 *
 * It also uses errorln(...) when the user made a mistake in specifying android.ndkVersion. For
 * example, not specifying enough precision. This case can't be ignored with warnln(...) because
 * otherwise we could fall back to an NDK version the user didn't intend.
 *
 * In the case that an NDK couldn't be found warnln(...) is used and null is returned. This is to
 * allow the build to proceed and fail naturally when the NDK is needed.
 */
private fun findNdkPathImpl(
    userSettings : NdkLocatorKey,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): File? {
    with(userSettings) {

        // Record status of user-supplied information
        logUserInputs(userSettings)

        // Try to get the parsed revision for the requested version. If it's unparseable then
        // emit an error and return.
        val ndkVersion =
            parseRevision(getNdkVersionOrDefault(ndkVersionFromDsl)) ?: return null

        // Function to get the parsed Pkg.Revision, return null if that failed for some reason.
        fun getNdkFolderRevision(ndkDirFolder: File) =
            getNdkFolderParsedRevision(ndkDirFolder, getNdkSourceProperties)

        // If ndk.dir value is present then use it.
        if (!ndkDirProperty.isNullOrBlank()) {
            val ndkDirFolder = File(ndkDirProperty)
            if (getNdkFolderRevision(ndkDirFolder) != null) return ndkDirFolder
            warnln(
                "Location specified by ndk.dir ($ndkDirProperty) did not contain a valid " +
                        "NDK and couldn't be used"
            )
            return null
        }

        // At this point, the only remaining options are found in the SDK folder. So if the SDK
        // folder value is missing then don't search for sub-folders.
        if (sdkFolder != null) {
            // If a folder exists under $SDK/ndk/$ndkVersion then use it.
            val versionedNdkPath = File(File(sdkFolder, FD_NDK_SIDE_BY_SIDE), "$ndkVersion")
            if (getNdkFolderRevision(versionedNdkPath) != null) return versionedNdkPath

            // If $SDK/ndk-bundle exists then use it.
            val ndkBundlePath = File(sdkFolder, FD_NDK)
            val bundleRevision = getNdkFolderRevision(ndkBundlePath)
            if (bundleRevision != null && bundleRevision == ndkVersion) return ndkBundlePath
        }

        // No NDK was found. Emit a warning that includes the locally-available versions in
        // the SxS folder.
        val available = sideBySideNdkFolderNames
            .map { File(it).name }
            .sortedBy { File(it).name }
            .joinToString(", ")
        warnln("No version of NDK matched the required version $ndkVersion. " +
                "Versions available locally: $available")
        return null
    }
}


/**
 * Given a candidate NDK folder, get Pkg.Revision as parsed Revision.
 * Return null if:
 * - source.properties file doesn't exist
 * - Pkg.Revision in source.properties doesn't exist
 * - Pkg.Revision can not be parsed as Revision
 */
fun getNdkFolderParsedRevision(
    ndkDirFolder: File,
    getNdkSourceProperties: (File) -> SdkSourceProperties?): Revision? {
    val properties = getNdkSourceProperties(ndkDirFolder)
    if (properties == null) {
        infoln("Folder $ndkDirFolder does not exist. Ignoring.")
        return null
    }
    val packageRevision = properties.getValue(SDK_PKG_REVISION)
    if (packageRevision == null) {
        errorln("Folder $ndkDirFolder has no Pkg.Revision in source.properties. Ignoring.")
        return null
    }
    return parseRevision(packageRevision)
}

/**
 * Log information about user-defined inputs to locator.
 */
private fun logUserInputs(userSettings : NdkLocatorKey) {
    with(userSettings) {
        infoln("android.ndkVersion from module build.gradle is ${ndkVersionFromDsl ?: "not set"}")
        infoln("$NDK_DIR_PROPERTY in local.properties is ${ndkDirProperty ?: "not set"}")
        infoln("Not considering ANDROID_NDK_HOME because support was removed after deprecation period.")

        if (sdkFolder != null) {
            infoln("sdkFolder is $sdkFolder")
            val sxsRoot = join(sdkFolder, "ndk")
            if (!sxsRoot.isDirectory) {
                infoln("NDK side-by-side folder from sdkFolder $sxsRoot does not exist")
            }
        } else {
            infoln("sdkFolder is not set")
        }
    }
}

/**
 * If the user specified android.ndkVersion then return it. Otherwise, return the default version
 */
private fun getNdkVersionOrDefault(ndkVersionFromDsl : String?) =
    if (ndkVersionFromDsl.isNullOrBlank()) {
        infoln(
            "Because no explicit NDK was requested, the default version " +
                    "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION' for this Android Gradle " +
                    "Plugin will be used"
        )
        ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
    } else {
        ndkVersionFromDsl
    }

/**
 * Parse the given version and ensure that it has exactly precision of 3 or higher
 */
private fun parseRevision(version : String) : Revision? {
    try {
        val revision =
            stripPreviewFromRevision(Revision.parseRevision(version))
        if (revision.toIntArray(true).size < 3) {
            errorln(
                "Specified NDK version '$version' does not have " +
                        "enough precision. Use major.minor.micro in version."
            )
            return null
        }
        return revision
    } catch (e: NumberFormatException) {
        errorln("Requested NDK version '$version' could not be parsed")
        return null
    }
}

/**
 * Given a path to NDK, return the content of source.properties.
 * Returns null if the file isn't found.
 */
@VisibleForTesting
fun getNdkVersionInfo(ndkRoot: File): SdkSourceProperties? {
    return try {
        SdkSourceProperties.fromInstallFolder(ndkRoot)
    } catch (e: FileNotFoundException) {
        null
    }
}

/**
 * Given a path to an NDK root folder, return a list of the NDKs installed there.
 */
@VisibleForTesting
fun getNdkVersionedFolders(ndkVersionRoot: File): List<String> {
    if (!ndkVersionRoot.isDirectory) {
        return listOf()
    }
    return ndkVersionRoot.list()!!.filter { File(ndkVersionRoot, it).isDirectory }
}

/**
 * If the revision contains a preview element (like rc2) then strip it.
 */
private fun stripPreviewFromRevision(revision : Revision) : Revision {
    val parts = revision.toIntArray(false)
    return when(parts.size) {
        1 -> Revision(parts[0])
        2 -> Revision(parts[0], parts[1])
        else -> Revision(parts[0], parts[1], parts[2])
    }
}

/**
 * Key used for caching the results of NDK resolution.
 */
data class NdkLocatorKey(
    val ndkVersionFromDsl: String?,
    val ndkDirProperty: String?,
    val sdkFolder: File?,
    val sideBySideNdkFolderNames : List<String>
)

/**
 * The result of NDK resolution saved into cache.
 */
data class NdkLocatorRecord(
    val ndkFolder: File?
)

/**
 * Wraps findNdkPathImpl with caching.
 */
@VisibleForTesting
fun findNdkPathImpl(
    ndkVersionFromDsl: String?,
    ndkDirProperty: String?,
    sdkFolder: File?,
    getNdkVersionedFolderNames: (File) -> List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): File? {
    val key = NdkLocatorKey(
        ndkVersionFromDsl,
        ndkDirProperty,
        sdkFolder,
        if(sdkFolder != null) getNdkVersionedFolderNames(join(sdkFolder, FD_NDK_SIDE_BY_SIDE)) else listOf())
    // Result of NDK location could be cached at machine level.
    // Here, it's cached at module level instead because uncleanable caches can lead to difficult bugs.
    return cache(key, {
        PassThroughDeduplicatingLoggingEnvironment().use {
            val ndkFolder = findNdkPathImpl(
                key,
                getNdkSourceProperties
            )
            NdkLocatorRecord(
                ndkFolder = ndkFolder
            )
        }
    }).ndkFolder
}

/**
 * There are three possible physical locations for NDK:
 *
 *  (1) SDK unversioned: $(SDK)/ndk-bundle
 *  (2) SDK versioned: $(SDK)/ndk/18.1.2 (where 18.1.2 is an example)
 *  (3) Custom: Any location on disk
 *
 * There are several ways the user can tell Android Gradle Plugin where to find the NDK
 *
 *  (1) Set an explicit folder in local.settings for ndk.dir
 *  (2) Don't specify a folder which implies an NDK from the SDK folder should be used
 *
 * If the user specifies android.ndkVersion in build.gradle then that version must be available
 * or it is an error. If no such version is specified then the default version is used.
 */
fun findNdkPath(
    issueReporter: IssueReporter,
    ndkVersionFromDsl: String?,
    projectDir: File
): File? {
    IssueReporterLoggingEnvironment(issueReporter).use {
        val properties = gradleLocalProperties(projectDir)
        val sdkPath = SdkLocator.getSdkDirectory(projectDir, issueReporter)
        return findNdkPathImpl(
            ndkVersionFromDsl,
            properties.getProperty(NDK_DIR_PROPERTY),
            sdkPath,
            ::getNdkVersionedFolders,
            ::getNdkVersionInfo
        )
    }
}
