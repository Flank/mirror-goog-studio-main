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
package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.BaseConfig
import com.google.common.collect.ImmutableList
import javax.inject.Inject
import org.gradle.api.provider.Property

/**
 * Encapsulates all product flavors properties for this project.
 *
 * Product flavors represent different versions of your project that you expect to co-exist on a
 * single device, the Google Play store, or repository. For example, you can configure 'demo' and
 * 'full' product flavors for your app, and each of those flavors can specify different features,
 * device requirements, resources, and application ID's--while sharing common source code and
 * resources. So, product flavors allow you to output different versions of your project by simply
 * changing only the components and settings that are different between them.
 *
 * Configuring product flavors is similar to
 * [configuring build types](https://developer.android.com/studio/build/build-variants.html#build-types):
 * add them to the `productFlavors` block of your project's `build.gradle` file
 * and configure the settings you want.
 *
 * Product flavors support the same properties as the
 * [com.android.build.gradle.BaseExtension.defaultConfig]
 * block—this is because `defaultConfig` defines a [ProductFlavor] object that the plugin
 * uses as the base configuration for all other flavors.
 * Each flavor you configure can then override any of the default values in
 * `defaultConfig`, such as the
 * [`applicationId`](https://d.android.com/studio/build/application-id.html).
 *
 * When using Android plugin 3.0.0 and higher,
 * *[each flavor must belong to a `dimension`](com.android.build.gradle.internal.dsl.ProductFlavor.html#com.android.build.gradle.internal.dsl.ProductFlavor:dimension)*.
 *
 * When you configure product flavors, the Android plugin automatically combines them with your
 * [com.android.build.gradle.internal.dsl.BuildType] configurations to
 * [create build variants](https://developer.android.com/studio/build/build-variants.html).
 * If the plugin creates certain build variants that you don't want, you can
 * [filter variants using `android.variantFilter`](https://developer.android.com/studio/build/build-variants.html#filter-variants).
 */
open class ProductFlavor @Inject constructor(name: String, dslServices: DslServices) :
    BaseFlavor(name, dslServices),
    com.android.build.api.dsl.ProductFlavor<AnnotationProcessorOptions> {

    private val _isDefaultProperty =
        dslServices.objectFactory.property(Boolean::class.java).convention(false)

    /** Whether this product flavor should be selected in Studio by default  */
    override var isDefault: Boolean
        get() = _isDefaultProperty.get()
        set(isDefault) = _isDefaultProperty.set(isDefault)

    private var _matchingFallbacks: ImmutableList<String> = ImmutableList.of()

    /**
     * Specifies a sorted list of product flavors that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     * Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, when you build a "freeDebug" version of your app, the
     * plugin tries to match it with "freeDebug" versions of the local library modules the app
     * depends on.
     *
     * However, there may be situations in which, for a given flavor dimension that exists in
     * both the app and its library dependencies, **your app includes flavors that a dependency
     * does not**. For example, consider if both your app and its library dependencies include a
     * "tier" flavor dimension. However, the "tier" dimension in the app includes "free" and "paid"
     * flavors, but one of its dependencies includes only "demo" and "paid" flavors for the same
     * dimension. When the plugin tries to build the "free" version of your app, it won't know which
     * version of the dependency to use, and you'll see an error message similar to the following:
     *
     * ```
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     * project :app
     * ```
     *
     * In this situation, you should use `matchingFallbacks` to specify alternative
     * matches for the app's "free" product flavor, as shown below:
     *
     * ```
     * // In the app's build.gradle file.
     * android {
     *     flavorDimensions 'tier'
     *     productFlavors {
     *         paid {
     *             dimension 'tier'
     *             // Because the dependency already includes a "paid" flavor in its
     *             // "tier" dimension, you don't need to provide a list of fallbacks
     *             // for the "paid" flavor.
     *         }
     *         free {
     *             dimension 'tier'
     *             // Specifies a sorted list of fallback flavors that the plugin
     *             // should try to use when a dependency's matching dimension does
     *             // not include a "free" flavor. You may specify as many
     *             // fallbacks as you like, and the plugin selects the first flavor
     *             // that's available in the dependency's "tier" dimension.
     *             matchingFallbacks = ['demo', 'trial']
     *         }
     *     }
     * }
     * ```
     *
     * Note that, for a given flavor dimension that exists in both the app and its library
     * dependencies, there is no issue when a library includes a product flavor that your app does
     * not. That's because the plugin simply never requests that flavor from the dependency.
     *
     * If instead you are trying to resolve an issue in which **a library dependency includes a
     * flavor dimension that your app does not**, use [missingDimensionStrategy].
     */
    var matchingFallbacks: List<String>
        get() = _matchingFallbacks
        set(value) {
            _matchingFallbacks = ImmutableList.copyOf(value)
        }

    fun setMatchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks =
            ImmutableList.copyOf(fallbacks)
    }

    fun setMatchingFallbacks(fallback: String) {
        matchingFallbacks = ImmutableList.of(fallback)
    }

    fun setIsDefault(isDefault: Boolean) {
        this.isDefault = isDefault
    }

    fun getIsDefault(): Property<Boolean> {
        return this._isDefaultProperty
    }

    override fun computeRequestedAndFallBacks(requestedValues: List<String>): DimensionRequest { // in order to have different fallbacks per variant for missing dimensions, we are
        // going to actually have the flavor request itself (in the other dimension), with
        // a modified name (in order to not have collision in case 2 dimensions have the same
        // flavor names). So we will always fail to find the actual request and try for
        // the fallbacks.
        return DimensionRequest(
            VariantManager.getModifiedName(name),
            ImmutableList.copyOf(requestedValues)
        )
    }

    override fun _initWith(that: BaseConfig) { // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (this === that) {
            return
        }
        super._initWith(that)
        if (that is ProductFlavor) {
            matchingFallbacks =
                that.matchingFallbacks
        }
    }
}
