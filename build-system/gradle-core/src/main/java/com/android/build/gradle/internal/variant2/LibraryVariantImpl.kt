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

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.extension.VariantOrExtensionProperties
import com.android.build.api.dsl.model.BuildTypeOrVariant
import com.android.build.api.dsl.model.ProductFlavorOrVariant
import com.android.build.api.dsl.model.VariantProperties
import com.android.build.api.dsl.variant.AndroidTestVariant
import com.android.build.api.dsl.variant.CommonVariantProperties
import com.android.build.api.dsl.variant.LibraryVariant
import com.android.build.api.dsl.variant.UnitTestVariant
import com.android.build.api.dsl.variant.Variant
import com.android.build.gradle.internal.api.dsl.extensions.VariantOrExtensionPropertiesImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.api.dsl.variant.LibraryVariantShim
import com.android.build.gradle.internal.api.dsl.variant.CommonVariantPropertiesImpl
import com.android.build.gradle.internal.api.dsl.variant.SealableVariant
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueReporter

/** Internal variant implementation.
 *
 * External access is via [LibraryVariantShim]
 */
class LibraryVariantImpl(
        override val variantType: VariantType,
        private val variantProperties: VariantPropertiesImpl,
        private val productFlavorOrVariant: ProductFlavorOrVariantImpl,
        private val buildTypeOrVariant: BuildTypeOrVariantImpl,
        private val variantExtensionProperties: VariantOrExtensionPropertiesImpl,
        private val commonVariantProperties: CommonVariantPropertiesImpl,
        private val variantDispatcher: Map<VariantType, Map<Variant, Variant>>,
        issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter),
        LibraryVariant,
        SealableVariant,
        VariantProperties by variantProperties,
        ProductFlavorOrVariant by productFlavorOrVariant,
        BuildTypeOrVariant by buildTypeOrVariant,
        VariantOrExtensionProperties by variantExtensionProperties,
        CommonVariantProperties by commonVariantProperties {

    override val androidTestVariant: AndroidTestVariant?
        get() = variantDispatcher[VariantType.ANDROID_TEST]?.get(this) as AndroidTestVariant?

    override val unitTestVariant: UnitTestVariant?
        get() = variantDispatcher[VariantType.UNIT_TEST]?.get(this) as UnitTestVariant?

    override fun createShim(): Variant = LibraryVariantShim(this)

    override fun seal() {
        super.seal()
        variantProperties.seal()
        productFlavorOrVariant.seal()
        buildTypeOrVariant.seal()
        variantExtensionProperties.seal()
        commonVariantProperties.seal()
    }
}