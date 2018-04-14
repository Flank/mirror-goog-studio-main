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

package com.android.tools.lint.checks

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.detector.api.Severity
import com.android.utils.XmlUtils.parseDocument
import com.android.utils.iterator
import org.w3c.dom.Element
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.text.Charsets.UTF_8

/** Remote URL for current SDK metadata */
const val SDK_REGISTRY_URL =
    "https://dl.google.com/dl/android/sdk/metadata/sdk_registry.xml"

/** Key used in cache directories to locate the deprecated SDK network cache */
const val DEPRECATED_SDK_CACHE_DIR_KEY = "sdk-registry.xml"

private const val TAG_ROOT = "sdk_metadata"
private const val TAG_LIBRARY = "library"
private const val TAG_VERSIONS = "versions"
private const val ATTR_GROUP_ID = "groupId"
private const val ATTR_ARTIFACT_ID = "artifactId"
private const val ATTR_FROM = "from"
private const val ATTR_TO = "to"
private const val ATTR_RECOMMENDED_VERSION = "recommended-version"
private const val ATTR_STATUS = "status"
private const val ATTR_DESCRIPTION = "description"

/**
 * Information about deprecated and vulnerable libraries, based on
 * metadata published on dl.google.com:
 *    https://dl.google.com/dl/android/sdk/metadata/sdk_registry.xml
 */
abstract class DeprecatedSdkRegistry(
    /** Location to search for cached repository content files */
    cacheDir: File? = null
) : NetworkCache(
    SDK_REGISTRY_URL,
    DEPRECATED_SDK_CACHE_DIR_KEY,
    cacheDir
) {

    private val groupToArtifactToElement: MutableMap<String, MutableList<Pair<String, Element>>> =
        HashMap(100)

    fun initialize(stream: InputStream) {
        val document = parseDocument(BufferedReader(InputStreamReader(stream, UTF_8)), false)
        val root = document.documentElement
        assert(root.tagName == TAG_ROOT, { root.tagName })
        for (library in root) {
            if (library.tagName != TAG_LIBRARY) { // Tolerate future extra tags
                continue
            }
            val groupId = library.getAttribute(ATTR_GROUP_ID)
            val artifactId = library.getAttribute(ATTR_ARTIFACT_ID)

            val artifactList: MutableList<Pair<String, Element>> =
                groupToArtifactToElement[groupId] ?: run {
                    val list: MutableList<Pair<String, Element>> = mutableListOf()
                    groupToArtifactToElement[groupId] = list
                    list
                }
            artifactList.add(Pair(artifactId, library))
        }
    }

    /** Returns the latest recommended version */
    fun getRecommendedVersion(dependency: GradleCoordinate): GradleVersion? {
        val library = findDeclaration(dependency) ?: return null
        val recommendedVersion = library.getAttribute(ATTR_RECOMMENDED_VERSION)
        return if (recommendedVersion.isBlank())
            null
        else {
            GradleVersion.tryParse(recommendedVersion.removeSuffix("+"))
        }
    }

    /** Finds the metadata element for the given coordinate */
    fun findDeclaration(dependency: GradleCoordinate): Element? {
        val groupId = dependency.groupId ?: return null
        val artifactId = dependency.artifactId ?: return null

        if (groupToArtifactToElement.isEmpty()) {
            val stream = findData("") ?: return null
            initialize(stream)
        }

        val artifactsInGroup = groupToArtifactToElement[groupId] ?: return null
        for (artifact in artifactsInGroup) {
            if (artifact.first == artifactId) {
                return artifact.second
            }
        }

        return null
    }

    /** Returns metadata about a given library if it is a known deprecated library */
    fun getVersionInfo(dependency: GradleCoordinate): DeprecatedLibrary? {
        val library = findDeclaration(dependency) ?: return null
        val currentVersion = dependency.version ?: return null

        for (versionElement in library) {
            if (versionElement.tagName == TAG_VERSIONS) { // Tolerate future extra tags
                val from = versionElement.getAttribute(ATTR_FROM)
                val to = versionElement.getAttribute(ATTR_TO)
                if (matches(currentVersion, from, to)) {
                    val recommendedVersion = library.getAttribute(ATTR_RECOMMENDED_VERSION)
                    val status = versionElement.getAttribute(ATTR_STATUS)
                    val description = versionElement.getAttribute(ATTR_DESCRIPTION)
                    val groupId = dependency.groupId ?: return null
                    val artifactId = dependency.artifactId ?: return null
                    return DeprecatedLibrary(
                        groupId,
                        artifactId,
                        description,
                        status,
                        Severity.WARNING,
                        recommendedVersion
                    )
                }
            }
        }

        return null
    }

    /** Returns true if the given version is at least [fromString] and at most [toString] */
    private fun matches(version: GradleVersion, fromString: String, toString: String): Boolean {
        if (toString.isNotEmpty()) {
            val to = GradleVersion.tryParse(toString)
            if (to != null && version > to) {
                return false
            }
        }
        if (fromString.isNotEmpty()) {
            val from = GradleVersion.tryParse(fromString)
            if (from != null && version < from) {
                return false
            }
        }
        return true
    }

    override fun readDefaultData(relative: String): InputStream? {
        assert(relative.isEmpty()) // only one file used in this cache
        return DeprecatedSdkRegistry::class.java.getResourceAsStream("/sdks-offline.xml")
    }

    /** Metadata about a deprecated library */
    data class DeprecatedLibrary(
        val groupId: String,
        val artifactId: String,
        val message: String,
        val status: String,
        val severity: Severity,
        val recommended: String?
    )
}