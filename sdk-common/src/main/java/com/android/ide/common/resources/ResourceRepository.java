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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Common interface for all Android resource repositories. */
public interface ResourceRepository {
    /**
     * Returns the resources with the given namespace, type and name.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @param resourceName the bane of the resources to return
     * @return the resources matching the namespace, type, and satisfying the name filter
     */
    @NonNull
    List<ResourceItem> getResourceItems(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName);

    @NonNull
    default List<ResourceItem> getResourceItems(@NonNull ResourceReference reference) {
        return getResourceItems(
                reference.getNamespace(), reference.getResourceType(), reference.getName());
    }

    /**
     * Returns the resources with the given namespace, type and satisfying the given predicate.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @param filter the predicate for checking resource items
     * @return the resources matching the namespace, type, and satisfying the name filter
     */
    @NonNull
    List<ResourceItem> getResourceItems(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull Predicate<ResourceItem> filter);

    /**
     * Returns the resources with the given namespace and type.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @return the resources matching the namespace and type
     */
    @NonNull
    List<ResourceItem> getResourceItems(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType);

    /**
     * Calls the {@link ResourceItemVisitor#visit(ResourceItem)} method for all resources in
     * the repository.
     *
     * @param visitor the visitor object
     */
    void accept(@NonNull ResourceItemVisitor visitor);

    /**
     * Returns a collection of <b>public</b> resource items matching a given resource type.
     *
     * @param type the type of the resources to return
     * @return a collection of items, possibly empty.
     */
    @NonNull
    Collection<ResourceItem> getPublicResourcesOfType(@NonNull ResourceType type);

    /**
     * Checks if the repository contains a resource with the given namespace, type and name.
     *
     * @param namespace the namespace of the resources to return
     * @param resourceType the type of the resources to return
     * @param resourceName the bane of the resources to return
     * @return true if there is at least one resource with the given namespace, type and name in
     *     the repository
     */
    boolean hasResourceItem(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName);

    /**
     * Returns types of the resources in the given namespace.
     *
     * @param namespace the namespace to get resource types for
     * @return the set of resource types
     */
    @NonNull
    Set<ResourceType> getAvailableResourceTypes(@NonNull ResourceNamespace namespace);

    /**
     * Returns the namespaces that the resources in this repository belong to. The returned set may
     * include namespaces that don't contain any resource items.
     */
    @NonNull
    Set<ResourceNamespace> getNamespaces();
}
