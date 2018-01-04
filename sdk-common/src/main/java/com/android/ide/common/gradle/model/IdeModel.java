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
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class IdeModel implements Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    protected IdeModel(@NonNull Object original, @NonNull ModelCache modelCache) {
        Object copy =
                modelCache.computeIfAbsent(
                        original, (Function<Object, Object>) recursiveModel1 -> this);
        if (copy != this) {
            throw new IllegalStateException("An existing copy was found in the cache");
        }
    }

    @Nullable
    protected static <K, V> V copyNewProperty(
            @NonNull ModelCache modelCache,
            @NonNull Supplier<K> keyCreator,
            @NonNull Function<K, V> mapper,
            @Nullable V defaultValue) {
        try {
            K key = keyCreator.get();
            return key != null ? modelCache.computeIfAbsent(key, mapper) : defaultValue;
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    @Nullable
    protected static <T> T copyNewProperty(
            @NonNull Supplier<T> propertyInvoker, @Nullable T defaultValue) {
        try {
            return propertyInvoker.get();
        } catch (UnsupportedOperationException ignored) {
            return defaultValue;
        }
    }

    @NonNull
    protected static <K, V> List<K> copy(
            @NonNull Collection<K> original,
            @NonNull ModelCache modelCache,
            @NonNull Function<K, V> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<K> copies = ImmutableList.builder();
        for (K item : original) {
            V copy = modelCache.computeIfAbsent(item, mapper);
            //noinspection unchecked
            copies.add((K) copy);
        }
        return copies.build();
    }

    @NonNull
    protected static <K, V> List<K> copy(
            @NonNull Supplier<Collection<K>> propertyInvoker,
            @NonNull ModelCache modelCache,
            @NonNull Function<K, V> mapper) {

        try {
            return copy(propertyInvoker.get(), modelCache, mapper);
        } catch (UnsupportedOperationException ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    protected static <K, V> Map<K, V> copy(
            @NonNull Map<K, V> original,
            @NonNull ModelCache modelCache,
            @NonNull Function<V, V> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, V> copies = ImmutableMap.builder();
        original.forEach(
                (k, v) -> {
                    V copy = modelCache.computeIfAbsent(v, mapper);
                    copies.put(k, copy);
                });
        return copies.build();
    }

    @Nullable
    protected static Set<String> copy(@Nullable Set<String> original) {
        return original != null ? ImmutableSet.copyOf(original) : null;
    }
}
