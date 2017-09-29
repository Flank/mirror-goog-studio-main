/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.api.dsl.extensions

import com.android.build.api.dsl.extension.VariantAwareProperties
import com.android.build.api.dsl.extension.VariantCallbackHandler
import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.dsl.variant.VariantFilter
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.dsl.sealing.SealableList
import com.android.build.gradle.internal.api.dsl.sealing.SealableNamedDomainObjectContainer
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.variant2.DslModelData
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

class VariantAwarePropertiesImpl(
            dslModelData: DslModelData,
            private val deprecationReporter: DeprecationReporter,
            issueReporter: EvalIssueReporter)
        : SealableObject(issueReporter),
        VariantAwareProperties,
        DefaultConfig by dslModelData.defaultConfig {

    override val productFlavors: SealableNamedDomainObjectContainer<ProductFlavor, ProductFlavorImpl> =
            SealableNamedDomainObjectContainer(
                    dslModelData.productFlavors, ProductFlavorImpl::class.java, issueReporter)
    override val buildTypes: SealableNamedDomainObjectContainer<BuildType, BuildTypeImpl> =
            SealableNamedDomainObjectContainer(
                    dslModelData.buildTypes, BuildTypeImpl::class.java, issueReporter)
    override val signingConfigs: SealableNamedDomainObjectContainer<SigningConfig, SigningConfigImpl> =
            SealableNamedDomainObjectContainer(
                    dslModelData.signingConfigs, SigningConfigImpl::class.java, issueReporter)

    private val _defaultConfig = dslModelData.defaultConfig

    private val _flavorDimensions: SealableList<String> = SealableList.new(issueReporter)
    private val _variantFilters: SealableList<Action<VariantFilter>> = SealableList.new(issueReporter)
    private val _preVariants: SealableList<Action<Void>> = SealableList.new(issueReporter)
    private val _postVariants: SealableList<Action<List<Variant>>> = SealableList.new(issueReporter)
    private val _variants: VariantCallbackHandler<Variant> =
            VariantCallbackHandlerShim(dslModelData.createVariantCallbackHandler())

    override var variantFilters:MutableList<Action<VariantFilter>>
        get() = _variantFilters
        set(value) {
            _variantFilters.reset(value)
        }

    override var flavorDimensions: MutableList<String>
        get() = _flavorDimensions
        set(value) {
            _flavorDimensions.reset(value)
        }

    override fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>) {
        action.execute(buildTypes)
    }

    override fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>) {
        action.execute(productFlavors)
    }

    override fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>) {
        action.execute(signingConfigs)
    }

    override var preVariantCallbacks: MutableList<Action<Void>>
        get() = _preVariants
        set(value) {
            _preVariants.reset(value)
        }

    override fun preVariantCallback(action: Action<Void>) {
        if (checkSeal()) {
            _preVariants.add(action)
        }
    }

    override val variants: VariantCallbackHandler<Variant>
        get() = _variants

    override fun variantFilter(action: Action<VariantFilter>) {
        if (checkSeal()) {
            _variantFilters.add(action)
        }
    }

    override var postVariants: MutableList<Action<List<Variant>>>
        get() = _postVariants
        set(value) {
            _postVariants.reset(value)
        }

    override fun postVariantCallback(action: Action<List<Variant>>) {
        if (checkSeal()) {
            _postVariants.add(action)
        }
    }

    override fun seal() {
        super.seal()
        _defaultConfig.seal()
        _flavorDimensions.seal()
        _preVariants.seal()
        _variantFilters.seal()
        _postVariants.seal()

        productFlavors.seal()
        buildTypes.seal()
        signingConfigs.seal()
    }

    // DEPRECATED

    @Suppress("OverridingDeprecatedMember")
    override var flavorDimensionList: MutableList<String>
        get() {
            deprecationReporter.reportDeprecatedUsage(
                    "android.flavorDimensions",
                    "android.flavorDimensionList",
                    DeprecationReporter.DeprecationTarget.VERSION_4_0)
            return flavorDimensions
        }
        set(value) {
            deprecationReporter.reportDeprecatedUsage(
                    "android.flavorDimensions",
                    "android.flavorDimensionList",
                    DeprecationReporter.DeprecationTarget.VERSION_4_0)
            flavorDimensions = value
        }

    @Suppress("OverridingDeprecatedMember")
    override val defaultConfig: DefaultConfig
        get() {
            deprecationReporter.reportDeprecatedUsage(
                    "android",
                    "android.defaultConfig",
                    DeprecationReporter.DeprecationTarget.VERSION_4_0)
            return _defaultConfig
        }

    @Suppress("OverridingDeprecatedMember")
    override fun defaultConfig(action: Action<DefaultConfig>) {
        deprecationReporter.reportDeprecatedUsage(
                "android",
                "android.defaultConfig",
                DeprecationReporter.DeprecationTarget.VERSION_4_0)
        action.execute(_defaultConfig)
    }
}