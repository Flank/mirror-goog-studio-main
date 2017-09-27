/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.model.BuildTypeOrProductFlavor
import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.CommonVariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.SealableVariant
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.android.utils.StringHelper
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ListMultimap
import com.google.common.collect.Lists
import org.gradle.api.Named
import java.io.File
import java.util.stream.Collectors

class VariantBuilder<in E: BaseExtension2>(
        private val dslModelData: DslModelData,
        private val extension: E,
        private val factories: List<VariantFactory2<E>>,
        private val deprecationReporter: DeprecationReporter,
        private val issueReporter: EvalIssueReporter) {

    fun generateVariants(): List<SealableVariant> {
        val variants= mutableListOf<SealableVariant>()

        // compute the flavor combinations
        val flavorCombinations = computeFlavorCombo()

        // call to the variant factory to create variant data.
        // Also need to merge the different items
        if (flavorCombinations.isEmpty()) {
            for (buildType in dslModelData.buildTypes) {
                variants.addAll(createVariant(buildType, null))
            }

        } else {
            for (buildType in dslModelData.buildTypes) {
                for (flavorCombo in flavorCombinations) {
                    variants.addAll(createVariant(buildType, flavorCombo))
                }

            }
        }

        return variants
    }

    private fun computeFlavorCombo(): List<FlavorCombination> {
        val flavorDimensions = extension.flavorDimensions

        if (dslModelData.productFlavors.isEmpty()) {
            // FIXME
            //configureDependencies()

            // call to variant factory to create variant data.
            // Also need to merge the different items

            return listOf()
        }

        // ensure that there is always a dimension
        if (flavorDimensions.isEmpty()) {
            issueReporter.reportError(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION,
                    "All flavors must now belong to a named flavor dimension. "
                            + "Learn more at "
                            + "https://d.android.com/r/tools/flavorDimensions-missing-error-message.html")

        } else if (flavorDimensions.size == 1) {
            // if there's only one dimension, auto-assign the dimension to all the flavors.
            val dimensionName = flavorDimensions[0]
            for (productFlavor in dslModelData.productFlavors) {
                // need to use the internal backing properties to bypass the seal
                productFlavor._dimension = dimensionName
            }
        }

        // can only call this after we ensure all flavors have a dimension.
        // FIXME
        //configureDependencies()

        // Get a list of all combinations of product flavors.
        return createCombinations(flavorDimensions, dslModelData.productFlavors, issueReporter)
    }

    private fun createVariant(buildType: BuildTypeImpl, flavorCombo: FlavorCombination?)
            : Collection<SealableVariant> {
        val items = mutableListOf<Any>()

        // merge just the default config + flavors into ProductFlavorOrVariant
        items.add(dslModelData.defaultConfig)
        flavorCombo?.let {
            items.addAll(it.flavors)
        }

        @Suppress("UNCHECKED_CAST")
        val productFlavorOrVariant = computeProductFlavorOrVariant(
                items as MutableList<ProductFlavorOrVariant>)

        // add build type and merge it all into VariantProperties
        items.add(buildType)
        @Suppress("UNCHECKED_CAST")
        val variantProperties = computeVariantProperties(items as MutableList<VariantProperties>)

        @Suppress("UNCHECKED_CAST")
        val appIdSuffixFromFlavors = combineSuffixes(
                items as MutableList<BuildTypeOrProductFlavor>,
                { it.applicationIdSuffix },
                '.')

        @Suppress("UNCHECKED_CAST")
        val variantNameSuffixFromFlavors = combineSuffixes(
                items as MutableList<BuildTypeOrProductFlavor>,
                { it.versionNameSuffix },
                null)

        // buildTypOrVariant can just be cloned from the BuildType delegate
        val buildTypOrVariant = computeBuildTypeOrVariant(buildType)

        // variantExtensionProperties is cloned from the extension's delegate
        val variantExtensionProperties = computeVariantExtensionProperties(
                extension.variantExtensionProperties)

        val variantDispatcher = mutableMapOf<VariantType, Map<Variant, Variant>>()

        val createdVariantMap = mutableMapOf<VariantType, SealableVariant>()
        for (factory in factories) {

            // Internal variant properties. Due to the variant name
            // this is made up of flavor/build type, extension, etc...
            val variantName = computeVariantName(
                    buildType.name, flavorCombo?.name, factory.generatedType, factory.testTarget)

            val internalVariantProperties = computeCommonVariantPropertiesImpl(
                    variantName, flavorCombo, buildType)

            // compute the application ID
            val appId = factory.computeApplicationId(
                    productFlavorOrVariant,
                    appIdSuffixFromFlavors)
            // set it back in the merged flavor delegate.

            // FIXME I think we need to handle the case where there is no appId but there are
            // suffixes, in which case we would have to read the manifest which we really shouldn't...
            productFlavorOrVariant.applicationId = appId

            val variant = factory.createVariant(
                    extension,
                    variantProperties,
                    productFlavorOrVariant,
                    buildTypOrVariant,
                    variantExtensionProperties,
                    internalVariantProperties,
                    variantDispatcher)

            val variantType = variant.variantType

            if (createdVariantMap[variantType] != null) {
                throw RuntimeException("More than one VariantFactory with same type $variantType")
            }
            createdVariantMap.put(variantType, variant)
        }

        for (factory in factories) {
            val generatedVariant = createdVariantMap[factory.generatedType] ?: throw RuntimeException("factory with no generated variant with type ${factory.generatedType}")

            val testTargetType = factory.testTarget
            if (testTargetType != null) {
                val testVariant = createdVariantMap[testTargetType]
                if (testVariant != null) {
                    val testMap = variantDispatcher.computeIfAbsent(testTargetType, { _ -> mutableMapOf() }) as MutableMap
                    testMap[generatedVariant] = testVariant
                }
            }

            for (testedByType in factory.testedBy) {
                val testedVariant = createdVariantMap[testedByType]
                if (testedVariant != null) {
                    val testedMap = variantDispatcher.computeIfAbsent(testedByType, { _ -> mutableMapOf() }) as MutableMap
                    testedMap[generatedVariant] = testedVariant
                }
            }
        }

        return createdVariantMap.values
    }

    private fun computeVariantProperties(items: List<VariantProperties>): VariantPropertiesImpl {
        val variantProperties = VariantPropertiesImpl(issueReporter)

        takeLastNonNull(variantProperties, items, SET_MULTIDEX_ENABLED, GET_MULTIDEX_ENABLED)
        takeLastNonNull(variantProperties, items, SET_MULTIDEX_KEEPFILE, GET_MULTIDEX_KEEPFILE)

        return variantProperties
    }

    private fun computeProductFlavorOrVariant(items: List<ProductFlavorOrVariant>): ProductFlavorOrVariantImpl {
        val productFlavorOrVariant = ProductFlavorOrVariantImpl(issueReporter)

        // merge the default-config + flavors in there.

        return productFlavorOrVariant
    }

    private fun combineSuffixes(
            items: MutableList<BuildTypeOrProductFlavor>,
            getter: (BuildTypeOrProductFlavor) -> String?,
            separator: Char?): String? {
        val suffixes: MutableList<String> = items.stream().map(getter).filter({ it != null }).collect(Collectors.toList())

        if (suffixes.isEmpty()) {
            return null
        }

        val sb = StringBuilder()
        for (suffix in suffixes) {
            if (separator == null || suffix[0] == separator) {
                sb.append(suffix)
            } else {
                sb.append(separator).append(suffix)
            }
        }

        return sb.toString()
    }

    private fun computeBuildTypeOrVariant(buildType: BuildTypeImpl): BuildTypeOrVariantImpl {
        // values here don't matter, we're going to run initWith
        val buildTypeOrVariant = BuildTypeOrVariantImpl(
                "Variant",
                false,
                false,
                false,
                deprecationReporter,
                issueReporter)

        buildTypeOrVariant.initWith(buildType.buildTypeOrVariant)

        return buildTypeOrVariant
    }

    private fun computeVariantExtensionProperties(
            variantExtensionProperties: VariantOrExtensionPropertiesImpl): VariantOrExtensionPropertiesImpl {
        val prop = VariantOrExtensionPropertiesImpl(issueReporter)

        prop.initWith(variantExtensionProperties)
        return prop
    }

    private fun computeCommonVariantPropertiesImpl(
            variantName: String,
            flavorCombo: FlavorCombination?,
            buildType: BuildTypeImpl): CommonVariantPropertiesImpl {

        val flavors: ImmutableList<ProductFlavorImpl> = flavorCombo?.flavors ?: ImmutableList.of()

        // use DslModelData as this one is not sealed.
        val allSourceSets = dslModelData.sourceSets

        val sourceSets: MutableList<AndroidSourceSet> = mutableListOf()
        // add Main
        sourceSets.add(allSourceSets.getByName(BuilderConstants.MAIN))

        // add the flavors.
        sourceSets.addAll(
                flavors.stream()
                        .map({ allSourceSets.getByName(it.name) })
                        .collect(Collectors.toList()))

        // add the build type
        sourceSets.add(allSourceSets.getByName(buildType.name))

        // create variant sourceset
        val variantSourceSet = allSourceSets.create(variantName)

        // create multi-flavor sourceset, optional.
        var multiFlavorSourceSet: AndroidSourceSet? = null
        flavorCombo?.name.let {
            multiFlavorSourceSet = allSourceSets.create(it)
        }

        val flavorNames: List<String> = if (flavors.isEmpty()) {
            ImmutableList.of()
        } else {
            flavors.stream().map(Named::getName).collect(Collectors.toList())
        }

        return CommonVariantPropertiesImpl(
                buildType.name,
                flavorNames,
                sourceSets,
                variantSourceSet,
                multiFlavorSourceSet,
                issueReporter)
    }
}

private val SET_MULTIDEX_ENABLED: (VariantProperties, Boolean?) -> Unit = { o, v -> o.multiDexEnabled = v}
private val GET_MULTIDEX_ENABLED: (VariantProperties) -> Boolean? = { it.multiDexEnabled }
private val SET_MULTIDEX_KEEPFILE: (VariantProperties, File?) -> Unit = { o, v -> o.multiDexKeepFile = v}
private val GET_MULTIDEX_KEEPFILE: (VariantProperties) -> File? = { it.multiDexKeepFile }

private fun <T, V> takeLastNonNull(outObject: T, inList: List<T>, setter: (T,V) -> Unit, getter: (T) -> V?) {
    for (i in inList.size - 1 downTo 0) {
        val value: V? = getter.invoke(inList[i])
        if (value != null) {
            setter.invoke(outObject, value)
            return
        }
    }
}

/**
 * A combination of flavors that makes up (with a BuildType) a variant
 *
 * @param name the optional name of the combination. Only valid for 2+ flavors
 * @param flavors the list of flavors
 */
private class FlavorCombination(val name: String?, val flavors: ImmutableList<ProductFlavorImpl>)

/**
 * Creates a list containing all combinations of ProductFlavors of the given dimensions.
 * @param flavorDimensions The dimensions each product flavor can belong to.
 * @param productFlavors An iterable of all ProductFlavors in the project..
 * @return A list of FlavorCombination representing all combinations of ProductFlavors.
 */
private fun createCombinations(
        flavorDimensions: List<String>,
        productFlavors: Set<ProductFlavorImpl>,
        issueReporter: EvalIssueReporter): List<FlavorCombination> {

    if (flavorDimensions.size == 1) {
        val result = Lists.newArrayListWithCapacity<FlavorCombination>(productFlavors.size)
        for (flavor in productFlavors) {
            val flavors = ImmutableList.of(flavor)
            result.add(FlavorCombination(computeMultiFlavorName(flavors), flavors))
        }

        return result
    } else {
        val result = mutableListOf<FlavorCombination>()
        // need to group the flavor per dimension.
        // First a map of dimension -> list(ProductFlavor)
        val map = ArrayListMultimap.create<String, ProductFlavorImpl>()

        for (flavor in productFlavors) {
            if (flavor.dimension == null) {
                issueReporter.reportError(
                        SyncIssue.TYPE_GENERIC,
                        "Flavor '${flavor.name}' has no flavor dimension.")
                continue
            }

            val flavorDimension: String = flavor.dimension!!

            if (!flavorDimensions.contains(flavorDimension)) {
                issueReporter.reportError(
                        SyncIssue.TYPE_GENERIC,
                        "Flavor '${flavor.name}' has unknown dimension '$flavorDimension")
                continue
            }

            map.put(flavorDimension, flavor)
        }

        // now go through all the dimensions and combine them
        createFlavorCombinations(
                result,
                Lists.newArrayListWithCapacity(flavorDimensions.size),
                0, flavorDimensions, map, issueReporter)

        return result
    }
}

/** Recursively go through all the dimensions and build all combinations */
private fun createFlavorCombinations(
        outCombos: MutableList<FlavorCombination>,
        flavorAccumulator: MutableList<ProductFlavorImpl>,
        index: Int,
        flavorDimensionList: List<String>,
        flavorMap: ListMultimap<String, ProductFlavorImpl>,
        issueReporter: EvalIssueReporter) {

    if (index == flavorDimensionList.size) {
        outCombos.add(FlavorCombination(
                computeMultiFlavorName(flavorAccumulator),
                ImmutableList.copyOf(flavorAccumulator)))
        return
    }

    // fill the array at the current index.
    // get the dimension name that matches the index we are filling.
    val dimensionName = flavorDimensionList[index]

    // from our map, get all the possible flavors in that dimension.
    val flavorList = flavorMap.get(dimensionName)

    // loop on all the flavors to add them to the current index and recursively fill the next
    // indices.
    if (flavorList.isEmpty()) {
        issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                "No flavor is associated with flavor dimension '$dimensionName'.")
        return
    }

    for (flavor in flavorList) {
        flavorAccumulator.add(flavor)

        createFlavorCombinations(
                outCombos,
                flavorAccumulator,
                index + 1,
                flavorDimensionList,
                flavorMap,
                issueReporter)

        flavorAccumulator.removeAt(index + 1)
    }
}

private fun computeMultiFlavorName(flavors: List<ProductFlavorImpl>): String {
    var first = true
    val sb = StringBuilder(flavors.size * 20)
    for (flavor in flavors) {
        if (first) {
            sb.append(flavor.name)
            first = false
        } else {
            sb.append(StringHelper.capitalize(flavor.name))
        }
    }

    return sb.toString()
}

/**
 * Returns the full, unique name of the variant in camel case (starting with a lower case),
 * including BuildType, Flavors and Test (if applicable).
 *
 * @param buildTypeName the build type name
 * @param multiFlavorName the flavor name, optional
 * @param type the variant type
 * @param testedType the tested type. Optional
 * @return the name of the variant
 */
private fun computeVariantName(
        buildTypeName: String,
        multiFlavorName: String?,
        type: VariantType,
        testedType: VariantType?): String {
    val sb = StringBuilder()

    if (multiFlavorName?.isEmpty() == false) {
        sb.append(multiFlavorName)
        sb.append(buildTypeName.capitalize())
    } else {
        sb.append(buildTypeName)
    }

    if (type == VariantType.FEATURE) {
        sb.append("Feature")
    }

    if (type.isForTesting) {
        if (testedType == VariantType.FEATURE) {
            sb.append("Feature")
        }
        sb.append(type.suffix)
    }
    return sb.toString()
}

