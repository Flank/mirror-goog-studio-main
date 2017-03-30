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
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;

/**
 * Configuration object used to setup dx when creating a dex archive. It contains information about
 * the number of parallel .class to .dex file conversions. Also, it specifies if the generated dex
 * should be optimized, or using larger opcodes (jumbo mode).
 *
 * <p>Currently, there is a limited set of options to configure, and if necessary, other can be
 * added in the future.
 */
public class DexArchiveBuilderConfig {

    /** Number of threads to use when creating the dex archive. */
    private final int numThreads;

    @NonNull private final DxContext dxContext;

    @NonNull private final DexOptions dexOptions;
    @NonNull private final CfOptions cfOptions;

    /**
     * Creates a configuration object used to set up the dex archive conversion.
     *
     * @param numThreads number of .class to .dex file conversions to run in parallel
     * @param dxContext used when invoking dx, mainly for getting the standard and error output
     * @param optimized generated dex should be optimized
     * @param jumboMode generated dex should contain longer opcodes
     * @param minSdkVersion minimum sdk version used to enable dx features
     */
    public DexArchiveBuilderConfig(
            int numThreads,
            @NonNull DxContext dxContext,
            boolean optimized,
            boolean jumboMode,
            int minSdkVersion) {
        this.numThreads = numThreads;
        this.dxContext = dxContext;

        this.dexOptions = new DexOptions();
        this.dexOptions.forceJumbo = jumboMode;
        this.dexOptions.minSdkVersion = minSdkVersion;

        this.cfOptions = new CfOptions();
        this.cfOptions.optimize = optimized;
        // default value used by dx
        this.cfOptions.localInfo = true;
    }

    @NonNull
    public DexOptions getDexOptions() {
        return dexOptions;
    }

    @NonNull
    public CfOptions getCfOptions() {
        return cfOptions;
    }

    public int getNumThreads() {
        return numThreads;
    }

    @NonNull
    public DxContext getDxContext() {
        return dxContext;
    }
}
