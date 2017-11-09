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

package com.android.build.api.dsl.extension

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.dsl.variant.Variant
import com.android.build.api.dsl.variant.VariantFilter
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer

/** Partial extension properties for modules that have variants made of [ProductFlavor] and
 * [BuildType]
 */
@Incubating
interface VariantAwareProperties : DefaultConfig {

    /** Build types used by this project.  */
    val buildTypes: NamedDomainObjectContainer<BuildType>

    fun buildTypes(action: Action<NamedDomainObjectContainer<BuildType>>)

    /** List of flavor dimensions.  */
    var flavorDimensions: MutableList<String>

    /** All product flavors used by this project.  */
    val productFlavors: NamedDomainObjectContainer<ProductFlavor>

    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavor>>)

    /** Signing configs used by this project.  */
    val signingConfigs: NamedDomainObjectContainer<SigningConfig>

    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfig>>)

    /** variant filters */
    var variantFilters: MutableList<Action<VariantFilter>>

    /** registers a new variant filter */
    fun variantFilter(action: Action<VariantFilter>)

    /** pre variant callbacks  */
    var preVariantCallbacks: MutableList<Action<Void>>
    /** register a pre variant callbacks  */
    fun preVariantCallback(action: Action<Void>)

    /** Callback object to register callbacks for variants */
    val variants: VariantCallbackHandler<Variant>

    /** post variant callbacks  */
    var postVariants: MutableList<Action<Collection<Variant>>>

    /** register a post-variant callback */
    fun postVariantCallback(action: Action<Collection<Variant>>)

    @Deprecated("Use flavorDimensions")
    var flavorDimensionList: MutableList<String>

    /** Default config, shared by all flavors.  */
    @Deprecated("Use properties on extension itself")
    val defaultConfig: DefaultConfig

    @Deprecated("Use properties on extension itself")
    fun defaultConfig(action: Action<DefaultConfig>)

}
