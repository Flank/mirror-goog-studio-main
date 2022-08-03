/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.LintFix
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Provides information about libraries from the Google Play SDK Index.
 */
abstract class GooglePlaySdkIndex(cacheDir: Path? = null) : NetworkCache(
    GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL,
    GOOGLE_PLAY_SDK_INDEX_KEY,
    cacheDir,
    cacheExpiryHours = TimeUnit.DAYS.toHours(GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS).toInt()
) {
    companion object {
        const val DEFAULT_SHOW_MESSAGES = true
        const val DEFAULT_SHOW_LINKS = true
        const val DEFAULT_SHOW_POLICY_ISSUES = false
        const val DEFAULT_SHOW_CRITICAL_ISSUES = true
        const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE = "snapshot.gz"
        const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE = "sdk-index-offline-snapshot.proto.gz"
        const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL = "https://dl.google.com/play-sdk/index/"
        const val GOOGLE_PLAY_SDK_INDEX_KEY = "sdk_index"
        const val GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS = 7L
        const val GOOGLE_PLAY_SDK_INDEX_URL = "https://play.google.com/sdks"
        const val VIEW_DETAILS_MESSAGE = "View details in Google Play SDK Index"
    }

    private var initialized: Boolean = false
    private var status: GooglePlaySdkIndexStatus = GooglePlaySdkIndexStatus.NOT_READY
    private val libraryToSdk = HashMap<String, LibraryToSdk>()
    var showMessages = DEFAULT_SHOW_MESSAGES
    var showLinks = DEFAULT_SHOW_LINKS
    var showPolicyIssues = DEFAULT_SHOW_POLICY_ISSUES
    var showCriticalIssues = DEFAULT_SHOW_CRITICAL_ISSUES

    /**
     * Read Index snapshot (locally if it is not old and remotely if old
     * and network is available) and store results in maps for later
     * consumption.
     */
    fun initialize() {
        initialize(null)
    }

    @VisibleForTesting
    fun initialize(overriddenData: InputStream? = null) {
        synchronized(this) {
            if (initialized) {
                return
            }
            initialized = true
            status = GooglePlaySdkIndexStatus.NOT_READY
        }
        var index: Index? = null
        // Read from cache/network
        if (overriddenData != null) {
            // Do not check for exceptions, calling from a test
            index = Index.parseFrom(overriddenData)
        } else {
            try {
                index = Index.parseFrom(GZIPInputStream(findData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE)))
            } catch (exception: Exception) {
                val message = if (exception.message.isNullOrEmpty()) exception.toString() else exception.message
                logCachingError(message)
                try {
                    index = Index.parseFrom(GZIPInputStream(readDefaultData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE)))
                } catch (defaultException: Exception) {
                    logErrorInDefaultData(defaultException.message)
                }
            }
        }
        if (index != null) {
            setMaps(index)
            status = GooglePlaySdkIndexStatus.READY
            logIndexLoadedCorrectly()
        }
    }

    /**
     * Tells if the Index is ready to be used (loaded and maps
     * generated)
     */
    fun isReady() = status == GooglePlaySdkIndexStatus.READY

    /**
     * Does this library have policy issues?
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @param versionString: version to check
     * @param buildFile: build file in which this dependency is
     *     declared, for logging purposes
     * @return true if the index has information about this particular
     *     version, and it has compliant issues.
     */
    fun isLibraryNonCompliant(groupId: String, artifactId: String, versionString: String, buildFile: File?): Boolean {
        val isNonCompliant = getLabels(groupId, artifactId, versionString)?.hasNonCompliantIssueInfo() ?: false
        if (isNonCompliant) {
            logNonCompliant(groupId, artifactId, versionString, buildFile)
        }
        return showMessages && showPolicyIssues && isNonCompliant
    }

    /**
     * Is this library marked as outdated?
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @param versionString: version to check
     * @param buildFile: build file in which this dependency is
     *     declared, for logging purposes
     * @return true if the index has information about this particular
     *     version, and it has been marked as outdated.
     */
    fun isLibraryOutdated(groupId: String, artifactId: String, versionString: String, buildFile: File?): Boolean {
        val isOutdated = getLabels(groupId, artifactId, versionString)?.hasOutdatedIssueInfo() ?: false
        if (isOutdated) {
            logOutdated(groupId, artifactId, versionString, buildFile)
        }
        return showMessages && isOutdated
    }

    /**
     * Does this library have critical issues?
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @param versionString: version to check
     * @param buildFile: build file in which this dependency is
     *     declared, for logging purposes
     * @return true if the index has information about this particular
     *     version, and it has critical issues reported by its authors.
     */
    fun hasLibraryCriticalIssues(groupId: String, artifactId: String, versionString: String, buildFile: File?): Boolean {
        val hasCriticalIssues = getLabels(groupId, artifactId, versionString)?.hasCriticalIssueInfo() ?: false
        if (hasCriticalIssues) {
            logHasCriticalIssues(groupId, artifactId, versionString, buildFile)
        }
        return showMessages && showCriticalIssues && hasCriticalIssues
    }

    /**
     * Does this library have blocking issues?
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @param versionString: version to check
     * @return true if the index has information about this particular
     *     version, and it has been labeled with blocking severity.
     */
    fun hasLibraryBlockingIssues(groupId: String, artifactId: String, versionString: String): Boolean {
        val labels = getLabels(groupId, artifactId, versionString) ?: return false
        val severity = labels.severity
        if (severity != null && severity == LibraryVersionLabels.Severity.BLOCKING_SEVERITY) {
            return true
        }
        // Non-compliant issues are always blocking
        val isLibraryNonCompliant = getLabels(groupId, artifactId, versionString)?.hasNonCompliantIssueInfo() ?: false
        return showPolicyIssues && isLibraryNonCompliant
    }

    /**
     * Get URL for the SDK associated to this library
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @return the associated URL or null if there is none or flag for
     *     links to SDK is not enabled
     */
    fun getSdkUrl(groupId: String, artifactId: String): String? {
        if (!isReady()) {
            return null
        }
        if (!showLinks)
            return null
        val sdk = getSdk(groupId, artifactId) ?: return null
        return sdk.sdk.indexUrl
    }

    private fun getLabels(groupId: String, artifactId: String, versionString: String): LibraryVersionLabels? {
        if (!isReady()) {
            return null
        }
        val libraryVersion = getLibraryVersion(groupId, artifactId, versionString) ?: return null
        return libraryVersion.versionLabels
    }

    private fun getLibraryVersion(groupId: String, artifactId: String, versionString: String): LibraryVersion? {
        val coordinate = createCoordinateString(groupId, artifactId)
        val sdk = libraryToSdk[coordinate] ?: return null
        return sdk.getVersion(versionString)
    }

    private fun getSdk(groupId: String, artifactId: String): LibraryToSdk? {
        val coordinate = createCoordinateString(groupId, artifactId)
        return libraryToSdk[coordinate]
    }

    private fun setMaps(index: Index) {
        libraryToSdk.clear()
        val sdkList = index.sdksList
        for (sdk in sdkList) {
            for (library in sdk.librariesList) {
                val coordinate = createCoordinateString(library.libraryId.mavenId.groupId, library.libraryId.mavenId.artifactId)
                val currentLibrary = LibraryToSdk(coordinate, sdk)
                for (version in library.versionsList) {
                    currentLibrary.addLibraryVersion(version.versionString, version)
                }
                libraryToSdk[currentLibrary.libraryId] = currentLibrary
            }
        }
    }

    private fun createCoordinateString(groupId: String, artifactId: String) = "$groupId:$artifactId"

    private enum class GooglePlaySdkIndexStatus {
        NOT_READY,
        READY,
    }

    private class LibraryToSdk(val libraryId: String, val sdk: Sdk) {
        private val versionToLibraryVersion = HashMap<String, LibraryVersion>()
        private var latestVersion: String? = null

        fun addLibraryVersion(versionString: String, libraryVersion: LibraryVersion) {
            versionToLibraryVersion[versionString] = libraryVersion
            if (libraryVersion.isLatestVersion) {
                latestVersion = versionString
            }
        }

        fun getVersion(versionString: String): LibraryVersion? {
            return versionToLibraryVersion[versionString]
        }
    }

    override fun readDefaultData(relative: String): InputStream? {
        if (GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE == relative) {
            return GooglePlaySdkIndex::class.java.getResourceAsStream("/$GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE")
        }
        return null
    }

    /**
     * Generates a LintFix that opens a browser to the given library.
     *
     * @param groupId: group id for library coordinates
     * @param artifactId: artifact id for library coordinates
     * @param versionString: version of the library (only used for
     *     logging)
     * @param buildFile: build file where this library is being used
     * @return a link to the SDK url this library belongs to if the
     *     index has information about it and [showLinks] is true.
     */
    open fun generateSdkLinkLintFix(groupId: String, artifactId: String, versionString: String, buildFile: File?): LintFix? {
        val url = getSdkUrl(groupId, artifactId)
        return if (url != null)
            LintFix.ShowUrl(VIEW_DETAILS_MESSAGE, null, url)
        else
            null
    }

    /** Generate a message for a library that has policy issues */
    fun generatePolicyMessage(groupId: String, artifactId: String, versionString: String) =
        "$groupId:$artifactId version $versionString has policy issues that will block publishing of your app to Play Console"

    /**
     * Generate a message for a library that has blocking critical
     * issues
     */
    fun generateBlockingCriticalMessage(groupId: String, artifactId: String, versionString: String) =
        "$groupId:$artifactId version $versionString has been reported as problematic by its author and will block publishing of your app to Play Console"

    /**
     * Generate a message for a library that has blocking outdated
     * issues
     */
    fun generateBlockingOutdatedMessage(groupId: String, artifactId: String, versionString: String) =
        "$groupId:$artifactId version $versionString has been marked as outdated by its author and will block publishing of your app to Play Console"

    /**
     * Generate a message for a library that has blocking outdated
     * issues
     */
    fun generateOutdatedMessage(groupId: String, artifactId: String, versionString: String) =
        "$groupId:$artifactId version $versionString has been marked as outdated by its author"

    /**
     * Generate a message for a library that has blocking critical
     * issues
     */
    fun generateCriticalMessage(groupId: String, artifactId: String, versionString: String) =
        "$groupId:$artifactId version $versionString has an associated message from its author"

    protected open fun logHasCriticalIssues(groupId: String, artifactId: String, versionString: String, file: File?) {
    }

    protected open fun logNonCompliant(groupId: String, artifactId: String, versionString: String, file: File?) {
    }

    protected open fun logOutdated(groupId: String, artifactId: String, versionString: String, file: File?) {
    }

    protected open fun logCachingError(message: String?) {
    }

    protected open fun logErrorInDefaultData(message: String?) {
    }

    protected open fun logIndexLoadedCorrectly() {
    }
}
