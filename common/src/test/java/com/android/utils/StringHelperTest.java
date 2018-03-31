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

package com.android.utils;

import com.google.common.truth.Truth;
import org.junit.Test;

public class StringHelperTest {

    @Test
    public void testBasicCapitalize() {
        Truth.assertThat(StringHelper.capitalize("foo")).isEqualTo("Foo");
    }

    @Test
    public void testNonLetterCapitalize() {
        Truth.assertThat(StringHelper.capitalize("1-Foo")).isEqualTo("1-Foo");
    }

    @Test
    public void testUnicodeCapitalize() {
        Truth.assertThat(StringHelper.capitalize("ê-foo")).isEqualTo("Ê-foo");
    }

    @Test
    public void testSurrogateValuesCapitalize() {
        // this double characters is apparently not capitalizable.
        // FIXME find a better example...
        Truth.assertThat(StringHelper.capitalize("\uD801\uDC00-foo")).isEqualTo("\uD801\uDC00-foo");
    }

    @Test
    public void testAppendCapitalized() {
        Truth.assertThat(StringHelper.appendCapitalized("assemble", "foo"))
                .isEqualTo("assembleFoo");
    }

    @Test
    public void testAppendCapitalizedVarArgs() {
        Truth.assertThat(StringHelper.appendCapitalized("assemble", "foo", "bar", "foo"))
                .isEqualTo("assembleFooBarFoo");
    }
}
