/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.ide.common.res2.AbstractResourceRepository.MAX_RESOURCE_INDIRECTION;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>NOTE:</b> LayoutLib tests depend on this class.
 */
public class ResourceResolver extends RenderResources {
    public static final String THEME_NAME = "Theme";
    public static final String THEME_NAME_DOT = "Theme.";

    /**
     * Constant passed to {@link #setDeviceDefaults(String)} to indicate the DeviceDefault styles
     * should point to the default styles
     */
    public static final String LEGACY_THEME = "";

    public static final Pattern DEVICE_DEFAULT_PATTERN =
            Pattern.compile("(\\p{Alpha}+)?\\.?DeviceDefault\\.?(.+)?");

    private final Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> mResources;
    private final Map<StyleResourceValue, StyleResourceValue> mStyleInheritanceMap =
            new HashMap<>();

    @Nullable private final StyleResourceValue mDefaultTheme;

    /** The resources should be searched in all the themes in the list in order. */
    @NonNull private final List<StyleResourceValue> mThemes;

    @NonNull private ResourceIdProvider mFrameworkIdProvider = new FrameworkResourceIdProvider();
    @NonNull private ResourceIdProvider mLibrariesIdProvider = new ResourceIdProvider();
    private LayoutLog mLogger;

    /** Contains the default parent for DeviceDefault styles (e.g. for API 18, "Holo") */
    private String mDeviceDefaultParent;

    private ResourceResolver(
            @NonNull Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> resources,
            @Nullable StyleResourceValue theme) {
        mResources = resources;
        mDefaultTheme = theme;
        mThemes = new LinkedList<>();
    }

    /**
     * Creates a new {@link ResourceResolver} object.
     *
     * @param resources all resources.
     * @param themeReference reference to the theme to be used.
     * @return a new {@link ResourceResolver}
     */
    public static ResourceResolver create(
            @NonNull Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> resources,
            @Nullable ResourceReference themeReference) {
        StyleResourceValue theme = null;
        if (themeReference != null) {
            assert themeReference.getResourceType() == ResourceType.STYLE;
            theme = findTheme(themeReference, resources);
        }
        ResourceResolver resolver = new ResourceResolver(resources, theme);
        resolver.preProcessStyles();
        return resolver;
    }

    @Nullable
    private static StyleResourceValue findTheme(
            @NonNull ResourceReference themeReference,
            @NonNull Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> resources) {
        Map<ResourceType, ResourceValueMap> namespaceMap =
                resources.get(themeReference.getNamespace());
        if (namespaceMap == null) {
            return null;
        }

        ResourceValueMap stylesMap = namespaceMap.get(ResourceType.STYLE);
        if (stylesMap == null) {
            return null;
        }

        ResourceValue resourceValue = stylesMap.get(themeReference.getName());

        return resourceValue instanceof StyleResourceValue
                ? (StyleResourceValue) resourceValue
                : null;
    }

    /**
     * Creates a new {@link ResourceResolver} copied from the given instance.
     *
     * @return a new {@link ResourceResolver} or null if the passed instance is null
     */
    @Nullable
    public static ResourceResolver copy(@Nullable ResourceResolver original) {
        if (original == null) {
            return null;
        }

        ResourceResolver resolver =
                new ResourceResolver(original.mResources, original.mDefaultTheme);
        resolver.mFrameworkIdProvider = original.mFrameworkIdProvider;
        resolver.mLibrariesIdProvider = original.mLibrariesIdProvider;
        resolver.mLogger = original.mLogger;
        resolver.mStyleInheritanceMap.putAll(original.mStyleInheritanceMap);
        resolver.mThemes.addAll(original.mThemes);

        return resolver;
    }

    /**
     * Creates a new {@link ResourceResolver} which contains only the given {@link ResourceValue}
     * objects, indexed by namespace, type and name. There can be no duplicate (namespace, type,
     * name) tuples in the input.
     *
     * <p>This method is meant for testing, where other components need to set up a simple {@link
     * ResourceResolver} with known contents.
     */
    @NonNull
    @VisibleForTesting
    public static ResourceResolver withValues(@NonNull ResourceValue... values) {
        return withValues(Arrays.asList(values), null);
    }

    /**
     * Creates a new {@link ResourceResolver} which contains only the given {@link ResourceValue}
     * objects, indexed by namespace, type and name. There can be no duplicate (namespace, type,
     * name) tuples in the input.
     *
     * <p>This method is meant for testing, where other components need to set up a simple {@link
     * ResourceResolver} with known contents.
     */
    @NonNull
    @VisibleForTesting
    public static ResourceResolver withValues(
            @NonNull Iterable<ResourceValue> values, @Nullable ResourceReference themeReference) {
        Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> resources = new HashMap<>();
        for (ResourceValue value : values) {
            Map<ResourceType, ResourceValueMap> byType =
                    resources.computeIfAbsent(
                            value.getNamespace(), ns -> new EnumMap<>(ResourceType.class));
            ResourceValueMap resourceValueMap =
                    byType.computeIfAbsent(value.getResourceType(), t -> ResourceValueMap.create());
            checkArgument(
                    !resourceValueMap.containsKey(value.getName()), "Duplicate resource: " + value);
            resourceValueMap.put(value);
        }

        return create(resources, themeReference);
    }

    @Nullable
    private ResourceValueMap getResourceValueMap(
            @NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
        Map<ResourceType, ResourceValueMap> row = mResources.get(namespace);
        return row != null ? row.get(type) : null;
    }

    /**
     * This will override the DeviceDefault styles so they point to the given parent styles (e.g. If
     * "Material" is passed, Theme.DeviceDefault parent will become Theme.Material). This patches
     * all the styles (not only themes) and takes care of the light and dark variants. If {@link
     * #LEGACY_THEME} is passed, parents will be directed to the default themes (i.e. Theme).
     */
    public void setDeviceDefaults(@NonNull String deviceDefaultParent) {
        if (deviceDefaultParent.equals(mDeviceDefaultParent)) {
            // No need to patch again with the same parent
            return;
        }

        mDeviceDefaultParent = deviceDefaultParent;
        // The joiner will ignore nulls so if the caller specified an empty name, we replace it with
        // a null so it gets ignored
        String parentName = Strings.emptyToNull(deviceDefaultParent);

        // TODO(namespaces): why only framework styles?
        ResourceValueMap frameworkStyles =
                getResourceValueMap(ResourceNamespace.ANDROID, ResourceType.STYLE);
        if (frameworkStyles == null) {
            return;
        }

        for (ResourceValue value : frameworkStyles.values()) {
            // The regexp gets the prefix and suffix if they exist (without the dots)
            Matcher matcher = DEVICE_DEFAULT_PATTERN.matcher(value.getName());
            if (!matcher.matches()) {
                continue;
            }

            String newParentStyle =
                    Joiner.on('.').skipNulls().join(matcher.group(1), parentName,
                            ((matcher.groupCount() > 1) ? matcher.group(2) : null));
            patchFrameworkStyleParent(value.getName(), newParentStyle);
        }
    }

    /**
     * Updates the parent of a given framework style. This method is used to patch DeviceDefault
     * styles when using a CompatibilityTarget
     */
    private void patchFrameworkStyleParent(String childStyleName, String parentName) {
        // TODO(namespaces): why only framework styles?
        ResourceValueMap map = getResourceValueMap(ResourceNamespace.ANDROID, ResourceType.STYLE);
        if (map != null) {
            StyleResourceValue from = (StyleResourceValue)map.get(childStyleName);
            StyleResourceValue to = (StyleResourceValue)map.get(parentName);

            if (from != null && to != null) {
                StyleResourceValue newStyle =
                        new StyleResourceValue(from, parentName, from.getLibraryName());
                newStyle.replaceWith(from);
                mStyleInheritanceMap.put(newStyle, to);
            }
        }
    }

    // ---- Methods to help dealing with older LayoutLibs.

    @Nullable
    public StyleResourceValue getTheme() {
        return mDefaultTheme;
    }

    @Deprecated // TODO(namespaces)
    public Map<ResourceType, ResourceValueMap> getProjectResources() {
        return mResources.get(ResourceNamespace.RES_AUTO);
    }

    @Deprecated // TODO(namespaces)
    public Map<ResourceType, ResourceValueMap> getFrameworkResources() {
        return mResources.get(ResourceNamespace.ANDROID);
    }

    public void setLibrariesIdProvider(@NonNull ResourceIdProvider provider) {
        mLibrariesIdProvider = provider;
    }

    // ---- RenderResources Methods

    @Override
    public void setFrameworkResourceIdProvider(@NonNull ResourceIdProvider provider) {
        mFrameworkIdProvider = provider;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setFrameworkResourceIdProvider(@NonNull FrameworkResourceIdProvider provider) {
        setFrameworkResourceIdProvider((ResourceIdProvider) provider);
    }

    @Override
    public void setLogger(LayoutLog logger) {
        mLogger = logger;
    }

    @Override
    public StyleResourceValue getDefaultTheme() {
        return mDefaultTheme;
    }

    @Override
    public void applyStyle(StyleResourceValue theme, boolean useAsPrimary) {
        if (theme == null) {
            return;
        }
        if (useAsPrimary) {
            mThemes.add(0, theme);
        } else {
            mThemes.add(theme);
        }
    }

    @Override
    public void clearStyles() {
        mThemes.clear();
        if (mDefaultTheme != null) {
            mThemes.add(mDefaultTheme);
        }
    }

    @Override
    public List<StyleResourceValue> getAllThemes() {
        return mThemes;
    }

    @Override
    public boolean themeIsParentOf(StyleResourceValue parentTheme, StyleResourceValue childTheme) {
        do {
            childTheme = mStyleInheritanceMap.get(childTheme);
            if (childTheme == null) {
                return false;
            } else if (childTheme == parentTheme) {
                return true;
            }
        } while (true);
    }

    @Override
    @Nullable
    public ItemResourceValue findItemInStyle(
            @NonNull StyleResourceValue style, @NonNull ResourceReference attr) {
        return findItemInStyle(style, attr, 0);
    }

    @Nullable
    private ItemResourceValue findItemInStyle(
            @NonNull StyleResourceValue style, @NonNull ResourceReference attr, int depth) {
        ItemResourceValue item = style.getItem(attr);

        // if we didn't find it, we look in the parent style (if applicable)
        //noinspection VariableNotUsedInsideIf
        if (item == null) {
            StyleResourceValue parentStyle = mStyleInheritanceMap.get(style);
            if (parentStyle != null) {
                if (depth >= MAX_RESOURCE_INDIRECTION) {
                    if (mLogger != null) {
                        mLogger.error(
                                LayoutLog.TAG_BROKEN,
                                String.format(
                                        "Cyclic style parent definitions: %1$s",
                                        computeCyclicStyleChain(style)),
                                null,
                                null,
                                null);
                    }

                    return null;
                }

                return findItemInStyle(parentStyle, attr, depth + 1);
            }
        }

        return item;
    }

    private String computeCyclicStyleChain(StyleResourceValue style) {
        StringBuilder sb = new StringBuilder(100);
        appendStyleParents(style, new HashSet<>(), 0, sb);
        return sb.toString();
    }

    private void appendStyleParents(StyleResourceValue style, Set<StyleResourceValue> seen,
            int depth, StringBuilder sb) {
        if (depth >= MAX_RESOURCE_INDIRECTION) {
            sb.append("...");
            return;
        }

        boolean haveSeen = seen.contains(style);
        seen.add(style);

        sb.append('"');
        sb.append(style.getResourceUrl().getQualifiedName());
        sb.append('"');

        if (haveSeen) {
            return;
        }

        StyleResourceValue parentStyle = mStyleInheritanceMap.get(style);
        if (parentStyle != null) {
            if (style.getParentStyleName() != null) {
                sb.append(" specifies parent ");
            } else {
                sb.append(" implies parent ");
            }

            appendStyleParents(parentStyle, seen, depth + 1, sb);
        }
    }

    @Override
    public ResourceValue getUnresolvedResource(ResourceReference reference) {
        ResourceValueMap resourceValueMap =
                getResourceValueMap(reference.getNamespace(), reference.getResourceType());
        if (resourceValueMap != null) {
            return resourceValueMap.get(reference.getName());
        }

        return null;
    }

    @Override
    @Nullable
    public ResourceValue dereference(@NonNull ResourceValue value) {
        ResourceReference reference = value.getReference();

        if (reference == null
                || !ResourceUrl.isValidName(reference.getName(), reference.getResourceType())) {
            // Looks like the value didn't reference anything. Return null.
            return null;
        }

        // It was successfully parsed as a ResourceUrl, so it cannot be null.
        assert value.getValue() != null;

        if (value.getValue().startsWith(PREFIX_THEME_REF)) {
            // No theme? No need to go further!
            if (mDefaultTheme == null) {
                return null;
            }

            if (reference.getResourceType() != ResourceType.ATTR) {
                // At this time, no support for ?type/name where type is not "attr"
                return null;
            }

            // Now look for the item in the theme, starting with the current one.
            return findItemInTheme(reference);
        } else {
            if (reference.getResourceType() == ResourceType.AAPT) {
                // Aapt resources are synthetic references that do not need to be resolved.
                return null;
            } else if (reference.getResourceType() == ResourceType.SAMPLE_DATA) {
                // Sample data resources are only available within the tools namespace
                return findSampleDataValue(reference);
            }

            ResourceValue result = getUnresolvedResource(reference);
            if (result != null) {
                return result;
            }

            if (value.getValue().startsWith(NEW_ID_PREFIX)) {
                return null;
            }

            if (reference.getResourceType() == ResourceType.ID) {
                // If it was not found and the type is an id, it is possible that the ID was
                // generated dynamically (by the '@+' syntax) when compiling the framework
                // resources or in a library, in which case it was not in the repositories.
                // See FileResourceRepository#myAarDeclaredIds.
                boolean idExists =
                        reference.isFramework()
                                ? mFrameworkIdProvider.getId(ResourceType.ID, reference.getName())
                                        != null
                                : mLibrariesIdProvider.getId(ResourceType.ID, reference.getName())
                                        != null;

                if (idExists) {
                    // TODO(namespaces): Cache these?
                    return new ResourceValue(reference, null);
                }
            }

            // Didn't find the resource anywhere.
            if (mLogger != null) {
                mLogger.warning(
                        LayoutLog.TAG_RESOURCES_RESOLVE,
                        "Couldn't resolve resource " + reference.getResourceUrl(),
                        null,
                        reference);
            }

            return null;
        }
    }

    @Override
    public ResourceValue resolveResValue(@Nullable ResourceValue resValue) {
        for (int depth = 0; depth < MAX_RESOURCE_INDIRECTION; depth++) {
            if (resValue == null) {
                return null;
            }

            String value = resValue.getValue();
            if (value == null || resValue instanceof ArrayResourceValue) {
                // If there's no value or this an array resource (e.g. <string-array>), return.
                return resValue;
            }

            // Else attempt to find another ResourceValue referenced by this one.
            ResourceValue resolvedResValue = dereference(resValue);

            // If the value did not reference anything, then return the input value.
            if (resolvedResValue == null) {
                return resValue;
            }

            if (resValue == resolvedResValue) {
                break; // Resource value referring to itself.
            }
            // Continue resolution with the new value.
            resValue = resolvedResValue;
        }

        if (mLogger != null) {
            mLogger.error(
                    LayoutLog.TAG_BROKEN,
                    String.format(
                            "Potential stack overflow trying to resolve '%s': "
                                    + "cyclic resource definitions? Render may not be accurate.",
                            resValue.getValue()),
                    null,
                    null,
                    null);
        }
        return resValue;
    }

    // ---- Private helper methods.

    private SampleDataManager mSampleDataManager = new SampleDataManager();

    private ResourceValue findSampleDataValue(@NonNull ResourceReference value) {
        String name = value.getName();
        return Optional.ofNullable(
                        getResourceValueMap(value.getNamespace(), value.getResourceType()))
                .map(t -> t.get(SampleDataManager.getResourceNameFromSampleReference(name)))
                .filter(SampleDataResourceValue.class::isInstance)
                .map(SampleDataResourceValue.class::cast)
                .map(SampleDataResourceValue::getValueAsLines)
                .map(content -> mSampleDataManager.getSampleDataLine(name, content))
                .map(
                        lineContent ->
                                new ResourceValue(
                                        value.getNamespace(),
                                        ResourceType.SAMPLE_DATA,
                                        name,
                                        lineContent))
                .orElse(null);
    }

    /** Computes style information, like the inheritance relation. */
    private void preProcessStyles() {
        if (mDefaultTheme == null) {
            return;
        }

        // This method will recalculate the inheritance map so any modifications done by
        // setDeviceDefault will be lost. Set mDeviceDefaultParent to null so when setDeviceDefault
        // is called again, it knows that it needs to modify the inheritance map again.
        mDeviceDefaultParent = null;

        for (Map<ResourceType, ResourceValueMap> mapForNamespace : mResources.values()) {
            ResourceValueMap styles = mapForNamespace.get(ResourceType.STYLE);
            if (styles == null) {
                continue;
            }

            for (ResourceValue value : styles.values()) {
                if (!(value instanceof StyleResourceValue)) {
                    continue;
                }

                StyleResourceValue style = (StyleResourceValue) value;
                ResourceReference parent = style.getParentStyle();

                if (parent != null) {
                    ResourceValue parentStyle = getUnresolvedResource(parent);
                    if (parentStyle instanceof StyleResourceValue) {
                        mStyleInheritanceMap.put(style, (StyleResourceValue) parentStyle);
                        continue; // Don't log below.
                    }
                }

                if (mLogger != null) {
                    mLogger.error(
                            LayoutLog.TAG_RESOURCES_RESOLVE,
                            String.format(
                                    "Unable to resolve parent style name: %s",
                                    style.getParentStyleName()),
                            null,
                            null,
                            null);
                }
            }
        }

        clearStyles();
    }

    @Override
    @Nullable
    public StyleResourceValue getParent(@NonNull StyleResourceValue style) {
        return mStyleInheritanceMap.get(style);
    }

    public boolean styleExtends(
            @NonNull StyleResourceValue child, @NonNull StyleResourceValue ancestor) {
        StyleResourceValue current = child;
        while (current != null) {
            if (current.equals(ancestor)) {
                return true;
            }

            current = getParent(current);
        }

        return false;
    }

    @Override
    @Nullable
    public StyleResourceValue getStyle(@NonNull ResourceReference styleReference) {
        ResourceValue style = getUnresolvedResource(styleReference);
        if (style == null) {
            return null;
        }

        if (style instanceof StyleResourceValue) {
            return (StyleResourceValue) style;
        }

        if (mLogger != null) {
            mLogger.error(
                    null,
                    String.format(
                            "Style %1$s is not of type STYLE (instead %2$s)",
                            styleReference, style.getResourceType().toString()),
                    null,
                    null,
                    styleReference);
        }

        return null;
    }

    /** Returns true if the given {@link ResourceValue} represents a theme */
    public boolean isTheme(
            @NonNull ResourceValue value,
            @Nullable Map<ResourceValue, Boolean> cache) {
        return isTheme(value, cache, 0);
    }

    private boolean isTheme(
            @NonNull ResourceValue value,
            @Nullable Map<ResourceValue, Boolean> cache,
            int depth) {
        if (cache != null) {
            Boolean known = cache.get(value);
            if (known != null) {
                return known;
            }
        }
        if (value instanceof StyleResourceValue) {
            StyleResourceValue srv = (StyleResourceValue) value;
            String name = srv.getName();
            if (srv.getNamespace() == ResourceNamespace.ANDROID
                    && (name.equals(THEME_NAME) || name.startsWith(THEME_NAME_DOT))) {
                if (cache != null) {
                    cache.put(value, true);
                }
                return true;
            }

            StyleResourceValue parentStyle = mStyleInheritanceMap.get(srv);
            if (parentStyle != null) {
                if (depth >= MAX_RESOURCE_INDIRECTION) {
                    if (mLogger != null) {
                        mLogger.error(
                                LayoutLog.TAG_BROKEN,
                                String.format(
                                        "Cyclic style parent definitions: %1$s",
                                        computeCyclicStyleChain(srv)),
                                null,
                                null,
                                null);
                    }

                    return false;
                }

                boolean result = isTheme(parentStyle, cache, depth + 1);
                if (cache != null) {
                    cache.put(value, result);
                }
                return result;
            }
        }

        return false;
    }

    /**
     * Creates a new {@link ResourceResolver} which records all resource resolution
     * lookups into the given list. Note that it is the responsibility of the caller
     * to clear/reset the list between subsequent lookup operations.
     *
     * @param lookupChain the list to write resource lookups into
     * @return a new {@link ResourceResolver}
     */
    public ResourceResolver createRecorder(List<ResourceValue> lookupChain) {
        ResourceResolver resolver =
                new RecordingResourceResolver(lookupChain, mResources, mDefaultTheme);
        resolver.mFrameworkIdProvider = mFrameworkIdProvider;
        resolver.mLogger = mLogger;
        resolver.mStyleInheritanceMap.putAll(mStyleInheritanceMap);
        resolver.mThemes.addAll(mThemes);
        return resolver;
    }

    private static class RecordingResourceResolver extends ResourceResolver {
        @NonNull private List<ResourceValue> mLookupChain;

        private RecordingResourceResolver(
                @NonNull List<ResourceValue> lookupChain,
                @NonNull Map<ResourceNamespace, Map<ResourceType, ResourceValueMap>> resources,
                @Nullable StyleResourceValue theme) {
            super(resources, theme);
            mLookupChain = lookupChain;
        }

        @Override
        public ResourceValue resolveResValue(ResourceValue resValue) {
            if (resValue != null) {
                mLookupChain.add(resValue);
            }

            return super.resolveResValue(resValue);
        }

        @Nullable
        @Override
        public ResourceValue dereference(@NonNull ResourceValue value) {
            if (!mLookupChain.isEmpty()
                    && !mLookupChain.get(mLookupChain.size() - 1).equals(value)) {
                mLookupChain.add(value);
            }

            ResourceValue resValue = super.dereference(value);

            if (resValue != null) {
                mLookupChain.add(resValue);
            }

            return resValue;
        }

        @Override
        public ItemResourceValue findItemInStyle(
                @NonNull StyleResourceValue style, @NonNull ResourceReference attr) {
            ItemResourceValue value = super.findItemInStyle(style, attr);
            if (value != null) {
                mLookupChain.add(value);
            }
            return value;
        }

        @Override
        public ResourceValue findItemInTheme(@NonNull ResourceReference attr) {
            ResourceValue value = super.findItemInTheme(attr);
            if (value != null) {
                mLookupChain.add(value);
            }
            return value;
        }
    }
}
