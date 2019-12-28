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
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.variant2.createFakeDslScope
import com.android.builder.core.BuilderConstants
import com.google.common.collect.ImmutableList
import org.gradle.api.Named
import org.mockito.Mockito

/**
 * Entry point for creating [VariantInputModel] instance during tests, backed by real build types
 * and product flavors, instantiated and configured via Kotlin DSL
 *
 * This creates a basic input model with an internally initialized [DslScope].
 */
fun android(action: VariantInputModelBuilder.() -> Unit) : TestVariantInputModel {
    val modelBuilder = VariantInputModelBuilder()
    action(modelBuilder)
    return modelBuilder.toModel()
}

/**
 * Entry point for creating [VariantInputModel] instance during tests, backed by real build types
 * and product flavors, instantiated and configured via Kotlin DSL
 *
 * This creates a basic input model with a a provided [DslScope].
 */
fun android(dslScope: DslScope, action: VariantInputModelBuilder.() -> Unit) : TestVariantInputModel {
    val modelBuilder = VariantInputModelBuilder(dslScope)
    action(modelBuilder)
    return modelBuilder.toModel()
}

/**
 * Entry point for creating [VariantInputModel] instance during tests, backed by real build types
 * and product flavors, instantiated and configured via Kotlin DSL
 *
 * This creates an input model, already initialited with the default build types (debug and release)
 * with a provided [DslScope]
 */
fun androidWithDefaults(
    dslScope: DslScope,
    action: VariantInputModelBuilder.() -> Unit
) : TestVariantInputModel {
    val modelBuilder = VariantInputModelBuilder(dslScope).also { it.createDefaults() }
    modelBuilder.buildTypes {
        create("debug")
        create("release")
    }
    action(modelBuilder)
    return modelBuilder.toModel()
}

class VariantInputModelBuilder(private val dslScope: DslScope = createFakeDslScope()) {
    val buildTypes: Container<BuildType> = Container { name -> BuildType(name, dslScope) }
    val productFlavors: Container<ProductFlavor> = Container { name -> ProductFlavor(name, dslScope) }

    fun buildTypes(action: Container<BuildType>.() -> Unit) {
        action(buildTypes)
    }

    fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
        action(productFlavors)
    }

    fun toModel() : TestVariantInputModel {
        val buildTypes = buildTypes.values.map {
            val mainSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val androidTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val unitTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)

            BuildTypeData(it, mainSourceSet, androidTestSourceSet, unitTestSourceSet)
        }.associateBy { it.buildType.name }

        val flavors = productFlavors.values.map {
            val mainSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val androidTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)
            val unitTestSourceSet = Mockito.mock(DefaultAndroidSourceSet::class.java)

            ProductFlavorData(it, mainSourceSet, androidTestSourceSet, unitTestSourceSet)
        }.associateBy { it.productFlavor.name }

        // the default Config
        val defaultConfig = ProductFlavorData(
            DefaultConfig(BuilderConstants.MAIN, dslScope),
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


    }
}

class Container<T: Named>(
    private val factory: (String) -> T
) {

    val values: MutableList<T> = mutableListOf()

    fun create(name: String) : T = maybeCreate(name)
    fun create(name: String, action: T.() -> Unit) = maybeCreate(name).also { action(it) }

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
    override val defaultConfig: ProductFlavorData<DefaultConfig>,
    override val buildTypes: Map<String, BuildTypeData>,
    override val productFlavors: Map<String, ProductFlavorData<ProductFlavor>>,
    override val signingConfigs: Map<String, SigningConfig>,
    /**
     * Implicit dimension list, gathered from looking at all the flavors in the order
     * they were added.
     * This allows not having to declare them during tests to simplify the fake DSL.
     */
    val implicitFlavorDimensions: List<String>
): VariantInputModel

