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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.io.SynchronizeFile
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Outcome.DELETED_DESTINATION_BECAUSE_SOURCE_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.decode
import com.android.build.gradle.internal.cxx.io.encode
import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IoStructuredLogCodecKtTest {
    @Test
    fun `round trip SynchronizeFile through codec`() {
        val expected = SynchronizeFile.newBuilder().apply {
            workingDirectory = "working-directory"
            sourceFile = "source-file"
            destinationFile = "destination-file"
            initialFileComparison = SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
            outcome = DELETED_DESTINATION_BECAUSE_SOURCE_DID_NOT_EXIST
        }.build()
        val strings = StringTable()
        val encoded = expected.encode(strings)
        val actual = encoded.decode(strings)
        assertThat(actual).isEqualTo(expected)
    }
}
