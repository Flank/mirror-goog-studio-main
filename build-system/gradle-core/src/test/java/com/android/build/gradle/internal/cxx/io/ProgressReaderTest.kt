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

package com.android.build.gradle.internal.cxx.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.StringReader

class ProgressReaderTest {
    @Test
    fun `basic read 0 interval`() {
        checkString(
            "string value",
            progressIntervalMillis = 0,
            expectedProgressCalls = "string value".length + 1,
        )
    }

    @Test
    fun `basic read with interval`() {
        checkString(
            "string value",
            progressIntervalMillis = 100L, // 100ms
            artificialPause = 100L,
            expectedProgressCalls = 1
        )
    }

    @Test
    fun `basic read max interval`() {
        checkString(
            "string value",
            progressIntervalMillis = Long.MAX_VALUE,
            expectedProgressCalls = 0
        )
    }

    private fun checkString(
        value : String,
        progressIntervalMillis : Long = Long.MAX_VALUE,
        artificialPause : Long = 0L,
        expectedProgressCalls : Int = -1,
    ) {
        val reader = value.progressReader(progressIntervalMillis)
        var progressCalls = 0

        fun progress(filename: String, totalBytes: Long, bytesRead: Long) {
            ++progressCalls
        }
        while(reader.read() != -1) {
            reader.postProgress(::progress)
        }
        if (artificialPause > 0L) {
            Thread.sleep(artificialPause)
        }
        reader.postProgress(::progress)
        if (expectedProgressCalls != -1) {
            assertThat(progressCalls).isEqualTo(expectedProgressCalls)
        }
    }

    private fun String.progressReader(
        progressIntervalMillis : Long
    ) = ProgressReader(
        reader = StringReader(this),
        filename = "a string",
        totalBytes = length.toLong(),
        progressIntervalMillis = progressIntervalMillis
    )
}
