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
import org.mockito.Mockito

/**
 * Fixtures for creating variant model instance during tests.
 *
 * This is likely temporary. Once all the DSL is kotlin based we should be able to run the code
 * that understand the DSL without all the Gradle infrastructure.
 */

fun android(action: VariantModelBuilder.() -> Unit) : VariantModel {
    val modelBuilder = VariantModelBuilder()
    action(modelBuilder)
    return modelBuilder.toModel()
}

class VariantModelBuilder {
    private val dslScope: DslScope = createFakeDslScope()
    val buildTypes: Container<BuildType> = Container { name -> BuildType(name, dslScope) }
    val productFlavors: Container<ProductFlavor> = Container { name -> ProductFlavor(name, dslScope) }

    fun buildTypes(action: Container<BuildType>.() -> Unit) {
        action(buildTypes)
    }

    fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
        action(productFlavors)
    }

    fun toModel() : VariantModel {
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

        return FakeVariantModel(
            defaultConfig,
            buildTypes,
            flavors,
            mapOf()
        )
    }
}

class Container<T>(
    private val factory: (String) -> T
) {

    val values: MutableList<T> = mutableListOf()

    fun create(name: String) : T = factory(name).also { values.add(it) }
    fun create(name: String, action: T.() -> Unit) = create(name).also { action(it) }
}


class FakeVariantModel(
    override val defaultConfig: ProductFlavorData<DefaultConfig>,
    override val buildTypes: Map<String, BuildTypeData>,
    override val productFlavors: Map<String, ProductFlavorData<ProductFlavor>>,
    override val signingConfigs: Map<String, SigningConfig>
): VariantModel
