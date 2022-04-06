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

package com.android.testing.utils

import org.junit.Test
import kotlin.test.assertEquals

class ManagedDeviceUtilsTest {

    @Test
    fun testComputeSystemImageHashFromDsl() {
        var expectedHash = "system-images;android-29;default;x86"
        var computedHash = computeSystemImageHashFromDsl(29, "aosp", "x86")

        assertEquals(expectedHash, computedHash)

        expectedHash = "system-images;android-30;google_apis;x86_64"
        computedHash = computeSystemImageHashFromDsl(30, "google", "x86_64")

        assertEquals(expectedHash, computedHash)

        expectedHash = "system-images;android-31;android-wear;x86_64"
        computedHash = computeSystemImageHashFromDsl(31, "android-wear", "x86_64")

        assertEquals(expectedHash, computedHash)
    }

    @Test
    fun testParseApiFromHash() {
        assertEquals(29, parseApiFromHash("system-images;android-29;default;x86"))
        assertEquals(24, parseApiFromHash("system-images;android-24;default;arm64-v8a"))
        assertEquals(
            30, parseApiFromHash("system-images;android-30;google_apis_playstore;x86_64")
        )
    }
}
