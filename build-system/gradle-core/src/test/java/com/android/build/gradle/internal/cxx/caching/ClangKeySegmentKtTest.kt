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

package com.android.build.gradle.internal.cxx.caching

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class ClangKeySegmentKtTest {

    @Test
    fun basic() {
        checkExpect("")
        checkExpect("x86_64-android/", "--target=x86_64-none-linux-android")
        checkExpect("-O0/", "-O0")
        checkExpect("x86_64-android/-O0/", "--target=x86_64-none-linux-android", "-O0")
    }

    private fun checkExpect(expect : String, vararg flagArray : String) {
        val flags = flagArray.toList()
        val result = computeClangKeySegment(flags)
        assertThat(result).isEqualTo(expect)
    }

}
