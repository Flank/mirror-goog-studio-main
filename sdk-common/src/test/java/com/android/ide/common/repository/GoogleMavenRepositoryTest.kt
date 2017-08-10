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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleMavenRepositoryTest : BaseTestCase() {
    companion object {
        @ClassRule @JvmField var temp = TemporaryFolder()
    }

    class TestGoogleMavenRepository(
            val map: Map<String, String> = emptyMap(),
            cacheDir: File? = null) : GoogleMavenRepository(cacheDir = cacheDir) {
        override fun readUrlData(url: String, timeout: Int): ByteArray? = map[url]?.toByteArray()

        override fun error(throwable: Throwable, message: String?) = throw throwable
    }

    @Test
    fun testBuiltin() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = true)
        assertNotNull(version)
        assertEquals("26.0.0", version.toString())
    }

    @Test
    fun testBuiltinStableOnly() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", allowPreview = false)
        assertNotNull(version)
        assertEquals("26.0.0", version.toString())
    }

    @Test
    fun testBuiltinFiltered() {
        val repo = TestGoogleMavenRepository() // no cache dir set: will only read built-in index
        val version = repo.findVersion("com.android.support", "appcompat-v7", filter = { it.major == 12 })
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
        assertEquals("2.6.0-rc1", repo.findVersion(gc3!!, null, allowPreview = true).toString())
    }
}
