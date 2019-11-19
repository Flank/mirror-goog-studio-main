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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.AppVariant
import com.android.build.api.variant.AppVariantProperties
import com.android.build.api.variant.DynamicFeatureVariant
import com.android.build.api.variant.DynamicFeatureVariantProperties
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantProperties
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.api.variant.impl.VariantOperations
import com.android.build.api.variant.impl.VariantScopeTransformers
import com.android.build.gradle.internal.scope.VariantScope
import org.gradle.api.Action

abstract class CommonExtensionImpl<VARIANT : Variant<VARIANT_PROPERTIES>, VARIANT_PROPERTIES : VariantProperties>
    : CommonExtension<VARIANT, VARIANT_PROPERTIES> {

    protected val variantOperations = VariantOperations<VARIANT>(VariantScopeTransformers.toVariant)
    protected val variantPropertiesOperations = VariantOperations<VARIANT_PROPERTIES>(
        VariantScopeTransformers.toVariantProperties
    )

    override fun onVariants(action: Action<VARIANT>) {
        variantOperations.actions.add(action)
    }

    override fun onVariants(action: (VARIANT) -> Unit) {
        variantOperations.actions.add(Action { action.invoke(it) } )
    }

    override fun onVariantProperties(action: Action<VARIANT_PROPERTIES>) {
        variantPropertiesOperations.actions.add(action)
    }

    override fun onVariantProperties(action: (VARIANT_PROPERTIES) -> Unit) {
        variantPropertiesOperations.actions.add(Action { action.invoke(it) } )
    }
}

class ApplicationExtensionImpl: CommonExtensionImpl<AppVariant, AppVariantProperties>(), ApplicationExtension, ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<AppVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<AppVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<AppVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, AppVariant::class.java)
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<AppVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations, AppVariantProperties::class.java)
    }
}

class DynamicFeatureExtensionImpl: CommonExtensionImpl<DynamicFeatureVariant, DynamicFeatureVariantProperties>(), DynamicFeatureExtension, ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<DynamicFeatureVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<DynamicFeatureVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<DynamicFeatureVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, DynamicFeatureVariant::class.java)
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<DynamicFeatureVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations, DynamicFeatureVariantProperties::class.java)
    }
}

class LibraryExtensionImpl: CommonExtensionImpl<LibraryVariant, LibraryVariantProperties>(), LibraryExtension, ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<LibraryVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<LibraryVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<LibraryVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, LibraryVariant::class.java)
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<LibraryVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations, LibraryVariantProperties::class.java)
    }
}

class TestExtensionImpl: CommonExtensionImpl<TestVariant, TestVariantProperties>(), TestExtension, ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<TestVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<TestVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<TestVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, TestVariant::class.java)
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<TestVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations, TestVariantProperties::class.java)
    }
}