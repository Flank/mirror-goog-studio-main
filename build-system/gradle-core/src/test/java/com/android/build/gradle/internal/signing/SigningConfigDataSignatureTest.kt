/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.signing

import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.signing.SigningConfigData.Companion.MIN_V2_SDK
import com.android.build.gradle.internal.signing.SigningConfigData.Companion.MIN_V3_SDK
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(Parameterized::class)

class SigningConfigDataSignatureTest(
    private val enableV1Signing: Boolean?,
    private val enableV2Signing: Boolean?,
    private val enableV3Signing: Boolean?,
    private val minSdk: Int,
    private val targetApi: Int?
) {

    @Mock
    lateinit var signingConfig: SigningConfig

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testSignatureVersions() {
        Mockito.`when`(signingConfig.enableV1Signing).thenReturn(enableV1Signing)
        Mockito.`when`(signingConfig.enableV2Signing).thenReturn(enableV2Signing)
        Mockito.`when`(signingConfig.enableV3Signing).thenReturn(enableV3Signing)

        val v1Signed = SigningConfigData.enableV1Signing(signingConfig, minSdk, targetApi)
        val v2Signed = SigningConfigData.enableV2Signing(signingConfig, minSdk, targetApi)
        val v3Signed = SigningConfigData.enableV3Signing(signingConfig, targetApi)

        // For each version, if it's explicitly disabled, we shouldn't sign with it.
        if (enableV1Signing == false) {
            assertThat(v1Signed).isFalse()
        }
        if (enableV2Signing == false) {
            assertThat(v2Signed).isFalse()
        }
        if (enableV3Signing == false) {
            assertThat(v3Signed).isFalse()
        }

        // If there are no resulting signatures, all versions must be either (1) explicitly disabled
        // (or not set if v3) or (2) not supported on the given targetApi.
        if (!v1Signed && !v2Signed && !v3Signed) {
            assertThat(enableV1Signing).isFalse()
            assertThat(enableV2Signing == false || (targetApi != null && targetApi < MIN_V2_SDK))
                .isTrue()
            assertThat(enableV3Signing != true || (targetApi != null && targetApi < MIN_V3_SDK))
                .isTrue()
        }

        // If targetApi doesn't support a given version, we shouldn't sign with that version;
        // otherwise, if it's explicitly enabled, we should sign with that version.
        if (enableV1Signing == true) {
            assertThat(v1Signed).isTrue()
        }
        if (targetApi != null && targetApi < MIN_V2_SDK) {
            assertThat(v2Signed).isFalse()
        } else if (enableV2Signing == true) {
            assertThat(v2Signed).isTrue()
        }
        if (targetApi != null && targetApi < MIN_V3_SDK) {
            assertThat(v3Signed).isFalse()
        } else if (enableV3Signing == true) {
            assertThat(v3Signed).isTrue()
        }

        // Check logic for device API == MIN_V3_SDK
        if (targetApi == MIN_V3_SDK || (targetApi == null && minSdk <= MIN_V3_SDK)) {
            when {
                enableV3Signing == true -> assertThat(v3Signed).isTrue()
                !v3Signed && enableV2Signing != false -> assertThat(v2Signed).isTrue()
                !v3Signed && !v2Signed && enableV1Signing != false -> assertThat(v1Signed).isTrue()
            }
        }

        // Check logic for device API == MIN_V2_SDK
        if (targetApi == MIN_V2_SDK || (targetApi == null && minSdk <= MIN_V2_SDK)) {
            when {
                enableV2Signing != false -> assertThat(v2Signed).isTrue()
                !v2Signed && enableV1Signing != false -> assertThat(v1Signed).isTrue()
            }
        }

        // Check logic for device API < MIN_V2_SDK
        if ((targetApi != null && targetApi < MIN_V2_SDK)
            || (targetApi == null && minSdk < MIN_V2_SDK)) {
            if (enableV1Signing != false) {
                assertThat(v1Signed).isTrue()
            }
        }
    }

    companion object {
        @Parameterized.Parameters(
            name = "enableV1Signing={0}, enableV2Signing={1}, enableV3Signing={2}, minSdk={3}, targetApi={4}"
        )
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            // cover every combination of parameters, limited to APIs 23, 24, and 28.
            val list: MutableList<Array<Any?>> = mutableListOf()
            listOf(true, false, null).forEach { enableV1Signing ->
                listOf(true, false, null).forEach { enableV2Signing ->
                    listOf(true, false, null).forEach { enableV3Signing ->
                        listOf(MIN_V2_SDK - 1, MIN_V2_SDK, MIN_V3_SDK, null).forEach { targetApi ->
                            listOf(MIN_V2_SDK - 1, MIN_V2_SDK, MIN_V3_SDK).forEach { minSdk ->
                                list.add(
                                    arrayOf(
                                        enableV1Signing,
                                        enableV2Signing,
                                        enableV3Signing,
                                        minSdk,
                                        targetApi
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return list.toTypedArray()
        }
    }
}