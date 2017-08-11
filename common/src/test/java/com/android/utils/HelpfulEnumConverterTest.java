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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Converter;
import org.junit.Assert;
import org.junit.Test;

public class HelpfulEnumConverterTest {
    private enum TestEnum {
        FOO,
        BAR_BAZ,
    }

    private Converter<String, TestEnum> converter = new HelpfulEnumConverter<>(TestEnum.class);

    @Test
    public void stringToEnum() throws Exception {
        assertThat(converter.convert("foo")).isEqualTo(TestEnum.FOO);
        assertThat(converter.convert("FOO")).isEqualTo(TestEnum.FOO);
        assertThat(converter.convert("bar_baz")).isEqualTo(TestEnum.BAR_BAZ);
        assertThat(converter.convert("BAR_BAZ")).isEqualTo(TestEnum.BAR_BAZ);

        assertThat(converter.convert(null)).isNull();

        try {
            converter.convert("made_up");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            "Unknown TestEnum value 'made_up'. Possible values are 'foo', 'bar_baz'.");
        }
    }

    @Test
    public void enumToString() throws Exception {
        assertThat(converter.reverse().convert(TestEnum.FOO)).isEqualTo("foo");
        assertThat(converter.reverse().convert(TestEnum.BAR_BAZ)).isEqualTo("bar_baz");
        assertThat(converter.reverse().convert(null)).isNull();
    }
}
