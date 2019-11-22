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
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.variant2.createFakeDslScope
import org.gradle.api.Project
import org.mockito.Mockito

/**
 * Fixtures for creating variant model instance during tests.
 *
 * This is likely temporary. Once all the DSL is kotlin based we should be able to run the code
 * that understand the DSL without all the Gradle infrastructure.
 */

fun variantModel(action: VariantModelBuilder.() -> Unit) : VariantModel {
    val modelBuilder = VariantModelBuilder()
    action(modelBuilder)
    return modelBuilder.toModel()
}

class VariantModelBuilder {
    private val buildTypeBuilder = BuildTypeBuilder()
    private val flavorBuilder = ProductFlavorBuilder()

    fun buildTypes(action: BuildTypeBuilder.() -> Unit) {
        action(buildTypeBuilder)
    }

    fun productFlavors(action: ProductFlavorBuilder.() -> Unit) {
        action(flavorBuilder)
    }

    fun toModel() : VariantModel {
        val project = Mockito.mock(Project::class.java)
        val dslScope: DslScope = createFakeDslScope()

        val buildTypes = buildTypeBuilder.types.map {
            val btData = Mockito.mock(BuildTypeData::class.java)
            val bt = Mockito.mock(BuildType::class.java)

            Mockito.`when`(bt.name).thenReturn(it)
            Mockito.`when`(btData.buildType).thenReturn(bt)

            btData
        }.associateBy { it.buildType.name }

        val flavors = flavorBuilder.flavors.map {
            @Suppress("UNCHECKED_CAST")
            val pfData =
                Mockito.mock(ProductFlavorData::class.java) as ProductFlavorData<ProductFlavor>
            val pf = ProductFlavor(it.second, project, dslScope)
            val dimension = it.first
            if (dimension != null) {
                pf.setDimension(dimension)
            }

            Mockito.`when`(pfData.productFlavor).thenReturn(pf)

            pfData
        }.associateBy { it.productFlavor.name }

        @Suppress("UNCHECKED_CAST")
        val defaultConfig: ProductFlavorData<DefaultConfig> =
            Mockito.mock(ProductFlavorData::class.java) as ProductFlavorData<DefaultConfig>

        return FakeVariantModel(
            defaultConfig,
            buildTypes,
            flavors,
            mapOf()
        )
    }
}

class BuildTypeBuilder {
    val types = mutableListOf<String>()
    fun create(type: String) {
        types.add(type);
    }
}

class ProductFlavorBuilder {
    val flavors = mutableListOf<Pair<String?, String>>()
    fun create(name: String, action: FlavorContentBuilder.() -> Unit) {
        val content = FlavorContentBuilder()
        action(content)

        flavors.add(Pair(content.dimension, name))
    }
}

class FlavorContentBuilder {
    var dimension: String? = null
}

class FakeVariantModel(
    override val defaultConfig: ProductFlavorData<DefaultConfig>,
    override val buildTypes: Map<String, BuildTypeData>,
    override val productFlavors: Map<String, ProductFlavorData<ProductFlavor>>,
    override val signingConfigs: Map<String, SigningConfig>
): VariantModel
