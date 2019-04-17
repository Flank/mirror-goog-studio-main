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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringHelperTest {

    @Test
    fun testBasicCapitalize() {
        assertThat("foo".usLocaleCapitalize()).isEqualTo("Foo")
    }

    @Test
    fun testNonLetterCapitalize() {
        assertThat("1-Foo".usLocaleCapitalize()).isEqualTo("1-Foo")
    }

    @Test
    fun testUnicodeCapitalize() {
        assertThat("ê-foo".usLocaleCapitalize()).isEqualTo("Ê-foo")
    }

    @Test
    fun testSurrogateValuesCapitalize() {
        // this double characters is apparently not capitalizable.
        // FIXME find a better example...
        assertThat("\uD801\uDC00-foo".usLocaleCapitalize()).isEqualTo("\uD801\uDC00-foo")
    }

    @Test
    fun testAppendCapitalized() {
        assertThat("assemble".appendCapitalized("foo"))
            .isEqualTo("assembleFoo")
    }

    @Test
    fun testAppendCapitalizedVarArgs() {
        assertThat("assemble".appendCapitalized("foo", "bar", "foo"))
            .isEqualTo("assembleFooBarFoo")
    }

    @Test
    fun testDecapitalizeEmpty() {
        assertThat("".usLocaleDecapitalize()).isEqualTo("")
    }


    @Test
    fun testDecapitalizeNonLetter() {
        assertThat("1-Foo".usLocaleDecapitalize()).isEqualTo("1-Foo")
    }


    @Test
    fun testDecapitalizeUnicode() {
        assertThat("Ê-foo".usLocaleDecapitalize()).isEqualTo("ê-foo")
    }


    @Test
    fun testSurrogateValuesDecapitalize() {
        assertThat("\uD801\uDC00-foo".usLocaleDecapitalize()).isEqualTo("\uD801\uDC00-foo")
    }
}
