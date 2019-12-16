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

class IncrementalPackagerBuilderV1SignatureTest(
    private val v1Enabled: Boolean,
    private val v1Configured: Boolean,
    private val minSdk: Int,
    private val targetSdk: Int?,
    private val expectedOutput: Boolean
) {
    @Test
    fun testV1Enablement() {
        assertThat(IncrementalPackagerBuilder.enableV1Signing(v1Enabled, v1Configured, minSdk, targetSdk)).isEqualTo(expectedOutput)
    }

    companion object {
        @Parameterized.Parameters(name = "v1Enabled={0}, v1Configured={1}, minSdk={2}, targetSdk={3}")
        @JvmStatic
        fun data(): Array<Array<Any?>> {
            return arrayOf<Array<Any?>>(
                // If v1SigningEnabled is explicitly enabled by the user, we do v1 signing
                arrayOf(true, true, 23, null, true),
                arrayOf(true, true, 23, 23, true),
                arrayOf(true, true, 23, 24, true),
                arrayOf(true, true, 24, null, true),
                arrayOf(true, true, 24, 23, true),
                arrayOf(true, true, 24, 24, true),
                // If v1SigningEnabled is explicitly disabled by the user, we don't do v1 signing
                arrayOf(false, true, 23, null, false),
                arrayOf(false, true, 23, 23, false),
                arrayOf(false, true, 23, 24, false),
                arrayOf(false, true, 24, null, false),
                arrayOf(false, true, 24, 23, false),
                arrayOf(false, true, 24, 24, false),
                // If the user has not explicitly enabled v1 signing, it can be automatically
                // disabled based on the min sdk and target sdk
                arrayOf(true, false, 23, null, true),
                arrayOf(true, false, 23, 23, true),
                arrayOf(true, false, 23, 24, false),
                arrayOf(true, false, 24, null, false),
                arrayOf(true, false, 24, 23, false),
                arrayOf(true, false, 24, 24, false)
            )
        }
    }
}