/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.repository

import com.android.SdkConstants
import com.google.common.collect.Maps
import com.google.common.io.Files
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import java.util.concurrent.TimeUnit

/**
 * Provides information about the artifacts and versions available on maven.google.com
 */
abstract class GoogleMavenRepository @JvmOverloads constructor(
        /** Location to search for cached repository content files */
        val cacheDir: File? = null,

        /**
         * Number of milliseconds to wait until timing out attempting to access the remote
         * repository
         */
        val networkTimeoutMs: Int = 3000,

        /** Maximum allowed age of cached data; default is 7 days */
        val cacheExpiryHours: Int = TimeUnit.DAYS.toHours(7).toInt()) {

    companion object {
        /** Key used in cache directories to locate the maven.google.com network cache */
        @JvmField val MAVEN_GOOGLE_CACHE_DIR_KEY = "maven.google"
    }

    /** Reads the given query URL in, with the given time out, and returns the bytes found */
    abstract protected fun readUrlData(url: String, timeout: Int): ByteArray?

    /** Reports an error found during I/O */
    abstract protected fun error(throwable: Throwable, message: String?)

    private var packageMap: MutableMap<String, PackageInfo>? = null

    fun findVersion(dependency: GradleCoordinate): GradleVersion? {
        return findVersion(dependency, dependency.isPreview)
    }

    fun findVersion(dependency: GradleCoordinate, allowPreview: Boolean = false): GradleVersion? {
        val groupId = dependency.groupId ?: return null
        val artifactId = dependency.artifactId ?: return null
        val filter = if (dependency.acceptsGreaterRevisions())
            dependency.revision.trimEnd('+') else null
        return findVersion(groupId, artifactId, filter, allowPreview)
    }

    fun findVersion(groupId: String,
                    artifactId: String,
                    filter: String? = null,
                    allowPreview: Boolean = false): GradleVersion? {
        val artifactInfo = findArtifact(groupId, artifactId) ?: return null
        return artifactInfo.findVersion(filter, allowPreview)
    }

    private fun findArtifact(groupId: String, artifactId: String): ArtifactInfo? {
        val packageInfo = getPackageMap()[groupId] ?: return null
        return packageInfo.findArtifact(artifactId)
    }

    private fun getPackageMap(): MutableMap<String, PackageInfo> {
        if (packageMap == null) {
            val map = Maps.newHashMapWithExpectedSize<String, PackageInfo>(28)
            findData("master-index.xml")?.let {
                readMasterIndex(it, map)
            }
            packageMap = map
        }

        return packageMap!!
    }

    private data class ArtifactInfo(val id: String, val versions: String) {
        fun findVersion(filter: String? = null, allowPreview: Boolean = false): GradleVersion? {
            return versions.splitToSequence(",")
                    .filter { filter == null || it.startsWith(filter) }
                    .map { GradleVersion.tryParse(it) }
                    .filterNotNull()
                    .filter { allowPreview || !it.isPreview }
                    .max()
        }
    }

    private fun findData(relative: String): InputStream? {
        if (cacheDir != null) {
            synchronized(this) {
                val file = File(cacheDir, relative)
                val refresh: Boolean
                if (file.exists()) {
                    val lastModified = file.lastModified()
                    val now = System.currentTimeMillis()
                    val expiryMs = TimeUnit.HOURS.toMillis(cacheExpiryHours.toLong())
                    if (lastModified != 0L && now - lastModified > expiryMs) {
                        refresh = true
                    } else {
                        // We found a cached file. Make sure it's actually newer than what the IDE
                        // ships with? Not really necessary since within the cache expiry interval
                        // it will be refreshed anyway
                        return BufferedInputStream(FileInputStream(file))
                    }
                } else {
                    // No cache yet: read remote index
                    refresh = true
                }

                if (refresh) {
                    try {
                        val index = readUrlData("https://maven.google.com/$relative",
                                networkTimeoutMs)
                        if (index != null) {
                            val parent = file.parentFile
                            parent?.mkdirs()
                            Files.write(index, file)
                            return ByteArrayInputStream(index)
                        }
                    } catch (e: Throwable) {
                        // timeouts etc: fall through to use built-in data
                    }
                }
            }
        }

        // Fallback: Builtin index, used for offline scenarios etc
        return GoogleMavenRepository::class.java.getResourceAsStream("/versions-offline/$relative")
    }

    private fun readMasterIndex(stream: InputStream, map: MutableMap<String, PackageInfo>) {
        try {
            stream.use { stream ->
                val parser = KXmlParser()
                parser.setInput(stream, SdkConstants.UTF_8)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.END_TAG) {
                        val tag = parser.name
                        val packageInfo = PackageInfo(tag)
                        map[tag] = packageInfo
                    } else if (eventType != XmlPullParser.START_TAG) {
                        continue
                    }
                }
            }
        } catch (e: IOException) {
            error(e, null)
        } catch (e: XmlPullParserException) {
            error(e, null)
        }
    }

    private inner class PackageInfo(val pkg: String) {
        private val artifacts: Map<String, ArtifactInfo> by lazy {
            val map = HashMap<String, ArtifactInfo>()
            initializeIndex(map)
            map
        }

        fun findArtifact(id: String): ArtifactInfo? = artifacts[id]

        private fun initializeIndex(map: MutableMap<String, ArtifactInfo>) {
            val stream = findData("${pkg.replace('.', '/')}/group-index.xml")
            stream?.let { readGroupData(stream, map) }
        }

        private fun readGroupData(stream: InputStream, map: MutableMap<String, ArtifactInfo>) {
            try {
                stream.use { stream ->
                    val parser = KXmlParser()
                    parser.setInput(stream, SdkConstants.UTF_8)
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        val eventType = parser.eventType
                        if (eventType == XmlPullParser.START_TAG) {
                            val artifactId = parser.name
                            val versions = parser.getAttributeValue(null, "versions")
                            if (versions != null) {
                                val artifactInfo = ArtifactInfo(artifactId, versions)
                                map[artifactId] = artifactInfo
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                error(e, null)
            }
        }
    }
}