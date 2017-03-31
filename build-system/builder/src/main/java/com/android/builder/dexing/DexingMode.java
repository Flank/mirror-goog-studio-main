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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.model.ApiVersion;

/**
 * The mode of dexing, which includes the {@link DexingType} and an optional minimum SDK version
 * associated with the dexing type.
 */
@Immutable
public final class DexingMode {

    @NonNull private final DexingType dexingType;

    /** The optional minimum SDK version associated with the dexing type. */
    @Nullable private final ApiVersion minSdkVersion;

    public DexingMode(@NonNull DexingType dexingType) {
        this.dexingType = dexingType;
        this.minSdkVersion = null;
    }

    public DexingMode(@NonNull DexingType dexingType, @NonNull ApiVersion minSdkVersion) {
        this.dexingType = dexingType;
        this.minSdkVersion = minSdkVersion;
    }

    @NonNull
    public DexingType getDexingType() {
        return dexingType;
    }

    public boolean isMultiDex() {
        return dexingType.isMultiDex();
    }

    public boolean isPreDex() {
        return dexingType.isPreDex();
    }

    /** Returns the optional minimum SDK version associated with the dexing type. */
    @Nullable
    public ApiVersion getMinSdkVersion() {
        return minSdkVersion;
    }

    /**
     * Returns the optional minimum SDK version associated with the dexing type as an integer value.
     */
    @Nullable
    public Integer getMinSdkVersionValue() {
        return minSdkVersion != null ? DefaultApiVersion.getFeatureLevel(minSdkVersion) : null;
    }
}
