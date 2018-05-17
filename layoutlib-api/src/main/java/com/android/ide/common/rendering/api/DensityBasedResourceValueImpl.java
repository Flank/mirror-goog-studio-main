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
import com.android.resources.Density;
import com.android.resources.ResourceType;

/**
 * This class will replace {@link DensityBasedResourceValue} when the latter becomes an interface.
 */
public class DensityBasedResourceValueImpl extends DensityBasedResourceValue {
    public DensityBasedResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @NonNull Density mDensity) {
        super(reference, value, mDensity);
    }

    public DensityBasedResourceValueImpl(
            @NonNull ResourceReference reference,
            @Nullable String value,
            @NonNull Density mDensity,
            @Nullable String libraryName) {
        super(reference, value, mDensity, libraryName);
    }

    public DensityBasedResourceValueImpl(
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @NonNull String name,
            @Nullable String value,
            @NonNull Density mDensity,
            @Nullable String libraryName) {
        super(namespace, type, name, value, mDensity, libraryName);
    }
}
