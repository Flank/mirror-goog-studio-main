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

package com.android.ide.common.rendering.api;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import java.util.Collections;
import java.util.List;

/**
 * A class containing all the resources needed to do a rendering.
 * <p>
 * This contains both the project specific resources and the framework resources, and provide
 * convenience methods to resolve resource and theme references.
 */
public class RenderResources {

    public static final String REFERENCE_NULL = "@null";
    public static final String REFERENCE_EMPTY = "@empty";
    public static final String REFERENCE_UNDEFINED = "@undefined";

    public static class ResourceIdProvider {
        public Integer getId(ResourceType resType, String resName) {
            return null;
        }
    }

    /**
     * @deprecated This class will be removed after layoutlib is updated. Use {@link
     *     ResourceIdProvider}
     */
    @Deprecated
    public static class FrameworkResourceIdProvider extends ResourceIdProvider {}

    public void setFrameworkResourceIdProvider(ResourceIdProvider provider) {}

    /**
     * @deprecated This method will be removed after layoutlib is updated. Use {@link
     *     #setFrameworkResourceIdProvider(ResourceIdProvider)}
     */
    @Deprecated
    public void setFrameworkResourceIdProvider(FrameworkResourceIdProvider provider) {
        setFrameworkResourceIdProvider((ResourceIdProvider) provider);
    }

    public void setLogger(LayoutLog logger) {
    }

    /**
     * Returns the {@link StyleResourceValue} representing the current theme.
     * @return the theme or null if there is no current theme.
     * @deprecated Use {@link #getDefaultTheme()} or {@link #getAllThemes()}
     */
    @Deprecated
    public StyleResourceValue getCurrentTheme() {
        // Default theme is same as the current theme was on older versions of the API.
        // With the introduction of applyStyle() "current" theme makes little sense.
        // Hence, simply return defaultTheme.
        return getDefaultTheme();
    }

    /**
     * Returns the {@link StyleResourceValue} representing the default theme.
     */
    public StyleResourceValue getDefaultTheme() {
        return null;
    }

    /**
     * Use this theme to resolve resources.
     * <p>
     * Remember to call {@link #clearStyles()} to clear the applied styles, so the default theme
     * may be restored.
     *
     * @param theme The style to use for resource resolution in addition to the the default theme
     *      and the styles applied earlier. If null, the operation is a no-op.
     * @param useAsPrimary If true, the {@code theme} is used first to resolve attributes. If
     *      false, the theme is used if the resource cannot be resolved using the default theme and
     *      all the themes that have been applied prior to this call.
     */
    public void applyStyle(StyleResourceValue theme, boolean useAsPrimary) {
    }

    /**
     * Clear all the themes applied with {@link #applyStyle(StyleResourceValue, boolean)}
     */
    public void clearStyles() {
    }

    /**
     * Returns a list of {@link StyleResourceValue} containing a list of themes to be used for
     * resolving resources. The order of the themes in the list specifies the order in which they
     * should be used to resolve resources.
     */
    public List<StyleResourceValue> getAllThemes() {
       return null;
    }

    /**
     * Returns a theme by its name.
     *
     * @deprecated Use {@link #getStyle(ResourceReference)}
     */
    @Deprecated
    public StyleResourceValue getTheme(String name, boolean frameworkTheme) {
        return getStyle(
                new ResourceReference(
                        ResourceNamespace.fromBoolean(frameworkTheme), ResourceType.STYLE, name));
    }

    /**
     * Returns whether a theme is a parent of a given theme.
     * @param parentTheme the parent theme
     * @param childTheme the child theme.
     * @return true if the parent theme is indeed a parent theme of the child theme.
     */
    public boolean themeIsParentOf(StyleResourceValue parentTheme, StyleResourceValue childTheme) {
        return false;
    }

    /**
     * Returns a framework resource by type and name. The returned resource is resolved.
     *
     * @param resourceType the type of the resource
     * @param resourceName the name of the resource
     * @deprecated Use {@link #getResolvedResource(ResourceReference)}
     */
    @Deprecated
    public ResourceValue getFrameworkResource(ResourceType resourceType, String resourceName) {
        return getResolvedResource(
                new ResourceReference(ResourceNamespace.ANDROID, resourceType, resourceName));
    }

    /**
     * Returns a project resource by type and name. The returned resource is resolved.
     *
     * @param resourceType the type of the resource
     * @param resourceName the name of the resource
     * @deprecated Use {@link #getResolvedResource(ResourceReference)}
     */
    @Deprecated
    public ResourceValue getProjectResource(ResourceType resourceType, String resourceName) {
        return getResolvedResource(
                new ResourceReference(ResourceNamespace.RES_AUTO, resourceType, resourceName));
    }

    /**
     * Returns the {@link ResourceValue} for a given attr in the all themes returned by {@link
     * #getAllThemes()}. If the item is not directly available in the a theme, its parent theme is
     * used before checking the next theme from the list.
     */
    @Nullable
    public ResourceValue findItemInTheme(@NonNull ResourceReference attr) {
        List<StyleResourceValue> allThemes = getAllThemes();
        if (allThemes == null) {
            return null;
        }
        for (StyleResourceValue theme : allThemes) {
            ResourceValue value = findItemInStyle(theme, attr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** @deprecated Use {@link #findItemInTheme(ResourceReference)}. */
    @Nullable
    @Deprecated
    public final ResourceValue findItemInTheme(String attrName, boolean isFrameworkAttr) {
        return findItemInTheme(
                new ResourceReference(
                        ResourceNamespace.fromBoolean(isFrameworkAttr),
                        ResourceType.ATTR,
                        attrName));
    }

    /**
     * Returns the {@link ResourceValue} matching a given attribute in a given style. If the item is
     * not directly available in the style, the method looks in its parent style.
     */
    @Nullable
    public ResourceValue findItemInStyle(
            @NonNull StyleResourceValue style, @NonNull ResourceReference attr) {
        return null;
    }

    /** @deprecated Use {@link #findItemInStyle(StyleResourceValue, ResourceReference)}. */
    @Nullable
    @Deprecated
    public final ResourceValue findItemInStyle(
            StyleResourceValue style, String attrName, boolean isFrameworkAttr) {
        return findItemInStyle(
                style,
                new ResourceReference(
                        ResourceNamespace.fromBoolean(isFrameworkAttr),
                        ResourceType.ATTR,
                        attrName));
    }

    /**
     * @deprecated Use {@link #dereference(ResourceValue)} instead, to provide context necessary to
     *     handle namespaces correctly, like the "current" namespace or namespace prefix lookup
     *     logic. Alternatively, use {@link #getUnresolvedResource(ResourceReference)} or {@link
     *     #getResolvedResource(ResourceReference)} if you already know exactly what you're looking
     *     for.
     */
    @Nullable
    @Deprecated
    public final ResourceValue findResValue(
            @Nullable String reference, boolean forceFrameworkOnly) {
        if (reference == null) {
            return null;
        }

        // Type is ignored. We don't call setNamespaceLookup, because this method is called from code that's not namespace aware anyway.
        return dereference(
                new ResourceValue(
                        ResourceType.ID,
                        "com.android.ide.common.rendering.api.RenderResources",
                        reference,
                        forceFrameworkOnly));
    }

    /**
     * Searches for a {@link ResourceValue} referenced by the given value. This method doesn't
     * perform recursive resolution, so the returned {@link ResourceValue} (if not null) may be just
     * another reference.
     *
     * <p>References to theme attributes is supported and resolved against the theme from {@link
     * #getDefaultTheme()}. For more details see <a
     * href="https://developer.android.com/guide/topics/resources/accessing-resources.html#ReferencesToThemeAttributes">Referencing
     * style attributes</a>
     *
     * <p>Unlike {@link #resolveResValue(ResourceValue)}, this method returns null if the input is
     * not a reference (i.e. doesn't start with '@' or '?').
     *
     * @param resourceValue the value to dereference. Its namespace and namespace lookup logic are
     *     used to handle namespaces when interpreting the textual value. The type is ignored and
     *     will not affect the type of the returned value.
     * @see #resolveResValue(ResourceValue)
     */
    @Nullable
    public ResourceValue dereference(@NonNull ResourceValue resourceValue) {
        return null;
    }

    /** Returns a resource by namespace, type and name. The returned resource is unresolved. */
    @Nullable
    public ResourceValue getUnresolvedResource(ResourceReference reference) {
        return null;
    }

    /**
     * Returns a resource by namespace, type and name. The returned resource is resolved, as defined
     * in {@link #resolveResValue(ResourceValue)}.
     *
     * @see #resolveResValue(ResourceValue)
     */
    @Nullable
    public ResourceValue getResolvedResource(ResourceReference reference) {
        ResourceValue referencedValue = getUnresolvedResource(reference);
        if (referencedValue == null) {
            return null;
        }

        return resolveResValue(referencedValue);
    }

    /**
     * Context used when dealing with old layoutlib code.
     *
     * <p>Historically we assumed that "tools:" means {@link SdkConstants#TOOLS_URI}, so need to
     * keep doing that, at least in non-namespaced projects.
     *
     * @see #resolveValue(ResourceType, String, String, boolean)
     */
    @Deprecated
    private static final ResourceNamespace.Resolver LEGACY_TOOLS_RESOLVER =
            Collections.singletonMap(SdkConstants.TOOLS_NS_NAME, SdkConstants.TOOLS_URI)::get;

    /**
     * Kept for layoutlib. Remove ASAP.
     *
     * @deprecated Use {@link #resolveResValue(ResourceValue)}
     */
    @Nullable
    @Deprecated
    public ResourceValue resolveValue(
            ResourceType type, String name, String value, boolean isFrameworkValue) {
        ResourceValue resourceValue = new ResourceValue(type, name, value, isFrameworkValue);
        resourceValue.setNamespaceLookup(LEGACY_TOOLS_RESOLVER);
        return resolveResValue(resourceValue);
    }

    /**
     * Returns the "final" {@link ResourceValue} referenced by the value of <var>value</var>.
     *
     * <p>This method ensures that the returned {@link ResourceValue} object is not a valid
     * reference to another resource. It may be just a leaf value (e.g. "#000000") or a reference
     * that could not be dereferenced.
     *
     * <p>If a value that does not need to be resolved is given, the method will return the input
     * value.
     *
     * @param value the value containing the reference to resolve.
     * @return a {@link ResourceValue} object or <code>null</code>
     */
    @Nullable
    public ResourceValue resolveResValue(@Nullable ResourceValue value) {
        return null;
    }

    /**
     * Returns the parent style of the given style, if any
     * @param style the style to look up
     * @return the parent style, or null
     */
    public StyleResourceValue getParent(StyleResourceValue style) {
        return null;
    }

    /**
     * Returns the style matching the given name. The name should not contain any namespace prefix.
     *
     * @param styleName Name of the style. For example, "Widget.ListView.DropDown".
     * @return the {link StyleResourceValue} for the style, or null if not found.
     * @deprecated Use {@link #getStyle(ResourceReference)}
     */
    @Deprecated
    public final StyleResourceValue getStyle(String styleName, boolean isFramework) {
        return getStyle(
                new ResourceReference(
                        ResourceNamespace.fromBoolean(isFramework), ResourceType.STYLE, styleName));
    }

    /** Returns the style matching the given reference. */
    @Nullable
    public StyleResourceValue getStyle(@NonNull ResourceReference reference) {
        return null;
    }
}
