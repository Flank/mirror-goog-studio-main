/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.sdklib

import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.fail

class AndroidVersionTest {

    /** Regression test for Issue 216736348 */
    @Test
    fun testAllowedCodenames() {
        val codenames = listOf("Tiramisu", "O_MR1", "S")
        codenames.forEach {
            val androidVersion = AndroidVersion(it)
            Truth.assertThat(androidVersion.codename).isEqualTo(it)
        }
    }

    @Test
    fun testDisallowedCodenames() {
        val codenames = listOf("tiramisu", "1S", "s")
        codenames.forEach {
            try {
                AndroidVersion(it)
                fail("expecting exception")
            } catch (expectedException: AndroidVersion.AndroidVersionException) {
                // do nothing
            }
        }
    }
}
