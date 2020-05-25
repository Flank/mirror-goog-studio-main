/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ide.common.resources.usage;

import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;
import static com.android.utils.SdkUtils.globToRegexp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.utils.SdkUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores information about all application resources. Supports two modes:
 *
 * <ul>
 *     <li>Resources from a single package. In this mode packageName inside resource objects is
 *         always null. Each resource in this mode is identified by type and name.
 *     <li>Resources from multiple packages. In this mode resource is identified by triple:
 *         packageName, type and name. This mode is used for Android applications with dynamic
 *         feature modules where each module defines their resources inside its own package and
 *         the same resource name and type may be used by different resources from different
 *         modules.
 * </ul>
 */
public class ResourceStore {
    private static final String ANDROID_RES = "android_res/";
    private static final int TYPICAL_RESOURCE_COUNT = 200;

    private static final Comparator<Resource> RESOURCE_COMPARATOR =
            Comparator.comparing((Resource r) -> r.type).thenComparing(r -> r.name);

    /** Does this store support resources from multiple packages. */
    private final boolean supportMultipackages;

    /** All known resources */
    private final Map<ResourceId, Resource> resourceById =
            Maps.newLinkedHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT);
    private final List<Resource> resources = Lists.newArrayListWithCapacity(TYPICAL_RESOURCE_COUNT);

    /** Resources partitioned by type and name. */
    private final Map<ResourceType, ListMultimap<String, Resource>> typeToName =
            Maps.newEnumMap(ResourceType.class);

    /** Map from resource ID value (R field value) to corresponding resource. */
    private final Map<Integer, Resource> valueToResource =
            Maps.newHashMapWithExpectedSize(TYPICAL_RESOURCE_COUNT);

    /** Set of resource names that are explicitly whitelisted as used. */
    private final Set<ResourceId> whitelistedResources = Sets.newHashSet();

    /**
     * Recorded list of keep attributes: these can contain wildcards,
     * so they can't be applied immediately; we have to apply them after
     * scanning through all resources.
     */
    private final List<String> keepAttributes = Lists.newArrayList();

    /**
     * Recorded list of discard attributes: these can contain wildcards,
     * so they can't be applied immediately; we have to apply them after
     * scanning through all resources.
     */
    private final List<String> discardAttributes = Lists.newArrayList();

    /**
     * Whether we should attempt to guess resources that should be kept based on looking
     * at the string pool and assuming some of the strings can be used to dynamically construct
     * the resource names. Can be turned off via {@code tools:shrinkMode="strict"}.
     */
    private boolean safeMode = true;

    public ResourceStore(boolean supportMultipackages) {
        this.supportMultipackages = supportMultipackages;
    }

    public ResourceStore() {
        this(false);
    }

    public boolean getSupportMultipackages() {
        return supportMultipackages;
    }

    /** Returns resource by its ID value (R field value). */
    @Nullable
    public Resource getResource(int value) {
        return valueToResource.get(value);
    }

    /**
     * Returns resource by triple: packageName, type and name. In a single package mode packageName
     * must be null.
     */
    @Nullable
    public Resource getResource(
            @Nullable String packageName, @NonNull ResourceType type, @NonNull String name) {
        Preconditions.checkArgument(
                supportMultipackages || packageName == null,
                "In a single package mode packageName must be null."
        );
        return resourceById.get(new ResourceId(packageName, name, type));
    }

    /**
     * Returns resources by type and name. In single package mode may return empty collection or
     * collection with only one element. In multiple package mode returns resources that have
     * specified type and name from all packages.
     */
    @NonNull
    public ImmutableList<Resource> getResources(@NonNull ResourceType type, @NonNull String name) {
        ListMultimap<String, Resource> resourcesByName = typeToName.get(type);
        if (resourcesByName == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(resourcesByName.get(resourceNameToFieldName(name)));
    }

    /**
     * Returns resources which are referenced by specified resource url. In single package mode may
     * return empty collection or collection with only one element. In multiple package mode returns
     * resources that are referenced by specified url from all packages.
     */
    @NonNull
    public ImmutableList<Resource> getResourcesFromUrl(@NonNull String possibleUrlReference) {
        ResourceUrl url = ResourceUrl.parse(possibleUrlReference);
        if (url == null || url.isFramework()) {
            return ImmutableList.of();
        }
        return getResources(url.type, url.name);
    }

    /**
     * Returns all resources that are referenced by provided {@param webUrl} (for example:
     * file:///android_res/drawable/bar.png). Looks for:
     *
     * <ul>
     *     <li>a full resource URL: /android_res/type/name.ext;
     *     <li>a partial URL that identifies resources: type/name.ext.
     * </ul>
     */
    @NonNull
    public ImmutableList<Resource> getResourcesFromWebUrl(@NonNull String webUrl) {
        String name, type;

        int androidResIndex = webUrl.indexOf(ANDROID_RES);
        if (androidResIndex != -1) {
            int typeSeparator = webUrl.indexOf(
                    '/', androidResIndex + ANDROID_RES.length());
            if (typeSeparator == -1) {
                return ImmutableList.of();
            }
            type = webUrl.substring(androidResIndex + ANDROID_RES.length(), typeSeparator);
            name = webUrl.substring(typeSeparator + 1);
        } else {
            List<String> parts = Splitter.on('/').trimResults().splitToList(webUrl);
            if (parts.size() < 2) {
                return ImmutableList.of();
            }
            type = parts.get(parts.size() - 2);
            name = parts.get(parts.size() - 1);
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(type);
        if (folderType == null) {
            return ImmutableList.of();
        }
        int dot = name.indexOf('.');
        String resourceName = name.substring(0, dot == -1 ? name.length() : dot);
        return FolderTypeRelationship.getRelatedResourceTypes(folderType).stream()
                .flatMap(t -> getResources(t, resourceName).stream())
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * Adds resource to store. In a single package mode, packageName is cleared inside provided
     * {@param resource}. If resource was already added to store but without its resource ID value
     * (value = -1) this method updates resource ID value.
     *
     * @return added or existing resource.
     */
    @NonNull
    public Resource addResource(@NonNull Resource resource) {
        if (!supportMultipackages) {
            resource.packageName = null;
        }
        Resource updated = resourceById.compute(
                ResourceId.fromResource(resource, supportMultipackages),
                (id, current) -> {
                    if (current != null) {
                        if (current.value == -1) {
                            current.value = resource.value;
                        }
                        assert current.value == resource.value || resource.value == -1;
                        return current;
                    }

                    typeToName
                            .computeIfAbsent(resource.type, t -> ArrayListMultimap.create())
                            .put(resource.name, resource);
                    resources.add(resource);
                    return resource;
                }
        );
        if (updated.value != -1) {
            valueToResource.putIfAbsent(updated.value, updated);
        }
        return updated;
    }

    /** Returns all resources. */
    @NonNull
    public List<Resource> getResources() {
        return Collections.unmodifiableList(resources);
    }

    /**
     * Returns all resources as collection of maps where each map contains resources of the same
     * type partitioned by name.
     */
    @NonNull
    public Collection<ListMultimap<String, Resource>> getResourceMaps() {
        return typeToName.values();
    }

    public boolean isSafeMode() {
        return safeMode;
    }

    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    public List<String> getKeepAttributes() {
        return Collections.unmodifiableList(keepAttributes);
    }

    public List<String> getDiscardAttributes() {
        return Collections.unmodifiableList(discardAttributes);
    }

    /**
     * Records resources to keep explicitly. Input is a value of 'tools:keep' attribute in a
     * &lt;resources&gt; tag.
     */
    public void recordKeepToolAttribute(@NonNull String value) {
        // We need to split value here because 'tools:keep' attribute value is comma-separated list
        // and may contain multiple keep rules.
        Splitter.on(',')
                .omitEmptyStrings()
                .trimResults()
                .split(value)
                .forEach(keepAttributes::add);
    }

    /**
     * Records resources to discard explicitly. Input is a value of 'tools:discard' attribute in a
     * &lt;resources&gt; tag.
     */
    public void recordDiscardToolAttribute(@NonNull String value) {
        // We need to split value here because 'tools:discard' attribute value is comma-separated
        // list and may contain multiple discard rules.
        Splitter.on(',')
                .omitEmptyStrings()
                .trimResults()
                .split(value)
                .forEach(discardAttributes::add);
    }

    /**
     * Processes all keep and discard rules which were added previously by
     * {@link #recordKeepToolAttribute)} and {@link #recordDiscardToolAttribute} and marks all
     * referenced resources as reachable/not reachable respectively.
     *
     * <p>If the same resource is referenced by some keep and discard rule then discard takes
     * precedence.
     */
    public void processToolsAttributes() {
        keepAttributes.forEach(this::keepResourcesExplicitly);
        discardAttributes.forEach(this::discardResourcesExplicitly);
    }

    private void keepResourcesExplicitly(@NonNull String pattern) {
        getResourcesStreamForKeepOrDiscard(pattern).forEach(resource -> {
            resource.setReachable(true);
            whitelistedResources.add(ResourceId.fromResource(resource, supportMultipackages));
        });
    }

    private void discardResourcesExplicitly(@NonNull String pattern) {
        getResourcesStreamForKeepOrDiscard(pattern)
                .forEach(resource -> resource.setReachable(false));
    }

    public List<Resource> findUnused() {
        List<Resource> roots = findRoots(resources);

        Map<Resource,Boolean> seen = new IdentityHashMap<>(resources.size());
        for (Resource root : roots) {
            visit(root, seen);
        }

        List<Resource> unused = Lists.newArrayListWithExpectedSize(resources.size());
        for (Resource resource : resources) {
            if (!resource.isReachable()
                    // Styles not yet handled correctly: don't mark as unused
                    && resource.type != ResourceType.ATTR
                    && resource.type != ResourceType.STYLEABLE
                    // Don't flag known service keys read by library
                    && !SdkUtils.isServiceKey(resource.name)) {
                unused.add(resource);
            }
        }

        return unused;
    }

    @NonNull
    private static List<Resource> findRoots(@NonNull List<Resource> resources) {
        List<Resource> roots = Lists.newArrayList();

        for (Resource resource : resources) {
            if (resource.isReachable() || resource.isKeep()) {
                roots.add(resource);
            }
        }
        return roots;
    }

    private static void visit(Resource root, Map<Resource, Boolean> seen) {
        if (seen.containsKey(root)) {
            return;
        }
        seen.put(root, Boolean.TRUE);
        root.setReachable(true);
        if (root.references != null) {
            for (Resource referenced : root.references) {
                visit(referenced, seen);
            }
        }
    }

    @NonNull
    public String dumpConfig() {
        return resources.stream()
                .sorted(RESOURCE_COMPARATOR)
                .map(r -> {
                    ResourceId resourceId = ResourceId.fromResource(r, supportMultipackages);
                    String actions = Stream.of(
                            !r.isReachable() ? "remove" : null,
                            whitelistedResources.contains(resourceId) ? "no_obfuscate" : null
                    )
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(","));
                    return r.type + "/" + r.name + "#" + actions;
                })
                .collect(Collectors.joining("\n", "", "\n"));
    }

    @NonNull
    public String dumpWhitelistedResources() {
        return whitelistedResources.stream()
                .map(id -> id.name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    @NonNull
    public String dumpReferences() {
        return resources.stream()
                .filter(r -> r.references != null)
                .map(r -> r + " => " + r.references)
                .collect(Collectors.joining("\n", "Resource Reference Graph:\n", ""));
    }

    @NonNull
    public String dumpResourceModel() {
        return resources.stream()
                .sorted(RESOURCE_COMPARATOR)
                .flatMap(r -> Stream.concat(
                        Stream.of(r.getUrl() + " : reachable=" + r.isReachable()),
                        r.references == null
                                ? Stream.empty()
                                : r.references.stream().map(ref -> "    " + ref.getUrl())

                ))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private Stream<Resource> getResourcesStreamForKeepOrDiscard(String pattern) {
        ResourceUrl url = ResourceUrl.parse(pattern);
        if (url == null || url.isFramework()) {
            return Stream.empty();
        }

        Multimap<String, Resource> resources = typeToName.get(url.type);
        if (resources == null) {
            return Stream.empty();
        }

        if (!url.name.contains("*") && !url.name.contains("?")) {
            return resources.get(url.name).stream();
        }

        String regexp = globToRegexp(resourceNameToFieldName(url.name));
        try {
            Pattern regExpPattern = Pattern.compile(regexp);
            return resources.entries().stream()
                    .filter(e -> regExpPattern.matcher(e.getKey()).matches())
                    .map(Map.Entry::getValue);
        } catch (PatternSyntaxException ignored) {
            return Stream.empty();
        }
    }

    private static final class ResourceId {
        public final String packageName;
        public final String name;
        public final ResourceType type;

        public ResourceId(String packageName, String name, ResourceType type) {
            this.packageName = packageName;
            this.name = name;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ResourceId that = (ResourceId) o;
            return Objects.equals(packageName, that.packageName) &&
                    Objects.equals(name, that.name) &&
                    type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, name, type);
        }

        static ResourceId fromResource(Resource resource, boolean usePackage) {
            String packageName = usePackage ? resource.packageName : null;
            return new ResourceId(packageName, resource.name, resource.type);
        }
    }

}
