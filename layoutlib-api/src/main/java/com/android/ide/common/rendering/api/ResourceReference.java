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

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.google.common.base.MoreObjects;
import java.io.Serializable;

/**
 * A resource reference, contains the namespace, type and name. Can be used to look for resources in
 * a resource repository.
 *
 * <p>This is an immutable class.
 */
@Immutable
public class ResourceReference implements Serializable {
    @NonNull private final ResourceType mResourceType;
    @NonNull private final ResourceNamespace mNamespace;
    @NonNull private final String mName;

    public ResourceReference(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType resourceType,
            @NonNull String name) {
        this.mNamespace = namespace;
        this.mResourceType = resourceType;
        this.mName = name;
    }

    /**
     * Used by layoutlib still. TODO(namespaces): remove this.
     *
     * @deprecated
     */
    @Deprecated
    public ResourceReference(@NonNull String name, boolean isFramework) {
        this(ResourceNamespace.fromBoolean(isFramework), ResourceType.LAYOUT, name);
    }

    @Deprecated
    public ResourceReference(
            @NonNull ResourceType type, @NonNull String name, boolean isFramework) {
        this(ResourceNamespace.fromBoolean(isFramework), type, name);
    }

    /** Returns the name of the resource, as defined in the XML. */
    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public ResourceType getResourceType() {
        return mResourceType;
    }

    @NonNull
    public ResourceNamespace getNamespace() {
        return mNamespace;
    }

    /**
     * Returns whether the resource is a framework resource (<code>true</code>) or a project
     * resource (<code>false</code>).
     *
     * @deprecated all namespaces should be handled not just "android:".
     */
    @Deprecated
    public final boolean isFramework() {
        return mNamespace == ResourceNamespace.ANDROID;
    }

    @NonNull
    public ResourceUrl getResourceUrl() {
        return ResourceUrl.create(mNamespace.getPackageName(), mResourceType, mName);
    }

    /**
     * Returns a {@link ResourceUrl} that can be used to refer to this resource from the given
     * namespace. This means the namespace part of the {@link ResourceUrl} will be null if the
     * context namespace is the same as the namespace of this resource.
     *
     * <p>This method assumes no namespace prefixes (aliases) are defined, so the returned {@link
     * ResourceUrl} will use the full package name of the target namespace, if necessary. Most use
     * cases should attempt to call the overloaded method instead and provide a {@link
     * ResourceNamespace.Resolver} from the XML element where the {@link ResourceUrl} will be used.
     *
     * @see #getRelativeResourceUrl(ResourceNamespace, ResourceNamespace.Resolver)
     */
    @NonNull
    public ResourceUrl getRelativeResourceUrl(@NonNull ResourceNamespace context) {
        return getRelativeResourceUrl(context, ResourceNamespace.Resolver.EMPTY_RESOLVER);
    }

    /**
     * Returns a {@link ResourceUrl} that can be used to refer to this resource from the given
     * namespace. This means the namespace part of the {@link ResourceUrl} will be null if the
     * context namespace is the same as the namespace of this resource.
     *
     * <p>This method uses the provided {@link ResourceNamespace.Resolver} to find the short prefix
     * that can be used to refer to the target namespace. If it is not found, the full package name
     * is used.
     */
    @NonNull
    public ResourceUrl getRelativeResourceUrl(
            @NonNull ResourceNamespace context, @NonNull ResourceNamespace.Resolver resolver) {
        String namespaceString;
        if (mNamespace.equals(context)) {
            namespaceString = null;
        } else {
            String prefix = resolver.uriToPrefix(mNamespace.getXmlNamespaceUri());
            if (prefix != null) {
                namespaceString = prefix;
            } else {
                namespaceString = mNamespace.getPackageName();
            }
        }

        return ResourceUrl.create(namespaceString, mResourceType, mName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResourceReference reference = (ResourceReference) o;

        if (mResourceType != reference.mResourceType) return false;
        if (!mNamespace.equals(reference.mNamespace)) return false;
        if (!mName.equals(reference.mName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mResourceType.hashCode();
        result = 31 * result + mNamespace.hashCode();
        result = 31 * result + mName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespace", mNamespace)
                .add("type", mResourceType)
                .add("name", mName)
                .toString();
    }
}
