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
import com.android.dx.command.dexer.DxContext;

/**
 * Config for dex archive merging. Contains all options necessary to configure {@link
 * DexArchiveMerger} which produces the final DEX file(s).
 */
public class DexMergerConfig {

    @NonNull private final DexingMode dexingMode;
    @NonNull private final DxContext dxContext;

    public DexMergerConfig(@NonNull DexingMode dexingMode, @NonNull DxContext dxContext) {
        this.dexingMode = dexingMode;
        this.dxContext = dxContext;
    }

    @NonNull
    public DexingMode getDexingMode() {
        return dexingMode;
    }

    @NonNull
    public DxContext getDxContext() {
        return dxContext;
    }
}
