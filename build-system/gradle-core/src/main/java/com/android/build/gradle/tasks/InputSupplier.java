/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import java.util.function.Supplier;

/**
 * A wrapper around a supplier that allows to recall the last value queried from it.
 */
public class InputSupplier<T> implements Supplier<T> {

    @NonNull
    private final Supplier<T> supplier;
    private T lastValue;

    @NonNull
    public static <T> InputSupplier<T> from(@NonNull Supplier<T> supplier) {
        return new InputSupplier<>(supplier);
    }

    @Override
    public T get() {
        lastValue = supplier.get();
        return lastValue;
    }

    public T getLastValue() {
        if (lastValue == null) {
            throw new RuntimeException(
                    "Call to InputSupplier.getLastValue() without a call to .get()");
        }
        return lastValue;
    }

    private InputSupplier(@NonNull Supplier<T> supplier) {
        this.supplier = supplier;
    }
}
