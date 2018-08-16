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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Wrapper around a {@link ResourceTable} that:
 *
 * <ul>
 *   <li>May compute cells in the table on-demand.
 *   <li>May change in the background, if underlying files or other sources of data have changed.
 *       Because of that access should be synchronized on the {@code ITEM_MAP_LOCK} object.
 * </ul>
 */
public abstract class AbstractResourceRepository implements ResourceRepository {
    /**
     * Returns the fully computed {@link ResourceTable} for this repository.
     *
     * <p>The returned object should be accessed only while holding {@link #ITEM_MAP_LOCK}.
     */
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    @NonNull
    protected abstract ResourceTable getFullTable();

    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    @Nullable
    protected abstract ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type, boolean create);

    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    @NonNull
    protected final ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        //noinspection ConstantConditions - won't return null if create is false.
        return getMap(namespace, type, true);
    }

    /**
     * The lock used to protect map access.
     *
     * <p>In the IDE, this needs to be obtained <b>AFTER</b> the IDE read/write lock, to avoid
     * deadlocks (most readers of the repository system execute in a read action, so obtaining the
     * locks in opposite order results in deadlocks).
     */
    public static final Object ITEM_MAP_LOCK = new Object();

    @Override
    @NonNull
    public List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
            if (map != null) {
                return map.get(resourceName);
            }
        }

        return Collections.emptyList();
    }

    @Override
    @NonNull
    public List<ResourceItem> getResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull Predicate<ResourceItem> filter) {
        List<ResourceItem> result = null;
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
            if (map != null) {
                for (ResourceItem item : map.values()) {
                    if (filter.test(item)) {
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(item);
                    }
                }
            }
        }

        return result == null ? Collections.emptyList() : result;
    }

    @Override
    @NonNull
    public ListMultimap<String, ResourceItem> getResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
            return map == null ? ImmutableListMultimap.of() : ImmutableListMultimap.copyOf(map);
        }
    }

    @Override
    public void accept(@NonNull ResourceVisitor visitor) {
        synchronized (ITEM_MAP_LOCK) {
            for (Map.Entry<ResourceNamespace, Map<ResourceType, ListMultimap<String, ResourceItem>>>
                    namespaceEntry : getFullTable().rowMap().entrySet()) {
                if (visitor.shouldVisitNamespace(namespaceEntry.getKey())) {
                    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> typeEntry :
                            namespaceEntry.getValue().entrySet()) {
                        if (visitor.shouldVisitResourceType(typeEntry.getKey())) {
                            for (ResourceItem item : typeEntry.getValue().values()) {
                                if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    @NonNull
    public Collection<ResourceItem> getPublicResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        return Collections.emptyList();
    }

    @Override
    public boolean hasResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);

            if (map != null) {
                List<ResourceItem> itemList = map.get(resourceName);
                return itemList != null && !itemList.isEmpty();
            }
        }

        return false;
    }

    @Override
    public boolean hasResources(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
            if (map != null && !map.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NonNull
    public Set<ResourceType> getResourceTypes(@NonNull ResourceNamespace namespace) {
        EnumSet<ResourceType> result = EnumSet.noneOf(ResourceType.class);
        synchronized (ITEM_MAP_LOCK) {
            for (ResourceType resourceType : ResourceType.values()) {
                if (hasResources(namespace, resourceType)) {
                    result.add(resourceType);
                }
            }
        }
        return Sets.immutableEnumSet(result);
    }
}
