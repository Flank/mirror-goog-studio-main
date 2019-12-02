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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.core.VariantBuilder.Companion.getBuilder
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SourceProvider
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import java.lang.RuntimeException
import java.util.function.BooleanSupplier

/** Builder for [GradleVariantConfiguration].
 *
 * This allows setting all temporary items on the builder before actually
 * instantiating the configuration, in order to keep it immutable.
 *
 * Use [getBuilder] as an entry point.
 */
abstract class VariantBuilder protected constructor(
    val variantType: VariantType,
    protected val defaultConfig: DefaultConfig,
    protected val defaultSourceProvider: SourceProvider,
    protected val buildType: BuildType,
    protected val buildTypeSourceProvider: SourceProvider? = null,
    protected val signingConfigOverride: SigningConfig?,
    protected val manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    protected val projectOptions: ProjectOptions,
    protected val issueReporter: EvalIssueReporter,
    protected val isInExecutionPhase: BooleanSupplier
) {

    companion object {
        /**
         * Returns a new builder
         */
        @JvmStatic
        fun getBuilder(
            variantType: VariantType,
            defaultConfig: DefaultConfig,
            defaultSourceSet: SourceProvider,
            buildType: BuildType,
            buildTypeSourceSet: SourceProvider? = null,
            signingConfigOverride: SigningConfig? = null,
            manifestAttributeSupplier: ManifestAttributeSupplier? = null,
            projectOptions: ProjectOptions,
            issueReporter: EvalIssueReporter,
            isInExecutionPhase: BooleanSupplier

        ): VariantBuilder {
            // if this is the test module, we have a slightly different builder
            return if (variantType.isForTesting && !variantType.isTestComponent) {
                TestModuleConfigurationBuilder(
                    variantType,
                    defaultConfig,
                    defaultSourceSet,
                    buildType,
                    buildTypeSourceSet,
                    signingConfigOverride,
                    manifestAttributeSupplier,
                    projectOptions,
                    issueReporter,
                    isInExecutionPhase

                )
            } else {
                VariantConfigurationBuilder(
                    variantType,
                    defaultConfig,
                    defaultSourceSet,
                    buildType,
                    buildTypeSourceSet,
                    signingConfigOverride,
                    manifestAttributeSupplier,
                    projectOptions,
                    issueReporter,
                    isInExecutionPhase
                )
            }
        }

        /**
         * Returns the full, unique name of the variant in camel case (starting with a lower case),
         * including BuildType, Flavors and Test (if applicable).
         *
         * This is to be used for the normal variant name. In case of Feature plugin, the library
         * side will be called the same as for library plugins, while the feature side will add
         * 'feature' to the name.
         *
         * Also computes the flavor name if applicable
         */
        @JvmStatic
        @JvmOverloads
        fun computeName(
            variantType: VariantType,
            flavors: List<com.android.builder.model.ProductFlavor>,
            buildType: com.android.builder.model.BuildType,
            flavorNameCallback: ((String) -> Unit)? = null
        ): String {
            // compute the flavor name
            val flavorName = if (flavors.isEmpty()) {
                ""
            } else {
                combineAsCamelCase(flavors, com.android.builder.model.ProductFlavor::getName)
            }
            flavorNameCallback?.let {
                flavorNameCallback.invoke(flavorName)
            }

            val sb = StringBuilder()
            if (flavorName.isNotEmpty()) {
                sb.append(flavorName)
                sb.appendCapitalized(buildType.name)
            } else {
                sb.append(buildType.name)
            }
            if (variantType.isTestComponent) {
                sb.append(variantType.suffix)
            }
            return sb.toString()
        }

    }

    private lateinit var variantName: String
    private lateinit var multiFlavorName: String

    val name: String
        get() {
            if (!::variantName.isInitialized) {
                computeNames()
            }

            return variantName
        }

    val flavorName: String
        get() {
            if (!::multiFlavorName.isInitialized) {
                computeNames()
            }
            return multiFlavorName

        }

    protected val flavors = mutableListOf<Pair<ProductFlavor, SourceProvider>>()

    var variantSourceProvider: SourceProvider? = null
    var multiFlavorSourceProvider: SourceProvider? = null
    var testedConfig: GradleVariantConfiguration? = null

    fun addProductFlavor(
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider
    ) {
        if (::variantName.isInitialized) {
            throw RuntimeException("call to getName() before calling all addProductFlavor")
        }
        flavors.add(Pair(productFlavor, sourceProvider))
    }

    /** Creates a variant configuration  */
    abstract fun createVariantConfig(): GradleVariantConfiguration


    /**
     * computes the name for the variant and the multi-flavor combination
     */
    private fun computeNames() {
        variantName = computeName(
            variantType,
            flavors.map { it.first },
            buildType
        ) {
            multiFlavorName = it
        }
    }
}

/** Builder for non test plugin variant configurations  */
private class VariantConfigurationBuilder(
    variantType: VariantType,
    defaultConfig: DefaultConfig,
    defaultSourceSet: SourceProvider,
    buildType: BuildType,
    buildTypeSourceSet: SourceProvider? = null,
    signingConfigOverride: SigningConfig?,
    manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    projectOptions: ProjectOptions,
    issueReporter: EvalIssueReporter,
    isInExecutionPhase: BooleanSupplier
) : VariantBuilder(
    variantType,
    defaultConfig,
    defaultSourceSet,
    buildType,
    buildTypeSourceSet,
    signingConfigOverride,
    manifestAttributeSupplier,
    projectOptions,
    issueReporter,
    isInExecutionPhase
) {

    override fun createVariantConfig(): GradleVariantConfiguration {
        return GradleVariantConfiguration(
            name,
            flavorName,
            variantType,
            defaultConfig,
            defaultSourceProvider,
            buildType,
            buildTypeSourceProvider,
            // this could be removed once the product flavor is internal only.
            flavors.map { it.first }.toImmutableList(),
            flavors.map { it.second }.toImmutableList(),
            multiFlavorSourceProvider,
            variantSourceProvider,
            signingConfigOverride,
            manifestAttributeSupplier,
            testedConfig,
            projectOptions,
            issueReporter,
            isInExecutionPhase
        )
    }
}

/**
 * Creates a [GradleVariantConfiguration] for a testing module variant.
 *
 *
 * The difference from the regular modules is how the original application id,
 * and application id are resolved. Our build process supports the absence of manifest
 * file for these modules, and that is why the value resolution for these attributes
 * is different.
 */
private class TestModuleConfigurationBuilder(
    variantType: VariantType,
    defaultConfig: DefaultConfig,
    defaultSourceSet: SourceProvider,
    buildType: BuildType,
    buildTypeSourceSet: SourceProvider? = null,
    signingConfigOverride: SigningConfig?,
    manifestAttributeSupplier: ManifestAttributeSupplier? = null,
    projectOptions: ProjectOptions,
    issueReporter: EvalIssueReporter,
    isInExecutionPhase: BooleanSupplier
) : VariantBuilder(
    variantType,
    defaultConfig,
    defaultSourceSet,
    buildType,
    buildTypeSourceSet,
    signingConfigOverride,
    manifestAttributeSupplier,
    projectOptions,
    issueReporter,
    isInExecutionPhase
) {

    override fun createVariantConfig(): GradleVariantConfiguration {
        return object:  GradleVariantConfiguration(
            name,
            flavorName,
            variantType,
            defaultConfig,
            defaultSourceProvider,
            buildType,
            buildTypeSourceProvider,
            // this could be removed once the product flavor is internal only.
            flavors.map { it.first }.toImmutableList(),
            flavors.map { it.second }.toImmutableList(),
            multiFlavorSourceProvider,
            variantSourceProvider,
            signingConfigOverride,
            manifestAttributeSupplier,
            testedConfig,
            projectOptions,
            issueReporter,
            isInExecutionPhase
        ) {
            override val applicationId: String
                get() {
                    val applicationId = mergedFlavor.testApplicationId
                    if (applicationId != null && applicationId.isNotEmpty()) {
                        return applicationId
                    }

                    return super.applicationId
                }

            override val originalApplicationId: String
                get() = applicationId

            override val testApplicationId: String
                get() = applicationId
        }
    }
}
