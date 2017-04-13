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

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.collect.*;
import java.util.*;

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

    @NonNull
    protected abstract ResourceTable getFullTable();

    @Nullable
    protected abstract ListMultimap<String, ResourceItem> getMap(
            @Nullable String namespace, @NonNull ResourceType type, boolean create);

    @NonNull
    public abstract Set<String> getNamespaces();

    @NonNull
    protected final ListMultimap<String, ResourceItem> getMap(
            @Nullable String namespace, @NonNull ResourceType type) {
        //noinspection ConstantConditions - won't return null if create is false.
        return getMap(namespace, type, true);
    }

    @NonNull
    public ResourceTable getItems() {
        return getFullTable();
    }

    /** Lock used to protect map access */
    protected static final Object ITEM_MAP_LOCK = new Object();

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

    // TODO: Rename to getResourceItemList?
    // TODO: namespaces
    @Nullable
    public List<ResourceItem> getResourceItem(
            @NonNull ResourceType resourceType, @NonNull String resourceName) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(null, resourceType, false);

            if (map != null) {
                return map.get(resourceName);
            }
        }

        return null;
    }

    @NonNull
    // TODO: namespaces
    public Collection<String> getItemsOfType(@NonNull ResourceType type) {
        synchronized (ITEM_MAP_LOCK) {
            Multimap<String, ResourceItem> map = getMap(null, type, false);
            if (map == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableCollection(map.keySet());
        }
    }

    /**
     * Returns true if this resource repository contains a resource of the given
     * name.
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
            if (colon != -1) {
                // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
                if (remainder.indexOf('/', colon) == -1) {
                    remainder = remainder.substring(0, colon) + RESOURCE_CLZ_ATTR + '/'
                            + remainder.substring(colon);
                }
                url = PREFIX_RESOURCE_REF + remainder;
                return hasResourceItem(url);
            } else {
                int slash = url.indexOf('/');
                if (slash == -1) {
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
        if (typeEnd != -1) {
            int nameBegin = typeEnd + 1;

            // Skip @ and @+
            int typeBegin = url.startsWith("@+") ? 2 : 1; //$NON-NLS-1$

            int colon = url.lastIndexOf(':', typeEnd);
            if (colon != -1) {
                typeBegin = colon + 1;
            }
            String typeName = url.substring(typeBegin, typeEnd);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type != null) {
                String name = url.substring(nameBegin);
                return hasResourceItem(type, name);
            }
        }

        return false;
    }

    /**
     * Returns true if this resource repository contains a resource of the given name.
     *
     * @param resourceType the type of resource to look up
     * @param resourceName the name of the resource
     * @return true if the resource is known
     */
    // TODO: namespaces
    public boolean hasResourceItem(
            @NonNull ResourceType resourceType, @NonNull String resourceName) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(null, resourceType, false);

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
    // TODO: namespaces
    public boolean hasResourcesOfType(@NonNull ResourceType resourceType) {
        synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = getMap(null, resourceType, false);
            return map != null && !map.isEmpty();
        }
    }

    @NonNull
    // TODO: namespaces
    public List<ResourceType> getAvailableResourceTypes() {
        synchronized (ITEM_MAP_LOCK) {
            return Lists.newArrayList(getFullTable().row(null).keySet());
        }
    }

    /**
     * Returns the {@link ResourceFile} matching the given name, {@link ResourceType} and
     * configuration.
     * <p>
     * This only works with files generating one resource named after the file
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
            ListMultimap<String, ResourceItem> typeItems = getMap(null, type, false);
            if (typeItems == null) {
                return Collections.emptyList();
            }
            seenNames.add(name);
            output = new ArrayList<>();
            List<ResourceItem> matchingItems = typeItems.get(name);
            List<ResourceItem> matches = config.findMatchingConfigurables(matchingItems);
            for (ResourceItem match : matches) {
                // if match is an alias, check if the name is in seen names.
                ResourceValue resourceValue = match.getResourceValue(isFramework());
                if (resourceValue != null) {
                    String value = resourceValue.getValue();
                    if (value != null && value.startsWith(PREFIX_RESOURCE_REF)) {
                        ResourceUrl url = ResourceUrl.parse(value);
                        if (url != null && url.type == type && url.framework == isFramework()) {
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
     * @return a map with guaranteed to contain an entry for each {@link ResourceType}
     */
    @NonNull
    // TODO: namespaces
    public Map<ResourceType, ResourceValueMap> getConfiguredResources(
            @NonNull FolderConfiguration referenceConfig) {
        Map<ResourceType, ResourceValueMap> map = Maps.newEnumMap(ResourceType.class);

        synchronized (ITEM_MAP_LOCK) {
            for (ResourceType key : ResourceType.values()) {
                // get the local results and put them in the map
                map.put(key, getConfiguredResources(key, referenceConfig));
            }
        }

        return map;
    }

    /**
     * Returns a map of (resource name, resource value) for the given {@link ResourceType}.
     *
     * <p>The values returned are taken from the resource files best matching a given {@link
     * FolderConfiguration}.
     *
     * @param type the type of the resources.
     * @param referenceConfig the configuration to best match.
     */
    @NonNull
    // TODO: namespaces
    public ResourceValueMap getConfiguredResources(
            @NonNull ResourceType type, @NonNull FolderConfiguration referenceConfig) {
        // get the resource item for the given type
        ListMultimap<String, ResourceItem> items = getFullTable().get(null, type);
        if (items == null) {
            return ResourceValueMap.create();
        }

        Set<String> keys = items.keySet();

        // create the map
        ResourceValueMap map = ResourceValueMap.createWithExpectedSize(keys.size());

        for (String key : keys) {
            List<ResourceItem> keyItems = items.get(key);

            // look for the best match for the given configuration
            // the match has to be of type ResourceFile since that's what the input list contains
            ResourceItem match = (ResourceItem) referenceConfig.findMatchingConfigurable(keyItems);
            if (match != null) {
                ResourceValue value = match.getResourceValue(isFramework());
                if (value != null) {
                    map.put(match.getName(), value);
                }
            }
        }

        return map;
    }

    @Nullable
    // TODO: namespaces
    public ResourceValue getConfiguredValue(
            @NonNull ResourceType type,
            @NonNull String name,
            @NonNull FolderConfiguration referenceConfig) {
        // get the resource item for the given type
        ListMultimap<String, ResourceItem> items = getMap(null, type, false);
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
        return match != null ? match.getResourceValue(isFramework()) : null;
    }

    /** Returns the sorted list of languages used in the resources. */
    @NonNull
    // TODO: namespaces
    public SortedSet<String> getLanguages() {
        SortedSet<String> set = new TreeSet<>();

        // As an optimization we could just look for values since that's typically where
        // the languages are defined -- not on layouts, menus, etc -- especially if there
        // are no translations for it
        Set<String> qualifiers = Sets.newHashSet();

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
        Set<String> qualifiers = Sets.newHashSet();

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
    // TODO: namespaces
    public SortedSet<String> getRegions(@NonNull String currentLanguage) {
        SortedSet<String> set = new TreeSet<>();

        // As an optimization we could just look for values since that's typically where
        // the languages are defined -- not on layouts, menus, etc -- especially if there
        // are no translations for it
        Set<String> qualifiers = Sets.newHashSet();
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
