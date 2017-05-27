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

import com.android.ide.common.res2.BaseTestCase
import com.android.utils.XmlUtils
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GoogleMavenRepositoryTest : BaseTestCase() {
    val UPDATE_OFFLINE_VERSIONS = false

    companion object {
        @ClassRule @JvmField var temp = TemporaryFolder()
    }

    class TestGoogleMavenRepository(
            val map: Map<String, String> = emptyMap(),
            cacheDir: File? = null) : GoogleMavenRepository(cacheDir = cacheDir) {
        override fun readUrlData(url: String, timeout: Int): ByteArray? = map[url]?.toByteArray()

        override fun error(throwable: Throwable, message: String?) {
            throw throwable
        }
    }

    @Test
    fun testBuiltin() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = true)
        assertNotNull(version)
        assertEquals("26.0.0-beta1", version.toString())
    }

    @Test
    fun testBuiltinStableOnly() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = false)
        assertNotNull(version)
        assertEquals("25.3.1", version.toString())
    }

    @Test
    fun testBuiltinFiltered() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", filter = "12.")
        assertNull(version)
    }

    @Test
    fun test2() {
        val map = mapOf("https://maven.google.com/master-index.xml" to """
                   <?xml version='1.0' encoding='UTF-8'?>
                   <metadata>
                     <foo.bar/>
                     <foo.bar.baz/>
                   </metadata>""".trimIndent(),
                "https://maven.google.com/foo/bar/group-index.xml" to """
                   <?xml version='1.0' encoding='UTF-8'?>
                   <foo.bar>
                     <my-artifact versions="1.0.1-alpha1"/>
                     <another-artifact versions="2.5.0,2.6.0-rc1"/>
                   </foo.bar>""".trimIndent())


        val repo = TestGoogleMavenRepository(map = map, cacheDir = temp.root)
        val version = repo.findVersion("foo.bar", "my-artifact", allowPreview = true)
        assertNotNull(version)
        assertEquals("1.0.1-alpha1", version.toString())

        val gc1 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.5.+")
        assertEquals("2.5.0", repo.findVersion(gc1!!).toString())
        val gc2 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.6.0-alpha1")
        assertEquals("2.6.0-rc1", repo.findVersion(gc2!!).toString())
        val gc3 = GradleCoordinate.parseCoordinateString("foo.bar:another-artifact:2.6.+")
        assertEquals("2.6.0-rc1", repo.findVersion(gc3!!, allowPreview = true).toString())
    }

    /** Reads the data from the given URL, with an optional timeout (in milliseconds)  */
    fun readUrlData(
            query: String,
            @Suppress("UNUSED_PARAMETER") timeout: Int): ByteArray? {
        val url = URL(query)

        val connection = url.openConnection()
        try {
            val stream = connection.getInputStream() ?: return null
            return ByteStreams.toByteArray(stream)
        } finally {
            if (connection is HttpURLConnection) {
                connection.disconnect()
            }
        }
    }

    /**
     * Reads the data from the given URL, with an optional timeout (in milliseconds),
     * and returns it as a UTF-8 encoded String
     */
    fun readUrlDataAsString(
            query: String,
            timeout: Int): String? {
        val bytes = readUrlData(query, timeout)
        if (bytes != null) {
            return String(bytes, com.google.common.base.Charsets.UTF_8)
        } else {
            return null
        }
    }

    @Test
    fun testUpdateSnapshots() {
        if (!UPDATE_OFFLINE_VERSIONS) {
            return
        }

        val ADT_SOURCE_TREE = "ADT_SOURCE_TREE"
        var sourceTree: String? = System.getenv(ADT_SOURCE_TREE)
        if (sourceTree == null) {
            sourceTree = System.getProperty(ADT_SOURCE_TREE)
        }
        val root = if (sourceTree != null) File(sourceTree) else null
        if (root != null && !File(root, ".repo").isDirectory) {
            fail("Invalid directory: should be pointing to the root of a tools checkout directory")
        }

        val dir = File(root, "tools/base/sdk-common/src/main/resources/versions-offline/")
        if (dir.exists()) {
            // Delete older copies to ensure we clean up obsolete packages
            dir.deleteRecursively()
            dir.mkdir()
            val master = readUrlDataAsString("https://maven.google.com/master-index.xml", 60000)
            assertNotNull(master!!)
            val masterFile = File(dir, "master-index.xml")
            Files.write(master, masterFile, Charsets.UTF_8)
            println("Wrote $masterFile")
            val masterDoc = XmlUtils.parseDocumentSilently(master, false)
            assertNotNull(masterDoc!!)
            var current = XmlUtils.getFirstSubTag(masterDoc.documentElement)
            while (current != null) {
                val group = current.tagName
                val relative = group.replace('.', '/')
                val groupIndex = readUrlDataAsString(
                        "https://maven.google.com/$relative/group-index.xml", 60000)
                assertNotNull(groupIndex!!)

                // Keep all but the last stable and unstable version
                val sb = StringBuilder()
                groupIndex.lines().forEach { line ->
                    var start: Int = line.indexOf("versions=\"")
                    var done = false
                    if (start != -1) {
                        start += "versions=\"".length
                        val end: Int = line.indexOf("\"", start)
                        val sub = line.substring(start, end)
                        var max: GradleVersion? = null
                        var maxStable: GradleVersion? = null
                        sub.splitToSequence(",").forEach {
                            val v = GradleVersion.parse(it)
                            if (max == null || v > max!!) {
                                max = v
                            }
                            if (!v.isPreview && (maxStable == null || v > maxStable!!)) {
                                maxStable = v
                            }
                        }
                        if (max != null) {
                            val newVersions =
                                    if (maxStable != null) {
                                        "$maxStable,$max"
                                    } else {
                                        max.toString()
                                    }
                            sb.append(line.substring(0, start))
                            sb.append(newVersions)
                            sb.append(line.substring(end))
                            sb.append("\n")
                            done = true
                        }
                    }

                    if (!done) {
                        sb.append(line).append("\n")
                    }
                }


                val file = File(dir, relative.replace('/', File.separatorChar) + File.separatorChar + "group-index.xml")
                file.parentFile.mkdirs()
                Files.write(sb, file, Charsets.UTF_8)
                println("Wrote $file")

                current = XmlUtils.getNextTag(current)
            }

            println("Updated indices. NOTE: You may need to update JarContentsTest if the set of " +
                    "packages has changed!")
        } else {
            println("Not generating up to date maven repository snapshot files")
        }
    }
}
