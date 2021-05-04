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

package com.android.build.gradle.internal.cxx.attribution

import com.android.testutils.TestResources
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildAttributionUtilsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `dolphin build attribution test`() {
        val zipFile =
            TestResources.getFile("/com/android/build/gradle/internal/cxx/attribution/dolphin-build-attribution.zip")
        val expectedTraceFile =
            TestResources.getFile("/com/android/build/gradle/internal/cxx/attribution/dolphin-build-attribution.json.gz")
        val outputFile = tmp.newFile("output.json.gz")
        generateChromeTrace(zipFile, outputFile)
        Truth.assertThat(outputFile.readBytes()).isEqualTo(expectedTraceFile.readBytes())
    }
}
