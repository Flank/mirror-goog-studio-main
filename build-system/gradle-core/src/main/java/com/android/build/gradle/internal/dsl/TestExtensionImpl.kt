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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.TestBuildFeatures
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantProperties
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
class TestExtensionImpl(
    dslScope: DslScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
) :
    CommonExtensionImpl<
            TestBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            TestVariant,
            TestVariantProperties>(
        dslScope,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),
    TestExtension<
            BuildType,
            CmakeOptions,
            CompileOptions,
            DefaultConfig,
            ExternalNativeBuild,
            JacocoOptions,
            NdkBuildOptions,
            ProductFlavor,
            SigningConfig,
            TestOptions,
            TestOptions.UnitTestOptions> {

    override val buildFeatures: TestBuildFeatures =
        dslScope.objectFactory.newInstance(TestBuildFeaturesImpl::class.java)

    override fun executeVariantOperations(variant: TestVariant) {
        variantOperations.executeActions(variant)
    }

    override fun executeVariantPropertiesOperations(variant: TestVariantProperties) {
        variantPropertiesOperations.executeActions(variant)
    }

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericVariantFilterBuilder<TestVariant>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            TestVariant::class.java
        ) as GenericVariantFilterBuilder<TestVariant>

    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericVariantFilterBuilder<TestVariantProperties>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            TestVariantProperties::class.java
        ) as GenericVariantFilterBuilder<TestVariantProperties>
}
