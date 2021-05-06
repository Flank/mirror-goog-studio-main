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
    fun `simple write and read`() {
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

        CxxStructuredLogEncoder(file).use { log ->
            original.forEach {
                log.write(it.encode(log))
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
}
