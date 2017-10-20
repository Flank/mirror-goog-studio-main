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
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.CommonVariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.SealableVariant
import com.android.build.gradle.internal.api.sourcesets.DefaultAndroidSourceSet
import com.android.builder.core.VariantType
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.android.utils.ImmutableCollectors
import com.android.utils.StringHelper
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import org.gradle.api.Named
import java.io.File
import java.util.stream.Collectors

/**
 * A builder of variants.
 *
 * This combines [ProductFlavor] and [com.android.build.api.dsl.model.BuildType], along with a
 * [BaseExtension2] to create [Variant] objects.
 *
 * Each combination of flavors and build type can create more than one variants. There is generally
 * a main variant accompanied by test variants.
 *
 * @param dslModelData the dsl model data containing flavors and build type and sourcesets
 * @param extension the extension
 * @param deprecationReporter the deprecation reporter
 * @param issueReporter the error/warning reporter.
 */
class VariantBuilder<in E: BaseExtension2>(
        private val dslModelData: DslModelDataImpl<E>,
        private val extension: E,
        private val deprecationReporter: DeprecationReporter,
        private val issueReporter: EvalIssueReporter) {

    /** whether the variants have been computed */
    private var generated: Boolean = false

    /** the generated list of variants */
    private val _variants: MutableList<SealableVariant> = mutableListOf()
    /** the generated map of variant shims. The key is the variant being shimmed */
    private val _shims: MutableMap<Variant, Variant> = Maps.newIdentityHashMap()

    /** property-style getter for the variant as a read-only list */
    val variants: List<SealableVariant>
        get() {
            if (!generated) {
                throw RuntimeException("VariantBuilder.generateVariants() not called")
            }
            return _variants
        }

    /** property-style getter for the shims as a read-only list */
    val shims: List<Variant>
        get() {
            if (!generated) {
                throw RuntimeException("VariantBuilder.generateVariants() not called")
            }
            return ImmutableList.copyOf(_shims.values)
        }

    /** Computes the variants */
    fun generateVariants() {
        // compute the flavor combinations
        val flavorCombinations = computeFlavorCombo()

        // call to the variant factory to create variant data.
        // Also need to merge the different items
        if (flavorCombinations.isEmpty()) {
            for (buildType in dslModelData._buildTypes) {
                createVariant(buildType, null)
            }

        } else {
            for (buildType in dslModelData._buildTypes) {
                for (flavorCombo in flavorCombinations) {
                    createVariant(buildType, flavorCombo)
                }

            }
        }
    }

    /**
     * Compute all the possible combinations of flavors.
     *
     * Each combo contains exactly one flavor from each flavor dimension
     *
     * @return a list of [FlavorCombination]
     */
    private fun computeFlavorCombo(): List<FlavorCombination> {
        val flavorDimensions = extension.flavorDimensions

        if (dslModelData._productFlavors.isEmpty()) {
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
            for (productFlavor in dslModelData._productFlavors) {
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

    /**
     * Creates one or more variants for a given build type and [FlavorCombination].
     *
     * The number of variants depends on the number of factories.
     *
     * For each generated variant, a shim is generated as well.
     */
    private fun createVariant(buildType: BuildTypeImpl, flavorCombo: FlavorCombination?) {
        // check if we have to run this at all via the externally-provided filters
        val filterObject = VariantFilterImpl(
                buildType.name,
                flavorCombo?.flavorNames ?: ImmutableList.of(),
                issueReporter)
        for (filter in extension.variantFilters) {
            filter.execute(filterObject)
            if (filterObject.ignoresAll) {
                return
            }
        }

        // seal so that people get notified if they try to change this too late.
        filterObject.seal()

        // -----
        // At this point we are going to merge all the build type, flavors and extension
        // properties into a single variant object.
        // As all these objects are actually composed of many delegates, some of which are
        // present in several object (build type and flavors, or build type and extension),
        // the variant is going to be assembled from the same delegates.
        // Therefore, the first thing we are doing is merging all the duplicated delegates
        // into new delegates of the same type.
        // Then we will create the variant object by passing it the merged delegate.

        // list of items to merge
        val items = mutableListOf<Any>()

        // merge just the default config + flavors into ProductFlavorOrVariant
        items.add(dslModelData.defaultConfig)
        flavorCombo?.let {
            items.addAll(it.flavors)
        }

        @Suppress("UNCHECKED_CAST")
        val productFlavorOrVariant = mergeProductFlavorOrVariant(
                items as MutableList<ProductFlavorOrVariant>)

        // add build type and merge it all into VariantProperties
        items.add(buildType)
        @Suppress("UNCHECKED_CAST")
        val variantProperties = mergeVariantProperties(items as MutableList<VariantProperties>)

        // Special case for app ID and variant name suffix
        // FIXME we need to change how we handle this, using a dynamic provider of
        // manifest data which will include the full appId
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

        // -----
        // the next two delegate are not duplicated in the source object, so we can just
        // duplicate the source delegate into a new delegate for the variant.

        // buildTypOrVariant can just be cloned from the BuildType delegate
        val buildTypOrVariant = cloneBuildTypeOrVariant(buildType)

        // variantExtensionProperties is cloned from the extension's delegate
        val variantExtensionProperties = cloneVariantOrExtensionProperties(
                extension.variantExtensionProperties)

        // -----
        // Loop on the factories and create the variants and shims.
        val variantFactories = dslModelData.variantFactories

        // creates a dispatcher to link the main variant and the tested variant in a bi-directional
        // way. It's a map of (VariantType -> Map of (variant -> variant))
        // The first key is the variant type that is requested from the dispatcher.
        // In the secondary map, the key is the variant making the request, and the value is the
        // variant of the type provided in the main key.
        val variantDispatcher = mutableMapOf<VariantType, Map<Variant, Variant>>()
        // map of generated variant by their type. Allow quick access when building the dispatcher
        val createdVariantMap = mutableMapOf<VariantType, Variant>()
        for (factory in variantFactories) {
            // what does the factory generates?
            val generatedType = factory.generatedType

            if (filterObject.ignores(generatedType)) {
                continue
            }

            // Internal variant properties. Due to the variant name
            // this is made up of flavor/build type, extension, etc...
            val variantName = computeVariantName(
                    buildType.name, flavorCombo?.name, generatedType, factory.testTarget)

            val commonVariantProperties = computeCommonVariantPropertiesImpl(
                    generatedType, variantName, flavorCombo, buildType)

            // compute the application ID and feed it back into the delegate that holds it
            // FIXME I think we need to handle the case where there is no appId but there are
            // suffixes, in which case we would have to read the manifest which we really shouldn't...
            productFlavorOrVariant.applicationId = factory.computeApplicationId(
                    productFlavorOrVariant,
                    appIdSuffixFromFlavors)

            // FIXME do same for versionName

            // FIXME we want to copy these items for each variant - Maybe have some sort of copy on write?
            val variant = factory.createVariant(
                    extension,
                    variantProperties,
                    productFlavorOrVariant,
                    buildTypOrVariant,
                    variantExtensionProperties,
                    commonVariantProperties,
                    variantDispatcher,
                    issueReporter)

            val variantType = variant.variantType

            // add variant to main list
            _variants.add(variant)

            // get shim and put it in variant-to-shim map
            val shim = variant.createShim()
            _shims[variant] = shim

            // and put the variant in the intermediate map for the dispatcher
            if (createdVariantMap[variantType] != null) {
                throw RuntimeException("More than one VariantFactory with same type $variantType")
            }
            createdVariantMap.put(variantType, variant)
        }

        // -----
        // setup the variant dispatcher linking the variants together.
        // The key is the internal variant but the result must be the shim
        for (factory in variantFactories) {
            val generatedVariant = createdVariantMap[factory.generatedType] ?: continue

            val testTargetType = factory.testTarget
            if (testTargetType != null) {
                val testVariant = createdVariantMap[testTargetType]
                if (testVariant != null) {
                    val testMap = variantDispatcher.computeIfAbsent(testTargetType, { _ -> mutableMapOf() }) as MutableMap
                    testMap[generatedVariant] = _shims[testVariant]!! // shim must be present
                }
            }

            for (testedByType in factory.testedBy) {
                val testedVariant = createdVariantMap[testedByType]
                if (testedVariant != null) {
                    val testedMap = variantDispatcher.computeIfAbsent(testedByType, { _ -> mutableMapOf() }) as MutableMap
                    testedMap[generatedVariant] = _shims[testedVariant]!! // shim must be present
                }
            }
        }
    }

    private fun mergeVariantProperties(items: List<VariantProperties>): VariantPropertiesImpl {
        val variantProperties = VariantPropertiesImpl(issueReporter)

        takeLastNonNull(variantProperties, items, SET_MULTIDEX_ENABLED, GET_MULTIDEX_ENABLED)
        takeLastNonNull(variantProperties, items, SET_MULTIDEX_KEEPFILE, GET_MULTIDEX_KEEPFILE)
        // TODO more

        return variantProperties
    }

    private fun mergeProductFlavorOrVariant(items: List<ProductFlavorOrVariant>): ProductFlavorOrVariantImpl {
        val productFlavorOrVariant = ProductFlavorOrVariantImpl(issueReporter)

        // merge the default-config + flavors in there.
        // TODO more

        return productFlavorOrVariant
    }

    private fun cloneBuildTypeOrVariant(that: BuildTypeImpl): BuildTypeOrVariantImpl {
        // values here don't matter, we're going to run initWith
        val buildTypeOrVariant = BuildTypeOrVariantImpl(
                "Variant",
                false,
                false,
                false,
                deprecationReporter,
                issueReporter)

        buildTypeOrVariant.initWith(that.buildTypeOrVariant)

        return buildTypeOrVariant
    }

    private fun cloneVariantOrExtensionProperties(
            that: VariantOrExtensionPropertiesImpl): VariantOrExtensionPropertiesImpl {
        val prop = VariantOrExtensionPropertiesImpl(issueReporter)

        prop.initWith(that)
        return prop
    }

    private fun computeCommonVariantPropertiesImpl(
            variantType: VariantType,
            variantName: String,
            flavorCombo: FlavorCombination?,
            buildType: BuildTypeImpl): CommonVariantPropertiesImpl {

        val flavors: ImmutableList<ProductFlavor> = flavorCombo?.flavors ?: ImmutableList.of()

        val sourceSets: MutableList<AndroidSourceSet> = mutableListOf()

        // add Main.
        // FIXME log error if source sets don't exist?
        dslModelData.defaultConfigData.getSourceSet(variantType)?.let {
            sourceSets.add(it)
        }

        // add the flavors.
        flavors.forEach {
            dslModelData.flavorData[it.name]?.getSourceSet(variantType)?.let {
                sourceSets.add(it)
            }
        }

        // create multi-flavor sourceset, optional, and add it
        var multiFlavorSourceSet: DefaultAndroidSourceSet? = null
        flavorCombo?.name.let {
            // use the internal container to bypass the seal
            multiFlavorSourceSet = dslModelData._sourceSets.maybeCreate(it)
        }

        multiFlavorSourceSet?.let {
            sourceSets.add(it)
        }

        // add the build type
        dslModelData.buildTypeData[buildType.name]?.getSourceSet(variantType)?.let {
            sourceSets.add(it)
        }

        // create variant source-set
        // use the internal container to bypass the seal
        var variantSourceSet: DefaultAndroidSourceSet? = null
        if (!flavors.isEmpty()) {
            variantSourceSet = dslModelData._sourceSets.maybeCreate(variantName)
            sourceSets.add(variantSourceSet)
        }

        return CommonVariantPropertiesImpl(
                variantName,
                buildType.name,
                flavorCombo?.flavorNames ?: ImmutableList.of(),
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
private class FlavorCombination(val name: String?, val flavors: ImmutableList<ProductFlavor>) {
    val flavorNames: List<String> = flavors.stream().map(Named::getName).collect(ImmutableCollectors.toImmutableList())
}

/**
 * Creates a list containing all combinations of ProductFlavors of the given dimensions.
 * @param flavorDimensions The dimensions each product flavor can belong to.
 * @param productFlavors An iterable of all ProductFlavors in the project..
 * @return A list of FlavorCombination representing all combinations of ProductFlavors.
 */
private fun createCombinations(
        flavorDimensions: List<String>,
        productFlavors: Set<ProductFlavor>,
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
        val map = ArrayListMultimap.create<String, ProductFlavor>()

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
        flavorAccumulator: MutableList<ProductFlavor>,
        index: Int,
        flavorDimensionList: List<String>,
        flavorMap: ListMultimap<String, ProductFlavor>,
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


private fun computeMultiFlavorName(flavors: List<ProductFlavor>): String {
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

