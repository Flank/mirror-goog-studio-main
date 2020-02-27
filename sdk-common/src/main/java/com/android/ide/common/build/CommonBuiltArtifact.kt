/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ide.common.build

/**
 * Common interface between studio specific and agp specific in memory representations of the
 * output.json file.
 *
 * agp representation is located in gradle-api package so end users can load/write those files
 * when consuming/producing artifacts.
 *
 * studio representation is located here in sdk-common and cannot import gradle-api interfaces.
 */
interface CommonBuiltArtifact {

    /**
     * Returns a read-only version code.
     *
     * @return version code
     */
    val versionCode: Int

    /**
     * Returns a read-only version name.
     *
     * @return version name
     */
    val versionName: String

    /**
     * Returns a read-only value to indicate if this output is enabled.
     *
     * @return true if enabled, false otherwise.
     */
    val isEnabled: Boolean

    /**
     * Absolute path to the built file
     *
     * @return the output file path
     */
    val outputFile: String

    /**
     * [Map] of [String] for properties that are associated with the output. Such properties
     * can be consumed by downstream Tasks but represents an implicit contract
     * between the producer and consumer.
     *
     * TODO: once cleanup is finished, consider removing this facility.
     */
    val properties: Map<String, String>
}