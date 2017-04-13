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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceUrl;
import com.google.common.collect.ForwardingTable;
import com.google.common.collect.Table;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Variant of a {@link Table} that knows how to use resource namespaces as the first dimension. For
 * the "default" namespace, the empty string is used for storing, but null values can be used for
 * querying, they will automatically be converted. This means the namespace value can be taken
 * straight from {@link ResourceUrl}.
 *
 * @see ResourceNamespaces
 */
public class NamespaceAwareTable<C, V> extends ForwardingTable<String, C, V> {

    /**
     * Returns the key that should be used for getting items.
     *
     * <p>In theory someone could define an object that is {@code {@link #equals(Object)}} to
     * certain namespace names, so this needs to operate on the {@link Object} type.
     */
    @NonNull
    private static Object getNamespaceKey(@Nullable Object namespace) {
        return namespace == null ? ResourceNamespaces.normalizeNamespace(null) : namespace;
    }

    @NonNull private final Table<String, C, V> delegate;

    public NamespaceAwareTable(@NonNull Table<String, C, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected Table<String, C, V> delegate() {
        return delegate;
    }

    @Override
    public boolean contains(@Nullable Object namespace, @NonNull Object resourceType) {
        return super.contains(getNamespaceKey(namespace), resourceType);
    }

    @Override
    public boolean containsRow(@Nullable Object namespace) {
        return super.containsRow(getNamespaceKey(namespace));
    }

    @Override
    public V get(@Nullable Object namespace, @NonNull Object resourceType) {
        return super.get(getNamespaceKey(namespace), resourceType);
    }

    @Override
    public V put(@Nullable String namespace, @NonNull C resourceType, @NonNull V value) {
        return super.put(ResourceNamespaces.normalizeNamespace(namespace), resourceType, value);
    }

    @Override
    public V remove(@Nullable Object namespace, @NonNull Object resourceType) {
        return super.remove(getNamespaceKey(namespace), resourceType);
    }

    @Override
    public Map<C, V> row(@Nullable String namespace) {
        return super.row(ResourceNamespaces.normalizeNamespace(namespace));
    }

    @Override
    public Set<String> rowKeySet() {
        return super.rowKeySet()
                .stream()
                .map(ResourceNamespaces::normalizeNamespace)
                .collect(Collectors.toSet());
    }
}
