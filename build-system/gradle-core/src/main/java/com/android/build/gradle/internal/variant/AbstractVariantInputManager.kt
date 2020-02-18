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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.core.VariantBuilder.Companion.computeSourceSetName
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.google.common.collect.Maps

/**
 * Implementation of [VariantInputModel].
 *
 *
 * This gets filled by the DSL/API execution.
 */
class AbstractVariantInputManager(
    private val globalScope: GlobalScope,
    private val extension: BaseExtension,
    private val variantFactory: VariantFactory<*, *>,
    private val sourceSetManager: SourceSetManager
) : VariantInputModel {

    override val defaultConfig: ProductFlavorData<DefaultConfig>
    override val buildTypes = mutableMapOf<String, BuildTypeData>()
    override val productFlavors = mutableMapOf<String, ProductFlavorData<ProductFlavor>>()
    override val signingConfigs = mutableMapOf<String, SigningConfig>()

    init {
        val mainSourceSet =
            extension.sourceSets.getByName(BuilderConstants.MAIN) as DefaultAndroidSourceSet
        var androidTestSourceSet: DefaultAndroidSourceSet? = null
        var unitTestSourceSet: DefaultAndroidSourceSet? = null
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                extension.sourceSets.getByName(VariantType.ANDROID_TEST_PREFIX) as DefaultAndroidSourceSet
            unitTestSourceSet =
                extension.sourceSets.getByName(VariantType.UNIT_TEST_PREFIX) as DefaultAndroidSourceSet
        }
        defaultConfig =
            ProductFlavorData(
                extension.defaultConfig,
                mainSourceSet,
                androidTestSourceSet,
                unitTestSourceSet
            )
    }

    fun addSigningConfig(signingConfig: SigningConfig) {
        signingConfigs[signingConfig.name] = signingConfig
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set, and adding it to
     * the map.
     *
     * @param buildType the build type.
     */
    fun addBuildType(buildType: BuildType) {
        val name = buildType.name
        checkName(name, "BuildType")
        if (productFlavors.containsKey(name)) {
            throw RuntimeException("BuildType names cannot collide with ProductFlavor names")
        }
        val mainSourceSet =
            sourceSetManager.setUpSourceSet(name) as DefaultAndroidSourceSet
        var androidTestSourceSet: DefaultAndroidSourceSet? = null
        var unitTestSourceSet: DefaultAndroidSourceSet? = null
        if (variantFactory.hasTestScope()) {
            if (buildType.name == extension.testBuildType) {
                androidTestSourceSet = sourceSetManager.setUpTestSourceSet(
                    computeSourceSetName(
                        buildType.name, VariantTypeImpl.ANDROID_TEST
                    )
                ) as DefaultAndroidSourceSet
            }
            unitTestSourceSet = sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    buildType.name, VariantTypeImpl.UNIT_TEST
                )
            ) as DefaultAndroidSourceSet
        }
        val buildTypeData = BuildTypeData(
            buildType, mainSourceSet, androidTestSourceSet, unitTestSourceSet
        )
        buildTypes[name] = buildTypeData
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets, and adding
     * it to the map.
     *
     * @param productFlavor the product flavor
     */
    fun addProductFlavor(productFlavor: ProductFlavor) {
        val name = productFlavor.name
        checkName(name, "ProductFlavor")
        if (buildTypes.containsKey(name)) {
            throw RuntimeException("ProductFlavor names cannot collide with BuildType names")
        }
        val mainSourceSet =
            sourceSetManager.setUpSourceSet(productFlavor.name) as DefaultAndroidSourceSet
        var androidTestSourceSet: DefaultAndroidSourceSet? = null
        var unitTestSourceSet: DefaultAndroidSourceSet? = null
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet = sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    productFlavor.name, VariantTypeImpl.ANDROID_TEST
                )
            ) as DefaultAndroidSourceSet
            unitTestSourceSet = sourceSetManager.setUpTestSourceSet(
                computeSourceSetName(
                    productFlavor.name, VariantTypeImpl.UNIT_TEST
                )
            ) as DefaultAndroidSourceSet
        }
        val productFlavorData =
            ProductFlavorData(
                productFlavor, mainSourceSet, androidTestSourceSet, unitTestSourceSet
            )
        productFlavors[productFlavor.name] = productFlavorData
    }

    companion object {
        private fun checkName(name: String, displayName: String) {
            checkPrefix(
                name,
                displayName,
                VariantType.ANDROID_TEST_PREFIX
            )
            checkPrefix(
                name,
                displayName,
                VariantType.UNIT_TEST_PREFIX
            )
            if (BuilderConstants.LINT == name) {
                throw RuntimeException(
                    String.format(
                        "%1\$s names cannot be %2\$s",
                        displayName,
                        BuilderConstants.LINT
                    )
                )
            }
        }

        private fun checkPrefix(
            name: String,
            displayName: String,
            prefix: String
        ) {
            if (name.startsWith(prefix)) {
                throw RuntimeException(
                    String.format(
                        "%1\$s names cannot start with '%2\$s'",
                        displayName,
                        prefix
                    )
                )
            }
        }
    }

}