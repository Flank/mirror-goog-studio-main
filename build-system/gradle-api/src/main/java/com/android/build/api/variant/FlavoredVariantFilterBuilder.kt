
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

import org.gradle.api.Action
import org.gradle.api.Incubating
import java.util.regex.Pattern

/**
 * Filter for [Variant] or [VariantProperties] with a product flavor.
 */
@Incubating
interface FlavoredVariantFilterBuilder<T: ActionableVariantObject>: BuildTypedVariantFilterBuilder<T> {
    /**
     * Filters [T] instances using a build type reference.
     *
     * @param buildType to filter [T] on
     * @return an instance of [BuildTypedVariantFilterBuilder] to further filter variants.
     */
    fun withBuildType(buildType: String): BuildTypedVariantFilterBuilder<T>

    /**
     * Specifies the build type this filter should apply when filtering variants.
     */
    fun withBuildType(buildType: String, action: Action<T>)

    /**
     * Filters [T] instances using a build type reference and run a lambda of all the filtered
     * instances.
     *
     * @param buildType to filter [T] on
     * @param action lambda function to run on filtered [T]
     */
    fun withBuildType(buildType: String, action: T.() -> Unit)
}
