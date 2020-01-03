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
import com.android.build.api.variant.VariantConfiguration
import org.gradle.api.Action
import java.util.regex.Pattern

/**
 * An [Action] on a filtered [List] of [com.android.build.api.variant.VariantProperties] objects.
 *
 * The filter can be based on either or both a build type of build flavor. Alternatively, the filter
 * can be named based either using strict [String] matching or using [Pattern] matching.
 */
class FilteredVariantOperation<T>(
    val specificType: Class<T>,
    private val buildType: String? = null,
    private val flavorToDimensionData: List<Pair<String, String>> = listOf(),
    private val variantNamePattern: Pattern? = null,
    private val variantName: String? = null,
    private val action: Action<T>
) where T: ActionableVariantObject, T: VariantConfiguration {

    fun executeFor(variant: T) {
        if (buildType != variant.buildType) {
            return
        }

        val flavorMap = variant.productFlavors.groupBy({ it.first }, { it.second })
        flavorToDimensionData.forEach {
            val values = flavorMap[it.first]
            if (values == null || values.size != 1 || values[0] != it.second) {
                return
            }
        }

        if (variantNamePattern != null) {
            if (!variantNamePattern.matcher(variant.name).matches()) {
                return
            }
        }

        if (variantName != null && variantName != variant.name) {
            return
        }

        action.execute(variant)
    }
}
