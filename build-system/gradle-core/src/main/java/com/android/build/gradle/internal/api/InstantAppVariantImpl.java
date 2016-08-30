/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.InstantAppVariant;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.InstallableVariantData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.builder.core.AndroidBuilder;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.Collection;

/**
 * Implementation of the {@link InstantAppVariant} interface around a
 * {@link InstantAppVariantData} object.
 */
public class InstantAppVariantImpl extends InstallableVariantImpl implements InstantAppVariant {

    @NonNull
    private final InstantAppVariantData variantData;

    public InstantAppVariantImpl(
            @NonNull InstantAppVariantData variantData,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        super(androidBuilder, readOnlyObjectProvider);
        this.variantData = variantData;
    }

    @NonNull
    @Override
    protected InstallableVariantData getInstallableVariantData() {
        return variantData;
    }

    @Nullable
    @Override
    public String getVersionName() {
        return getVariantData().getVariantConfiguration().getVersionName();
    }

    @Override
    public int getVersionCode() {
        return getVariantData().getVariantConfiguration().getVersionCode();
    }

    @NonNull
    @Override
    protected BaseVariantData<?> getVariantData() {
        return variantData;
    }
}
