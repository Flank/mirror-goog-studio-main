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

import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.TestVariantProperties
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.NamedDomainObjectContainer

class DynamicFeatureExtensionImpl(
    dslScope: DslScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
)  :
    CommonExtensionImpl<
            DynamicFeatureBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            DynamicFeatureVariant,
            DynamicFeatureVariantProperties>(
        dslScope,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),

    DynamicFeatureExtension<
            AaptOptions,
            AdbOptions,
            BuildType,
            CmakeOptions,
            CompileOptions,
            DataBindingOptions,
            DefaultConfig,
            ExternalNativeBuild,
            JacocoOptions,
            NdkBuildOptions,
            ProductFlavor,
            SigningConfig,
            TestOptions,
            TestOptions.UnitTestOptions> {

    override val buildFeatures: DynamicFeatureBuildFeatures =
        dslScope.objectFactory.newInstance(DynamicFeatureBuildFeaturesImpl::class.java)

    override fun executeVariantOperations(variant: DynamicFeatureVariant) {
        variantOperations.executeActions(variant)
    }

    override fun executeVariantPropertiesOperations(variant: DynamicFeatureVariantProperties) {
        variantPropertiesOperations.executeActions(variant)
    }

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericVariantFilterBuilder<DynamicFeatureVariant>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            DynamicFeatureVariant::class.java
        ) as GenericVariantFilterBuilder<DynamicFeatureVariant>

    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericVariantFilterBuilder<DynamicFeatureVariantProperties>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            DynamicFeatureVariantProperties::class.java
        ) as GenericVariantFilterBuilder<DynamicFeatureVariantProperties>
}
