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

package com.android.build.gradle.internal.services

import com.google.common.truth.Truth
import org.gradle.api.services.BuildServiceParameters
import org.junit.Test

class StringCachingBuildServiceTest {

    private val stringCachingService = object : StringCachingBuildService() {
        override fun getParameters(): BuildServiceParameters.None {
            TODO("Not yet implemented")
        }
    }

    @Test
    fun testBasicCache() {
        val oldFoo = "foo"
        val newFoo = stringCachingService.cacheString(oldFoo);
        Truth.assertThat(newFoo).isEqualTo(oldFoo)
        Truth.assertThat(newFoo).isSameInstanceAs(oldFoo)

        var o = "o"
        val newerFoo = stringCachingService.cacheString("f" + o + "o")
        Truth.assertThat(newerFoo).isSameInstanceAs(newFoo)
    }

    @Test
    fun testReset() {
        val oldFoo = stringCachingService.cacheString("foo");
        stringCachingService.close()

        var o = "o"
        val newFoo = stringCachingService.cacheString("f" + o + "o")

        Truth.assertThat(newFoo).isNotSameInstanceAs(oldFoo)
    }
}