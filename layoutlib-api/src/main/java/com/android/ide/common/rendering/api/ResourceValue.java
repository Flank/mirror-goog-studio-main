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
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents an android resource with a name and a string value.
 */
public class ResourceValue extends ResourceReference {
    @Nullable private final String mLibraryName;
    @Nullable protected String mValue;

    @NonNull
    private Function<String, String> mNamespaceLookup = ResourceNamespace.EMPTY_NAMESPACE_CONTEXT;

    /**
     * Constructor still used by layoutlib. Remove ASAP.
     *
     * @deprecated Use {@link #ResourceValue(ResourceType, String, String, boolean)}
     */
    @Deprecated
    public ResourceValue(
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String value,
            boolean isFramework) {
        this(ResourceNamespace.fromBoolean(isFramework), type, name, value);
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
        super(namespace, type, name);
        mValue = value;
        mLibraryName = libraryName;
    }

    /**
     * Returns the name of the library where this resource was found or null if it is not from a library.
     */
    public String getLibraryName() {
        return mLibraryName;
    }

    /**
     * Returns true if the resource is user defined.
     */
    public boolean isUserDefined() {
        // TODO: namespaces
        return !isFramework() && mLibraryName == null;
    }

    /**
     * Returns the value of the resource, as defined in the XML. This can be <code>null</code>,
     * for example for instances of {@link StyleResourceValue}.
     */
    @Nullable
    public String getValue() {
        return mValue;
    }

    /**
     * If this {@link ResourceValue} references another one, returns a {@link ResourceReference} to
     * it, otherwise null.
     *
     * <p>This method should be called before inspecting the textual value ({@link #getValue}), as
     * it handles namespaces correctly.
     *
     * <p>TODO(namespaces): Use this in ResourceResolver.
     */
    @Nullable
    public ResourceReference getReference() {
        if (mValue == null) {
            return null;
        }

        ResourceUrl url = ResourceUrl.parse(mValue);
        if (url == null) {
            return null;
        }

        return url.resolve(getNamespace(), mNamespaceLookup);
    }

    /**
     * Similar to {@link #getValue()}, but returns the raw XML value. This is <b>usually</b>
     * the same as getValue, but with a few exceptions. For example, for markup strings,
     * you can have * {@code <string name="markup">This is <b>bold</b></string>}.
     * Here, {@link #getValue()} will return "{@code This is bold}" -- e.g. just
     * the plain text flattened. However, this method will return "{@code This is <b>bold</b>}",
     * which preserves the XML markup elements.
     */
    public String getRawXmlValue() {
        return getValue();
    }

    /**
     * Sets the value of the resource.
     * @param value the new value
     */
    public void setValue(String value) {
        mValue = value;
    }

    /**
     * Sets the value from another resource.
     * @param value the resource value
     */
    public void replaceWith(ResourceValue value) {
        mValue = value.mValue;
    }

    /**
     * Specifies logic used to resolve namespace aliases for values that come from XML files.
     *
     * <p>This method is meant to be called by the XML parser that created this {@link
     * ResourceValue}.
     */
    public void setNamespaceLookup(@NonNull Function<String, String> namespaceLookup) {
        this.mNamespaceLookup = namespaceLookup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ResourceValue that = (ResourceValue) o;

        return Objects.equals(mLibraryName, that.mLibraryName)
                && Objects.equals(mValue, that.mValue);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(mLibraryName);
        result = 31 * result + Objects.hashCode(mValue);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespace", getNamespace())
                .add("type", getResourceType())
                .add("name", getName())
                .add("value", mValue)
                .toString();
    }
}
