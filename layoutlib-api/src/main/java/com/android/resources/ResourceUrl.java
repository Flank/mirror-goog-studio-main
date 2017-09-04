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
package com.android.resources;

import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_EMPTY;
import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_NULL;
import static com.android.ide.common.rendering.api.RenderResources.REFERENCE_UNDEFINED;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import java.io.Serializable;
import java.util.Objects;

/**
 * A {@linkplain ResourceUrl} represents a parsed resource url such as {@code @string/foo} or {@code
 * ?android:attr/bar}
 */
@Immutable
public class ResourceUrl implements Serializable {
    /** Type of resource */
    @NonNull public final ResourceType type;

    /** Name of resource */
    @NonNull public final String name;

    /** The namespace, or null if it's in the project namespace */
    @Nullable public final String namespace;

    /** If true, the resource is in the android: framework */
    public final boolean framework;

    /** Whether an id resource is of the form {@code @+id} rather than just {@code @id} */
    public final boolean create;

    /** Whether this is a theme resource reference */
    public final boolean theme;

    private ResourceUrl(
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String namespace,
            boolean framework,
            boolean create,
            boolean theme) {
        if (name.isEmpty() && type != ResourceType.PUBLIC) {
            throw new IllegalArgumentException("Resource name cannot be empty.");
        }

        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace provided but it's an empty string.");
        }

        if (create && theme) {
            throw new IllegalArgumentException(
                    "Both `create` and `theme` cannot be used at the same time.");
        }

        this.type = type;
        this.name = name;
        this.framework = framework;
        this.namespace = namespace;
        this.create = create;
        this.theme = theme;
    }

    /**
     * Creates a new resource URL, representing "@type/name" or "@android:type/name".
     *
     * @see #parse(String)
     * @param type the resource type
     * @param name the name
     * @param framework whether it's a framework resource
     * @deprecated This factory method is used where we have no way of knowing the namespace. We
     *     need to migrate every call site to the other factory method that takes a namespace.
     */
    @Deprecated
    public static ResourceUrl create(
            @NonNull ResourceType type, @NonNull String name, boolean framework) {
        return new ResourceUrl(
                type,
                name,
                framework ? SdkConstants.ANDROID_NS_NAME : null,
                framework,
                false,
                false);
    }

    /**
     * Creates a new resource URL, representing "@namespace:type/name".
     *
     * @see #parse(String)
     * @param namespace the resource namespace
     * @param type the resource type
     * @param name the name
     */
    public static ResourceUrl create(
            @Nullable String namespace, @NonNull ResourceType type, @NonNull String name) {
        return new ResourceUrl(
                type,
                name,
                namespace,
                SdkConstants.ANDROID_NS_NAME.equals(namespace),
                false,
                false);
    }

    /**
     * Return the resource type of the given url, and the resource name
     *
     * @param url the resource url to be parsed
     * @return a pair of the resource type and the resource name
     */
    @Nullable
    public static ResourceUrl parse(@NonNull String url) {
        return parse(url, false);
    }

    /**
     * Return the resource type of the given url, and the resource name.
     *
     * @param url the resource url to be parsed
     * @param forceFramework force the returned value to be a framework resource.
     * @return a pair of the resource type and the resource name
     */
    @Nullable
    public static ResourceUrl parse(@NonNull String url, boolean forceFramework) {
        boolean isTheme = false;
        // Handle theme references
        if (url.startsWith(SdkConstants.PREFIX_THEME_REF)) {
            isTheme = true;
            String remainder = url.substring(SdkConstants.PREFIX_THEME_REF.length());
            if (url.startsWith(SdkConstants.ATTR_REF_PREFIX)) {
                url =
                        SdkConstants.PREFIX_RESOURCE_REF
                                + url.substring(SdkConstants.PREFIX_THEME_REF.length());
            } else {
                int colon = url.indexOf(':');
                if (colon != -1) {
                    // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
                    if (remainder.indexOf('/', colon) == -1) {
                        remainder =
                                remainder.substring(0, colon)
                                        + SdkConstants.RESOURCE_CLZ_ATTR
                                        + '/'
                                        + remainder.substring(colon);
                    }
                    url = SdkConstants.PREFIX_RESOURCE_REF + remainder;
                } else {
                    int slash = url.indexOf('/');
                    if (slash == -1) {
                        url =
                                SdkConstants.PREFIX_RESOURCE_REF
                                        + SdkConstants.RESOURCE_CLZ_ATTR
                                        + '/'
                                        + remainder;
                    }
                }
            }
        }

        if (!url.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || isNullOrEmpty(url)) {
            return null;
        }

        int typeEnd = url.indexOf('/', 1);
        if (typeEnd == -1) {
            return null;
        }
        int nameBegin = typeEnd + 1;

        // Skip @ and @+
        boolean create = url.startsWith("@+");
        int typeBegin = create ? 2 : 1;

        int colon = url.lastIndexOf(':', typeEnd);
        boolean framework = forceFramework;
        String namespace = forceFramework ? SdkConstants.ANDROID_NS_NAME : null;
        if (colon != -1) {
            if (url.startsWith(SdkConstants.ANDROID_NS_NAME, typeBegin)) {
                framework = true;
                namespace = SdkConstants.ANDROID_NS_NAME;
            } else {
                namespace = url.substring(typeBegin, colon);
            }
            typeBegin = colon + 1;
        }
        String typeName = url.substring(typeBegin, typeEnd);
        ResourceType type = ResourceType.getEnum(typeName);
        if (type == null) {
            return null;
        }
        String name = url.substring(nameBegin);
        if (name.isEmpty()) {
            return null;
        }
        return new ResourceUrl(type, name, namespace, framework, create, isTheme);
    }

    /** Returns if the resource url is @null, @empty or @undefined. */
    public static boolean isNullOrEmpty(@NonNull String url) {
        return url.equals(REFERENCE_NULL) || url.equals(REFERENCE_EMPTY) ||
                url.equals(REFERENCE_UNDEFINED);
    }

    /**
     * Checks whether this resource has a valid name. Used when parsing data that isn't
     * necessarily known to be a valid resource; for example, "?attr/hello world"
     */
    public boolean hasValidName() {
        // Make sure it looks like a resource name; if not, it could just be a string
        // which starts with a ?, etc.
        if (name.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1, n = name.length(); i < n; i++) {
            char c = name.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && c != '.') {
                // Sample data allows for extra characters
                if (type != ResourceType.SAMPLE_DATA
                        || (c != '/' && c != '[' && c != ']' && c != ':')) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Creates a copy of this {@link ResourceUrl} with the {@code framework} field set to the given
     * value.
     */
    @NonNull
    public ResourceUrl withFramework(boolean isFramework) {
        return new ResourceUrl(type, name, namespace, isFramework, create, theme);
    }

    /** Creates a copy of this {@link ResourceUrl} with the {@code theme} field set to true. */
    @NonNull
    public ResourceUrl asThemeUrl() {
        return new ResourceUrl(type, name, namespace, framework, create, true);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(theme ? SdkConstants.PREFIX_THEME_REF : SdkConstants.PREFIX_RESOURCE_REF);
        if (create) {
            sb.append('+');
        }
        if (framework) {
            sb.append(SdkConstants.ANDROID_NS_NAME);
            sb.append(':');
        }
        sb.append(type.getName());
        sb.append('/');
        sb.append(name);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResourceUrl that = (ResourceUrl) o;
        return framework == that.framework
                && create == that.create
                && theme == that.theme
                && type == that.type
                && Objects.equals(name, that.name)
                && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, namespace, framework, create, theme);
    }
}
