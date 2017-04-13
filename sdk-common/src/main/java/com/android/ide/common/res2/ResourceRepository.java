/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Set;

public final class ResourceRepository extends AbstractResourceRepository {
    private final ResourceTable resourceTable = new ResourceTable();

    @NonNull
    @Override
    protected ResourceTable getFullTable() {
        return resourceTable;
    }

    @Nullable
    @Override
    protected ListMultimap<String, ResourceItem> getMap(
            @Nullable String namespace, @NonNull ResourceType type, boolean create) {
        ListMultimap<String, ResourceItem> multimap = resourceTable.get(namespace, type);
        if (multimap == null && create) {
            multimap = ArrayListMultimap.create();
            resourceTable.put(namespace, type, multimap);
        }
        return multimap;
    }

    @NonNull
    @Override
    public Set<String> getNamespaces() {
        return resourceTable.rowKeySet();
    }
}
