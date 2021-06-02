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

@file:JvmName("PublishingUtils")

package com.android.build.gradle.internal.utils

import com.android.build.api.dsl.SingleVariant
import com.android.build.gradle.internal.dsl.AbstractPublishing
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.LibraryPublishingImpl
import com.android.build.gradle.internal.dsl.MultipleVariantsImpl
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.publishing.ComponentPublishingInfo
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.NamedDomainObjectContainer

fun createPublishingInfoForLibrary(
    publishing: LibraryPublishingImpl,
    projectOptions: ProjectOptions,
    variantName: String,
    buildType: BuildType,
    flavorList: List<ProductFlavor>,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    testFixtureMainVariantName: String?,
    issueReporter: IssueReporter
): VariantPublishingInfo {
    val optIn = publishingFeatureOptIn(publishing as AbstractPublishing<SingleVariant>, projectOptions)
    val components = mutableListOf<ComponentPublishingInfo>()
    // attach the testFixtures variants to the main variant component
    val variantBasedComponentName = testFixtureMainVariantName ?: variantName

    if (!optIn) {
        components.add(ComponentPublishingInfo(
            variantBasedComponentName,
            AbstractPublishing.Type.AAR))

        components.add(ComponentPublishingInfo(
            "all",
            AbstractPublishing.Type.AAR,
            ComponentPublishingInfo.AttributesConfig(
                buildType.name,
                flavorList.map { it.dimension!! }.toSet()
            ),
            isClassifierRequired = true
        ))
    } else {
        ensureComponentNameUniqueness(publishing, issueReporter)

        val singleVariant = publishing.singleVariants.find {
            if (testFixtureMainVariantName != null) {
                it.variantName == testFixtureMainVariantName
            } else {
                it.variantName == variantName
            }
        }
        if (singleVariant != null) {
            components.add(ComponentPublishingInfo(
                variantBasedComponentName,
                AbstractPublishing.Type.AAR
            ))
        }

        for (multipleVariant in publishing.multipleVariantsContainer) {
            ensureUsersInputCorrectness(multipleVariant, buildTypes, productFlavors, issueReporter)

            val buildTypeAttribute = computeBuildTypeAttribute(multipleVariant, buildType)
            val flavorDimensionAttributes = computeFlavorDimensionAttribute(multipleVariant)

            val isClassifierRequired = buildTypeAttribute != null || flavorDimensionAttributes.isNotEmpty()

            val publishThisVariant = multipleVariant.allVariants
                || isVariantSelectedExplicitly(multipleVariant, buildType, flavorList)

            if (publishThisVariant) {
                components.add(ComponentPublishingInfo(
                    multipleVariant.componentName,
                    AbstractPublishing.Type.AAR,
                    ComponentPublishingInfo.AttributesConfig(
                        buildTypeAttribute,
                        flavorDimensionAttributes
                    ),
                    isClassifierRequired)
                )
            }
        }
    }
    return VariantPublishingInfo(components)
}

private fun ensureUsersInputCorrectness(
    multipleVariant: MultipleVariantsImpl,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    issueReporter: IssueReporter
) {
    fun computeErrorMessage(element: String) =
        "Using non-existing $element when selecting variants to be published in " +
                "multipleVariants publishing DSL."

    val allBuildTypes = buildTypes.map { it.name }.toSet()
    for (includeBuildType in multipleVariant.includedBuildTypes) {
        if (!allBuildTypes.contains(includeBuildType)) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                computeErrorMessage("build type \"$includeBuildType\"")
            )
        }
    }

    for (entry in multipleVariant.includedFlavorDimensionAndValues.entries) {
        val flavorsWithSpecifiedDimension = productFlavors.filter { it.dimension == entry.key }
        if (flavorsWithSpecifiedDimension.isEmpty()) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                computeErrorMessage("dimension \"${entry.key}\"")
            )
        }
        val allFlavorValues = flavorsWithSpecifiedDimension.map { it.name }.toSet()
        for (flavorValue in entry.value) {
            if (!allFlavorValues.contains(flavorValue)) {
                issueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    computeErrorMessage("flavor value \"$flavorValue\"")
                )
            }
        }
    }
}

private fun computeBuildTypeAttribute(
    multipleVariant : MultipleVariantsImpl,
    buildType: BuildType
): String? {
    return if (multipleVariant.includedBuildTypes.size > 1 || multipleVariant.allVariants)
        buildType.name
    else
        null
}

private fun computeFlavorDimensionAttribute(
    multipleVariant : MultipleVariantsImpl,
): MutableSet<String> {
    val flavorDimensionAttributes = mutableSetOf<String>()
    for (entry in multipleVariant.includedFlavorDimensionAndValues.entries) {
        if (entry.value.size > 1 || multipleVariant.allVariants) {
            flavorDimensionAttributes.add(entry.key)
        }
    }
    return flavorDimensionAttributes
}

private fun isVariantSelectedExplicitly(
    multipleVariant : MultipleVariantsImpl,
    buildType: BuildType,
    flavorList: List<ProductFlavor>,
): Boolean {
    if (!multipleVariant.includedBuildTypes.contains(buildType.name)) {
        return false
    }
    for (productFlavor in flavorList) {
        val dimensionValue = multipleVariant.includedFlavorDimensionAndValues[productFlavor.dimension]
        if (dimensionValue == null || !dimensionValue.contains(productFlavor.name)) {
            return false
        }
    }
    return true
}

private fun ensureComponentNameUniqueness(
    publishing: LibraryPublishingImpl,
    issueReporter: IssueReporter
) {
    val singleVariantPubComponents = publishing.singleVariants.map { it.variantName }.toSet()
    publishing.multipleVariantsContainer.map {
        if (singleVariantPubComponents.contains(it.componentName)) {
            issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                "Publishing variants to the \"${it.componentName}\" component using both" +
                        " singleVariant and multipleVariants publishing DSL is not allowed."
            )
        }
    }
}

fun createPublishingInfoForApp(
    publishing: ApplicationPublishingImpl,
    projectOptions: ProjectOptions,
    variantName: String,
    hasDynamicFeatures: Boolean,
    issueReporter: IssueReporter
): VariantPublishingInfo {
    val optIn = publishingFeatureOptIn(publishing as AbstractPublishing<SingleVariant>, projectOptions)
    val components = mutableListOf<ComponentPublishingInfo>()
    if (optIn) {
        publishing.singleVariants.find { it.variantName == variantName }?.let {
            if (it.publishVariantAsApk) {
                // do not publish the APK(s) if there are dynamic feature.
                if (hasDynamicFeatures) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "When dynamic feature modules exist, publishing APK is not allowed."
                    )
                } else {
                    components.add(
                        ComponentPublishingInfo(variantName, AbstractPublishing.Type.APK))
                }
            } else {
                components.add(
                    ComponentPublishingInfo(variantName, AbstractPublishing.Type.AAB))
            }
        }
    } else {
        // do not publish the APK(s) if there are dynamic feature.
        if (!hasDynamicFeatures) {
            components.add(
                ComponentPublishingInfo(
                    "${variantName}_apk",
                    AbstractPublishing.Type.APK))
        }
        components.add(
            ComponentPublishingInfo(
                "${variantName}_aab",
                AbstractPublishing.Type.AAB))
    }
    return VariantPublishingInfo(components)
}

fun publishingFeatureOptIn(
    publishing: AbstractPublishing<SingleVariant>,
    projectOptions: ProjectOptions
): Boolean {
    val publishingDslUsed = if (publishing is LibraryPublishingImpl) {
        publishing.singleVariants.isNotEmpty() || publishing.multipleVariantsContainer.isNotEmpty()
    } else {
        publishing.singleVariants.isNotEmpty()
    }
    val disableComponentCreation =
        projectOptions.get(OptionalBooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION) == true
    return publishingDslUsed || disableComponentCreation
}
