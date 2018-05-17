/*
 * Copyright (C) 2018 The Android Open Source Project
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

/** Temporary interface that will be removed after {@link ResourceValue} becomes an interface. */
public interface ResourceValueTemp {
    @NonNull
    ResourceType getResourceType();

    @NonNull
    ResourceNamespace getNamespace();

    @NonNull
    String getName();

    @Nullable
    String getLibraryName();

    boolean isUserDefined();

    boolean isFramework();

    @Nullable
    String getValue();

    @NonNull
    ResourceReference asReference();

    @NonNull
    ResourceUrl getResourceUrl();

    @Nullable
    ResourceReference getReference();

    String getRawXmlValue();

    void setValue(@Nullable String value);

    void replaceWith(@NonNull ResourceValue value);

    @NonNull
    ResourceNamespace.Resolver getNamespaceResolver();

    void setNamespaceResolver(@NonNull ResourceNamespace.Resolver resolver);

    @Deprecated // TODO(namespaces): Called by layoutlib.
    void setNamespaceLookup(@NonNull ResourceNamespace.Resolver resolver);
}
