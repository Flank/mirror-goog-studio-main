/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.io.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.InputStream

abstract class TestCache(cacheDir: File = Files.createTempDir(), networkEnabled: Boolean) :
    NetworkCache("", "cacheKey", cacheDir, networkEnabled = networkEnabled) {
    fun loadArtifact() {
        findData("/artifact.xml")
    }

    override fun readDefaultData(relative: String): InputStream? = null

    override fun error(throwable: Throwable, message: String?) =
        fail("No error calls expected")
}

class NetworkCacheTest {
    @Test
    fun testNetworkCache() {
        val cache = object : TestCache(networkEnabled = false) {
            override fun readUrlData(url: String, timeout: Int): ByteArray? {
                fail("No network calls expected")
                return null
            }
        }

        cache.loadArtifact()

        var networkCalls = 0
        val networkEnabledCache = object : TestCache(networkEnabled = true) {
            override fun readUrlData(url: String, timeout: Int): ByteArray? {
                networkCalls++
                return null
            }
        }

        networkEnabledCache.loadArtifact()
        assertEquals(1, networkCalls)
    }
}

