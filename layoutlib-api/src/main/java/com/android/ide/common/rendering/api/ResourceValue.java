/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Objects;

/** Represents an Android resource with a name and a string value. */
public abstract class ResourceValue implements ResourceValueTemp, Serializable {
    @NonNull private final ResourceType resourceType;
    @NonNull private final ResourceNamespace namespace;
    @NonNull private final String name;

    @Nullable private final String libraryName;
    @Nullable private String value;

    @NonNull
    protected transient ResourceNamespace.Resolver mNamespaceResolver =
            ResourceNamespace.Resolver.EMPTY_RESOLVER;

    public ResourceValue(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String value) {
        this(namespace, type, name, value, null);
    }

    public ResourceValue(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String value,
            @Nullable String libraryName) {
        this.namespace = namespace;
        this.resourceType = type;
        this.name = name;
        this.value = value;
        this.libraryName = libraryName;
    }

    public ResourceValue(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @Nullable String libraryName) {
        this(
                reference.getNamespace(),
                reference.getResourceType(),
                reference.getName(),
                value,
                libraryName);
    }

    public ResourceValue(@NonNull ResourceReference reference, @Nullable String value) {
        this(reference, value, null);
    }

    @Override
    @NonNull
    public ResourceType getResourceType() {
        return resourceType;
    }

    @Override
    @NonNull
    public ResourceNamespace getNamespace() {
        return namespace;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Returns the name of the library where this resource was found or null if it is not from a
     * library.
     */
    @Override
    @Nullable
    public String getLibraryName() {
        return libraryName;
    }

    /** Returns true if the resource is user defined. */
    @Override
    public boolean isUserDefined() {
        // TODO: namespaces
        return !isFramework() && libraryName == null;
    }

    @Override
    public boolean isFramework() {
        // When transferring this accross the wire, the instance check won't be correct.
        return ResourceNamespace.ANDROID.equals(namespace);
    }

    /**
     * Returns the value of the resource, as defined in the XML. This can be <code>null</code>, for
     * example for instances of {@link StyleResourceValue}.
     */
    @Override
    @Nullable
    public String getValue() {
        return value;
    }

    @Override
    @NonNull
    public ResourceReference asReference() {
        return new ResourceReference(namespace, resourceType, name);
    }

    @Override
    @NonNull
    public ResourceUrl getResourceUrl() {
        return asReference().getResourceUrl();
    }

    /**
     * If this {@link ResourceValue} references another one, returns a {@link ResourceReference} to
     * it, otherwise null.
     *
     * <p>This method should be called before inspecting the textual value ({@link #getValue}), as
     * it handles namespaces correctly.
     */
    @Override
    @Nullable
    public ResourceReference getReference() {
        if (value == null) {
            return null;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null) {
            return null;
        }

        return url.resolve(getNamespace(), mNamespaceResolver);
    }

    /**
     * Similar to {@link #getValue()}, but returns the raw XML value. This is <b>usually</b> the
     * same as getValue, but with a few exceptions. For example, for markup strings, you can have
     * {@code <string name="markup">This is <b>bold</b></string>}. Here, {@link #getValue()} will
     * return "{@code This is bold}" -- e.g. just the plain text flattened. However, this method
     * will return "{@code This is <b>bold</b>}", which preserves the XML markup elements.
     */
    @Override
    public String getRawXmlValue() {
        return getValue();
    }

    /**
     * Sets the value of the resource.
     *
     * @param value the new value
     */
    @Override
    public void setValue(@Nullable String value) {
        this.value = value;
    }

    /**
     * Sets the value from another resource.
     *
     * @param value the resource value
     */
    @Override
    public void replaceWith(@NonNull ResourceValue value) {
        this.value = value.value;
    }

    @Override
    @NonNull
    public ResourceNamespace.Resolver getNamespaceResolver() {
        return mNamespaceResolver;
    }

    /**
     * Specifies logic used to resolve namespace aliases for values that come from XML files.
     *
     * <p>This method is meant to be called by the XML parser that created this {@link
     * ResourceValue}.
     */
    @Override
    public void setNamespaceResolver(@NonNull ResourceNamespace.Resolver resolver) {
        this.mNamespaceResolver = resolver;
    }

    @Override
    @Deprecated // TODO(namespaces): Called by layoutlib.
    public void setNamespaceLookup(@NonNull ResourceNamespace.Resolver resolver) {
        setNamespaceResolver(
                new ResourceNamespace.Resolver() {
                    @Nullable
                    @Override
                    public String uriToPrefix(@NonNull String namespaceUri) {
                        return null;
                    }

                    @Nullable
                    @Override
                    public String prefixToUri(@NonNull String namespacePrefix) {
                        return resolver.prefixToUri(namespacePrefix);
                    }
                });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResourceValue that = (ResourceValue) o;
        return resourceType == that.resourceType
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(name, that.name)
                && Objects.equals(libraryName, that.libraryName)
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(
                resourceType.hashCode(),
                namespace.hashCode(),
                name.hashCode(),
                Objects.hashCode(libraryName),
                Objects.hashCode(value));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespace", getNamespace())
                .add("type", getResourceType())
                .add("name", getName())
                .add("value", getValue())
                .toString();
    }
}
