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
import java.io.Serializable;

/** Represents an Android resource with a name and a string value. */
public interface ResourceValue extends Serializable {
    @NonNull
    ResourceType getResourceType();

    @NonNull
    ResourceNamespace getNamespace();

    @NonNull
    String getName();

    /**
     * Returns the name of the library where this resource was found or null if it is not from a
     * library.
     */
    @Nullable
    String getLibraryName();

    /** Returns true if the resource is user defined. */
    boolean isUserDefined();

    boolean isFramework();

    /**
     * Returns the value of the resource, as defined in the XML. This can be null, for example for
     * instances of {@link StyleResourceValue}.
     */
    @Nullable
    String getValue();

    @NonNull
    ResourceReference asReference();

    @NonNull
    ResourceUrl getResourceUrl();

    /**
     * If this {@link ResourceValue} references another one, returns a {@link ResourceReference} to
     * it, otherwise null.
     *
     * <p>This method should be called before inspecting the textual value ({@link #getValue}), as
     * it handles namespaces correctly.
     */
    @Nullable
    ResourceReference getReference();

    /**
     * Similar to {@link #getValue()}, but returns the raw XML value. This is <b>usually</b> the
     * same as getValue, but with a few exceptions. For example, for markup strings, you can have
     * {@code <string name="markup">This is <b>bold</b></string>}. Here, {@link #getValue()} will
     * return "{@code This is bold}" -- e.g. just the plain text flattened. However, this method
     * will return "{@code This is <b>bold</b>}", which preserves the XML markup elements.
     */
    @Nullable
    String getRawXmlValue();

    /**
     * Sets the value of the resource.
     *
     * @param value the new value
     */
    void setValue(@Nullable String value);

    /**
     * Sets the value from another resource.
     *
     * @param value the resource value
     */
    void replaceWith(@NonNull ResourceValue value);

    @NonNull
    ResourceNamespace.Resolver getNamespaceResolver();

    /**
     * Specifies logic used to resolve namespace aliases for values that come from XML files.
     *
     * <p>This method is meant to be called by the XML parser that created this ResourceValue.
     */
    void setNamespaceResolver(@NonNull ResourceNamespace.Resolver resolver);
}
