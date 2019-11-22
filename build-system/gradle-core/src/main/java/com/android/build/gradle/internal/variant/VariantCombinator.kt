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

package com.android.build.gradle.internal.variant

import com.android.build.api.variant.VariantConfiguration
import com.android.build.api.variant.impl.VariantConfigurationImpl
import com.android.build.gradle.internal.errors.SyncIssueHandler
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueReporter
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import com.google.common.collect.ImmutableList

/**
 * Computes the combination of variants from a [VariantModel] and returns a list of
 * [VariantConfiguration]
 */
class VariantCombinator(
    private val variantModel : VariantModel,
    private val errorReporter: SyncIssueHandler,
    private val variantType: VariantType,
    private val flavorDimensionList: List<String>
) {

    /**
     * Computes and returns the list of variants as [VariantConfiguration] objects.
     */
    fun computeVariants() : List<VariantConfiguration> {
        // different paths for flavors or no flavors to optimize things a bit

        if (variantModel.productFlavors.isEmpty()) {
            return computeFlavorLessVariants()
        }

        return computeVariantsWithFlavors()
    }

    /**
     * Computes [VariantConfiguration] for the case where there are no flavors.
     */
    private fun computeFlavorLessVariants() : List<VariantConfiguration> {
        return if (variantModel.buildTypes.isEmpty()) {
            ImmutableList.of(VariantConfigurationImpl(variantName = "main"))
        } else {
            val builder = ImmutableList.builder<VariantConfiguration>()

            for (buildType in variantModel.buildTypes.keys) {
                builder.add(VariantConfigurationImpl(
                    variantName = buildType,
                    buildType = buildType))
            }

            builder.build()
        }
    }

    /**
     * Computes [VariantConfiguration] for the case with flavors.
     */
    private fun computeVariantsWithFlavors(): List<VariantConfiguration> {
        val flavorDimensionList = validateFlavorDimensions()

        // get a Map of (dimension, list of names) for the flavors
        val flavorMap = variantModel.productFlavors.values
            .asSequence()
            .map { it.productFlavor }
            .groupBy({ it.dimension!! }, { it.name })

        // get the flavor combos and combine them with build types.
        val builder = ImmutableList.builder<FlavorCombination>()
        createProductFlavorCombinations(
            flavorDimensionList,
            flavorMap,
            builder,
            errorReporter
        )

        return combineFlavorsAndBuildTypes(builder.build())
    }

    /**
     * Computes [VariantConfiguration] from a list of [FlavorCombination] by combining them
     * with build types.
     */
    private fun combineFlavorsAndBuildTypes(
        flavorCombos: List<FlavorCombination>
    ) : List<VariantConfiguration> {
        if (variantModel.buildTypes.isEmpty()) {
            // just convert the Accumulators to VariantConfiguration with no build type info
            return flavorCombos.map {
                val flavorNames = it.getFlavorNames()
                VariantConfigurationImpl(
                    variantName = computeVariantName(
                        flavorNames = flavorNames,
                        buildType = null,
                        type = variantType
                    ),
                    flavors = flavorNames
                )
            }
        } else {
            val builder = ImmutableList.builder<VariantConfiguration>()

            for (buildType in variantModel.buildTypes.keys) {
                builder.addAll(flavorCombos.map {
                    val flavors = it.getFlavorNames()
                    VariantConfigurationImpl(
                        variantName = computeVariantName(flavors, buildType, variantType),
                        buildType = buildType,
                        flavors = flavors
                    )
                })
            }

            return builder.build()
        }
    }

    /**
     * Validates the flavor dimensions.
     *
     * This checks that there's at least a dimension declared, and for the case of a single
     * dimension, assign that dimension to all flavors. This is to facilitate migrating from
     * AGP versions that did not required explicitly declared dimension name (for single dimension
     * case).
     */
    private fun validateFlavorDimensions(): List<String> {
        // ensure that there is always a dimension if there are flavors
        if (flavorDimensionList.isEmpty()) {
            errorReporter
                .reportError(
                    EvalIssueReporter.Type.UNNAMED_FLAVOR_DIMENSION,
                    "All flavors must now belong to a named flavor dimension."
                            + " Learn more at "
                            + "https://d.android.com/r/tools/flavorDimensions-missing-error-message.html"
                )

            // because the dimension list is missing but we do have flavors, we need to rebuild the
            // dimension list. This list is not going to be correct because we cannot infer its
            // order from just gathering the dimension values set in the flavors (because the
            // flavors themselves are not ordered by dimension.
            // So this list is just so that we can keep going with sync, but this means the
            // variants returned maybe be incorrect (due to wrong order of dimensions, if there
            // is more than one dimension).
            // Studio should act on UNNAMED_FLAVOR_DIMENSION error and prevent actually doing
            // anything until the error clears anyway, but we may want to investigate not
            // returning any variant at all in this case (in the model, we still need them to
            // resolve consumers.)
            // Use a set to de-duplicate values quickly, since the order does not matter.
            val dimensions = mutableSetOf<String>()

            // fake dimension in case a flavor does not have one
            val fakeDimension = "agp-missing-dimension-for-sync-only"

            for (flavor in variantModel.productFlavors.values) {
                val productFlavor = flavor.productFlavor
                val dim = productFlavor.dimension
                if (dim == null) {
                    productFlavor.setDimension(fakeDimension)
                } else {
                    dimensions.add(dim)
                }
            }

            if (dimensions.isEmpty()) {
                dimensions.add(fakeDimension)
            }

            // convert to list, see comment above regarding the order.
            return dimensions.toList()

        } else if (flavorDimensionList.size == 1) {
            // if there's only one dimension, auto-assign the dimension to all the flavors.
            val dimensionName = flavorDimensionList[0]
            for (flavorData in variantModel.productFlavors.values) {
                val flavor = flavorData.productFlavor
                if (flavor.dimension == null) {
                    flavor.setDimension(dimensionName)
                }
            }
        }

        return flavorDimensionList
    }
}

/**
 * Recursively creates all combinations of product flavors.
 *
 * This runs through the flavor dimensions list and for the current dimension, gathers the list
 * of flavors. It then loops on these and for each, add the current (dimension, value) pair to a new
 * list and then recursively calls into the next dimension for it to loop on its own flavors and
 * do the same (fill the list.) This goes on until there's no dimension left.
 *
 * The recursion is handled by 2 objects:
 * - `dimensionIndex`: this is the dimensionIndex of the current dimension in the dimension list.
 *   Each recursive call increases this to go to the next dimension.
 *
 * - `currentFlavorCombo`: this accumulates the list of (dimension, value) pairs for the dimensions
 *   already processed. Each new dimension adds its new pairs to it (or rather to a new copy of it
 *   for each pair)
 *
 * At the end of the recursion (based on `dimensionIndex` reaching the end of the list),
 * `currentFlavorCombo` contains a pair for each dimension. This is then added to `comboList`
 *
 * This function is out of the main class because its `flavorDimensionList` param shadows
 * [VariantCombinator.flavorDimensionList], therefore it's clearer and more obvious that
 * this is not the same value.
 *
 * @param flavorDimensionList the list of flavor dimension
 * @param flavorMap the map of (dimension, list of flavors)
 * @param comboList the list that receives the final (filled-up) [FlavorCombination] objects.
 * @param errorReporter a [SyncIssueHandler] to report errors.
 * @param currentFlavorCombo the current accumulator containing pairs of (dimension, value) for already visited dimensions
 * @param dimensionIndex the index of the dimension this calls must handle
 */
private fun createProductFlavorCombinations(
    flavorDimensionList: List<String>,
    flavorMap: Map<String, List<String>>,
    comboList: ImmutableList.Builder<FlavorCombination>,
    errorReporter: SyncIssueHandler,
    currentFlavorCombo: FlavorCombination = FlavorCombination(),
    dimensionIndex: Int = 0
)  {
    if (dimensionIndex == flavorDimensionList.size) {
        // we visited all the dimensions, currentFlavorCombo is filled.
        comboList.add(currentFlavorCombo)
        return
    }

    // get the dimension name that matches the index we are filling.
    val dimension = flavorDimensionList[dimensionIndex]

    // from our map, get all the possible flavors in that dimension.
    val flavorList = flavorMap[dimension]

    // loop on all the flavors to add them to the current index and recursively fill the next
    // indices.
    return if (flavorList == null || flavorList.isEmpty()) {
        errorReporter.reportError(
            EvalIssueReporter.Type.GENERIC,
            "No flavor is associated with flavor dimension '$dimension'."
        )
    } else {
        for (flavor in flavorList) {
            val newCombo = currentFlavorCombo.add(dimension, flavor)

            createProductFlavorCombinations(
                flavorDimensionList,
                flavorMap,
                comboList,
                errorReporter,
                newCombo,
                dimensionIndex + 1
            )
        }
    }
}

/**
 * Represents a combination of flavors each from a different dimension.
 *
 * This class is immutable. To add a new (dimension, value) pair to the list, use [add] that will
 * return a new instance.
 */
private class FlavorCombination(
    /**
     * The list of (dimension, value) pairs. The order is important and match the
     * dimension priority order.
     */
    private val flavorPairs: ImmutableList<Pair<String, String>> = ImmutableList.of()) {

    /**
     * Returns a new instance with the new pair information added to the existing list.
     */
    fun add(dimension: String, name: String) : FlavorCombination {
        val builder = ImmutableList.builder<Pair<String,String>>()
        builder.addAll(flavorPairs)
        builder.add(Pair(dimension, name))

        return FlavorCombination(builder.build())
    }

    /**
     * Returns the list of flavor names, properly sorted based on the associated dimension order
     */
    fun getFlavorNames(): List<String> = flavorPairs.map { it.second }

    override fun toString(): String {
        return "FlavorCombination(flavorPairs=$flavorPairs)"
    }
}

/**
 * Returns the full, unique name of the variant in camel case (starting with a lower case),
 * including BuildType, Flavors and Test (if applicable).
 *
 *
 * This is to be used for the normal variant name. In case of Feature plugin, the library
 * side will be called the same as for library plugins, while the feature side will add
 * 'feature' to the name.
 *
 * @param flavorNames the ordered list of flavor names
 * @param buildType the build type
 * @param type the variant type
 * @return the name of the variant
 */
private fun computeVariantName(
    flavorNames: List<String>, buildType: String?, type: VariantType
): String {
    val sb = StringBuilder()

    if (flavorNames.isNotEmpty()) {
        sb.append(flavorNames.combineAsCamelCase())
        if (buildType != null) {
            sb.appendCapitalized(buildType)
        }
    } else if (buildType != null) {
        sb.append(buildType)
    } else {
        // this should not happen so no need to use errorReporter
        throw IllegalStateException("build type cannot be null with no flavors")
    }

    if (type.isTestComponent) {
        sb.append(type.suffix)
    }

    return sb.toString()
}
