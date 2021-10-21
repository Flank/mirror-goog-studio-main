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

package com.android.build.gradle.internal.utils

import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

internal class SdkUtilsKtTest {

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    @Test
    fun `api level target`() {
        Truth.assertThat(parseTargetHash("android-23")).isEqualTo(
            CompileData(apiLevel = 23)
        )
    }

    @Test
    fun `preview target`() {
        Truth.assertThat(parseTargetHash("android-R")).isEqualTo(
            CompileData(codeName = "R")
        )

        Truth.assertThat(parseTargetHash("android-Rv2")).isEqualTo(
            CompileData(codeName = "Rv2")
        )

        Truth.assertThat(validatePreviewTargetValue("android-Rv2")).isNull()

        Truth.assertThat(validatePreviewTargetValue("Rv2")).isEqualTo("Rv2")
    }

    @Test
    fun `api level + extension target`() {
        Truth.assertThat(parseTargetHash("android-23-ext12")).isEqualTo(
            CompileData(apiLevel = 23, sdkExtension = 12)
        )
    }

    @Test
    fun `preview with extension`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: android-S-ext12. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash("android-S-ext12")
    }

    @Test
    fun `addon target`() {
        Truth.assertThat(parseTargetHash("foo:bar:12")).isEqualTo(
            CompileData(
                vendorName = "foo",
                addonName = "bar",
                apiLevel = 12,
            )
        )
    }

    @Test
    fun `empty target`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: android-. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash("android-")
    }

    @Test
    fun `api level + broken extension target`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: android-23-ext. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash("android-23-ext")
    }

    @Test
    fun `missing vendorName target`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: :name:31. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash(":name:31")
    }

    @Test
    fun `missing addonName target`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: vendor::31. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash("vendor::31")
    }

    @Test
    fun `addon with preview target`() {
        exceptionRule.expectMessage(
            """
                Unsupported value: vendor:name:R. Format must be one of:
                - android-31
                - android-31-ext2
                - android-T
                - vendorName:addonName:31
                """.trimIndent())
        parseTargetHash("vendor:name:R")
    }
}
