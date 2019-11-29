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
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.model.ProductFlavor
import com.google.common.base.Joiner
import org.gradle.api.Action
import java.lang.RuntimeException
import java.util.regex.Pattern

/**
 * An [Action] on a filtered [List] of [com.android.build.api.variant.VariantProperties] objects.
 *
 * The filter can be based on either or both a build type of build flavor. Alternatively, the filter
 * can be named based either using strict [String] matching or using [Pattern] matching.
 */
class FilteredVariantOperation<T: ActionableVariantObject>(
    private val specificType: Class<T>,
    private val buildType: String? = null,
    private val flavorToDimensionData: List<Pair<String, String>> = listOf(),
    private val variantNamePattern: Pattern? = null,
    private val variantName: String? = null,
    private val action: Action<T>
) {
    fun execute(transformer: VariantScopeTransformers, variants: List<VariantScope>) {
        var currentVariants = variants
        if (buildType != null) {
            currentVariants = currentVariants.filter { variant -> variant.variantConfiguration.buildType.name == buildType }
        }
        flavorToDimensionData.forEach {
            currentVariants = currentVariants.filter { variant ->
                matchFlavors(it, variant.variantConfiguration.productFlavors)
            }
        }
        if (variantNamePattern != null) {
            currentVariants = currentVariants.filter {
                    variant ->  variantNamePattern.matcher(variant.fullVariantName).matches()
            }
        }
        if (variantName != null) {
            val variant = currentVariants.find { variant -> variant.fullVariantName == variantName }
                ?: throw RuntimeException("""
                    Cannot find variant with name $variantName, possible candidates are 
                    ${Joiner.on(',').join(variants.map { variant -> variant.fullVariantName })}
                """.trimIndent())
            transformer.transform(variant, specificType)?.apply { action.execute(this) }
        } else {
            currentVariants.forEach { variant ->
                transformer.transform(variant, specificType)?.apply { action.execute(this) }
            }
        }
    }
    private fun matchFlavors(match: Pair<String, String>, flavors: List<ProductFlavor>): Boolean {
        flavors.forEach { productFlavor ->
            if (productFlavor.name == match.first && productFlavor.dimension == match.second) {
                return true
            }
        }
        return false
    }
}
