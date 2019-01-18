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
package com.android.ide.common.repository

import com.google.common.io.Files
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Provides a basic network cache with local disk fallback for data read
 * from a URL.
 */
abstract class NetworkCache constructor(
    private val baseUrl: String,

    /** Key used in cache directories to locate the network cache */
    val cacheKey: String,

    /** Location to search for cached repository content files */
    val cacheDir: File? = null,

    /**
     * Number of milliseconds to wait until timing out attempting to access the remote
     * repository
     */
    private val networkTimeoutMs: Int = 3000,

    /** Maximum allowed age of cached data; default is 7 days */
    private val cacheExpiryHours: Int = TimeUnit.DAYS.toHours(7).toInt(),

    /** If false, this repository won't make any network requests */
    private val networkEnabled: Boolean = true
) {

    /** Reads the given query URL in, with the given time out, and returns the bytes found */
    protected abstract fun readUrlData(url: String, timeout: Int): ByteArray?

    /** Provides the data from offline/local storage, if possible */
    protected abstract fun readDefaultData(relative: String): InputStream?

    /** Reports an error found during I/O */
    protected abstract fun error(throwable: Throwable, message: String?)

    /** Reads the given data relative to the base URL. */
    protected open fun findData(relative: String): InputStream? {
        if (cacheDir != null) {
            synchronized(cacheDir) {
                val file = File(cacheDir, if (relative.isNotEmpty()) relative else cacheKey)
                if (file.exists()) {
                    val lastModified = file.lastModified()
                    val now = System.currentTimeMillis()
                    val expiryMs = TimeUnit.HOURS.toMillis(cacheExpiryHours.toLong())

                    if (lastModified == 0L || now - lastModified <= expiryMs) {
                        // We found a cached file. Make sure it's actually newer than what the IDE
                        // ships with? Not really necessary since within the cache expiry interval
                        // it will be refreshed anyway
                        return BufferedInputStream(FileInputStream(file))
                    }
                }

                if (networkEnabled) {
                    try {
                        val data = readUrlData(
                          "$baseUrl$relative",
                          networkTimeoutMs
                        )
                        if (data != null) {
                            val parent = file.parentFile
                            parent?.mkdirs()
                            Files.write(data, file)
                            return ByteArrayInputStream(data)
                        }
                    }
                    catch (e: AssertionError) {
                        // Make sure that we propagate assertions
                        throw e
                    }
                    catch (e: Throwable) {
                        // timeouts etc: fall through to use built-in data
                    }
                }
            }
        }

        // Fallback: Builtin index, used for offline scenarios etc
        return readDefaultData(relative)
    }
}
