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
package com.android.ide.common.util;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only map that delegates read operations to two disjoint maps.
 *
 * <p>The set of keys of both maps needs to be disjoint. This class is meant to "join" two disjoint
 * maps in a single object, not to implement an overlay.
 */
public class LazyUnionMap<K, V> implements Map<K, V> {

    private final Map<K, V> first;
    private final Map<K, V> second;

    public LazyUnionMap(Map<K, V> first, Map<K, V> second) {
        Preconditions.checkArgument(
                Sets.intersection(first.keySet(), second.keySet()).isEmpty(),
                "Key sets are not disjoint.");
        this.first = first;
        this.second = second;
    }

    @Override
    public int size() {
        return first.size() + second.size();
    }

    @Override
    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return first.containsKey(key) || second.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return first.containsValue(value) || second.containsValue(value);
    }

    @Override
    public V get(Object key) {
        V result = first.get(key);
        if (result == null) {
            result = second.get(key);
        }

        return result;
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        return unionOfDisjointSets(first.keySet(), second.keySet());
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return unionOfDisjointSets(first.entrySet(), second.entrySet());
    }

    @NonNull
    private static <T> Set<T> unionOfDisjointSets(Set<T> first, Set<T> second) {
        Set<T> allKeys = Sets.newHashSetWithExpectedSize(first.size() + second.size());
        allKeys.addAll(first);
        allKeys.addAll(second);
        return allKeys;
    }

    @NotNull
    @Override
    public Collection<V> values() {
        List<V> allValues = new ArrayList<>(first.size() + second.size());
        allValues.addAll(first.values());
        allValues.addAll(second.values());
        return allValues;
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
