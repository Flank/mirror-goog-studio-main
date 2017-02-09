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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;


/**
 * Patching policy for delivering incremental code changes and triggering a cold start (application
 * restart).
 */
public enum InstantRunPatchingPolicy {

    /**
     * For Dalvik, a patch dex file will be generated with the incremental changes from the last
     * non incremental build or the last build that contained changes identified by the verifier as
     * incompatible.
     */
    PRE_LOLLIPOP(DexPackagingPolicy.STANDARD, false /* useMultidex */),

    /**
     * For L and above, each shard dex file described above will be packaged in a single pure
     * split APK that will be pushed and installed on the device using adb install-multiple
     * commands.
     */
    MULTI_APK(DexPackagingPolicy.INSTANT_RUN_MULTI_APK, true /* useMultidex */);

    @NonNull
    private final DexPackagingPolicy dexPatchingPolicy;
    private final boolean useMultiDex;

    InstantRunPatchingPolicy(@NonNull DexPackagingPolicy dexPatchingPolicy, boolean useMultiDex) {
        this.dexPatchingPolicy = dexPatchingPolicy;
        this.useMultiDex = useMultiDex;
    }

    /**
     * Returns true of this packaging policy relies on multidex or not.
     */
    public boolean useMultiDex() {
        return useMultiDex;
    }

    /**
     * Returns the dex packaging policy for this patching policy. There can be variations depending
     * on the target platforms.
     * @return the desired dex packaging policy for dex files
     */
    @NonNull
    public DexPackagingPolicy getDexPatchingPolicy() {
        return dexPatchingPolicy;
    }

    /**
     * Returns the patching policy following the {@link AndroidProject#PROPERTY_BUILD_API} value
     * passed by Android Studio.
     *
     * @param featureLevel the feature level of the target device
     * @return a {@link InstantRunPatchingPolicy} instance.
     */
    @NonNull
    public static InstantRunPatchingPolicy getPatchingPolicy(
            int featureLevel) {

        if (featureLevel < AndroidVersion.ART_RUNTIME.getFeatureLevel()) {
            return PRE_LOLLIPOP;
        } else {
            return MULTI_APK;
        }
    }

}
