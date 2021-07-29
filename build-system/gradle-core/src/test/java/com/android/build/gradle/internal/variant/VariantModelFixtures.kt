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

import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.DefaultConfigData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.core.BuilderConstants
import com.google.common.collect.ImmutableList
import org.gradle.api.Named
import org.mockito.Mockito

/**
 * Basic DSL entry point for simulating a [VariantInputModel]
 *
 * This may later be replaced by the real DSL extension if they can be run without a full
 * project/plugin
 */
interface VariantInputModelDsl {
    fun defaultConfig(action: DefaultConfig.() -> Unit)
    fun buildTypes(action: Container<BuildType>.() -> Unit)
    fun productFlavors(action: Container<ProductFlavor>.() -> Unit)
}

/**
 * Fake DSL Container to simulate [VariantInputModel]
 *
 * This may later be replaced by the real DSL extension if they can be run without a full
 * project/plugin
 */
interface Container<T: Named> {
    fun create(name: String): T
    fun create(name: String, action: T.() -> Unit): T
}

/**
 * DSL-type builder to create instances of [VariantInputModel].
 *
 * After running the DSL, use [toModel]
 */
class VariantInputModelBuilder(
    private val dslServices: DslServices = createDslServices()
): VariantInputModelDsl {
    val defaultConfig: DefaultConfig = dslServices.newDecoratedInstance(DefaultConfig::class.java, BuilderConstants.MAIN, dslServices)
    val buildTypes: ContainerImpl<BuildType> = ContainerImpl { name ->
        dslServices.newDecoratedInstance(BuildType::class.java, name, dslServices)
    }
    val productFlavors: ContainerImpl<ProductFlavor> = ContainerImpl { name ->
        dslServices.newDecoratedInstance(ProductFlavor::class.java, name, dslServices)
    }

    override fun defaultConfig(action: DefaultConfig.() -> Unit) {
        action(defaultConfig)
    }

    override fun buildTypes(action: Container<BuildType>.() -> Unit) {
        action(buildTypes)
    }

    override fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
        action(productFlavors)
    }

    fun toModel() : TestVariantInputModel {
        val buildTypes = buildTypes.values.map {
            val mainSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val testFixturesSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val androidTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val unitTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)

            BuildTypeData(it, mainSourceSet, testFixturesSourceSet, androidTestSourceSet, unitTestSourceSet)
        }.associateBy { it.buildType.name }

        val flavors = productFlavors.values.map {
            val mainSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val testFixturesSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val androidTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val unitTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)

            ProductFlavorData(it, mainSourceSet, testFixturesSourceSet, androidTestSourceSet, unitTestSourceSet)
        }.associateBy { it.productFlavor.name }

        // the default Config
        val defaultConfig = DefaultConfigData(
            defaultConfig,
            Mockito.mock(DefaultAndroidSourceSet::class.java),
            Mockito.mock(DefaultAndroidSourceSet::class.java),
            Mockito.mock(DefaultAndroidSourceSet::class.java),
            Mockito.mock(DefaultAndroidSourceSet::class.java)
        )

        // compute the implicit dimension list
        val dimensionBuilder = ImmutableList.builder<String>()
        val nameSet = mutableSetOf<String>()
        for (flavor in productFlavors.values) {
            val dim = flavor.dimension ?: continue
            if (!nameSet.contains(dim)) {
                nameSet.add(dim)
                dimensionBuilder.add(dim)
            }
        }

        return TestVariantInputModel(
            defaultConfig,
            buildTypes,
            flavors,
            mapOf(),
            dimensionBuilder.build()
        )
    }

    fun createDefaults() {
        buildTypes {
            create("debug") {
                isDebuggable = true
            }
            create("release")
        }
    }
}

class ContainerImpl<T: Named>(
    private val factory: (String) -> T
): Container<T> {

    val values: MutableList<T> = mutableListOf()

    override fun create(name: String) : T = maybeCreate(name)
    override fun create(name: String, action: T.() -> Unit) = maybeCreate(name).also { action(it) }

    private fun maybeCreate(name: String): T {
        val result = values.find { it.name == name }
        if (result != null) {
            return result
        }

        return factory(name).also { values.add(it) }
    }
}

/**
 * Implementation of [VariantInputModel] adding an implicit flavor dimension list.
 */
class TestVariantInputModel(
    override val defaultConfigData: DefaultConfigData<DefaultConfig>,
    override val buildTypes: Map<String, BuildTypeData<BuildType>>,
    override val productFlavors: Map<String, ProductFlavorData<ProductFlavor>>,
    override val signingConfigs: Map<String, SigningConfig>,
    /**
     * Implicit dimension list, gathered from looking at all the flavors in the order
     * they were added.
     * This allows not having to declare them during tests to simplify the fake DSL.
     */
    val implicitFlavorDimensions: List<String>,
    override val sourceSetManager: SourceSetManager = Mockito.mock(SourceSetManager::class.java)
): VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>

