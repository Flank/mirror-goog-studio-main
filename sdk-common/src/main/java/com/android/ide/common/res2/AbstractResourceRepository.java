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

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ATTR_REF_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.RESOURCE_CLZ_ATTR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Wrapper around a {@link ResourceTable} that:
 *
 * <ul>
 *   <li>May compute cells in the table on-demand.
 *   <li>May change in the background, if underlying files or other sources of data have changed.
 *       Because of that access should be synchronized on the {@code ITEM_MAP_LOCK} object.
 * </ul>
 */
public abstract class AbstractResourceRepository {
    /**
     * Number of indirections we'll follow for resource resolution before assuming there is a cyclic
     * dependency error in the input.
     */
    public static final int MAX_RESOURCE_INDIRECTION = 50;

    public AbstractResourceRepository() {}

    /**
     * For now we don't use {@link AbstractResourceRepository} for framework resources, but
     * eventually we will start at which point a single repository may contain both framework and
     * app resources. The "truth" lies in the namespace of the {@link ResourceItem}s. Soon we'll get
     * rid of this method.
     */
    @Deprecated
    public final boolean isFramework() {
        return false;
    }

    /**
     * Returns the fully computed {@link ResourceTable} for this repository.
     *
     * <p>The returned object should be accessed only while holding {@link #ITEM_MAP_LOCK}.
     */
    @NonNull
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    protected abstract ResourceTable getFullTable();

    @Nullable
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    protected abstract ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type, boolean create);

    @NonNull
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    public abstract Set<ResourceNamespace> getNamespaces();

    @NonNull
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    protected final ListMultimap<String, ResourceItem> getMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        //noinspection ConstantConditions - won't return null if create is false.
        return getMap(namespace, type, true);
    }

    @NonNull
    @GuardedBy("AbstractResourceRepository.ITEM_MAP_LOCK")
    public ResourceTable getItems() {
        return getFullTable();
    }

    /**
     * The lock used to protect map access.
     *
     * <p>In the IDE, this needs to be obtained <b>AFTER</b> the IDE read/write lock, to avoid
     * deadlocks (most readers of the repository system execute in a read action, so obtaining the
     * locks in opposite order results in deadlocks).
     */
    public static final Object ITEM_MAP_LOCK = new Object();

    @NonNull
    public final List<ResourceItem> getAllResourceItems() {
        synchronized (ITEM_MAP_LOCK) {
            ResourceTable table = getFullTable();
            List<ResourceItem> result = new ArrayList<>(table.size());

            for (ListMultimap<String, ResourceItem> multimap : table.values()) {
                result.addAll(multimap.values());
            }

            return result;
        }
    }

    /**
     * @deprecated Use {@link #getResourceItems(ResourceNamespace, ResourceType, String)} instead.
     */
    @Deprecated
    @Nullable
    public List<ResourceItem> getResourceItem(
            @NonNull ResourceType resourceType, @NonNull String resourceName) {
        List<ResourceItem> items =
                getResourceItems(ResourceNamespace.TODO, resourceType, resourceName);
        // That's what the method used to return, let's keep it this way for now.
        return items.isEmpty() ? null : items;
    }

    @NonNull
    public List<ResourceItem> getResourceItems(
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

    @NonNull
    public List<ResourceItem> getResourceItems(@NonNull ResourceReference reference) {
        return getResourceItems(
                reference.getNamespace(), reference.getResourceType(), reference.getName());
    }

    /** @deprecated Use {@link #getItemsOfType(ResourceNamespace, ResourceType)} instead. */
    @Deprecated
    @NonNull
    public Collection<String> getItemsOfType(@NonNull ResourceType type) {
        return getItemsOfType(ResourceNamespace.TODO, type);
    }

    @NonNull
    public Collection<String> getItemsOfType(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        synchronized (ITEM_MAP_LOCK) {
            Multimap<String, ResourceItem> map = getMap(namespace, type, false);
            if (map != null) {
                return ImmutableSet.copyOf(map.keySet());
            }
        }
        return Collections.emptySet();
    }

    /**
     * Returns a collection of <b>public</b> resource items matching a given resource type.
     *
     * <p>This implementation returns an empty collection. Subclasses may override this behavior.
     *
     * @param type the type of the resources to return
     * @return a collection of items, possibly empty.
     */
    @NonNull
    public Collection<ResourceItem> getPublicResourcesOfType(@NonNull ResourceType type) {
        return Collections.emptyList();
    }

    /**
     * Returns true if this resource repository contains a resource of the given name.
     *
     * @param url the resource URL
     * @return true if the resource is known
     */
    public boolean hasResourceItem(@NonNull String url) {
        // Handle theme references
        if (url.startsWith(PREFIX_THEME_REF)) {
            String remainder = url.substring(PREFIX_THEME_REF.length());
            if (url.startsWith(ATTR_REF_PREFIX)) {
                url = PREFIX_RESOURCE_REF + url.substring(PREFIX_THEME_REF.length());
                return hasResourceItem(url);
            }
            int colon = url.indexOf(':');
            if (colon >= 0) {
                // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
                if (remainder.indexOf('/', colon) == -1) {
                    remainder = remainder.substring(0, colon) + RESOURCE_CLZ_ATTR + '/'
                            + remainder.substring(colon);
                }
                url = PREFIX_RESOURCE_REF + remainder;
                return hasResourceItem(url);
            } else {
                int slash = url.indexOf('/');
                if (slash < 0) {
                    url = PREFIX_RESOURCE_REF + RESOURCE_CLZ_ATTR + '/' + remainder;
                    return hasResourceItem(url);
                }
            }
        }

        if (!url.startsWith(PREFIX_RESOURCE_REF)) {
            return false;
        }

        assert url.startsWith("@") || url.startsWith("?") : url;

        int typeEnd = url.indexOf('/', 1);
        if (typeEnd >= 0) {
            int nameBegin = typeEnd + 1;

            // Skip @ and @+
            int typeBegin = url.startsWith("@+") ? 2 : 1; //$NON-NLS-1$

            int colon = url.lastIndexOf(':', typeEnd);
            ResourceNamespace namespace = ResourceNamespace.RES_AUTO;
            if (colon >= 0) {
                if (colon - typeBegin == ANDROID_NS_NAME.length()
                        && url.startsWith(ANDROID_NS_NAME, typeBegin)) {
                    namespace = ResourceNamespace.ANDROID;
                } else {
                    // TODO: namespaces
                    namespace = ResourceNamespace.TODO;
                }
                typeBegin = colon + 1;
            }

            String typeName = url.substring(typeBegin, typeEnd);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type != null) {
                String name = url.substring(nameBegin);
                return hasResourceItem(namespace, type, name);
            }
        }

        return false;
    }

    /**
     * @deprecated Use {@link #getResourceItems(ResourceNamespace, ResourceType, String)} or
     *     {@link #hasResourceItem(ResourceNamespace, ResourceType, String)} instead
     */
    @Deprecated
    public boolean hasResourceItem(
            @NonNull ResourceType resourceType, @NonNull String resourceName) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map =
                    getMap(ResourceNamespace.TODO, resourceType, false);

            if (map != null) {
                List<ResourceItem> itemList = map.get(resourceName);
                return itemList != null && !itemList.isEmpty();
            }
        }

        return false;
    }

    /**
     * Returns true if this resource repository contains a resource of the given name.
     *
     * @param namespace the namespace of the resource to look up
     * @param resourceType the type of resource to look up
     * @param resourceName the name of the resource
     * @return true if the resource is known
     */
    public boolean hasResourceItem(
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

    /**
     * Returns whether the repository has resources of a given {@link ResourceType}.
     *
     * @param resourceType the type of resource to check.
     * @return true if the repository contains resources of the given type, false otherwise.
     */
    public boolean hasResourcesOfType(@NonNull ResourceType resourceType) {
        synchronized (ITEM_MAP_LOCK) {
            for (ResourceNamespace namespace : getNamespaces()) {
                ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
                if (map != null && !map.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the repository has resources of a given {@link ResourceType}.
     *
     * <p>Do not call this method if you you are going to call
     * {@link #getItemsOfType(ResourceNamespace, ResourceType)} immediately after.
     *
     * @param resourceType the type of resource to check.
     * @return true if the repository contains resources of the given type, false otherwise.
     */
    public boolean hasResourcesOfType(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(namespace, resourceType, false);
            return map != null && !map.isEmpty();
        }
    }

    @NonNull
    public Set<ResourceType> getAvailableResourceTypes() {
        EnumSet<ResourceType> result = null;
        synchronized (ITEM_MAP_LOCK) {
            for (ResourceNamespace namespace : getNamespaces()) {
                Set<ResourceType> types = getFullTable().row(namespace).keySet();
                if (result == null) {
                    result = EnumSet.copyOf(types);
                } else {
                    result.addAll(types);
                }
            }
        }
        return result == null ? EnumSet.noneOf(ResourceType.class) : result;
    }

    /**
     * Returns the {@link ResourceFile} matching the given name, {@link ResourceType} and
     * configuration.
     *
     * <p>This only works with files generating one resource named after the file
     * (for instance, layouts, bitmap based drawable, xml, anims).
     *
     * @param name the resource name
     * @param type the folder type search for
     * @param config the folder configuration to match for
     * @return the matching file or <code>null</code> if no match was found.
     */
    @Nullable
    public ResourceFile getMatchingFile(
            @NonNull String name,
            @NonNull ResourceType type,
            @NonNull FolderConfiguration config) {
        List<ResourceFile> matchingFiles = getMatchingFiles(name, type, config);
        return matchingFiles.isEmpty() ? null : matchingFiles.get(0);
    }

    /**
     * Returns a list of {@link ResourceFile} matching the given name, {@link ResourceType} and
     * configuration. This ignores the qualifiers which are missing from the configuration.
     * <p>
     * This only works with files generating one resource named after the file (for instance,
     * layouts, bitmap based drawable, xml, anims).
     *
     * @param name the resource name
     * @param type the folder type search for
     * @param config the folder configuration to match for
     *
     * @see #getMatchingFile(String, ResourceType, FolderConfiguration)
     */
    @NonNull
    public List<ResourceFile> getMatchingFiles(
            @NonNull String name,
            @NonNull ResourceType type,
            @NonNull FolderConfiguration config) {
        return getMatchingFiles(name, type, config, new HashSet<>(), 0);
    }

    @NonNull
    // TODO: namespaces
    private List<ResourceFile> getMatchingFiles(
            @NonNull String name,
            @NonNull ResourceType type,
            @NonNull FolderConfiguration config,
            @NonNull Set<String> seenNames,
            int depth) {
        assert !seenNames.contains(name);
        if (depth >= MAX_RESOURCE_INDIRECTION) {
            return Collections.emptyList();
        }
        List<ResourceFile> output;
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> typeItems =
                    getMap(ResourceNamespace.TODO, type, false);
            if (typeItems == null) {
                return Collections.emptyList();
            }
            seenNames.add(name);
            output = new ArrayList<>();
            List<ResourceItem> matchingItems = typeItems.get(name);
            List<ResourceItem> matches = config.findMatchingConfigurables(matchingItems);
            for (ResourceItem match : matches) {
                // if match is an alias, check if the name is in seen names.
                ResourceValue resourceValue = match.getResourceValue();
                if (resourceValue != null) {
                    String value = resourceValue.getValue();
                    if (value != null && value.startsWith(PREFIX_RESOURCE_REF)) {
                        ResourceUrl url = ResourceUrl.parse(value);
                        if (url != null && url.type == type && url.isFramework() == isFramework()) {
                            if (!seenNames.contains(url.name)) {
                                // This resource alias needs to be resolved again.
                                output.addAll(getMatchingFiles(
                                        url.name, type, config, seenNames, depth + 1));
                            }
                            continue;
                        }
                    }
                }
                output.add(match.getSource());

            }
        }

        return output;
    }

    /**
     * Returns the resources values matching a given {@link FolderConfiguration}.
     *
     * @param referenceConfig the configuration that each value must match.
     * @return a {@link Table} with one row for every namespace present in this repository, where
     *     every row contains an entry for all resource types.
     */
    @NonNull
    public Table<ResourceNamespace, ResourceType, ResourceValueMap> getConfiguredResources(
            @NonNull FolderConfiguration referenceConfig) {
        synchronized (ITEM_MAP_LOCK) {
            Set<ResourceNamespace> namespaces = getNamespaces();

            Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> backingMap;
            if (KnownNamespacesMap.canContainAll(namespaces)) {
                backingMap = new KnownNamespacesMap<>();
            } else {
                backingMap = new HashMap<>();
            }
            Table<ResourceNamespace, ResourceType, ResourceValueMap> table =
                    Tables.newCustomTable(backingMap, () -> new EnumMap<>(ResourceType.class));

            for (ResourceNamespace namespace : namespaces) {
                // TODO(namespaces): Move this method to ResourceResolverCache.
                // For performance reasons don't mix framework and non-framework resources since
                // they have different life spans.
                assert namespaces.size() == 1 || namespace != ResourceNamespace.ANDROID;

                for (ResourceType type : ResourceType.values()) {
                    // get the local results and put them in the map
                    table.put(
                            namespace,
                            type,
                            getConfiguredResources(namespace, type, referenceConfig));
                }
            }
            return table;
        }
    }

    /**
     * Returns a map of (resource name, resource value) for the given {@link ResourceType}.
     *
     * <p>The values returned are taken from the resource files best matching a given {@link
     * FolderConfiguration}.
     *
     * @param namespace namespaces of the resources
     * @param type the type of the resources.
     * @param referenceConfig the configuration to best match.
     */
    @NonNull
    // TODO: namespaces
    public ResourceValueMap getConfiguredResources(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull FolderConfiguration referenceConfig) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> items = getFullTable().get(namespace, type);
            if (items == null) {
                return ResourceValueMap.create();
            }

            Set<String> keys = items.keySet();
            ResourceValueMap map = ResourceValueMap.createWithExpectedSize(keys.size());

            for (String key : keys) {
                List<ResourceItem> keyItems = items.get(key);

                // Look for the best match for the given configuration.
                ResourceItem match = referenceConfig.findMatchingConfigurable(keyItems);
                if (match != null) {
                    ResourceValue value = match.getResourceValue();
                    if (value != null) {
                        map.put(match.getName(), value);
                    }
                }
            }

            return map;
        }
    }

    @Nullable
    // TODO: namespaces
    public ResourceValue getConfiguredValue(
            @NonNull ResourceType type,
            @NonNull String name,
            @NonNull FolderConfiguration referenceConfig) {
        synchronized (ITEM_MAP_LOCK) {
            // get the resource item for the given type
            ListMultimap<String, ResourceItem> items = getMap(ResourceNamespace.TODO, type, false);
            if (items == null) {
                return null;
            }

            List<ResourceItem> keyItems = items.get(name);
            if (keyItems == null) {
                return null;
            }

            // look for the best match for the given configuration
            // the match has to be of type ResourceFile since that's what the input list contains
            ResourceItem match = (ResourceItem) referenceConfig.findMatchingConfigurable(keyItems);
            return match != null ? match.getResourceValue() : null;
        }
    }

    /** Returns the sorted list of languages used in the resources. */
    @NonNull
    // TODO: namespaces
    public SortedSet<String> getLanguages() {
        SortedSet<String> set = new TreeSet<>();

        // As an optimization we could just look for values since that's typically where
        // the languages are defined -- not on layouts, menus, etc -- especially if there
        // are no translations for it
        Set<String> qualifiers = new HashSet<>();

        synchronized (ITEM_MAP_LOCK) {
            for (ListMultimap<String, ResourceItem> map : getFullTable().values()) {
                for (ResourceItem item : map.values()) {
                    qualifiers.add(item.getQualifiers());
                }
            }
        }

        for (String s : qualifiers) {
            FolderConfiguration configuration = FolderConfiguration.getConfigForQualifierString(s);
            if (configuration != null) {
                LocaleQualifier locale = configuration.getLocaleQualifier();
                if (locale != null) {
                    set.add(locale.getLanguage());
                }
            }
        }

        return set;
    }

    /** Returns the sorted list of languages used in the resources. */
    @NonNull
    // TODO: namespaces
    public SortedSet<LocaleQualifier> getLocales() {
        SortedSet<LocaleQualifier> set = new TreeSet<>();

        // As an optimization we could just look for values since that's typically where
        // the languages are defined -- not on layouts, menus, etc -- especially if there
        // are no translations for it
        Set<String> qualifiers = new HashSet<>();

        synchronized (ITEM_MAP_LOCK) {
            for (ListMultimap<String, ResourceItem> map : getFullTable().values()) {
                for (ResourceItem item : map.values()) {
                    qualifiers.add(item.getQualifiers());
                }
            }
        }

        for (String s : qualifiers) {
            FolderConfiguration configuration = FolderConfiguration.getConfigForQualifierString(s);
            if (configuration != null) {
                LocaleQualifier locale = configuration.getLocaleQualifier();
                if (locale != null) {
                    set.add(locale);
                }
            }
        }

        return set;
    }

    /**
     * Returns the sorted list of regions used in the resources with the given language.
     *
     * @param currentLanguage the current language the region must be associated with.
     */
    @NonNull
    // TODO: This method is not used. Consider removing it.
    public SortedSet<String> getRegions(@NonNull String currentLanguage) {
        SortedSet<String> set = new TreeSet<>();

        // As an optimization we could just look for values since that's typically where
        // the languages are defined -- not on layouts, menus, etc -- especially if there
        // are no translations for it
        Set<String> qualifiers = new HashSet<>();
        synchronized (ITEM_MAP_LOCK) {
            for (ListMultimap<String, ResourceItem> map : getFullTable().values()) {
                for (ResourceItem item : map.values()) {
                    qualifiers.add(item.getQualifiers());
                }
            }
        }

        for (String s : qualifiers) {
            FolderConfiguration configuration = FolderConfiguration.getConfigForQualifierString(s);
            if (configuration != null) {
                LocaleQualifier locale = configuration.getLocaleQualifier();
                if (locale != null && locale.getRegion() != null
                        && locale.getLanguage().equals(currentLanguage)) {
                    set.add(locale.getRegion());
                }
            }
        }

        return set;
    }
}
