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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;

/**
 * A wrapper around a file supplier that allows to recall the last value queried from it.
 */
public class TaskInputHelper {

    /**
     * Returns a new supplier wrapping the provided one that cache the result of the supplier to
     * only run it once.
     *
     * @param supplier the supplier to wrap.
     * @param <T> the return type for the supplier.
     * @return a new supplier.
     */
    @NonNull
    public static <T> Supplier<T> memoize(@NonNull Supplier<T> supplier) {
        return Suppliers.memoize(supplier::get);
    }
}