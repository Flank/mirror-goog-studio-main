/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.logging

import com.android.build.gradle.internal.cxx.caching.DependenciesKey
import com.android.build.gradle.internal.cxx.caching.EncodedDependenciesKey
import com.android.build.gradle.internal.cxx.caching.decode
import com.android.build.gradle.internal.cxx.caching.encode
import com.android.build.gradle.internal.cxx.logging.LoggingMessage.LoggingLevel.ERROR
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CxxStructuredLogCodecTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `string write and read`() {
        val folder = temporaryFolder.newFolder()
        val file = folder.resolve("log.bin")
        val original = listOf(createLoggingMessage(
                level = ERROR,
                message = "message",
                file = "file",
                tag = "tag",
                diagnosticCode = 1000
            ), createLoggingMessage(
                level = ERROR,
                message = "message",
                file = "file",
                tag = "tag",
                diagnosticCode = 1000
            ))

        CxxStructuredLogEncoder(file).use { encoder ->
            original.forEach {
                encoder.write(it.encode(encoder))
            }
        }

        var count = 0
        streamCxxStructuredLog(file) { decoder, timestamp, record ->
            if (record !is EncodedLoggingMessage) {
                error("Expected EncodedLoggingMessage")
            }
            val decoded = record.decode(decoder)
            assertThat(decoded).isEqualTo(original[count])
            ++count
        }
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `list write and read`() {
        val folder = temporaryFolder.newFolder()
        val file = folder.resolve("log.bin")
        val key1 = DependenciesKey.newBuilder()
            .addAllCompilerFlags(listOf("flag1", "flag2"))
            .build()
        val key2 = DependenciesKey.newBuilder()
            .addAllCompilerFlags(listOf("flag2", "flag1"))
            .build()
        val original = listOf(key1, key2, key1, key2)

        CxxStructuredLogEncoder(file).use { encoder ->
            original.forEach {
                encoder.write(it.encode(encoder))
            }
        }

        var count = 0
        streamCxxStructuredLog(file) { decoder, timestamp, record ->
            if (record !is EncodedDependenciesKey) {
                error("Expected EncodedDependenciesKey")
            }
            val decoded = record.decode(decoder)
            assertThat(decoded).isEqualTo(original[count])
            ++count
        }
        assertThat(count).isEqualTo(original.size)
    }

    /**
     * Try to trigger missing flush/close if they are introduced.
     */
    @Test
    fun stress() {
        val folder = temporaryFolder.newFolder()
        val file = folder.resolve("log.bin")
        val key1 = DependenciesKey.newBuilder()
            .addAllCompilerFlags(listOf("flag1", "flag2"))
            .build()
        val key2 = DependenciesKey.newBuilder()
            .addAllCompilerFlags(listOf("flag2", "flag1"))
            .build()
        val original = listOf(key1, key2, key1, key2)

        val iterations = 1000
        repeat(iterations) {
            CxxStructuredLogEncoder(file).use { encoder ->
                original.forEach {
                    encoder.write(it.encode(encoder))
                }
            }
        }

        var count = 0
        streamCxxStructuredLog(file) { decoder, timestamp, record ->
            if (record !is EncodedDependenciesKey) {
                error("Expected EncodedDependenciesKey")
            }
            val decoded = record.decode(decoder)
            assertThat(decoded).isEqualTo(original[count % original.size])
            ++count
        }
        assertThat(count).isEqualTo(original.size * iterations)
    }
}
