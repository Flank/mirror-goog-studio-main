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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CmakeCompilerSettingsCacheTest {
    private val ndkProperties = SdkSourceProperties(mapOf("x" to "y"))

    @Test
    fun testCacheMiss() {
        val cacheFolder = cacheFolder()
        val cache = CmakeCompilerSettingsCache(cacheFolder)

        val key = CmakeCompilerCacheKey(null, ndkProperties, listOf("abc"))
        val initial = cache.tryGetValue(key)
        assertThat(initial).isNull()
    }

    @Test
    fun testCacheHit() {
        val cacheFolder = cacheFolder()
        val cache = CmakeCompilerSettingsCache(cacheFolder)

        val key = CmakeCompilerCacheKey(null, ndkProperties, listOf("abc"))
        cache.saveKeyValue(key, "My Value")
        val final = cache.tryGetValue(key)!!
        assertThat(final).isEqualTo("My Value")
    }

    @Test
    fun basicWithHashCollision() {
        val cacheFolder = cacheFolder()

        // Configure with a hash function that just takes the first character.
        val cache = CmakeCompilerSettingsCache(cacheFolder) { _ ->
            "A"
        }
        val key1 = CmakeCompilerCacheKey(null, ndkProperties, listOf("abc"))
        val key2 = CmakeCompilerCacheKey(null, ndkProperties, listOf("abd"))

        cache.saveKeyValue(key1, "ABC")
        assertThat(cache.tryGetValue(key1)).isEqualTo("ABC")
        assertThat(cache.tryGetValue(key2)).isNull()

        cache.saveKeyValue(key2, "ABD")
    }

    @Test
    fun spam() {
        val cacheFolder = cacheFolder()

        val successfulReads = AtomicInteger(0)
        val unsuccessfulReads = AtomicInteger(0)

        fun test(cache : CmakeCompilerSettingsCache, thread : Int) {

            // Read an unknown key
            val unknownKey = CmakeCompilerCacheKey(null, ndkProperties, listOf("xyz"))
            val unknown = cache.tryGetValue(unknownKey)
            assertThat(unknown).isNull()

            // Write and read a key
            val key = CmakeCompilerCacheKey(null, ndkProperties, listOf("abc"))
            cache.saveKeyValue(key, "My Value $thread")
            val final = cache.tryGetValue(key)
            // Final can be null in the case that another th
            if (final != null) {
                successfulReads.incrementAndGet()
                assertThat(final).startsWith("My Value")
            } else {
                unsuccessfulReads.incrementAndGet()
            }

        }

        val outstanding = AtomicInteger(0)
        for (i in 0..5000) {
            // Give each iteration it's own unique hash collision
            val cache = CmakeCompilerSettingsCache(cacheFolder) { _ -> i.toString() }

            // Multiple threads trying to write to the same key
            for (t in 0..2) {
                thread {
                    try {
                        outstanding.incrementAndGet()
                        test(cache, t)
                    } finally {
                        outstanding.decrementAndGet()
                    }
                }
            }

            // Simulate the user deleting the cache directory every once in a while
            if (i % 20 == 0) {
                thread {
                    cacheFolder.deleteRecursively()
                }
            }

            // Key the total number of threads down to a reasonable number to avoid running
            // out of resources.
            while (outstanding.get() > 200) {
                Thread.sleep(50)
            }
        }

        // Spin while threads finish
        while (outstanding.get() > 0) {
            Thread.sleep(50)
        }

        assertThat(successfulReads.get()).isGreaterThan(0)
        assertThat(unsuccessfulReads.get()).isGreaterThan(0)
    }

    private fun cacheFolder(): File {
        val cacheFile = File("./my-cache")
        cacheFile.deleteRecursively()
        return cacheFile
    }
}