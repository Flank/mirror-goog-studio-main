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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.utils.HashCodes;
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

    @NonNull public final UrlType urlType;

    /** If true, the resource is in the android: framework */
    public boolean isFramework() {
        return SdkConstants.ANDROID_NS_NAME.equals(namespace);
    }

    /** Whether an id resource is of the form {@code @+id} rather than just {@code @id} */
    public boolean isCreate() {
        return urlType == UrlType.CREATE;
    }

    /** Whether this is a theme resource reference */
    public boolean isTheme() {
        return urlType == UrlType.THEME;
    }

    public enum UrlType {
        /** Reference of the form {@code @string/foo}. */
        NORMAL,

        /** Reference of the form {@code @+id/foo}. */
        CREATE,

        /** Reference of the form {@code ?android:textColor}. */
        THEME,

        /** Reference of the form {@code android:textColor}. */
        ATTR,
    }

    private ResourceUrl(
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String namespace,
            @NonNull UrlType urlType) {
        this.type = type;
        this.name = name;
        this.namespace = namespace;
        this.urlType = urlType;
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
    @Deprecated // TODO: namespaces
    public static ResourceUrl create(
            @NonNull ResourceType type, @NonNull String name, boolean framework) {
        return new ResourceUrl(
                type, name, framework ? SdkConstants.ANDROID_NS_NAME : null, UrlType.NORMAL);
    }

    /**
     * Creates a new resource URL, representing "@namespace:type/name".
     *
     * @see #parse(String)
     * @param namespace the resource namespace
     * @param type the resource type
     * @param name the name
     */
    @NonNull
    public static ResourceUrl create(
            @Nullable String namespace, @NonNull ResourceType type, @NonNull String name) {
        return new ResourceUrl(type, name, namespace, UrlType.NORMAL);
    }

    /**
     * Creates a new resource URL, representing "?namespace:type/name".
     *
     * @see #parse(String)
     * @param namespace the resource namespace
     * @param type the resource type
     * @param name the name
     */
    @NonNull
    public static ResourceUrl createThemeReference(
            @Nullable String namespace, @NonNull ResourceType type, @NonNull String name) {
        return new ResourceUrl(type, name, namespace, UrlType.THEME);
    }

    /**
     * Creates a new resource URL, representing "namespace:name".
     *
     * @see #parse(String)
     * @param namespace the resource namespace
     * @param name the name
     */
    @NonNull
    public static ResourceUrl createAttrReference(
            @Nullable String namespace, @NonNull String name) {
        return new ResourceUrl(ResourceType.ATTR, name, namespace, UrlType.ATTR);
    }

    /**
     * Returns a {@link ResourceUrl} representation of the given string, or null if it's not a valid
     * resource reference. This method works only for strings of type {@link UrlType#NORMAL}, {@link
     * UrlType#CREATE} and {@link UrlType#THEME}, see dedicated methods for parsing references to
     * style parents and to {@code attr} resources in the {@code name} XML attribute of style items.
     *
     * @param url the resource url to be parsed
     * @return a pair of the resource type and the resource name
     */
    @Nullable
    public static ResourceUrl parse(@NonNull String url) {
        return parse(url, false);
    }

    /**
     * Returns a {@link ResourceUrl} representation of the given string, or null if it's not a valid
     * resource reference. This method works only for strings of type {@link UrlType#NORMAL}, {@link
     * UrlType#CREATE} and {@link UrlType#THEME}, see dedicated methods for parsing references to
     * style parents and to {@code attr} resources in the {@code name} XML attribute of style items.
     *
     * @param url the resource url to be parsed
     * @param forceFramework force the returned value to be a framework resource.
     *     <p>TODO(namespaces): remove the forceFramework argument.
     */
    @Nullable
    public static ResourceUrl parse(@NonNull String url, boolean forceFramework) {
        UrlType urlType = UrlType.NORMAL;
        // Handle theme references
        if (url.startsWith(SdkConstants.PREFIX_THEME_REF)) {
            urlType = UrlType.THEME;
            String remainder = url.substring(SdkConstants.PREFIX_THEME_REF.length());
            if (url.startsWith(SdkConstants.ATTR_REF_PREFIX)) {
                url =
                        SdkConstants.PREFIX_RESOURCE_REF
                                + url.substring(SdkConstants.PREFIX_THEME_REF.length());
            } else {
                int colon = url.indexOf(':');
                if (colon >= 0) {
                    // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
                    if (remainder.indexOf('/', colon) < 0) {
                        remainder =
                                remainder.substring(0, colon)
                                        + SdkConstants.RESOURCE_CLZ_ATTR
                                        + '/'
                                        + remainder.substring(colon);
                    }
                    url = SdkConstants.PREFIX_RESOURCE_REF + remainder;
                } else {
                    int slash = url.indexOf('/');
                    if (slash < 0) {
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
        if (typeEnd < 0) {
            return null;
        }
        int nameBegin = typeEnd + 1;

        // Skip @ and @+
        int typeBegin;
        if (url.startsWith("@+")) {
            urlType = UrlType.CREATE;
            typeBegin = 2;
        } else {
            typeBegin = 1;
        }

        int colon = url.lastIndexOf(':', typeEnd);
        String namespace = forceFramework ? SdkConstants.ANDROID_NS_NAME : null;
        if (colon >= 0) {
            if (colon - typeBegin == SdkConstants.ANDROID_NS_NAME.length()
                    && url.startsWith(SdkConstants.ANDROID_NS_NAME, typeBegin)) {
                namespace = SdkConstants.ANDROID_NS_NAME;
            } else {
                namespace = url.substring(typeBegin, colon);
                if (namespace.isEmpty()) {
                    return null;
                }
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
        return new ResourceUrl(type, name, namespace, urlType);
    }

    /**
     * Returns a {@link ResourceUrl} representation of the given reference to an {@code attr}
     * resources, most likely the contents of {@code <item name="..." >}.
     */
    @Nullable
    public static ResourceUrl parseAttrReference(@NonNull String input) {
        String namespace = null;
        String name;

        if (input.charAt(0) == '@' || input.charAt(0) == '?') {
            return null;
        }

        if (input.indexOf('/') != -1) {
            return null;
        }

        int colon = input.indexOf(':');
        if (colon == -1) {
            name = input;
        } else {
            namespace = input.substring(0, colon);
            if (namespace.isEmpty()) {
                return null;
            }
            name = input.substring(colon + 1);
        }

        if (name.isEmpty()) {
            return null;
        }

        return new ResourceUrl(ResourceType.ATTR, name, namespace, UrlType.ATTR);
    }

    /** Returns a {@link ResourceUrl} representation of the given reference to a style's parent. */
    @Nullable
    public static ResourceUrl parseStyleParentReference(@NonNull String input) {
        if (input.isEmpty()) {
            return null;
        }

        int pos = 0;

        if (input.charAt(pos) == '@' || input.charAt(pos) == '?') {
            pos++;
        }

        String namespace = null;
        int colon = input.indexOf(':', pos);
        if (colon != -1) {
            namespace = input.substring(pos, colon);
            if (namespace.isEmpty()) {
                return null;
            }
            pos = colon + 1;
        }

        int slash = input.indexOf('/', pos);
        if (slash != -1) {
            if (!input.startsWith(SdkConstants.REFERENCE_STYLE, pos)) {
                // Wrong resource type used.
                return null;
            }

            pos = slash + 1;
        }

        String name = input.substring(pos);
        if (name.isEmpty()) {
            return null;
        }

        return new ResourceUrl(ResourceType.STYLE, name, namespace, UrlType.NORMAL);
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
        return isValidName(name, type);
    }

    public static boolean isValidName(@NonNull String input, @NonNull ResourceType type) {
        // TODO(namespaces): This (almost) duplicates ValueResourceNameValidator.

        // Make sure it looks like a resource name; if not, it could just be a string
        // which starts with a ?, etc.
        if (input.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(input.charAt(0))) {
            return false;
        }
        for (int i = 1, n = input.length(); i < n; i++) {
            char c = input.charAt(i);
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
     * Tries to resolve this {@link ResourceUrl} into a valid {@link ResourceReference} by expanding
     * the namespace alias (or lack thereof) based on the context in which this {@link ResourceUrl}
     * was used.
     *
     * @param contextNamespace aapt namespace of the module in which this URL was used
     * @param resolver logic for expanding namespaces aliases, most likely by walking up the XML
     *     tree.
     * @see ResourceNamespace#fromNamespacePrefix(String, ResourceNamespace,
     *     ResourceNamespace.Resolver)
     */
    @Nullable
    public ResourceReference resolve(
            @NonNull ResourceNamespace contextNamespace,
            @NonNull ResourceNamespace.Resolver resolver) {
        ResourceNamespace resolvedNamespace =
                ResourceNamespace.fromNamespacePrefix(this.namespace, contextNamespace, resolver);
        if (resolvedNamespace == null) {
            return null;
        }
        return new ResourceReference(resolvedNamespace, type, name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (urlType) {
            case NORMAL:
                sb.append(SdkConstants.PREFIX_RESOURCE_REF);
                break;
            case CREATE:
                sb.append("@+");
                break;
            case THEME:
                sb.append(SdkConstants.PREFIX_THEME_REF);
                break;
            case ATTR:
                // No prefix.
                break;
        }
        if (namespace != null) {
            sb.append(namespace);
            sb.append(':');
        }

        if (urlType != UrlType.ATTR) {
            sb.append(type.getName());
            sb.append('/');
        }

        sb.append(name);
        return sb.toString();
    }

    /**
     * Returns a short string representation, which includes just the namespace (if defined in this
     * {@link ResourceUrl} and name, separated by a colon. For example {@code
     * ResourceUrl.parse("@android:style/Theme").getQualifiedName()} returns {@code "android:Theme"}
     * and {@code ResourceUrl.parse("?myColor").getQualifiedName()} returns {@code "myColor"}.
     *
     * <p>This is used when the type is implicit, e.g. when specifying attribute for a style item or
     * a parent for a style.
     */
    @NonNull
    public String getQualifiedName() {
        if (namespace == null) {
            return name;
        } else {
            return namespace + ':' + name;
        }
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
        return urlType == that.urlType
                && type == that.type
                && Objects.equals(name, that.name)
                && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(
                type.hashCode(),
                Objects.hashCode(name),
                Objects.hashCode(namespace),
                urlType.hashCode());
    }
}
