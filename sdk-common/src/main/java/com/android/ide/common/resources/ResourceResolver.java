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

import static com.android.SdkConstants.*;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceResolver extends RenderResources {
    public static final String THEME_NAME = "Theme";
    public static final String THEME_NAME_DOT = "Theme.";
    public static final String XLIFF_NAMESPACE_PREFIX = "urn:oasis:names:tc:xliff:document:";
    public static final String XLIFF_G_TAG = "g";
    public static final String ATTR_EXAMPLE = "example";

    /**
     * Constant passed to {@link #setDeviceDefaults(String)} to indicate the DeviceDefault styles
     * should point to the default styles
     */
    public static final String LEGACY_THEME = "";

    public static final Pattern DEVICE_DEFAULT_PATTERN = Pattern
            .compile("(\\p{Alpha}+)?\\.?DeviceDefault\\.?(.+)?");

    /**
     * Number of indirections we'll follow for resource resolution before assuming there
     * is a cyclic dependency error in the input
     */
    public static final int MAX_RESOURCE_INDIRECTION = 50;

    private final Map<ResourceType, ResourceValueMap> mProjectResources;
    private final Map<ResourceType, ResourceValueMap> mFrameworkResources;
    private final Map<StyleResourceValue, StyleResourceValue> mStyleInheritanceMap =
        new HashMap<StyleResourceValue, StyleResourceValue>();
    private StyleResourceValue mDefaultTheme;
    // The resources should be searched in all the themes in the list in order.
    private final List<StyleResourceValue> mThemes;
    private FrameworkResourceIdProvider mFrameworkProvider;
    private LayoutLog mLogger;
    private final String mThemeName;
    private boolean mIsProjectTheme;

    /** Contains the default parent for DeviceDefault styles (e.g. for API 18, "Holo") */
    private String mDeviceDefaultParent = null;

    private ResourceResolver(
            Map<ResourceType, ResourceValueMap> projectResources,
            Map<ResourceType, ResourceValueMap> frameworkResources,
            String themeName, boolean isProjectTheme) {
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;
        mThemeName = themeName;
        mIsProjectTheme = isProjectTheme;
        mThemes = new LinkedList<StyleResourceValue>();
    }

    /**
     * Creates a new {@link ResourceResolver} object.
     *
     * @param projectResources the project resources.
     * @param frameworkResources the framework resources.
     * @param themeName the name of the current theme.
     * @param isProjectTheme Is this a project theme?
     * @return a new {@link ResourceResolver}
     */
    public static ResourceResolver create(
            Map<ResourceType, ResourceValueMap> projectResources,
            Map<ResourceType, ResourceValueMap> frameworkResources,
            String themeName, boolean isProjectTheme) {

        ResourceResolver resolver = new ResourceResolver(projectResources, frameworkResources,
                themeName, isProjectTheme);
        resolver.computeStyleMaps();

        return resolver;
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

        ResourceResolver resolver = new ResourceResolver(
          original.mProjectResources, original.mFrameworkResources,
          original.mThemeName, original.mIsProjectTheme);
        resolver.mFrameworkProvider = original.mFrameworkProvider;
        resolver.mLogger = original.mLogger;
        resolver.mDefaultTheme = original.mDefaultTheme;
        resolver.mStyleInheritanceMap.putAll(original.mStyleInheritanceMap);
        resolver.mThemes.addAll(original.mThemes);

        return resolver;
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

        // The regexp gets the prefix and suffix if they exist (without the dots)
        for (ResourceValue value : mFrameworkResources.get(ResourceType.STYLE).values()) {
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
        ResourceValueMap map = mFrameworkResources.get(ResourceType.STYLE);
        if (map != null) {
            StyleResourceValue from = (StyleResourceValue)map.get(childStyleName);
            StyleResourceValue to = (StyleResourceValue)map.get(parentName);

            if (from != null && to != null) {
                StyleResourceValue newStyle =
                        new StyleResourceValue(
                                from.getResourceUrl(), parentName, from.getLibraryName());
                newStyle.replaceWith(from);
                mStyleInheritanceMap.put(newStyle, to);
            }
        }
    }

    // ---- Methods to help dealing with older LayoutLibs.

    public String getThemeName() {
        return mThemeName;
    }

    public boolean isProjectTheme() {
        return mIsProjectTheme;
    }

    public Map<ResourceType, ResourceValueMap> getProjectResources() {
        return mProjectResources;
    }

    public Map<ResourceType, ResourceValueMap> getFrameworkResources() {
        return mFrameworkResources;
    }

    // ---- RenderResources Methods

    @Override
    public void setFrameworkResourceIdProvider(FrameworkResourceIdProvider provider) {
        mFrameworkProvider = provider;
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
        mThemes.add(mDefaultTheme);
    }

    @Override
    public List<StyleResourceValue> getAllThemes() {
        return mThemes;
    }

  @Override
    public StyleResourceValue getTheme(String name, boolean frameworkTheme) {
        ResourceValue theme;

        if (frameworkTheme) {
            ResourceValueMap frameworkStyleMap = mFrameworkResources.get(
                    ResourceType.STYLE);
            theme = frameworkStyleMap.get(name);
        } else {
            ResourceValueMap projectStyleMap = mProjectResources.get(ResourceType.STYLE);
            theme = projectStyleMap.get(name);
        }

        if (theme instanceof StyleResourceValue) {
            return (StyleResourceValue) theme;
        }

        return null;
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
    public ResourceValue getFrameworkResource(ResourceType resourceType, String resourceName) {
        return getResource(resourceType, resourceName, mFrameworkResources);
    }

    @Override
    public ResourceValue getProjectResource(ResourceType resourceType, String resourceName) {
        return getResource(resourceType, resourceName, mProjectResources);
    }

    @SuppressWarnings("deprecation") // Required to support older layoutlib clients
    @Override
    @Deprecated
    public ItemResourceValue findItemInStyle(StyleResourceValue style, String attrName) {
        // this method is deprecated because it doesn't know about the namespace of the
        // attribute so we search for the project namespace first and then in the
        // android namespace if needed.
        ItemResourceValue item = findItemInStyle(style, attrName, false);
        if (item == null) {
            item = findItemInStyle(style, attrName, true);
        }

        return item;
    }

    @Override
    public ItemResourceValue findItemInStyle(StyleResourceValue style, String itemName,
            boolean isFrameworkAttr) {
        return findItemInStyle(style, itemName, isFrameworkAttr, 0);
    }

    private ItemResourceValue findItemInStyle(StyleResourceValue style, String itemName,
                                              boolean isFrameworkAttr, int depth) {
        ItemResourceValue item = style.getItem(itemName, isFrameworkAttr);

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
                                null);
                    }

                    return null;
                }

                return findItemInStyle(parentStyle, itemName, isFrameworkAttr, depth + 1);
            }
        }

        return item;
    }

    private String computeCyclicStyleChain(StyleResourceValue style) {
        StringBuilder sb = new StringBuilder(100);
        appendStyleParents(style, new HashSet<StyleResourceValue>(), 0, sb);
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
        if (style.isFramework()) {
            sb.append(PREFIX_ANDROID);
        }
        sb.append(style.getName());
        sb.append('"');

        if (haveSeen) {
            return;
        }

        StyleResourceValue parentStyle = mStyleInheritanceMap.get(style);
        if (parentStyle != null) {
            if (style.getParentStyle() != null) {
                sb.append(" specifies parent ");
            } else {
                sb.append(" implies parent ");
            }

            appendStyleParents(parentStyle, seen, depth + 1, sb);
        }
    }

    @Override
    public ResourceValue findResValue(String reference, boolean forceFrameworkOnly) {
        if (reference == null) {
            return null;
        }

        ResourceUrl resource = ResourceUrl.parse(reference);
        if (resource == null || !resource.hasValidName()) {
            // Looks like the value didn't reference anything. Return null.
            return null;
        }

        if (resource.theme) {
            // no theme? no need to go further!
            if (mDefaultTheme == null) {
                return null;
            }

            if (resource.type != ResourceType.ATTR) {
                // At this time, no support for ?type/name where type is not "attr"
                return null;
            }

            // Now look for the item in the theme, starting with the current one.
            return findItemInTheme(resource.name, forceFrameworkOnly || resource.framework);
        } else {
            return findResValue(resource, forceFrameworkOnly);
        }
    }

    @Override
    public ResourceValue resolveResValue(ResourceValue resValue) {
        return resolveResValue(resValue, 0);
    }

    private ResourceValue resolveResValue(ResourceValue resValue, int depth) {
        if (resValue == null) {
            return null;
        }

        String value = resValue.getValue();
        if (value == null || resValue instanceof ArrayResourceValue) {
            // If there's no value or this an array resource (eg. <string-array>), return.
            return resValue;
        }

        // else attempt to find another ResourceValue referenced by this one.
        ResourceValue resolvedResValue = findResValue(value, resValue.isFramework());

        // if the value did not reference anything, then we simply return the input value
        if (resolvedResValue == null) {
            return resValue;
        }

        // detect potential loop due to mishandled namespace in attributes
        if (resValue == resolvedResValue || depth >= MAX_RESOURCE_INDIRECTION) {
            if (mLogger != null) {
                mLogger.error(
                        LayoutLog.TAG_BROKEN,
                        String.format(
                                "Potential stack overflow trying to resolve '%s': cyclic resource definitions? Render may not be accurate.",
                                value),
                        null);
            }
            return resValue;
        }

        // otherwise, we attempt to resolve this new value as well
        return resolveResValue(resolvedResValue, depth + 1);
    }

    // ---- Private helper methods.

    private SampleDataManager mSampleDataManager = new SampleDataManager();

    private ResourceValue findSampleDataValue(@NonNull ResourceUrl url) {
        // TODO: Remove this once repositories have namespace support
        // Resource repositories do not support namespaces yet. Because of this
        // we currently hack the namespace support as part of the item name.
        String name = (url.namespace == null ? "" : url.namespace + ":") + url.name;
        return Optional.ofNullable(mProjectResources.get(ResourceType.SAMPLE_DATA))
                .map(t -> t.get(SampleDataManager.getResourceNameFromSampleReference(name)))
                .filter(SampleDataResourceValue.class::isInstance)
                .map(SampleDataResourceValue.class::cast)
                .map(SampleDataResourceValue::getValueAsLines)
                .map(content -> mSampleDataManager.getSampleDataLine(name, content))
                .map(
                        lineContent ->
                                new ResourceValue(
                                        ResourceUrl.create(
                                                url.namespace, ResourceType.SAMPLE_DATA, name),
                                        lineContent))
                .orElse(null);
    }

    /**
     * Searches for, and returns a {@link ResourceValue} by its parsed reference.
     *
     * @param url the parsed resource
     * @param forceFramework if <code>true</code>, the method does not search in the project
     *     resources
     */
    private ResourceValue findResValue(ResourceUrl url, boolean forceFramework) {
        if (url.type == ResourceType.AAPT) {
            // Aapt resources are synthetic references that do not need to be resolved.
            return null;
        } else if (url.type == ResourceType.SAMPLE_DATA) {
            // Sample data resources are only available within the tools namespace
            return findSampleDataValue(url);
        }

        // map of ResourceValue for the given type
        ResourceValueMap typeMap;
        ResourceType resType = url.type;
        String resName = url.name;
        boolean isFramework = forceFramework || url.framework;
        if (!isFramework) {
            typeMap = mProjectResources.get(resType);
            ResourceValue item = typeMap.get(resName);
            if (item != null) {
                return item;
            }
        } else {
            typeMap = mFrameworkResources.get(resType);
            if (typeMap != null) {
                ResourceValue item = typeMap.get(resName);
                if (item != null) {
                    return item;
                }
            }

            // if it was not found and the type is an id, it is possible that the ID was
            // generated dynamically when compiling the framework resources.
            // Look for it in the R map.
            if (mFrameworkProvider != null && resType == ResourceType.ID) {
                if (mFrameworkProvider.getId(resType, resName) != null) {
                    return new ResourceValue(url.withFramework(true), null);
                }
            }
        }

        // didn't find the resource anywhere.
        if (!url.create && mLogger != null) {
            mLogger.warning(
                    LayoutLog.TAG_RESOURCES_RESOLVE,
                    "Couldn't resolve resource @"
                            + (isFramework ? "android:" : "")
                            + resType
                            + "/"
                            + resName,
                    null,
                    url.withFramework(isFramework));
        }
        return null;
    }

    private ResourceValue getResource(ResourceType resourceType, String resourceName,
            Map<ResourceType, ResourceValueMap> resourceRepository) {
        ResourceValueMap typeMap = resourceRepository.get(resourceType);
        if (typeMap != null) {
            ResourceValue item = typeMap.get(resourceName);
            if (item != null) {
                item = resolveResValue(item);
                return item;
            }
        }

        // didn't find the resource anywhere.
        return null;
    }

    /**
     * Compute style information from the given list of style for the project and framework.
     */
    private void computeStyleMaps() {
        ResourceValueMap projectStyleMap = mProjectResources.get(ResourceType.STYLE);
        ResourceValueMap frameworkStyleMap = mFrameworkResources.get(ResourceType.STYLE);

        // first, get the theme
        ResourceValue theme = null;

        // project theme names have been prepended with a *
        if (mIsProjectTheme) {
            if (projectStyleMap != null) {
                theme = projectStyleMap.get(mThemeName);
            }
        } else {
            if (frameworkStyleMap != null) {
                theme = frameworkStyleMap.get(mThemeName);
            }
        }

        if (theme instanceof StyleResourceValue) {
            // compute the inheritance map for both the project and framework styles
            computeStyleInheritance(projectStyleMap.values(), projectStyleMap,
                    frameworkStyleMap);

            // Compute the style inheritance for the framework styles/themes.
            // Since, for those, the style parent values do not contain 'android:'
            // we want to force looking in the framework style only to avoid using
            // similarly named styles from the project.
            // To do this, we pass null in lieu of the project style map.
            if (frameworkStyleMap != null) {
                computeStyleInheritance(frameworkStyleMap.values(), null /*inProjectStyleMap */,
                        frameworkStyleMap);
            }

            mDefaultTheme = (StyleResourceValue) theme;
            mThemes.clear();
            mThemes.add(mDefaultTheme);
        }
    }

    /**
     * Compute the parent style for all the styles in a given list.
     * @param styles the styles for which we compute the parent.
     * @param inProjectStyleMap the map of project styles.
     * @param inFrameworkStyleMap the map of framework styles.
     */
    private void computeStyleInheritance(Collection<ResourceValue> styles,
            ResourceValueMap inProjectStyleMap,
            ResourceValueMap inFrameworkStyleMap) {
        // This method will recalculate the inheritance map so any modifications done by
        // setDeviceDefault will be lost. Set mDeviceDefaultParent to null so when setDeviceDefault
        // is called again, it knows that it needs to modify the inheritance map again.
        mDeviceDefaultParent = null;
        for (ResourceValue value : styles) {
            if (value instanceof StyleResourceValue) {
                StyleResourceValue style = (StyleResourceValue)value;

                String parentName = getParentName(style);

                if (parentName != null) {
                    StyleResourceValue parentStyle = getStyle(parentName, inProjectStyleMap,
                            inFrameworkStyleMap);

                    if (parentStyle != null) {
                        mStyleInheritanceMap.put(style, parentStyle);
                    }
                }
            }
        }
    }

    /**
     * Computes the name of the parent style, or <code>null</code> if the style is a root style.
     * You probably want to use {@code ResolutionUtils,getParentQualifiedName(StyleResourceValue)}
     * instead
     */
    @Nullable
    public static String getParentName(StyleResourceValue style) {
        String parentName = style.getParentStyle();
        if (parentName != null) {
            return parentName;
        }

        String styleName = style.getName();
        int index = styleName.lastIndexOf('.');
        if (index != -1) {
            return styleName.substring(0, index);
        }
        return null;
    }

    @Override
    @Nullable
    public StyleResourceValue getParent(@NonNull StyleResourceValue style) {
        return mStyleInheritanceMap.get(style);
    }

    @Override
    @Nullable
    public StyleResourceValue getStyle(@NonNull String styleName, boolean isFramework) {
        ResourceValue res;
        ResourceValueMap styleMap;

        // First check if we can find the style directly.
        if (isFramework) {
            styleMap = mFrameworkResources.get(ResourceType.STYLE);
        } else {
            styleMap = mProjectResources.get(ResourceType.STYLE);
        }
        res = getStyleFromMap(styleMap, styleName);

        // If the obtained resource is not StyleResourceValue, return null.
        return res instanceof StyleResourceValue ? (StyleResourceValue) res : null;
    }

    /**
     * Search for the style in the given map and log an error if the obtained resource is not
     * {@link StyleResourceValue}.
     *
     * @return The {@link ResourceValue} found in the map.
     */
    @Nullable
    private ResourceValue getStyleFromMap(@NonNull ResourceValueMap styleMap,
            @NonNull String styleName) {
        ResourceValue res;
        res = styleMap.get(styleName);
        if (res != null) {
            if (!(res instanceof StyleResourceValue) && mLogger != null) {
                mLogger.error(
                        null,
                        String.format(
                                "Style %1$s is not of type STYLE (instead %2$s)",
                                styleName, res.getResourceType().toString()),
                        null);
            }
        }
        return res;
    }

    /**
     * Searches for and returns the {@link StyleResourceValue} from a given name.
     * <p>The format of the name can be:
     * <ul>
     * <li>[android:]&lt;name&gt;</li>
     * <li>[android:]style/&lt;name&gt;</li>
     * <li>@[android:]style/&lt;name&gt;</li>
     * </ul>
     * @param parentName the name of the style.
     * @param inProjectStyleMap the project style map. Can be <code>null</code>
     * @param inFrameworkStyleMap the framework style map.
     * @return The matching {@link StyleResourceValue} object or <code>null</code> if not found.
     */
    private StyleResourceValue getStyle(String parentName,
            ResourceValueMap inProjectStyleMap,
            ResourceValueMap inFrameworkStyleMap) {
        boolean frameworkOnly = false;

        String name = parentName;

        // remove the useless @ if it's there
        if (name.startsWith(PREFIX_RESOURCE_REF)) {
            name = name.substring(PREFIX_RESOURCE_REF.length());
        }

        // check for framework identifier.
        if (name.startsWith(PREFIX_ANDROID)) {
            frameworkOnly = true;
            name = name.substring(PREFIX_ANDROID.length());
        }

        // at this point we could have the format <type>/<name>. we want only the name as long as
        // the type is style.
        if (name.startsWith(REFERENCE_STYLE)) {
            name = name.substring(REFERENCE_STYLE.length());
        } else if (name.indexOf('/') != -1) {
            return null;
        }

        ResourceValue parent = null;

        // if allowed, search in the project resources.
        if (!frameworkOnly && inProjectStyleMap != null) {
            parent = inProjectStyleMap.get(name);
        }

        // if not found, then look in the framework resources.
        if (parent == null) {
            if (inFrameworkStyleMap == null) {
                return null;
            }
            parent = inFrameworkStyleMap.get(name);
        }

        // make sure the result is the proper class type and return it.
        if (parent instanceof StyleResourceValue) {
            return (StyleResourceValue) parent;
        }

        if (mLogger != null) {
            mLogger.error(
                    LayoutLog.TAG_RESOURCES_RESOLVE,
                    String.format("Unable to resolve parent style name: %s", parentName),
                    null);
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
            if (srv.isFramework() && (name.equals(THEME_NAME) || name.startsWith(THEME_NAME_DOT))) {
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
     * Returns true if the given {@code themeStyle} extends the theme given by
     * {@code parentStyle}
     */
    public boolean themeExtends(@NonNull String parentStyle, @NonNull String themeStyle) {
        ResourceValue parentValue = findResValue(parentStyle,
                parentStyle.startsWith(ANDROID_STYLE_RESOURCE_PREFIX));
        if (parentValue instanceof StyleResourceValue) {
            ResourceValue themeValue = findResValue(themeStyle,
                    themeStyle.startsWith(ANDROID_STYLE_RESOURCE_PREFIX));
            if (themeValue == parentValue) {
                return true;
            }
            if (themeValue instanceof StyleResourceValue) {
                return themeIsParentOf((StyleResourceValue) parentValue,
                        (StyleResourceValue) themeValue);
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
        ResourceResolver resolver = new RecordingResourceResolver(
                lookupChain, mProjectResources, mFrameworkResources, mThemeName, mIsProjectTheme);
        resolver.mFrameworkProvider = mFrameworkProvider;
        resolver.mLogger = mLogger;
        resolver.mDefaultTheme = mDefaultTheme;
        resolver.mStyleInheritanceMap.putAll(mStyleInheritanceMap);
        resolver.mThemes.addAll(mThemes);
        return resolver;
    }

    private static class RecordingResourceResolver extends ResourceResolver {
        @NonNull private List<ResourceValue> mLookupChain;

        private RecordingResourceResolver(
                @NonNull List<ResourceValue> lookupChain,
                @NonNull Map<ResourceType, ResourceValueMap> projectResources,
                @NonNull Map<ResourceType, ResourceValueMap> frameworkResources,
                @NonNull String themeName, boolean isProjectTheme) {
            super(projectResources, frameworkResources, themeName, isProjectTheme);
            mLookupChain = lookupChain;
        }

        @Override
        public ResourceValue resolveResValue(ResourceValue resValue) {
            if (resValue != null) {
                mLookupChain.add(resValue);
            }

            return super.resolveResValue(resValue);
        }

        @Override
        public ResourceValue findResValue(String reference, boolean forceFrameworkOnly) {
            if (!mLookupChain.isEmpty() && reference != null && reference.startsWith(PREFIX_RESOURCE_REF)) {
                ResourceValue prev = mLookupChain.get(mLookupChain.size() - 1);
                if (!reference.equals(prev.getValue())) {
                    ResourceValue next =
                            new ResourceValue(prev.getResourceUrl(), null, prev.getLibraryName());
                    next.setValue(reference);
                    mLookupChain.add(next);
                }
            }

            ResourceValue resValue = super.findResValue(reference, forceFrameworkOnly);

            if (resValue != null) {
                mLookupChain.add(resValue);
            }

            return resValue;
        }

        @Override
        public ItemResourceValue findItemInStyle(StyleResourceValue style, String itemName,
                boolean isFrameworkAttr) {
            ItemResourceValue value = super.findItemInStyle(style, itemName, isFrameworkAttr);
            if (value != null) {
                mLookupChain.add(value);
            }
            return value;
        }

        @Override
        public ResourceValue findItemInTheme(String attrName, boolean isFrameworkAttr) {
            ResourceValue value = super.findItemInTheme(attrName, isFrameworkAttr);
            if (value != null) {
                mLookupChain.add(value);
            }
            return value;
        }
    }
}
