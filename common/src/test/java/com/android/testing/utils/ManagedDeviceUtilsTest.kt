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

import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagedDeviceUtilsTest {

    @Test
    fun testComputeSystemImageHashFromDsl() {
        var expectedHash = "system-images;android-29;default;x86"
        var computedHash = computeSystemImageHashFromDsl(29, "aosp", "x86")

        assertEquals(expectedHash, computedHash)

        expectedHash = "system-images;android-30;google_apis_playstore;x86_64"
        computedHash = computeSystemImageHashFromDsl(30, "google", "x86_64")

        assertEquals(expectedHash, computedHash)
    }

    @Test
    fun testIsValidSystemImageHash() {
        assertFalse(isValidSystemImageHash("system-image;android-29;default;x86"))
        assertFalse(isValidSystemImageHash("anything;that;is;different"))
        assertFalse(isValidSystemImageHash("system-images;missing;abi"))
        assertTrue(isValidSystemImageHash("system-images;android-24;default;arm64-v8a"))
    }

    @Test
    fun testParseApiFromHash() {
        assertEquals(29, parseApiFromHash("system-images;android-29;default;x86"))
        assertEquals(24, parseApiFromHash("system-images;android-24;default;arm64-v8a"))
        assertEquals(
            30, parseApiFromHash("system-images;android-30;google_apis_playstore;x86_64")
        )
    }

    @Test
    fun testComputeOffsetZeroWhenValidAndEqual() {
        var systemImageHash = "system-images;android-29;default;x86"
        assertEquals(0, computeOffset(systemImageHash, systemImageHash))
        systemImageHash = "system-images;android-23;default;armeabi-v7a"
        assertEquals(0, computeOffset(systemImageHash, systemImageHash))
        systemImageHash = "system-images;android-29;google_apis_playstore;x86_64"
        assertEquals(0, computeOffset(systemImageHash, systemImageHash))
    }

    @Test
    fun testComputeOffsetMaxWhenInvalidAndEqual() {
        var systemImageHash = "system-image;android-29;default;x86"
        assertEquals(Int.MAX_VALUE, computeOffset(systemImageHash, systemImageHash))
        systemImageHash = "anything;that;is;different"
        assertEquals(Int.MAX_VALUE, computeOffset(systemImageHash, systemImageHash))
        systemImageHash = "system-images;missing;abi"
        assertEquals(Int.MAX_VALUE, computeOffset(systemImageHash, systemImageHash))
    }

    @Test
    fun testComputeOffsetIsDifferenceInApi() {
        var systemImage1 = computeSystemImageHashFromDsl(28, "aosp", "x86")
        var systemImage2 = computeSystemImageHashFromDsl(29, "aosp", "x86")
        assertEquals(1, computeOffset(systemImage1, systemImage2))
        assertEquals(1, computeOffset(systemImage2, systemImage1))
        systemImage1 = computeSystemImageHashFromDsl(26, "google", "x86_64")
        systemImage2 = computeSystemImageHashFromDsl(30, "google", "x86_64")
        assertEquals(4, computeOffset(systemImage1, systemImage2))
        assertEquals(4, computeOffset(systemImage2, systemImage1))
    }

    @Test
    fun testComputeOffsetIsOneIfSourceDifferent() {
        val systemImage1 = computeSystemImageHashFromDsl(29, "aosp", "x86_64")
        val systemImage2 = computeSystemImageHashFromDsl(29, "google", "x86_64")
        assertEquals(1, computeOffset(systemImage1, systemImage2))
        assertEquals(1, computeOffset(systemImage2, systemImage1))
    }

    @Test
    fun testComputeOffsetIsOneIfAbiDifferent() {
        val systemImage1 = computeSystemImageHashFromDsl(29, "aosp", "x86")
        val systemImage2 = computeSystemImageHashFromDsl(29, "aosp", "x86_64")
        assertEquals(1, computeOffset(systemImage1, systemImage2))
        assertEquals(1, computeOffset(systemImage2, systemImage1))
    }

    @Test
    fun testComputeOffsetSumsDifferences() {
        val systemImage1 = computeSystemImageHashFromDsl(29, "aosp", "x86")
        val systemImage2 = computeSystemImageHashFromDsl(27, "google", "x86_64")
        assertEquals(4, computeOffset(systemImage1, systemImage2))
        assertEquals(4, computeOffset(systemImage2, systemImage1))
    }

    @Test
    fun testFindClosestHashes() {
        val targetImage = computeSystemImageHashFromDsl(30, "aosp", "x86")
        val validImages = listOf(
            computeSystemImageHashFromDsl(30, "google", "x86_64"),
            computeSystemImageHashFromDsl(29, "google", "x86_64"),
            computeSystemImageHashFromDsl(29, "aosp", "x86_64"),
            computeSystemImageHashFromDsl(28, "google", "x86_64"),
            computeSystemImageHashFromDsl(28, "aosp", "x86"),
            computeSystemImageHashFromDsl(28, "aosp", "x86_64"),
            computeSystemImageHashFromDsl(27, "google", "x86"),
            computeSystemImageHashFromDsl(27, "aosp", "x86")
        )
        val closestHashes = findClosestHashes(targetImage, validImages)

        assertEquals(3, closestHashes.size)
        assertTrue(
            closestHashes.containsAll(
                listOf(
                    computeSystemImageHashFromDsl(30, "google", "x86_64"),
                    computeSystemImageHashFromDsl(29, "aosp", "x86_64"),
                    computeSystemImageHashFromDsl(28, "aosp", "x86")
                )
            ),
            "Invalid closestHashes contents. Was: $closestHashes"
        )
    }
}
