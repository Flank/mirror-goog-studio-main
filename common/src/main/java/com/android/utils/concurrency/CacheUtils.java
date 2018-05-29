/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.utils.concurrency;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Utilities for dealing with Guava's {@link Cache}. */
public class CacheUtils {
    private CacheUtils() {}

    /**
     * Same as {@link Cache#get(Object, Callable)} but in case of exceptions unwraps the {@link
     * ExecutionException} layer.
     *
     * <p>This is useful if the loader can throw "special" exceptions like ProcessCanceledException
     * in the IDE.
     */
    public static <K, V> V getAndUnwrap(Cache<K, V> cache, K key, Callable<? extends V> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException | UncheckedExecutionException e) {
            Throwables.throwIfUnchecked(e.getCause());
            throw new UncheckedExecutionException(e);
        }
    }
}
