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

package com.android.builder.internal.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * Consumer that can throw an {@link IOException}.
 */
@FunctionalInterface
public interface IOExceptionConsumer<T> {

    /**
     * Performs an operation on the given input.
     *
     * @param input the input
     */
    void accept(@Nullable T input) throws IOException;

    /**
     * Wraps a consumer that may throw an IO Exception throwing an {@code UncheckedIOException}.
     *
     * @param c the consumer
     */
    @NonNull
    static <T> Consumer<T> asConsumer(@NonNull IOExceptionConsumer<T> c)  {
        return i -> {
            try {
                c.accept(i);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
