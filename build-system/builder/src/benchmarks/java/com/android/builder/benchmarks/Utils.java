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

package com.android.builder.benchmarks;

import com.android.annotations.NonNull;
import java.util.Arrays;

public class Utils {

    static final int BENCHMARK_SAMPLE_SIZE = 3;

    static long median(@NonNull long[] values) {
        if (values.length == 0) {
            throw new IllegalStateException("Empty array has no median");
        }
        Arrays.sort(values);

        int index = values.length / 2;
        boolean isOdd = values.length % 2 == 1;
        if (isOdd) {
            return values[index];
        } else {
            return (values[index] + values[index + 1]) / 2;
        }
    }
}
