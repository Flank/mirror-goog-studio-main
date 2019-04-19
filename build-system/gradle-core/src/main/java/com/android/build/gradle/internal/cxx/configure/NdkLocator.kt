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
import com.android.SdkConstants.FN_SOURCE_PROP
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.cxx.configure.LocationType.ANDROID_NDK_HOME_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_BUNDLE_FOLDER_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_DIR_LOCATION
import com.android.build.gradle.internal.cxx.configure.LocationType.NDK_VERSIONED_FOLDER_LOCATION
import com.android.build.gradle.internal.cxx.configure.SdkSourceProperties.Companion.SdkSourceProperty.SDK_PKG_REVISION

import com.android.build.gradle.internal.cxx.logging.ErrorsAreFatalThreadLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.LoggingRecord
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment

import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.repository.Revision
import java.io.File
import java.io.FileNotFoundException
import java.lang.RuntimeException

private enum class LocationType(val tag: String) {
    // These are in order of preferred in the case when versions are identical.
    NDK_VERSIONED_FOLDER_LOCATION("in SDK ndk folder"),
    NDK_BUNDLE_FOLDER_LOCATION("in SDK ndk-bundle folder"),
    NDK_DIR_LOCATION("by $NDK_DIR_PROPERTY"),
    ANDROID_NDK_HOME_LOCATION("by ANDROID_NDK_HOME");
}

private data class Location(val type: LocationType, val ndkRoot: File)

/**
 * Logic to find the NDK.
 *
 * ndkVersionFromDsl - the literal version string from build.gradle. null if there was nothing
 * ndkDirProperty - the string ndk.dir from local.settings
 * androidNdkHomeEnvironmentVariable - the value of ANDROID_NDK_HOME from the environment
 * sdkFolder - the folder to the SDK if it exists
 * getNdkVersionedFolderNames - function that returns the NDK folders under $SDK/ndk
 * getNdkSourceProperties - given a folder to an NDK, this function returns the version of that NDK.
 */
private fun findNdkPathImpl(
    ndkDirProperty: String?,
    androidNdkHomeEnvironmentVariable: String?,
    sdkFolder: File?,
    ndkVersionFromDsl: String?,
    getNdkVersionedFolderNames: (File) -> List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): File? {

    // Record status of user-supplied information
    infoln("android.ndkVersion from module build.gradle is ${ndkVersionFromDsl ?: "not set"}")
    infoln("$NDK_DIR_PROPERTY in local.properties is ${ndkDirProperty ?: "not set"}")
    infoln(
        "ANDROID_NDK_HOME environment variable is " +
                (androidNdkHomeEnvironmentVariable ?: "not set")
    )
    infoln("sdkFolder is ${sdkFolder ?: "not set"}")

    // ANDROID_NDK_HOME is deprectated
    if (androidNdkHomeEnvironmentVariable != null) {
        warnln("Support for ANDROID_NDK_HOME is deprecated and will be removed in the future. Use android.ndkVersion in build.gradle instead.")
    }

    // Record that a location was consider and rejected and for what reason
    fun considerAndReject(location: Location, reason: String) {
        infoln("Rejected ${location.ndkRoot} ${location.type.tag} because $reason")
    }

    val foundLocations = mutableListOf<Location>()
    if (ndkDirProperty != null) {
        foundLocations += Location(NDK_DIR_LOCATION, File(ndkDirProperty))
    }
    if (androidNdkHomeEnvironmentVariable != null) {
        foundLocations += Location(
            ANDROID_NDK_HOME_LOCATION,
            File(androidNdkHomeEnvironmentVariable)
        )
    }
    if (sdkFolder != null) {
        foundLocations += Location(NDK_BUNDLE_FOLDER_LOCATION, File(sdkFolder, FD_NDK))
    }

    // Parse the user-supplied version and give an error if it can't be parsed.
    var ndkVersionFromDslRevision: Revision? = null
    if (ndkVersionFromDsl != null) {
        try {
            ndkVersionFromDslRevision = Revision.parseRevision(ndkVersionFromDsl)
        } catch (e: NumberFormatException) {
            errorln("Requested NDK version '$ndkVersionFromDsl' could not be parsed")
        }
    }

    if (sdkFolder != null) {
        val versionRoot = File(sdkFolder, FD_NDK_SIDE_BY_SIDE)
        foundLocations += getNdkVersionedFolderNames(versionRoot)
            .map { version ->
                Location(
                    NDK_VERSIONED_FOLDER_LOCATION,
                    File(versionRoot, version)
                )
            }
    }

    // Log all found locations
    foundLocations.forEach { location ->
        infoln("Considering ${location.ndkRoot} ${location.type.tag}")
    }

    // Eliminate those that don't look like NDK folders
    val versionedLocations = foundLocations
        .mapNotNull { location ->
            val versionInfo = getNdkSourceProperties(location.ndkRoot)
            when {
                versionInfo == null -> {
                    if (location.ndkRoot.resolve("RELEASE.TXT").exists()) {
                        considerAndReject(location, "it contains an unsupported (pre-r11) NDK")
                    } else {
                        considerAndReject(location, "that location has no $FN_SOURCE_PROP")
                    }
                    null
                }
                versionInfo.getValue(SDK_PKG_REVISION) == null -> {
                    considerAndReject(
                        location, "that location had $FN_SOURCE_PROP " +
                                "with no ${SDK_PKG_REVISION.key}"
                    )
                    null
                }
                else -> {
                    val revision = versionInfo.getValue(SDK_PKG_REVISION)!!
                    try {
                        Revision.parseRevision(revision)
                        Pair(location, versionInfo)
                    } catch (e: NumberFormatException) {
                        considerAndReject(
                            location, "that location had " +
                                    "source.properties with invalid ${SDK_PKG_REVISION.key}=$revision"
                        )
                        null
                    }
                }
            }
        }
        .sortedWith(compareBy({ -it.first.type.ordinal }, { it.second.revision }))
        .asReversed()



    // From the existing NDKs find the highest. We'll use this as a fall-back in case there's an
    // error. We still want to succeed the sync and recover as best we can.
    val highest = versionedLocations.firstOrNull()

    if (highest == null) {
        // The text of this message shouldn't change without also changing the corresponding
        // hotfix in Android Studio that recognizes this text
        if (ndkVersionFromDslRevision == null) {
            warnln("Compatible side by side NDK version was not found.")
        } else {
            warnln(
                "Compatible side by side NDK version was not found for android.ndkVersion " +
                        "'$ndkVersionFromDslRevision'"
            )
        }
        return null
    }

    // If the user requested a specific version then honor it now
    if (ndkVersionFromDslRevision != null) {
        // If the user specified ndk.dir then it must be used. It must also match the version
        // supplied in build.gradle.
        if (ndkDirProperty != null) {
            val ndkDirLocation = versionedLocations.find { (location, _) ->
                location.type == NDK_DIR_LOCATION
            }
            if (ndkDirLocation == null) {
                errorln(
                    "Location specified by ndk.dir ($ndkDirProperty) did not contain a " +
                            "valid NDK and so couldn't satisfy the requested NDK version " +
                            "$ndkVersionFromDsl"
                )
            } else {
                val (location, version) = ndkDirLocation
                if (isAcceptableNdkVersion(version.revision, ndkVersionFromDslRevision)) {
                    infoln(
                        "Choosing ${location.ndkRoot} from $NDK_DIR_PROPERTY which had the requested " +
                                "version $ndkVersionFromDsl"
                    )
                } else {
                    errorln(
                        "Requested NDK version $ndkVersionFromDsl did not match the version " +
                                "${version.revision} requested by $NDK_DIR_PROPERTY at ${location.ndkRoot}"
                    )
                }
                return location.ndkRoot
            }
        }

        // If not ndk.dir then take the version that matches the requested NDK version
        val matchingLocations = versionedLocations
            .filter { (_, sourceProperties) ->
                isAcceptableNdkVersion(sourceProperties.revision, ndkVersionFromDslRevision)
            }
            .toList()

        if (matchingLocations.isEmpty()) {
            // No versions matched the requested revision
            versionedLocations.onEach { (location, version) ->
                considerAndReject(
                    location,
                    "that NDK had version ${version.revision} which didn't " +
                            "match the requested version $ndkVersionFromDsl"
                )
            }
            if (versionedLocations.isNotEmpty()) {
                val available =
                    versionedLocations
                        .sortedBy { (_, version) -> version.revision }
                        .joinToString(", ") { (_, version) -> version.revision.toString() }
                errorln("No version of NDK matched the requested version $ndkVersionFromDsl. Versions available locally: $available")
            } else {
                errorln("No version of NDK matched the requested version $ndkVersionFromDsl")
            }
            return highest.first.ndkRoot
        }

        // There could be multiple. Choose the preferred location and if there are multiple in that
        // location then choose the highest version there.
        val foundNdkRoot = matchingLocations.first().first.ndkRoot

        if (matchingLocations.size > 1) {
            infoln(
                "Found ${matchingLocations.size} NDK folders that matched requested " +
                        "version $ndkVersionFromDslRevision:"
            )
            matchingLocations.forEachIndexed { index, (location, _) ->
                infoln(" (${index + 1}) ${location.ndkRoot} ${location.type.tag}")
            }
            infoln("  choosing $foundNdkRoot")
        } else {
            infoln("Found requested NDK version $ndkVersionFromDslRevision at $foundNdkRoot")
        }
        return foundNdkRoot

    } else {
        // If the user specified ndk.dir then it must be used.
        if (ndkDirProperty != null) {
            val ndkDirLocation =
                versionedLocations.find { (location, _) ->
                    location.type == NDK_DIR_LOCATION
                }
            if (ndkDirLocation == null) {
                errorln(
                    "Location specified by ndk.dir ($ndkDirProperty) did not contain a " +
                            "valid NDK and and couldn't be used"
                )

                infoln(
                    "Using ${highest.first.ndkRoot} which is " +
                            "version ${highest.second.revision} as fallback but build will fail"
                )
                return highest.first.ndkRoot
            }
            val (location, version) = ndkDirLocation
            infoln("Found requested ndk.dir (${location.ndkRoot}) which has version ${version.revision}")
            return location.ndkRoot
        }

        // No NDK version was requested.
        infoln(
            "No user requested version, choosing ${highest.first.ndkRoot} which is " +
                    "version ${highest.second.revision}"
        )
        return highest.first.ndkRoot
    }
}

@VisibleForTesting
fun findNdkPathWithRecord(
    ndkVersionFromDsl: String?,
    ndkDirProperty: String?,
    androidNdkHomeEnvironmentVariable: String?,
    sdkFolder: File?,
    getNdkVersionedFolderNames: (File) -> List<String>,
    getNdkSourceProperties: (File) -> SdkSourceProperties?
): NdkLocatorRecord {
    ErrorsAreFatalThreadLoggingEnvironment().use {
        PassThroughRecordingLoggingEnvironment().use { loggingEnvironment ->
            val ndkFolder = findNdkPathImpl(
                ndkDirProperty,
                androidNdkHomeEnvironmentVariable,
                sdkFolder,
                ndkVersionFromDsl,
                getNdkVersionedFolderNames,
                getNdkSourceProperties
            )
            return NdkLocatorRecord(
                ndkFolder = ndkFolder,
                messages = loggingEnvironment.record
            )
        }
    }
}

/**
 * Returns true if the found revision sourcePropertiesRevision can satisfy the user's requested
 * revision from build.gradle.
 */
private fun isAcceptableNdkVersion(
    sourcePropertiesRevision: Revision, revisionFromDsl: Revision
): Boolean {
    val parts = revisionFromDsl.toIntArray(true)
    return when (parts.size) {
        3, 4 -> sourcePropertiesRevision == revisionFromDsl
        2 -> revisionFromDsl.major == sourcePropertiesRevision.major &&
                revisionFromDsl.minor == sourcePropertiesRevision.minor
        1 -> revisionFromDsl.major == sourcePropertiesRevision.major
        else -> throw RuntimeException("Unexpected")
    }
}

@VisibleForTesting
fun getNdkVersionInfo(ndkRoot: File): SdkSourceProperties? {
    return try {
        SdkSourceProperties.fromInstallFolder(ndkRoot)
    } catch (e: FileNotFoundException) {
        null
    }
}

@VisibleForTesting
fun getNdkVersionedFolders(ndkVersionRoot: File): List<String> {
    if (!ndkVersionRoot.isDirectory) {
        return listOf()
    }
    return ndkVersionRoot.list().filter { File(ndkVersionRoot, it).isDirectory }
}

data class NdkLocatorRecord(
    val ndkFolder: File?,
    val messages: List<LoggingRecord> = listOf()
)

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
 *  (2) Specify an explicit folder with environment variable ANDROID_NDK_HOME
 *  (3) Don't specify a folder which implies an NDK from the SDK folder should be used
 *
 * If the user specifies android.ndkVersion in build.gradle then that version must be available
 * or it is an error. If no such version is specifies then the highest version available is
 * used.
 *
 * The value android.ndkVersion in build.gradle can be partial. In this case, the existing parts
 * of the version must match the found NDK(s). By example, this works as follows:
 *
 * build.gradle NDK        is acceptable
 * ------------ ---------- -------------
 * 18.1.12346   18.1.12346 yes
 * 18.1.12346   18.1.12345 no
 * 18.1         18.1.12346 yes
 * 18.2         18.1.12346 no
 * 18           18.1.12346 yes
 * 19           18.1.12346 no
 *
 * If multiple NDKs satisfy the version then the highest NDK is taken.
 *
 * Failure behaviour -- even if there is a failure, this function tries to at least return *some*
 * NDK so that the gradle Sync can continue.
 */
fun findNdkPath(
    ndkVersionFromDsl: String?,
    projectDir: File
): NdkLocatorRecord {
    val properties = gradleLocalProperties(projectDir)
    val sdkLocation = SdkHandler.findSdkLocation(properties, projectDir)
    val sdkPath = sdkLocation.first
    return findNdkPathWithRecord(
        ndkVersionFromDsl,
        properties.getProperty(NDK_DIR_PROPERTY),
        System.getenv("ANDROID_NDK_HOME"),
        sdkPath,
        ::getNdkVersionedFolders,
        ::getNdkVersionInfo
    )
}