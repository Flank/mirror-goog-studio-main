/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.Property

/**
 * Defines a variant output.
 */
@Incubating
interface VariantOutput {

    /**
     * Type of package file, either the main APK or a full split APK file containing resources for a
     * particular split dimension.
     */
    @Incubating
    enum class OutputType {
        MAIN,
        FULL_SPLIT
    }

    /** Split dimension type  */
    @Incubating
    enum class FilterType {
        DENSITY,
        ABI,
        LANGUAGE
    }

    /**
     * Returns a modifiable [Property] representing the variant output version code.
     *
     * This will be initialized with the variant's merged flavor value or read from the manifest
     * file if unset.
     */
    val versionCode: Property<Int>

    /**
     * Returns a modifiable [Property] representing the variant output version name.
     *
     * This will be initialized with the variant's merged flavor value, or it will be read from the
     * manifest source file if it's not set via the DSL, or it will be null if it's also not set in
     * the manifest.
     */
    val versionName: Property<String>

    /**
     * Returns a modifiable [Property] to enable or disable the production of this [VariantOutput]
     *
     * @return a [Property] to enable or disable this output.
     */
    val isEnabled: Property<Boolean>

    /**
     * Returns true if this [VariantOutput] represents a Universal APK, false otherwise.
     *
     * @return a readonly boolean whether this output is a universal APK or not.
     */
    val isUniversal: Boolean

    /** Returns the output type of the referenced APK. */
    val outputType: String
}