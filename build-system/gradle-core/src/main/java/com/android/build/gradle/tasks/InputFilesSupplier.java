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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * A wrapper around a file supplier that allows to recall the last value queried from it.
 *
 * It also allows bypassing the supplier during task graph creation (TODO)
 */
public class InputFilesSupplier implements Supplier<Collection<File>> {

    @NonNull
    private final Supplier<Collection<File>> supplier;
    private Collection<File> lastValue;
    private static boolean bypassSupplier = false;

    @NonNull
    public static InputFilesSupplier from(@NonNull Supplier<Collection<File>> supplier) {
        return new InputFilesSupplier(supplier);
    }

    @Override
    public Collection<File> get() {
        if (bypassSupplier) {
            return ImmutableList.of();
        }

        lastValue = supplier.get();
        return lastValue;
    }

    public Collection<File> getLastValue() {
        if (lastValue == null) {
            throw new RuntimeException(
                    "Call to InputSupplier.getLastValue() without a call to .get()");
        }
        return lastValue;
    }

    public static void setBypassSupplier(boolean value) {
        bypassSupplier = value;
    }

    private InputFilesSupplier(@NonNull Supplier<Collection<File>> supplier) {
        this.supplier = supplier;
    }
}
