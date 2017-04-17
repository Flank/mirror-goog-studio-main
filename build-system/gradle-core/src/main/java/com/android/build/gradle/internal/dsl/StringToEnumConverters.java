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

package com.android.build.gradle.internal.dsl;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Enums;

/** Utility for using enums in the DSL. */
public final class StringToEnumConverters {
    private StringToEnumConverters() {}

    public static <T extends Enum<T>> Converter<String, T> forClass(Class<T> klass) {
        return CaseFormat.LOWER_UNDERSCORE
                .converterTo(CaseFormat.UPPER_UNDERSCORE)
                .andThen(Enums.stringConverter(klass));
    }
}
