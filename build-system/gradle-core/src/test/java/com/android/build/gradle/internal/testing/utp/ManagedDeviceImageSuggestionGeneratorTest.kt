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

package com.android.build.gradle.internal.testing.utp

import com.google.common.truth.Truth.assertThat
import com.android.utils.CpuArchitecture
import org.junit.Test

class ManagedDeviceImageSuggestionGeneratorTest {

    @Test
    fun ensureInvalidAllArchitectureMessage() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "invalid_device",
            30,
            "aosp",
            false,
            listOf()
        )

        assertThat(generator.message).isEqualTo(
            "System Image specified by invalid_device does not exist.\n\n" +
                    "Try one of the following fixes:"
        )
    }

    @Test
    fun ensureValidForOtherArchitectureMessage() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "possible_valid_device",
            30,
            "aosp",
            false,
            listOf("system-images;android-30;default;arm64-v8a")
        )

        assertThat(generator.message).isEqualTo(
            "System Image for possible_valid_device does not exist for this architecture. " +
                    "However it is valid for ARM. This may be intended, but " +
                    "possible_valid_device cannot be used on this device.\n\n" +
                    "If this is not intended, try one of the following fixes:"
        )
    }

    @Test
    fun ensureATDSuggestionWorks() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "atd_device",
            27,
            "aosp-atd",
            false,
            listOf("system-images;android-27;default;x86")
        )

        assertThat(generator.message).isEqualTo(
            "System Image specified by atd_device does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. Automated Test Device image does not exist for this architecture on the " +
                    "given apiLevel. However, a normal emulator image does exist from a " +
                    "comparable source. Set systemImageSource = \"aosp\" to use."
        )
    }

    @Test
    fun ensureAlternativeSourceSuggestionWorks() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "another_atd_device",
            28,
            "google-atd",
            true,
            listOf(
                "system-images;android-28;aosp_atd;x86_64",
                "system-images;android-28;default;x86_64"
            )
        )

        assertThat(generator.message).isEqualTo(
            "System Image specified by another_atd_device does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. The image does not exist from google-atd for this architecture on the " +
                    "given apiLevel. However, other sources exist. Set systemImageSource to any " +
                    "of [aosp-atd, aosp] to use."
        )
    }

    @Test
    fun ensureSuggestingHigherApiLevelWorks() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "api_not_valid",
            12,
            "aosp",
            false,
            listOf(
                "system-images;android-14;default;x86",
                // Lower api levels should be ignored.
                "system-images;android-11;default;x86"
            )
        )
        assertThat(generator.message).isEqualTo(
            "System Image specified by api_not_valid does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. The system image does not exist for apiLevel 12. However an image exists " +
                    "for apiLevel 14. Set apiLevel = 14 to use."
        )
    }

    @Test
    fun ensureSuggestingLatestIfHigherDoesNotExistWorks() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.ARM,
            "too_high_api",
            200,
            "aosp",
            false,
            listOf(
                "system-images;android-31;default;arm64-v8a"
            )
        )
        assertThat(generator.message).isEqualTo(
            "System Image specified by too_high_api does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. The system image does not presently exist for apiLevel 200. The latest " +
                    "available apiLevel is 31. Set apiLevel = 31 to use."
        )
    }

    @Test
    fun ensure32BitSuggestionWorks() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "require_64",
            24,
            "aosp",
            true,
            listOf(
                "system-images;android-24;default;x86"
            )
        )

        assertThat(generator.message).isEqualTo(
            "System Image specified by require_64 does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. There is an available X86 image for apiLevel 24. Set require64Bit = " +
                    "false to use. Be aware tests involving native X86_64 code will not be run " +
                    "with this change."
        )
    }

    @Test
    fun testMultipleSuggestionsWork() {
        val generator = ManagedDeviceImageSuggestionGenerator(
            CpuArchitecture.X86_64,
            "test_device",
            29,
            "aosp-atd",
            true,
            listOf(
                // Valid compatible non-atd image
                "system-images;android-29;default;x86_64",
                // Valid higher api level image
                "system-images;android-31;aosp_atd;x86_64",
                // Valid non require64Bit image, will be skipped because only first 2 suggestions
                // are accepted.
                "system-images;android-29;aosp_atd;x86",
                // Irrelevant images are ignored.
                "system-images;android-29;google_apis;arm64-v8a"
            )
        )

        assertThat(generator.message).isEqualTo(
            "System Image specified by test_device does not exist.\n\n" +
                    "Try one of the following fixes:\n" +
                    "1. Automated Test Device image does not exist for this architecture on the " +
                    "given apiLevel. However, a normal emulator image does exist from a " +
                    "comparable source. Set systemImageSource = \"aosp\" to use.\n" +
                    "2. The system image does not exist for apiLevel 29. However an image exists " +
                    "for apiLevel 31. Set apiLevel = 31 to use."
        )
    }
}
