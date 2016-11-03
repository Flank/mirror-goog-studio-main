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

/**
 * Enumeration of the possible file types produced by an instant run enabled build.
 */
public enum FileType {
    /**
     * Main APK file for 19, and 21 platforms when using the {@link ColdswapMode#MULTIDEX} mode.
     */
    MAIN,
    /**
     * Main APK file when application is using the {@link ColdswapMode#MULTIAPK} mode.
     */
    SPLIT_MAIN,
    /**
     * Reload dex file that can be used to patch application live.
     */
    RELOAD_DEX,
    /**
     * Shard dex file that can be used to replace originally installed multi-dex shard.
     */
    DEX,
    /**
     * Pure split (code only) that can be installed individually on M+ devices.
     */
    SPLIT,
    /**
     * Resources: res.ap_ file
     */
    RESOURCES,
}
