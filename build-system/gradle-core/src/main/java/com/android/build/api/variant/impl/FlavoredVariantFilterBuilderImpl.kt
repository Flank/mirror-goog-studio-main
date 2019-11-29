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
package com.android.build.api.variant.impl
import com.android.build.api.variant.ActionableVariantObject
import com.android.build.api.variant.FlavoredVariantFilterBuilder
import org.gradle.api.Action

internal class FlavoredVariantQueryFilterImpl<T: ActionableVariantObject>(
    private val operations: VariantOperations<in T>,
    private val flavorToDimension: Pair<String, String>,
    private val type: Class<T>
)
    : FlavoredVariantFilterBuilder<T> {
    override fun withBuildType(buildType: String, action: Action<T>) {
        operations.addFilteredAction(
            FilteredVariantOperation(
                specificType = type,
                buildType = buildType,
                flavorToDimensionData = listOf(flavorToDimension),
                action = action
            ))
    }
}