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
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(Parameterized::class)

class SigningConfigDataV1SignatureTest(
    private val enableV1Signing: Boolean?,
    private val minSdk: Int,
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
    fun testV1Enablement() {
        Mockito.`when`(signingConfig.enableV1Signing).thenReturn(enableV1Signing)
        assertThat(
            SigningConfigData.enableV1Signing(signingConfig, minSdk, targetSdk)
        ).isEqualTo(expectedOutput)
    }

    companion object {
        @Parameterized.Parameters(
            name = "enableV1Signing={0}, minSdk={1}, targetSdk={2}"
        )
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            return arrayOf<Array<Any?>>(
                // If enableV1Signing is explicitly enabled by the user, we do v1 signing
                arrayOf(true, 23, null, true),
                arrayOf(true, 23, 23, true),
                arrayOf(true, 23, 24, true),
                arrayOf(true, 24, null, true),
                arrayOf(true, 24, 23, true),
                arrayOf(true, 24, 24, true),
                // If enableV1Signing is explicitly disabled by the user, we don't do v1 signing
                arrayOf(false, 23, null, false),
                arrayOf(false, 23, 23, false),
                arrayOf(false, 23, 24, false),
                arrayOf(false, 24, null, false),
                arrayOf(false, 24, 23, false),
                arrayOf(false, 24, 24, false),
                // If the user has not explicitly enabled v1 signing, it can be automatically
                // disabled based on the min sdk and target sdk
                arrayOf(null, 23, null, true),
                arrayOf(null, 23, 23, true),
                arrayOf(null, 23, 24, false),
                arrayOf(null, 24, null, false),
                arrayOf(null, 24, 23, false),
                arrayOf(null, 24, 24, false)
            )
        }
    }
}