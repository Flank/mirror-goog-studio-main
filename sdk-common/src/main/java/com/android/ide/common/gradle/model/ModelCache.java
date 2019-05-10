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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ModelCache {
    @NonNull private final Map<Object, Object> myData = new HashMap<>();

    /**
     * Conceptually the same as {@link Map#computeIfAbsent(Object, Function)} except that this
     * method is synchronized and re-entrant.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public synchronized <K, V> V computeIfAbsent(
            @NonNull K key, @NonNull Function<K, V> mappingFunction) {
        if (myData.containsKey(key)) {
            return (V) myData.get(key);
        } else {
            V result = mappingFunction.apply(key);
            myData.put(key, result);
            return result;
        }
    }

    /**
     * Adds a mapping to the model cache, throwing if the value is already present and is a
     * different object.
     *
     * <p>Used in Ide* model copy constructors to validate that those objects should have been
     * constructed rather than got from the cache.
     *
     * @throws IllegalStateException if the given key exists in the map with a different value.
     */
    synchronized void putDisallowingReplacement(@NonNull Object key, @NonNull Object value) {
        Object cacheValue = myData.get(value);
        if (cacheValue == null) {
            myData.put(key, value);
            return;
        }
        if (cacheValue != value) {
            throw new IllegalStateException(
                    "Model cache unexpectedly already contains object!"
                            + "\n key = "
                            + key
                            + "\n cache value = "
                            + cacheValue
                            + "\n new value = "
                            + value);
        }
    }

    @NonNull
    @VisibleForTesting
    Map<Object, Object> getData() {
        return myData;
    }
}
