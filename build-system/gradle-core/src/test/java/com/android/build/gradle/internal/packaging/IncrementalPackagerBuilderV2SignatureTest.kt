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

package com.android.build.gradle.internal.packaging

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)

class IncrementalPackagerBuilderV2SignatureTest(
    private val v2Enabled: Boolean,
    private val v2Configured: Boolean,
    private val targetSdk: Int?,
    private val v3Signed: Boolean,
    private val expectedOutput: Boolean
) {
    @Test
    fun testV2Enablement() {
        assertThat(
            IncrementalPackagerBuilder.enableV2Signing(v2Enabled, v2Configured, targetSdk, v3Signed)
        ).isEqualTo(expectedOutput)
    }

    companion object {
        @Parameterized.Parameters(name = "v2Enabled={0}, v2Configured={1}, targetSdk={2}, v3Signed={3}")
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            return arrayOf<Array<Any?>>(
                // If v2SigningEnabled is explicitly enabled by the user, we do v2 signing
                arrayOf(true, true, null, false, true),
                arrayOf(true, true, null, true, true),
                arrayOf(true, true, 23, false, true),
                arrayOf(true, true, 23, true, true),
                arrayOf(true, true, 24, false, true),
                arrayOf(true, true, 24, true, true),
                // If v2SigningEnabled is explicitly disabled by the user, we don't do v2 signing
                arrayOf(false, true, null, false, false),
                arrayOf(false, true, null, true, false),
                arrayOf(false, true, 23, false, false),
                arrayOf(false, true, 23, true, false),
                arrayOf(false, true, 24, false, false),
                arrayOf(false, true, 24, true, false),
                // If the user has not explicitly enabled v2 signing, it can be automatically
                // disabled based on targetSdk and v3Signed
                arrayOf(true, false, null, false, true),
                arrayOf(true, false, null, true, false),
                arrayOf(true, false, 23, false, false),
                arrayOf(true, false, 23, true, false),
                arrayOf(true, false, 24, false, true),
                arrayOf(true, false, 24, true, false)
            )
        }
    }
}