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

package com.android.build.api.dsl

import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantProperties
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer

/**
 * Common extension properties for the Android Application. Library and Dynamic Feature Plugins.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
@Incubating
interface CommonExtension<
        BuildTypeT : BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : ProductFlavor,
        SigningConfigT : SigningConfig,
        VariantT : Variant<VariantPropertiesT>,
        VariantPropertiesT : VariantProperties> {
    // TODO(b/140406102)

    /**
     * Encapsulates all build type configurations for this project.
     *
     * Unlike using [ProductFlavor] to create
     * different versions of your project that you expect to co-exist on a single device, build
     * types determine how Gradle builds and packages each version of your project. Developers
     * typically use them to configure projects for various stages of a development lifecycle. For
     * example, when creating a new project from Android Studio, the Android plugin configures a
     * 'debug' and 'release' build type for you. By default, the 'debug' build type enables
     * debugging options and signs your APK with a generic debug keystore. Conversely, The 'release'
     * build type strips out debug symbols and requires you to
     * [create a release key and keystore](https://developer.android.com/studio/publish/app-signing.html#sign-apk)
     * for your app. You can then combine build types with product flavors to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     *
     * @see [BuildType
     */
    val buildTypes: NamedDomainObjectContainer<BuildTypeT>

    /**
     * Encapsulates all build type configurations for this project.
     *
     * For more information about the properties you can configure in this block, see [BuildType]
     */
    fun buildTypes(action: Action<in NamedDomainObjectContainer<BuildTypeT>>)

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     *
     * Product flavors represent different versions of your project that you expect to co-exist
     * on a single device, the Google Play store, or repository. For example, you can configure
     * 'demo' and 'full' product flavors for your app, and each of those flavors can specify
     * different features, device requirements, resources, and application ID's--while sharing
     * common source code and resources. So, product flavors allow you to output different versions
     * of your project by simply changing only the components and settings that are different
     * between them.
     *
     *
     * Configuring product flavors is similar to
     * [configuring build types](https://developer.android.com/studio/build/build-variants.html#build-types):
     * add them to the `productFlavors` block of your project's `build.gradle` file
     * and configure the settings you want.
     * Product flavors support the same properties as the `defaultConfig`
     * block--this is because `defaultConfig` defines an object that the plugin uses as the base
     * configuration for all other flavors. Each flavor you configure can then override any of the
     * default values in `defaultConfig`, such as the
     * [`applicationId`](https://d.android.com/studio/build/application-id.html).
     *
     *
     * When using Android plugin 3.0.0 and higher, *each flavor must belong to a
     * [`flavorDimension`](com.android.build.gradle.BaseExtension.html#com.android.build.gradle.BaseExtension:flavorDimensions(java.lang.String[]))
     * value*. By default, when you specify only one
     * dimension, all flavors you configure belong to that dimension. If you specify more than one
     * flavor dimension, you need to manually assign each flavor to a dimension. To learn more, read
     * [Use Flavor Dimensions for variant-aware dependency management](https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html#variant_aware).
     *
     *
     * When you configure product flavors, the Android plugin automatically combines them with
     * your [BuildType] configurations to
     * [create build variants](https://developer.android.com/studio/build/build-variants.html).
     * If the plugin creates certain build variants that you don't want, you can
     * [filter variants](https://developer.android.com/studio/build/build-variants.html#filter-variants).
     *
     * @see [ProductFlavor]
     */
    val productFlavors: NamedDomainObjectContainer<ProductFlavorT>

    /**
     * Encapsulates all product flavors configurations for this project.
     *
     * For more information about the properties you can configure in this block,
     * see [ProductFlavor]
     */
    fun productFlavors(action: Action<NamedDomainObjectContainer<ProductFlavorT>>)


    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DefaultConfig].
     */
    val defaultConfig: DefaultConfigT

    /**
     * Specifies defaults for variant properties that the Android plugin applies to all build
     * variants.
     *
     * You can override any `defaultConfig` property when
     * [configuring product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     *
     * For more information about the properties you can configure in this block, see [DefaultConfig].
     */
    fun defaultConfig(action: Action<DefaultConfigT>)


    /**
     * Encapsulates signing configurations that you can apply to [ ] and [ ] configurations.
     *
     *
     * Android requires that all APKs be digitally signed with a certificate before they can be
     * installed onto a device. When deploying a debug version of your project from Android Studio,
     * the Android plugin automatically signs your APK with a generic debug certificate. However, to
     * build an APK for release, you must
     * [sign the APK](https://developer.android.com/studio/publish/app-signing.html)
     * with a release key and keystore.
     * You can do this by either
     * [using the Android Studio UI](https://developer.android.com/studio/publish/app-signing.html#sign-apk)
     * or manually
     * [configuring your `build.gradle` file](https://developer.android.com/studio/publish/app-signing.html#gradle-sign).
     *
     * @see [SigningConfig]
     */
    val signingConfigs: NamedDomainObjectContainer<SigningConfigT>

    /**
     * Encapsulates signing configurations that you can apply to
     * [BuildType] and [ProductFlavor] configurations.
     *
     * For more information about the properties you can configure in this block,
     * see [SigningConfig].
     */
    fun signingConfigs(action: Action<NamedDomainObjectContainer<SigningConfigT>>)

    /**
     * Adds a [Action] to be performed on all [Variant] objects associated with this module.
     */
    fun onVariants(action: Action<VariantT>)

    /**
     * Adds a lambda function to be performed on all [Variant] objects associated with his module
     */
    fun onVariants(action: VariantT.() -> Unit)

    /**
     * Creates a filter on [Variant] objects for this module. The filter will reduce the set of
     * applicable variants to run an action on.
     *
     * @return a [GenericVariantFilterBuilder] of [VariantT]
     */
    fun onVariants(): GenericVariantFilterBuilder<VariantT>

    /**
     * Registers an [Action] to be executed on each [VariantProperties] of the project.
     * This method is a shortcut for calling [onVariants] followed by [Variant.onProperties].
     *
     * @param action an [Action] taking a [VariantProperties] as a parameter.
     */
    fun onVariantProperties(action: Action<VariantPropertiesT>)

    /**
     * Adds a lambda function to be performed on all [VariantProperties] objects associated
     * with his module
     */
    fun onVariantProperties(action: VariantPropertiesT.() -> Unit)

    /**
     * Creates a filter on [VariantProperties] objects for this module. The filter will reduce the
     * set of application variants to run an action on.
     *
     * @areturn a [GenericVariantFilterBuilder] of [VariantPropertiesT]
     */
    fun onVariantProperties(): GenericVariantFilterBuilder<VariantPropertiesT>
}
