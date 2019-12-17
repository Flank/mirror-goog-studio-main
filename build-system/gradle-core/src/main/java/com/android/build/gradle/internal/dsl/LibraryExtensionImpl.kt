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

import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.NamedDomainObjectContainer

/** Internal implementation of the 'new' DSL interface */
class LibraryExtensionImpl(
    dslScope: DslScope,
    buildTypes: NamedDomainObjectContainer<BuildType>,
    defaultConfig: DefaultConfig,
    productFlavors: NamedDomainObjectContainer<ProductFlavor>,
    signingConfigs: NamedDomainObjectContainer<SigningConfig>
) :
    CommonExtensionImpl<
            LibraryBuildFeatures,
            BuildType,
            DefaultConfig,
            ProductFlavor,
            SigningConfig,
            LibraryVariant,
            LibraryVariantProperties>(
        dslScope,
        buildTypes,
        defaultConfig,
        productFlavors,
        signingConfigs
    ),
    LibraryExtension<
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

    override val buildFeatures: LibraryBuildFeatures =
        dslScope.objectFactory.newInstance(LibraryBuildFeaturesImpl::class.java)

    override fun executeVariantOperations(variant: LibraryVariant) {
        variantOperations.executeActions(variant)
    }

    override fun executeVariantPropertiesOperations(variant: LibraryVariantProperties) {
        variantPropertiesOperations.executeActions(variant)
    }

    @Suppress("UNCHECKED_CAST")
    override val onVariants: GenericVariantFilterBuilder<LibraryVariant>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            LibraryVariant::class.java
        ) as GenericVariantFilterBuilder<LibraryVariant>

    @Suppress("UNCHECKED_CAST")
    override val onVariantProperties: GenericVariantFilterBuilder<LibraryVariantProperties>
        get() = dslScope.objectFactory.newInstance(
            GenericVariantFilterBuilderImpl::class.java,
            dslScope,
            variantOperations,
            LibraryVariantProperties::class.java
        ) as GenericVariantFilterBuilder<LibraryVariantProperties>
}