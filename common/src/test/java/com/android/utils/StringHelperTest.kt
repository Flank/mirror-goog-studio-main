/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.utils

import com.google.common.truth.Truth
import org.junit.Test

class StringHelperTest {

    @Test
    fun testBasicCapitalize() {
        Truth.assertThat("foo".capitalize()).isEqualTo("Foo")
    }

    @Test
    fun testNonLetterCapitalize() {
        Truth.assertThat("1-Foo".capitalize()).isEqualTo("1-Foo")
    }

    @Test
    fun testUnicodeCapitalize() {
        Truth.assertThat("ê-foo".capitalize()).isEqualTo("Ê-foo")
    }

    @Test
    fun testSurrogateValuesCapitalize() {
        // this double characters is apparently not capitalizable.
        // FIXME find a better example...
        Truth.assertThat("\uD801\uDC00-foo".capitalize()).isEqualTo("\uD801\uDC00-foo")
    }

    @Test
    fun testAppendCapitalized() {
        Truth.assertThat("assemble".appendCapitalized("foo"))
            .isEqualTo("assembleFoo")
    }

    @Test
    fun testAppendCapitalizedVarArgs() {
        Truth.assertThat("assemble".appendCapitalized("foo", "bar", "foo"))
            .isEqualTo("assembleFooBarFoo")
    }
}
