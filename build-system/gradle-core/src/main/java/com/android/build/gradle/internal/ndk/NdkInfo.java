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

package com.android.build.gradle.internal.ndk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.Abi;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.Collection;

/**
 * Interface describing the NDK.
 */
public interface NdkInfo {
    /**
     * Retrieve the newest supported version if it is not the specified version is not supported.
     *
     * An older NDK may not support the specified compiledSdkVersion.  In that case, determine what
     * is the newest supported version and modify compileSdkVersion.
     */
    @Nullable
    String findLatestPlatformVersion(@NonNull String targetPlatformString);

    int findSuitablePlatformVersion(
            @NonNull String abi,
            @NonNull String variantName,
            @Nullable AndroidVersion androidVersion);

    /** Return the executable for removing debug symbols from a shared object. */
    @NonNull
    File getStripExecutable(Abi abi);

    @NonNull
    Collection<Abi> getDefault32BitsAbis();

    @NonNull
    Collection<Abi> getDefaultAbis();

    @NonNull
    Collection<Abi> getSupported32BitsAbis();

    @NonNull
    Collection<Abi> getSupportedAbis();
}
