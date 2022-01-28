/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.api.variant.impl.DirectoryEntries
import com.android.build.api.variant.impl.DirectoryEntry

/**
 * Interface to calculate the default list of sources [DirectoryEntry] per source type.
 */
interface DefaultSourcesProvider {

    /**
     * the list of sources [DirectoryEntry] for java
     */
    val java: List<DirectoryEntry>

    /**
     * the list of sources [DirectoryEntries] for android resources.
     *
     * The [List] is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on.
     */
    val res: List<DirectoryEntries>

    /**
     * the list of [DirectoryEntries] for assets.
     *
     * The [List] is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on.
     */
    val assets: List<DirectoryEntries>

    /**
     * the list of [DirectoryEntries] for jni libraries.
     *
     * The [List] is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on.
     */
    val jniLibs: List<DirectoryEntries>

    /**
     * the list of [DirectoryEntries] for shaders or null if the feature is disabled.
     *
     * The [List] is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on.
     */
    val shaders: List<DirectoryEntries>?

    /**
     * the list of sources [DirectoryEntry] for AIDL or null if the feature is disabled.
     */
    val aidl: List<DirectoryEntry>?

    /**
     * the list of [DirectoryEntries] for machine learning models.
     *
     * The [List] is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on.
     */
    val mlModels: List<DirectoryEntries>

    /**
     * the list of sources [DirectoryEntry] for renderscript or null if the feature is disabled.
     */
    val renderscript: List<DirectoryEntry>?
}
