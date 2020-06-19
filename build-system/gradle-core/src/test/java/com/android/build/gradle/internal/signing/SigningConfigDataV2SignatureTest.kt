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
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(Parameterized::class)

class SigningConfigDataV2SignatureTest(
    private val enableV2Signing: Boolean?,
    private val enableV3Signing: Boolean?,
    private val targetSdk: Int?,
    private val expectedOutput: Boolean
) {

    @Mock
    lateinit var signingConfig: SigningConfig

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testV2Enablement() {
        Mockito.`when`(signingConfig.enableV2Signing).thenReturn(enableV2Signing)
        Mockito.`when`(signingConfig.enableV3Signing).thenReturn(enableV3Signing)
        assertThat(
            SigningConfigData.enableV2Signing(signingConfig, targetSdk)
        ).isEqualTo(expectedOutput)
    }

    companion object {
        @Parameterized.Parameters(
            name = "enableV2Signing={0}, enableV3Signing={1}, targetSdk={2}"
        )
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            return arrayOf<Array<Any?>>(
                // If enableV2Signing is explicitly enabled by the user, we do v2 signing
                arrayOf(true, false, null, true),
                arrayOf(true, true, null, true),
                arrayOf(true, null, null, true),
                arrayOf(true, false, 23, true),
                arrayOf(true, true, 23, true),
                arrayOf(true, null, 23, true),
                arrayOf(true, false, 24, true),
                arrayOf(true, true, 24, true),
                arrayOf(true, null, 24, true),
                // If enableV2Signing is explicitly disabled by the user, we don't do v2 signing
                arrayOf(false, false, null, false),
                arrayOf(false, true, null, false),
                arrayOf(false, null, null, false),
                arrayOf(false, false, 23, false),
                arrayOf(false, true, 23, false),
                arrayOf(false, null, 23, false),
                arrayOf(false, false, 24, false),
                arrayOf(false, true, 24, false),
                arrayOf(false, null, 24, false),
                // If the user has not explicitly enabled v2 signing, it can be automatically
                // disabled based on targetSdk and v3Signed
                arrayOf(null, false, null, true),
                arrayOf(null, true, null, false),
                arrayOf(null, null, null, true),
                arrayOf(null, false, 23, false),
                arrayOf(null, true, 23, false),
                arrayOf(null, null, 23, false),
                arrayOf(null, false, 24, true),
                arrayOf(null, true, 24, false),
                arrayOf(null, null, 24, true)
            )
        }
    }
}